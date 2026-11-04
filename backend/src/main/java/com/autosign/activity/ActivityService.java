package com.autosign.activity;

import com.autosign.adapter.WeiboActivityAdapter;
import com.autosign.adapter.WeiboActivityResult;
import com.autosign.entity.PlatformAccount;
import com.autosign.entity.SignRecord;
import com.autosign.entity.WeiboSuperTopic;
import com.autosign.service.AccountService;
import com.autosign.service.SignRecordService;
import com.autosign.service.WeiboTopicService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/** Executes bounded actions; schedule waits live in the database, never in a sleeping thread. */
@Service
public class ActivityService {
    private static final Logger log = LoggerFactory.getLogger(ActivityService.class);
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final List<String> ACTIONS = Arrays.asList("sign", "post", "reply", "forward", "view");
    private final ActivityMapper mapper;
    private final DailyActivityPlanner planner;
    private final WeiboActivityAdapter adapter;
    private final AccountService accounts;
    private final WeiboTopicService topics;
    private final SignRecordService records;
    private final ActivityWorkerLock lock;
    private final Clock clock;
    private final Random random = new Random();

    @Autowired
    public ActivityService(ActivityMapper mapper, DailyActivityPlanner planner, WeiboActivityAdapter adapter,
                           AccountService accounts, WeiboTopicService topics, SignRecordService records,
                           ActivityWorkerLock lock) {
        this(mapper, planner, adapter, accounts, topics, records, lock, Clock.system(ZONE));
    }

    ActivityService(ActivityMapper mapper, DailyActivityPlanner planner, WeiboActivityAdapter adapter,
                    AccountService accounts, WeiboTopicService topics, SignRecordService records,
                    ActivityWorkerLock lock, Clock clock) {
        this.mapper = mapper; this.planner = planner; this.adapter = adapter; this.accounts = accounts;
        this.topics = topics; this.records = records; this.lock = lock; this.clock = clock;
    }

    public void tick() {
        try {
            lock.runLocked(() -> runTick(now()));
        } catch (Exception e) {
            log.error("Weibo daily worker tick failed; persisted claims will not be blindly replayed", e);
        }
    }

    void runTick(LocalDateTime now) {
        LocalDate day = now.toLocalDate();
        mapper.recoverRunning(now.minusMinutes(10), now);
        mapper.expireBefore(day, now);
        if (!now.toLocalTime().isBefore(LocalTime.of(22, 0))) {
            mapper.expireToday(day, now);
            return;
        }
        planner.generate(null, now);
        if (now.toLocalTime().isBefore(LocalTime.of(8, 0))) return;
        int pending = mapper.countPending(day);
        int cooldown = ActivitySchedule.cooldownSeconds(pending,
                ChronoUnit.SECONDS.between(now, day.atTime(22, 0)));
        LocalDateTime previous = mapper.lastActivityAt();
        if (previous != null && previous.plusSeconds(cooldown).isAfter(now)) return;
        ActivityTask task = mapper.findDue(day, now);
        if (task == null) return;
        PlatformAccount account = accounts.getById(task.getAccountId());
        WeiboSuperTopic topic = topics.getById(task.getTopicId());
        if (!validOwnershipAndEnabled(task, account, topic)) {
            mapper.expireTask(task.getId(), "账号或超话已停用、删除或归属已变化", now);
            return;
        }
        if (!Integer.valueOf(1).equals(account.getLoginStatus())) {
            pause(task, "账号需要重新登录或人工验证", now, account.getLoginStatus() != null && account.getLoginStatus() == 3);
            return;
        }
        if (mapper.isAccountPaused(task.getAccountId()) > 0) {
            mapper.pauseTasks(task.getAccountId(), day, "账号已暂停，请处理登录或验证后恢复");
            return;
        }
        if (mapper.claim(task.getId(), now) != 1) return;
        // Reload the durable claim so a concurrent template save cannot leave us with stale content.
        ActivityTask claimed = mapper.findTask(task.getId());
        if (claimed == null) return; // Claim is already durable; recovery will expose UNKNOWN if DB state is lost.
        account = accounts.getById(claimed.getAccountId());
        topic = topics.getById(claimed.getTopicId());
        if (!validOwnershipAndEnabled(claimed, account, topic)) {
            mapper.finish(claimed.getId(), "EXPIRED", "执行前账号或超话已停用或归属已变化", now);
            return;
        }
        if (mapper.isAccountPaused(claimed.getAccountId()) > 0 || !Integer.valueOf(1).equals(account.getLoginStatus())) {
            pause(claimed, "账号已暂停，请处理登录或验证后恢复", now,
                    Integer.valueOf(3).equals(account.getLoginStatus()));
            mapper.finish(claimed.getId(), "PAUSED", "账号已暂停，请处理登录或验证后恢复", now);
            return;
        }
        WeiboActivityResult result;
        log.info("Start Weibo activity id={}, action={}, account={}, topic={}",
                claimed.getId(), claimed.getActionType(), claimed.getAccountId(), claimed.getTopicId());
        try {
            Set<String> excluded = new HashSet<>(mapper.excludedPostIds(claimed));
            result = adapter.execute(account, topic, claimed.getActionType(), claimed.getContent(),
                    claimed.getPostId(), excluded, postId -> {
                        if (mapper.reserve(claimed.getId(), postId) != 1)
                            throw new IllegalStateException("无法保存所选帖子，停止执行");
                    });
            if (result == null) result = WeiboActivityResult.unknown("浏览器未返回结果，待人工核实");
        } catch (Exception e) {
            log.warn("Activity {} interrupted; retaining claim as unknown", claimed.getId(), e);
            result = WeiboActivityResult.unknown("操作中断，提交结果待人工核实");
        }
        complete(claimed, topic, result, now());
    }

