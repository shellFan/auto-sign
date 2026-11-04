package com.autosign.activity;

import com.autosign.entity.PlatformAccount;
import com.autosign.entity.WeiboSuperTopic;
import com.autosign.service.AccountService;
import com.autosign.service.WeiboTopicService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class DailyActivityPlanner {
    private static final LocalTime WINDOW_START = LocalTime.of(8, 0);
    private static final LocalTime WINDOW_END = LocalTime.of(22, 0);

    private final ActivityMapper mapper;
    private final AccountService accountService;
    private final WeiboTopicService topicService;
    private final Random random;

    @Autowired
    public DailyActivityPlanner(ActivityMapper mapper, AccountService accountService,
                                WeiboTopicService topicService) {
        this(mapper, accountService, topicService, new Random());
    }

    public DailyActivityPlanner(ActivityMapper mapper, AccountService accountService,
                                WeiboTopicService topicService, Random random) {
        this.mapper = mapper;
        this.accountService = accountService;
        this.topicService = topicService;
        this.random = random;
    }

    @Transactional
    public int generate(Long userId, LocalDateTime now) {
        if (!now.toLocalTime().isBefore(WINDOW_END)) {
            return 0;
        }
        LocalDate day = now.toLocalDate();
        LocalDateTime start = now.toLocalTime().isBefore(WINDOW_START) ? day.atTime(WINDOW_START) : now;
        LocalDateTime end = day.atTime(WINDOW_END);
        int createdPlans = 0;
        for (PlatformAccount account : accountService.listEnabledByPlatform(userId, "weibo")) {
            if (!eligibleAccount(account, userId) || mapper.isAccountPaused(account.getId()) > 0) {
                continue;
            }
            List<TaskDraft> missing = new ArrayList<>();
            for (WeiboSuperTopic topic : topicService.listByAccountId(account.getId())) {
                if (!eligibleTopic(topic, account)) {
                    continue;
                }
                DailyPlan stored = mapper.findPlan(day, account.getId(), topic.getId());
                if (stored == null) {
                    DailyPlan candidate = newPlan(account, topic, day, now);
                    int inserted = mapper.insertPlan(candidate);
                    createdPlans += inserted;
                    stored = mapper.findPlan(day, account.getId(), topic.getId());
                }
                if (stored == null) {
                    continue;
                }
                int expected = targetCount(stored);
                if (mapper.countTasks(stored.getId()) == expected) {
                    continue;
                }
                ActivityTemplates templates = mapper.getTemplates(stored.getUserId(), stored.getTopicId());
                if (templates == null) {
                    templates = ActivityTemplateSupport.defaults(stored.getUserId(), stored.getTopicId());
                }
                addDrafts(missing, stored, topic, templates, now);
            }
            Collections.shuffle(missing, random);
            for (int i = 0; i < missing.size(); i++) {
                TaskDraft draft = missing.get(i);
                mapper.insertTask(draft.toTask(ActivitySchedule.dueAt(start, end, i, missing.size(), random)));
            }
        }
        return createdPlans;
    }

    private boolean eligibleAccount(PlatformAccount account, Long requestedUserId) {
        return account != null && account.getId() != null && account.getUserId() != null
                && (requestedUserId == null || requestedUserId.equals(account.getUserId()))
                && "weibo".equals(account.getPlatform())
                && Integer.valueOf(1).equals(account.getEnableStatus())
                && Integer.valueOf(1).equals(account.getLoginStatus());
    }

    private boolean eligibleTopic(WeiboSuperTopic topic, PlatformAccount account) {
        return topic != null && topic.getId() != null
                && account.getId().equals(topic.getAccountId())
                && account.getUserId().equals(topic.getUserId())
                && Integer.valueOf(1).equals(topic.getEnableStatus());
    }

    private DailyPlan newPlan(PlatformAccount account, WeiboSuperTopic topic,
                              LocalDate day, LocalDateTime now) {
        DailyPlan plan = new DailyPlan();
        plan.setId(IdWorker.getId());
        plan.setUserId(account.getUserId());
        plan.setAccountId(account.getId());
        plan.setTopicId(topic.getId());
        plan.setTaskDate(day);
        plan.setAccountName(account.getAccountName());
        plan.setTopicName(topic.getTopicName());
        plan.setSignTarget(1);
        plan.setPostTarget(1);
        plan.setForwardTarget(inclusive(2, 4));
        plan.setReplyTarget(inclusive(4, 6));
        plan.setViewTarget(inclusive(40, 50));
        plan.setCreatedAt(now);
        return plan;
    }

    private int inclusive(int minimum, int maximum) {
        return minimum + random.nextInt(maximum - minimum + 1);
    }

    private int targetCount(DailyPlan plan) {
        int total = 0;
        for (Integer count : plan.targets().values()) {
            if (count != null && count > 0) {
                total += count;
            }
        }
        return total;
    }

    private void addDrafts(List<TaskDraft> drafts, DailyPlan plan, WeiboSuperTopic topic,
                           ActivityTemplates templates, LocalDateTime now) {
        String topicName = topic.getTopicName();
        boolean signedToday = plan.getTaskDate().toString().equals(topic.getLastSignDate())
                && (Integer.valueOf(1).equals(topic.getLastSignStatus()) || Integer.valueOf(2).equals(topic.getLastSignStatus()));
        LocalDateTime signedAt = topic.getUpdateTime();
        if (signedAt == null || !signedAt.toLocalDate().equals(plan.getTaskDate()) || signedAt.isAfter(now)) signedAt = now;
        for (Map.Entry<String, Integer> entry : plan.targets().entrySet()) {
            int count = entry.getValue() == null ? 0 : entry.getValue();
            for (int ordinal = 1; ordinal <= count; ordinal++) {
                String content = null;
                if (ActivityTask.ACTION_POST.equals(entry.getKey())) {
                    content = ActivityTemplateSupport.render(templates.getPostTemplates(), topicName, random);
                } else if (ActivityTask.ACTION_REPLY.equals(entry.getKey())) {
                    content = ActivityTemplateSupport.render(templates.getReplyTemplates(), topicName, random);
                }
                drafts.add(new TaskDraft(plan, entry.getKey(), ordinal, content,
                        signedToday && ActivityTask.ACTION_SIGN.equals(entry.getKey()) ? signedAt : null));
            }
        }
    }

    private static class TaskDraft {
        private final DailyPlan plan;
        private final String action;
        private final int ordinal;
        private final String content;
        private final LocalDateTime importedSignAt;

        TaskDraft(DailyPlan plan, String action, int ordinal, String content, LocalDateTime importedSignAt) {
            this.plan = plan;
            this.action = action;
            this.ordinal = ordinal;
            this.content = content;
            this.importedSignAt = importedSignAt;
        }

        ActivityTask toTask(LocalDateTime dueAt) {
            ActivityTask task = new ActivityTask();
            task.setId(IdWorker.getId());
            task.setPlanId(plan.getId());
            task.setUserId(plan.getUserId());
            task.setAccountId(plan.getAccountId());
            task.setTopicId(plan.getTopicId());
            task.setTaskDate(plan.getTaskDate());
            task.setActionType(action);
            task.setOrdinal(ordinal);
            task.setDueAt(dueAt);
            task.setStatus(ActivityTask.PENDING);
            task.setContent(content);
            task.setAttempts(0);
            if (importedSignAt != null) {
                task.setStatus(ActivityTask.SUCCESS);
                task.setFinishedAt(importedSignAt);
                task.setMessage("沿用本日已确认的超话签到记录，无需重复签到");
            }
            return task;
        }
    }
}
