package com.autosign.activity;

import com.autosign.entity.PlatformAccount;
import com.autosign.entity.WeiboSuperTopic;
import com.autosign.service.AccountService;
import com.autosign.service.WeiboTopicService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityPlannerTest {

    @Test
    void upgradingAfterConfirmedSigninDoesNotQueueAnotherSigninToday() {
        Fixture fixture = fixture(new EndpointRandom(false), 2);
        fixture.topics.get(0).setLastSignDate("2026-10-22");
        fixture.topics.get(0).setLastSignStatus(1);
        fixture.topics.get(0).setUpdateTime(LocalDateTime.of(2026, 10, 22, 8, 21));
        fixture.topics.get(1).setLastSignDate("2026-10-21");
        fixture.topics.get(1).setLastSignStatus(1);
        fixture.planner.generate(7L, LocalDateTime.of(2026, 10, 22, 10, 0));
        assertThat(fixture.tasks).filteredOn(task -> "sign".equals(task.getActionType()) && task.getTopicId().equals(101L))
                .singleElement().satisfies(task -> {
                    assertThat(task.getStatus()).isEqualTo("SUCCESS");
                    assertThat(task.getFinishedAt()).isEqualTo(LocalDateTime.of(2026, 10, 22, 8, 21));
                });
        assertThat(fixture.tasks).filteredOn(task -> "sign".equals(task.getActionType()) && task.getTopicId().equals(102L))
                .singleElement().satisfies(task -> assertThat(task.getStatus()).isEqualTo("PENDING"));
    }

    @Test
    void scheduleIncludesWindowEndpointsAndCooldownIsCapacityAware() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 22, 8, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 22, 22, 0);

        assertThat(ActivitySchedule.dueAt(start, end, 0, 1, new EndpointRandom(false))).isEqualTo(start);
        assertThat(ActivitySchedule.dueAt(start, end, 0, 1, new EndpointRandom(true)))
                .isEqualTo(end.minusSeconds(1));
        assertThat(ActivitySchedule.cooldownSeconds(0, 60)).isEqualTo(60);
        assertThat(ActivitySchedule.cooldownSeconds(100, 100)).isEqualTo(5);
        assertThat(ActivitySchedule.cooldownSeconds(2, 600)).isEqualTo(60);
    }

    @Test
    void templatesUseTopicPlaceholderAndRejectBlankAlternatives() {
        ActivityTemplates defaults = ActivityTemplateSupport.defaults(8L, 9L);

        assertThat(defaults.getUserId()).isEqualTo(8L);
        assertThat(defaults.getTopicId()).isEqualTo(9L);
        assertThat(ActivityTemplateSupport.render(defaults.getPostTemplates(), "航天", new EndpointRandom(false)))
                .contains("航天");
        assertThat(ActivityTemplateSupport.render("第一条 {topic}\n第二条 {topic}", "电影", new EndpointRandom(true)))
                .isEqualTo("第二条 电影");
        assertThat(ActivityTemplateSupport.validate(" \n\t ")).isFalse();
        assertThat(ActivityTemplateSupport.validate("支持 {topic}")).isTrue();
    }

    @Test
    void generatesInclusiveQuotasForEveryOwnedEnabledTopicIncludingSixth() {
        Fixture fixture = fixture(new EndpointRandom(true), 6);

        int created = fixture.planner.generate(7L, LocalDateTime.of(2026, 10, 22, 7, 30));

        assertThat(created).isEqualTo(6);
        assertThat(fixture.plans).hasSize(6);
        assertThat(fixture.plans).allSatisfy(plan -> {
            assertThat(plan.getSignTarget()).isEqualTo(1);
            assertThat(plan.getPostTarget()).isEqualTo(1);
            assertThat(plan.getForwardTarget()).isEqualTo(4);
            assertThat(plan.getReplyTarget()).isEqualTo(6);
            assertThat(plan.getViewTarget()).isEqualTo(50);
        });
        assertThat(fixture.tasks).hasSize(6 * (1 + 1 + 4 + 6 + 50));
        assertThat(fixture.tasks).extracting(ActivityTask::getTopicId).contains(106L);
        assertThat(fixture.tasks).allSatisfy(task -> {
            assertThat(task.getDueAt()).isBetween(
                    LocalDateTime.of(2026, 10, 22, 8, 0),
                    LocalDateTime.of(2026, 10, 22, 21, 59, 59));
            assertThat(task.getStatus()).isEqualTo("PENDING");
        });
    }

    @Test
    void repeatGenerationUsesStoredTargetsAndOnlyRepairsMissingRows() {
        EndpointRandom random = new EndpointRandom(false);
        Fixture fixture = fixture(random, 1);
        LocalDateTime now = LocalDateTime.of(2026, 10, 22, 10, 0);

        assertThat(fixture.planner.generate(7L, now)).isEqualTo(1);
        int firstSize = fixture.tasks.size();
        int firstRandomCalls = random.calls;
        assertThat(fixture.planner.generate(7L, now.plusMinutes(1))).isZero();

        assertThat(fixture.plans).hasSize(1);
        assertThat(fixture.tasks).hasSize(firstSize);
        DailyPlan stored = fixture.plans.get(0);
        assertThat(stored.getForwardTarget()).isEqualTo(2);
        assertThat(stored.getReplyTarget()).isEqualTo(4);
        assertThat(stored.getViewTarget()).isEqualTo(40);
        assertThat(random.calls).isEqualTo(firstRandomCalls);
    }

    @Test
    void repairsAPartialPlanToItsStoredTargetWithoutAddingQuota() {
        Fixture fixture = fixture(new EndpointRandom(false), 1);
        LocalDateTime now = LocalDateTime.of(2026, 10, 22, 10, 0);
        fixture.planner.generate(7L, now);
        DailyPlan stored = fixture.plans.get(0);
        int expected = stored.targets().values().stream().mapToInt(Integer::intValue).sum();
        fixture.tasks.remove(fixture.tasks.size() - 1);

        assertThat(fixture.planner.generate(7L, now.plusMinutes(1))).isZero();

        assertThat(fixture.plans).containsExactly(stored);
        assertThat(fixture.tasks).hasSize(expected);
        assertThat(stored.targets()).containsEntry("view", 40).containsEntry("reply", 4)
                .containsEntry("forward", 2);
    }

    @Test
    void filtersPausedAndForeignTopicsAndDoesNotCreateAtOrAfterCutoff() {
        ActivityMapper mapper = mock(ActivityMapper.class);
        AccountService accounts = mock(AccountService.class);
        WeiboTopicService topics = mock(WeiboTopicService.class);
        PlatformAccount paused = account(11L, 7L);
        PlatformAccount active = account(12L, 7L);
        when(accounts.listEnabledByPlatform(7L, "weibo")).thenReturn(Arrays.asList(paused, active));
        when(mapper.isAccountPaused(11L)).thenReturn(1);
        when(mapper.isAccountPaused(12L)).thenReturn(0);
        when(topics.listByAccountId(12L)).thenReturn(Arrays.asList(
                topic(1L, 7L, 12L, 1),
                topic(2L, 99L, 12L, 1),
                topic(3L, 7L, 999L, 1),
                topic(4L, 7L, 12L, 0)));
        when(mapper.insertPlan(any())).thenAnswer(invocation -> {
            DailyPlan plan = invocation.getArgument(0);
            return plan.getTopicId().equals(1L) ? 1 : 0;
        });
        AtomicInteger planLookups = new AtomicInteger();
        when(mapper.findPlan(any(), anyLong(), anyLong())).thenAnswer(invocation -> {
            if (planLookups.getAndIncrement() == 0) {
                return null;
            }
            DailyPlan p = new DailyPlan();
            p.setId(44L);
            p.setUserId(7L);
            p.setAccountId(12L);
            p.setTopicId(1L);
            p.setTaskDate(LocalDate.of(2026, 10, 22));
            p.setSignTarget(1);
            p.setPostTarget(1);
            p.setForwardTarget(2);
            p.setReplyTarget(4);
            p.setViewTarget(40);
            return p;
        });
        when(mapper.countTasks(44L)).thenReturn(0);
        when(mapper.getTemplates(7L, 1L)).thenReturn(null);
        when(mapper.insertTask(any())).thenReturn(1);
        DailyActivityPlanner planner = new DailyActivityPlanner(mapper, accounts, topics, new EndpointRandom(false));

        assertThat(planner.generate(7L, LocalDateTime.of(2026, 10, 22, 10, 0))).isEqualTo(1);
        assertThat(planner.generate(7L, LocalDateTime.of(2026, 10, 22, 22, 0))).isZero();
        verify(topics, never()).listByAccountId(11L);
    }

    private static Fixture fixture(Random random, int topicCount) {
        ActivityMapper mapper = mock(ActivityMapper.class);
        AccountService accounts = mock(AccountService.class);
        WeiboTopicService topics = mock(WeiboTopicService.class);
        PlatformAccount account = account(11L, 7L);
        List<WeiboSuperTopic> topicList = new ArrayList<>();
        for (int i = 1; i <= topicCount; i++) {
            topicList.add(topic(100L + i, 7L, 11L, 1));
        }
        List<DailyPlan> plans = new ArrayList<>();
        List<ActivityTask> tasks = new ArrayList<>();
        Map<String, DailyPlan> byKey = new HashMap<>();
        when(accounts.listEnabledByPlatform(7L, "weibo")).thenReturn(Collections.singletonList(account));
        when(topics.listByAccountId(11L)).thenReturn(topicList);
        when(mapper.isAccountPaused(11L)).thenReturn(0);
        when(mapper.insertPlan(any())).thenAnswer(invocation -> {
            DailyPlan plan = invocation.getArgument(0);
            String key = plan.getTaskDate() + ":" + plan.getAccountId() + ":" + plan.getTopicId();
            if (byKey.containsKey(key)) {
                return 0;
            }
            byKey.put(key, plan);
            plans.add(plan);
            return 1;
        });
        when(mapper.findPlan(any(), anyLong(), anyLong())).thenAnswer(invocation ->
                byKey.get(invocation.getArgument(0) + ":" + invocation.getArgument(1) + ":" + invocation.getArgument(2)));
        when(mapper.countTasks(anyLong())).thenAnswer(invocation -> {
            Long planId = invocation.getArgument(0);
            return (int) tasks.stream().filter(task -> planId.equals(task.getPlanId())).count();
        });
        when(mapper.getTemplates(anyLong(), anyLong())).thenReturn(null);
        when(mapper.insertTask(any())).thenAnswer(invocation -> {
            ActivityTask task = invocation.getArgument(0);
            boolean exists = tasks.stream().anyMatch(existing -> existing.getPlanId().equals(task.getPlanId())
                    && existing.getActionType().equals(task.getActionType())
                    && existing.getOrdinal().equals(task.getOrdinal()));
            if (exists) return 0;
            tasks.add(task);
            return 1;
        });
        return new Fixture(new DailyActivityPlanner(mapper, accounts, topics, random), plans, tasks, topicList);
    }

    private static PlatformAccount account(Long id, Long userId) {
        PlatformAccount account = new PlatformAccount();
        account.setId(id);
        account.setUserId(userId);
        account.setAccountName("账号" + id);
        account.setPlatform("weibo");
        account.setEnableStatus(1);
        account.setLoginStatus(1);
        return account;
    }

    private static WeiboSuperTopic topic(Long id, Long userId, Long accountId, int enabled) {
        WeiboSuperTopic topic = new WeiboSuperTopic();
        topic.setId(id);
        topic.setUserId(userId);
        topic.setAccountId(accountId);
        topic.setTopicName("超话" + id);
        topic.setEnableStatus(enabled);
        return topic;
    }

    private static class Fixture {
        final DailyActivityPlanner planner;
        final List<DailyPlan> plans;
        final List<ActivityTask> tasks;
        final List<WeiboSuperTopic> topics;

        Fixture(DailyActivityPlanner planner, List<DailyPlan> plans, List<ActivityTask> tasks, List<WeiboSuperTopic> topics) {
            this.planner = planner;
            this.plans = plans;
            this.tasks = tasks;
            this.topics = topics;
        }
    }

    private static class EndpointRandom extends Random {
        private final boolean maximum;
        private int calls;

        EndpointRandom(boolean maximum) {
            this.maximum = maximum;
        }

        @Override
        public int nextInt(int bound) {
            calls++;
            return maximum ? bound - 1 : 0;
        }

        @Override
        public long nextLong() {
            calls++;
            return maximum ? -1L : 0L;
        }

        @Override
        public double nextDouble() {
            calls++;
            return maximum ? Math.nextDown(1.0d) : 0.0d;
        }
    }
}