    private void complete(ActivityTask task, WeiboSuperTopic topic, WeiboActivityResult result, LocalDateTime ended) {
        String message = trimMessage(result.getMessage());
        WeiboActivityResult.Status status = result.getStatus();
        log.info("Weibo activity id={}, action={}, outcome={}", task.getId(), task.getActionType(), status);
        if (status == WeiboActivityResult.Status.SUCCESS) {
            if (mapper.finish(task.getId(), "SUCCESS", message, ended) == 1) recordSign(task, topic, 2, message);
        } else if (status == WeiboActivityResult.Status.FAILED) {
            LocalDateTime retryAt = ended.plusMinutes(5);
            if (task.getAttempts() < 3 && retryAt.isBefore(task.getTaskDate().atTime(22, 0))) {
                mapper.retry(task.getId(), retryAt, message);
            } else {
                mapper.finish(task.getId(), "FAILED", message, ended);
                recordSign(task, topic, 4, message);
            }
        } else if (status == WeiboActivityResult.Status.NEED_LOGIN || status == WeiboActivityResult.Status.NEED_VERIFICATION) {
            boolean verification = status == WeiboActivityResult.Status.NEED_VERIFICATION;
            pause(task, message, ended, verification);
            mapper.finish(task.getId(), "PAUSED", message, ended);
            recordSign(task, topic, verification ? 6 : 5, message);
        } else {
            mapper.finish(task.getId(), "UNKNOWN", message, ended);
            String lower = message.toLowerCase(Locale.ROOT);
            if (lower.contains("captcha") || lower.contains("需要人工验证") || lower.contains("验证码")) {
                pause(task, message, ended, true);
            } else if (lower.contains("需要重新登录") || lower.contains("need login") || lower.contains("login expired")) {
                pause(task, message, ended, false);
            }
            recordSign(task, topic, 4, message);
        }
    }

    private void pause(ActivityTask task, String message, LocalDateTime now, boolean verification) {
        mapper.pauseAccount(task.getUserId(), task.getAccountId(), message, now);
        mapper.pauseTasks(task.getAccountId(), task.getTaskDate(), message);
        accounts.updateLoginStatus(task.getAccountId(), verification ? 3 : 2);
    }

    private void recordSign(ActivityTask task, WeiboSuperTopic topic, int status, String message) {
        if (!"sign".equals(task.getActionType())) return;
        // This journal is secondary to the durable action result. A logging failure must never replay a sign.
        try {
            topics.updateSignResult(task.getTopicId(), status == 2 ? 1 : status == 6 ? 4 : 3);
            SignRecord record = records.createRecord(task.getUserId(), task.getAccountId(), task.getId(), "weibo");
            records.updateRecordResult(record.getId(), status, status == 2 ? 1 : 0, status == 4 ? 1 : 0, message);
            records.addDetail(record.getId(), "super_topic", topic.getTopicName(), topic.getTopicUrl(),
                    status == 2 ? 1 : status == 6 ? 4 : 0, message);
            if (status == 2) accounts.updateSignTime(task.getAccountId());
        } catch (Exception e) {
            log.error("Could not append sign journal for completed activity {}", task.getId(), e);
        }
    }

