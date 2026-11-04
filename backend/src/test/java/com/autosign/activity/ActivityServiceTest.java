package com.autosign.activity;

import com.autosign.adapter.WeiboActivityAdapter;
import com.autosign.adapter.WeiboActivityResult;
import com.autosign.entity.PlatformAccount;
import com.autosign.entity.WeiboSuperTopic;
import com.autosign.service.AccountService;
import com.autosign.service.SignRecordService;
import com.autosign.service.WeiboTopicService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ActivityServiceTest {
    private final ActivityMapper mapper = mock(ActivityMapper.class);
    private final DailyActivityPlanner planner = mock(DailyActivityPlanner.class);
    private final WeiboActivityAdapter adapter = mock(WeiboActivityAdapter.class);
    private final AccountService accounts = mock(AccountService.class);
    private final WeiboTopicService topics = mock(WeiboTopicService.class);
    private final SignRecordService records = mock(SignRecordService.class);
    private final ActivityWorkerLock lock = mock(ActivityWorkerLock.class);
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 22, 12, 0);
    private ActivityService service;
    private ActivityTask task;
    private PlatformAccount account;
    private WeiboSuperTopic topic;

    @BeforeEach void setup() {
        Clock clock = Clock.fixed(now.atZone(ZoneId.of("Asia/Shanghai")).toInstant(), ZoneId.of("Asia/Shanghai"));
        service = new ActivityService(mapper, planner, adapter, accounts, topics, records, lock, clock);
        task = new ActivityTask();
        task.setId(10L); task.setPlanId(20L); task.setUserId(1L); task.setAccountId(2L);
        task.setTopicId(3L); task.setTaskDate(now.toLocalDate()); task.setActionType("reply");
        task.setStatus("PENDING"); task.setAttempts(1); task.setContent("支持{topic}");
        account = new PlatformAccount(); account.setId(2L); account.setUserId(1L);
        account.setPlatform("weibo"); account.setEnableStatus(1); account.setLoginStatus(1);
        topic = new WeiboSuperTopic(); topic.setId(3L); topic.setUserId(1L);
        topic.setAccountId(2L); topic.setEnableStatus(1); topic.setTopicName("测试超话");
        when(accounts.getById(2L)).thenReturn(account);
        when(topics.getById(3L)).thenReturn(topic);
        when(mapper.findDue(now.toLocalDate(), now)).thenReturn(task);
        when(mapper.findTask(10L)).thenReturn(task);
        when(mapper.claim(10L, now)).thenReturn(1);
        when(mapper.finish(anyLong(), anyString(), anyString(), any())).thenReturn(1);
        when(mapper.excludedPostIds(task)).thenReturn(Collections.emptyList());
        when(mapper.countPending(now.toLocalDate())).thenReturn(50);
        when(lock.runLocked(any())).thenAnswer(invocation -> {
            invocation.<Runnable>getArgument(0).run(); return true;
        });
    }

    @Test void atomicClaimRejectionNeverOpensBrowser() {
        when(mapper.claim(10L, now)).thenReturn(0);
        service.runTick(now);
        verifyNoInteractions(adapter);
    }

    @Test void uncertainSubmissionNeverRetriesOrReportsSuccess() {
        when(adapter.execute(any(), any(), anyString(), anyString(), any(), anySet(), any()))
                .thenReturn(WeiboActivityResult.of(WeiboActivityResult.Status.UNKNOWN, "提交后无法确认", "abc"));
        service.runTick(now);
        verify(mapper).finish(10L, "UNKNOWN", "提交后无法确认", now);
        verify(mapper, never()).retry(anyLong(), any(), anyString());
        verifyNoInteractions(records);
    }

    @Test void retryKeepsReservedPostAndWaitsFiveMinutes() {
        task.setPostId("abc");
        when(adapter.execute(any(), any(), anyString(), anyString(), eq("abc"), anySet(), any()))
                .thenReturn(WeiboActivityResult.of(WeiboActivityResult.Status.FAILED, "提交按钮尚未出现", "abc"));
        service.runTick(now);
        verify(mapper).retry(10L, now.plusMinutes(5), "提交按钮尚未出现");
        verify(mapper, never()).reserve(anyLong(), isNull());
    }

    @Test void thirdSafeFailureIsTerminal() {
        task.setAttempts(3);
        when(adapter.execute(any(), any(), anyString(), anyString(), any(), anySet(), any()))
                .thenReturn(WeiboActivityResult.of(WeiboActivityResult.Status.FAILED, "目标不存在", null));
        service.runTick(now);
        verify(mapper).finish(10L, "FAILED", "目标不存在", now);
        verify(mapper, never()).retry(anyLong(), any(), anyString());
    }

    @Test void captchaPausesCurrentAndRemainingAccountWorkDurably() {
        when(adapter.execute(any(), any(), anyString(), anyString(), any(), anySet(), any()))
                .thenReturn(WeiboActivityResult.of(WeiboActivityResult.Status.NEED_VERIFICATION, "需要人工验证", null));
        service.runTick(now);
        verify(mapper).pauseAccount(1L, 2L, "需要人工验证", now);
        verify(mapper).pauseTasks(2L, now.toLocalDate(), "需要人工验证");
        verify(mapper).finish(10L, "PAUSED", "需要人工验证", now);
        verify(accounts).updateLoginStatus(2L, 3);
    }

    @Test void captchaAfterSubmissionKeepsCurrentUnknownAndPausesOthers() {
        when(adapter.execute(any(), any(), anyString(), anyString(), any(), anySet(), any()))
                .thenReturn(WeiboActivityResult.of(WeiboActivityResult.Status.UNKNOWN, "提交后需要人工验证，结果待核实", "abc"));
        service.runTick(now);
        verify(mapper).finish(10L, "UNKNOWN", "提交后需要人工验证，结果待核实", now);
        verify(mapper).pauseTasks(2L, now.toLocalDate(), "提交后需要人工验证，结果待核实");
        verify(mapper, never()).retry(anyLong(), any(), anyString());
    }

    @Test void pauseBetweenClaimAndEffectPreventsSending() {
        when(mapper.isAccountPaused(2L)).thenReturn(0, 1);
        service.runTick(now);
        verify(mapper).claim(10L, now);
        verifyNoInteractions(adapter);
        verify(mapper).finish(eq(10L), eq("PAUSED"), anyString(), eq(now));
    }

    @Test void disabledTopicExpiresInsteadOfSending() {
        topic.setEnableStatus(0);
        service.runTick(now);
        verify(mapper).expireTask(eq(10L), anyString(), eq(now));
        verifyNoInteractions(adapter);
    }

    @Test void foreignTopicCannotExecuteOnAccount() {
        topic.setUserId(999L);
        service.runTick(now);
        verifyNoInteractions(adapter);
        verify(mapper).expireTask(eq(10L), anyString(), eq(now));
    }

    @Test void persistedCooldownPreventsCatchupBurst() {
        when(mapper.lastActivityAt()).thenReturn(now.minusSeconds(10));
        service.runTick(now);
        verifyNoInteractions(adapter);
        verify(mapper, never()).claim(anyLong(), any());
    }

    @Test void windowEndExpiresTodayAndNeverStartsAnAction() {
        LocalDateTime end = now.withHour(22);
        service.runTick(end);
        verify(mapper).expireToday(end.toLocalDate(), end);
        verifyNoInteractions(adapter);
    }

    @Test void startupRecoversOldRunningWithoutReplayingIt() {
        service.runTick(now.withHour(7));
        verify(mapper).recoverRunning(now.withHour(7).minusMinutes(10), now.withHour(7));
        verify(mapper).expireBefore(now.toLocalDate(), now.withHour(7));
        verifyNoInteractions(adapter);
    }

    @Test void failedReservationPreventsBrowserEffect() {
        when(mapper.reserve(10L, "abc")).thenReturn(0);
        when(adapter.execute(any(), any(), anyString(), anyString(), any(), anySet(), any()))
                .thenAnswer(invocation -> {
                    invocation.<java.util.function.Consumer<String>>getArgument(6).accept("abc");
                    fail("must stop before the browser submission"); return null;
                });
        service.runTick(now);
        verify(mapper).finish(eq(10L), eq("UNKNOWN"), anyString(), eq(now));
    }

    @Test void templateOwnershipIsCheckedBeforeSaving() {
        ActivityTemplates templates = new ActivityTemplates();
        templates.setPostTemplates("支持{topic}"); templates.setReplyTemplates("加油{topic}");
        assertThrows(IllegalArgumentException.class, () -> service.saveTemplates(99L, 3L, templates));
        verify(mapper, never()).saveTemplates(any());
    }

    @Test void resumingLoggedOutAccountCreatesMissingPlanBeforeUnpausing() {
        account.setLoginStatus(2);
        when(adapter.checkLogin(account)).thenReturn(WeiboActivityResult.of(WeiboActivityResult.Status.SUCCESS, "已登录", null));
        task.setStatus("PAUSED");
        when(mapper.listTasks(1L, now.toLocalDate())).thenReturn(Collections.singletonList(task));
        service.resume(1L, 2L);
        InOrder order = inOrder(adapter, accounts, mapper, planner);
        order.verify(adapter).checkLogin(account);
        order.verify(accounts).updateLoginStatus(2L, 1);
        order.verify(mapper).clearAccountPause(1L, 2L);
        order.verify(planner).generate(1L, now);
        order.verify(mapper).listTasks(1L, now.toLocalDate());
        verify(mapper).reschedule(eq(10L), any());
    }

    @Test void foreignAccountCannotBeResumed() {
        assertThrows(IllegalArgumentException.class, () -> service.resume(99L, 2L));
        verifyNoInteractions(adapter);
    }

    @Test void dashboardCountsOnlySuccessAndNamesTheActuallyScheduledNextAction() {
        DailyPlan plan = new DailyPlan();
        plan.setId(20L); plan.setUserId(1L); plan.setAccountId(2L); plan.setTopicId(3L);
        plan.setTaskDate(now.toLocalDate()); plan.setSignTarget(1); plan.setPostTarget(1);
        plan.setForwardTarget(2); plan.setReplyTarget(4); plan.setViewTarget(40);
        task.setStatus("UNKNOWN"); task.setDueAt(now.minusMinutes(1));
        ActivityTask next = new ActivityTask();
        next.setId(11L); next.setPlanId(20L); next.setStatus("PENDING");
        next.setActionType("view"); next.setDueAt(now.plusMinutes(1));
        when(mapper.listPlans(1L, now.toLocalDate())).thenReturn(Collections.singletonList(plan));
        when(mapper.listTasks(1L, now.toLocalDate())).thenReturn(Arrays.asList(task, next));
        Map<String, Object> row = service.today(1L).get(0);
        assertEquals("view", row.get("nextActionType"));
        assertEquals(now.plusMinutes(1), row.get("nextRunAt"));
        assertEquals(0, ((Map<?, ?>) row.get("completed")).get("reply"));
        assertEquals(1, ((Map<?, ?>) row.get("states")).get("UNKNOWN"));
    }

    @Test void yesterdayPausedAccountIsVisibleForRecoveryEvenWithoutTodaysPlan() {
        account.setLoginStatus(2);
        when(accounts.listByUserId(1L)).thenReturn(Collections.singletonList(account));
        when(mapper.listPlans(1L, now.toLocalDate())).thenReturn(Collections.emptyList());
        when(mapper.isAccountPaused(2L)).thenReturn(1);
        Map<String, Object> row = service.accountStates(1L).get(0);
        assertEquals(2L, row.get("accountId"));
        assertEquals(true, row.get("paused"));
        assertEquals(false, row.get("hasPlan"));
    }

    @Test void manualEnsureOnlyPlansForTheAuthenticatedUser() {
        service.ensureToday(1L);
        verify(planner).generate(1L, now);
        verifyNoInteractions(adapter);
        assertThrows(IllegalArgumentException.class, () -> service.ensureToday(null));
    }
}