    public Map<String, Object> ensureToday(Long userId) {
        requireUser(userId);
        LocalDateTime now = now();
        int created = planner.generate(userId, now);
        String message = now.toLocalTime().isBefore(LocalTime.of(22, 0))
                ? "今日计划已检查，新增 " + created + " 个超话计划；将按计划分散执行，已有额度不变"
                : "今日执行窗口已结束，明日 08:00 起自动生成新计划";
        return Collections.singletonMap("message", message);
    }

    public List<Map<String, Object>> today(Long userId) {
        requireUser(userId);
        LocalDate day = now().toLocalDate();
        List<ActivityTask> tasks = mapper.listTasks(userId, day);
        Map<Long, List<ActivityTask>> grouped = tasks.stream().collect(Collectors.groupingBy(ActivityTask::getPlanId));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DailyPlan plan : mapper.listPlans(userId, day)) {
            List<ActivityTask> work = grouped.getOrDefault(plan.getId(), Collections.emptyList());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", plan.getId()); row.put("accountId", plan.getAccountId()); row.put("accountName", plan.getAccountName());
            row.put("topicId", plan.getTopicId()); row.put("topicName", plan.getTopicName()); row.put("taskDate", plan.getTaskDate());
            Map<String, Integer> targets = new LinkedHashMap<>();
            targets.put("sign", plan.getSignTarget()); targets.put("post", plan.getPostTarget());
            targets.put("reply", plan.getReplyTarget()); targets.put("forward", plan.getForwardTarget()); targets.put("view", plan.getViewTarget());
            Map<String, Integer> completed = new LinkedHashMap<>();
            ACTIONS.forEach(action -> completed.put(action, 0));
            Map<String, Integer> states = new LinkedHashMap<>();
            LocalDateTime next = null, latest = null;
            String nextActionType = null;
            String lastMessage = "";
            for (ActivityTask task : work) {
                states.merge(task.getStatus(), 1, Integer::sum);
                if ("SUCCESS".equals(task.getStatus())) completed.merge(task.getActionType(), 1, Integer::sum);
                if ("PENDING".equals(task.getStatus()) && (next == null || task.getDueAt().isBefore(next))) {
                    next = task.getDueAt(); nextActionType = task.getActionType();
                }
                LocalDateTime event = task.getFinishedAt() != null ? task.getFinishedAt() : task.getStartedAt();
                if (task.getMessage() != null && (latest == null || (event != null && !event.isBefore(latest)))) {
                    latest = event; lastMessage = task.getMessage();
                }
            }
            row.put("targets", targets); row.put("completed", completed); row.put("states", states);
            row.put("nextRunAt", next); row.put("nextActionType", nextActionType); row.put("lastMessage", lastMessage);
            row.put("paused", mapper.isAccountPaused(plan.getAccountId()) > 0 || states.getOrDefault("PAUSED", 0) > 0);
            rows.add(row);
        }
        return rows;
    }

    public List<ActivityTask> history(Long userId) { requireUser(userId); return mapper.history(userId); }

    /** Recovery remains accessible even if a paused account has no plan on the new date. */
    public List<Map<String, Object>> accountStates(Long userId) {
        requireUser(userId);
        Set<Long> planned = mapper.listPlans(userId, now().toLocalDate()).stream()
                .map(DailyPlan::getAccountId).collect(Collectors.toSet());
        List<Map<String, Object>> result = new ArrayList<>();
        for (PlatformAccount account : accounts.listByUserId(userId)) {
            if (!userId.equals(account.getUserId()) || !"weibo".equals(account.getPlatform())
                    || !Integer.valueOf(1).equals(account.getEnableStatus())) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("accountId", account.getId()); row.put("accountName", account.getAccountName());
            row.put("loginStatus", account.getLoginStatus()); row.put("paused", mapper.isAccountPaused(account.getId()) > 0);
            row.put("hasPlan", planned.contains(account.getId())); result.add(row);
        }
        return result;
    }

    public ActivityTemplates templates(Long userId, Long topicId) {
        ownedTopic(userId, topicId);
        ActivityTemplates templates = mapper.getTemplates(userId, topicId);
        return templates == null ? ActivityTemplateSupport.defaults(userId, topicId) : templates;
    }

    @Transactional
    public void saveTemplates(Long userId, Long topicId, ActivityTemplates templates) {
        WeiboSuperTopic topic = ownedTopic(userId, topicId);
        if (templates == null) throw new IllegalArgumentException("请填写文案模板");
        validateTemplate(templates.getPostTemplates()); validateTemplate(templates.getReplyTemplates());
        templates.setUserId(userId); templates.setTopicId(topicId);
        mapper.saveTemplates(templates);
        for (ActivityTask task : mapper.listTasks(userId, now().toLocalDate())) {
            if (!topicId.equals(task.getTopicId())) continue;
            if ("post".equals(task.getActionType()) || "reply".equals(task.getActionType())) {
                String source = "post".equals(task.getActionType()) ? templates.getPostTemplates() : templates.getReplyTemplates();
                mapper.updateContent(task.getId(), ActivityTemplateSupport.render(source, topic.getTopicName(), random));
            }
        }
    }

    public Map<String, Object> resume(Long userId, Long accountId) {
        PlatformAccount account = ownedAccount(userId, accountId);
        if (!Integer.valueOf(1).equals(account.getEnableStatus())) throw new IllegalArgumentException("请先启用账号");
        boolean acquired = lock.runLocked(() -> {
            WeiboActivityResult login = adapter.checkLogin(account);
            if (login.getStatus() != WeiboActivityResult.Status.SUCCESS) {
                mapper.pauseAccount(userId, accountId, trimMessage(login.getMessage()), now());
                accounts.updateLoginStatus(accountId, login.getStatus() == WeiboActivityResult.Status.NEED_VERIFICATION ? 3 : 2);
                throw new IllegalArgumentException(trimMessage(login.getMessage()));
            }
            accounts.updateLoginStatus(accountId, 1);
            mapper.clearAccountPause(userId, accountId);
            LocalDateTime now = now();
            planner.generate(userId, now);
            if (!now.isBefore(now.toLocalDate().atTime(22, 0))) return;
            List<ActivityTask> remaining = mapper.listTasks(userId, now.toLocalDate()).stream()
                    .filter(task -> accountId.equals(task.getAccountId()))
                    .filter(task -> "PENDING".equals(task.getStatus()) || "PAUSED".equals(task.getStatus()))
                    .sorted(Comparator.comparing(ActivityTask::getId)).collect(Collectors.toList());
            Collections.shuffle(remaining, random);
            LocalDateTime start = now.isBefore(now.toLocalDate().atTime(8, 0)) ? now.toLocalDate().atTime(8, 0) : now;
            for (int i = 0; i < remaining.size(); i++) {
                mapper.reschedule(remaining.get(i).getId(), ActivitySchedule.dueAt(start, now.toLocalDate().atTime(22, 0), i, remaining.size(), random));
            }
        });
        if (!acquired) throw new IllegalArgumentException("浏览器任务正在执行，请稍后恢复账号");
        return Collections.singletonMap("message", "登录状态已确认，剩余任务按时间分散执行；待核实任务不会自动重发");
    }

    private WeiboSuperTopic ownedTopic(Long userId, Long topicId) {
        requireUser(userId);
        WeiboSuperTopic topic = topics.getById(topicId);
        if (topic == null || !userId.equals(topic.getUserId())) throw new IllegalArgumentException("超话不存在或无权访问");
        ownedAccount(userId, topic.getAccountId());
        return topic;
    }

    private PlatformAccount ownedAccount(Long userId, Long accountId) {
        requireUser(userId);
        PlatformAccount account = accounts.getById(accountId);
        if (account == null || !userId.equals(account.getUserId()) || !"weibo".equals(account.getPlatform()))
            throw new IllegalArgumentException("微博账号不存在或无权访问");
        return account;
    }

    private boolean validOwnershipAndEnabled(ActivityTask task, PlatformAccount account, WeiboSuperTopic topic) {
        return account != null && topic != null && "weibo".equals(account.getPlatform())
                && Integer.valueOf(1).equals(account.getEnableStatus()) && Integer.valueOf(1).equals(topic.getEnableStatus())
                && task.getUserId().equals(account.getUserId()) && task.getUserId().equals(topic.getUserId())
                && task.getAccountId().equals(topic.getAccountId());
    }

    private static void requireUser(Long userId) {
        if (userId == null || userId <= 0) throw new IllegalArgumentException("请先登录");
    }

    private static void validateTemplate(String template) {
        if (template == null || template.trim().isEmpty() || template.length() > 4000)
            throw new IllegalArgumentException("发帖和评论模板均不能为空，且每项最多 4000 字");
    }

    private static String trimMessage(String message) {
        if (message == null || message.trim().isEmpty()) return "未返回详细结果";
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private LocalDateTime now() { return LocalDateTime.ofInstant(clock.instant(), ZONE); }
}
