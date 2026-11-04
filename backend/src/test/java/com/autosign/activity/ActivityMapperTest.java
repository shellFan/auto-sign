package com.autosign.activity;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityMapperTest {

    private ActivityMapper mapper;
    private JdbcTemplate jdbc;
    private SqlSession session;

    @BeforeEach
    void createTables() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:activity-" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration(
                new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(ActivityMapper.class);
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
        session = factory.openSession(false);
        mapper = session.getMapper(ActivityMapper.class);
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP TABLE IF EXISTS activity_task");
        jdbc.execute("DROP TABLE IF EXISTS daily_activity_plan");
        jdbc.execute("DROP TABLE IF EXISTS activity_account_pause");
        jdbc.execute("DROP TABLE IF EXISTS activity_templates");
        jdbc.execute("DROP TABLE IF EXISTS platform_account");
        jdbc.execute("DROP TABLE IF EXISTS weibo_super_topic");
        jdbc.execute("CREATE TABLE platform_account(id BIGINT PRIMARY KEY,user_id BIGINT,account_name VARCHAR(100))");
        jdbc.execute("CREATE TABLE weibo_super_topic(id BIGINT PRIMARY KEY,topic_name VARCHAR(100))");
        jdbc.execute("CREATE TABLE daily_activity_plan(id BIGINT PRIMARY KEY,user_id BIGINT NOT NULL,account_id BIGINT NOT NULL,topic_id BIGINT NOT NULL,task_date DATE NOT NULL,sign_target INT,post_target INT,reply_target INT,forward_target INT,view_target INT,created_at TIMESTAMP,UNIQUE(task_date,account_id,topic_id))");
        jdbc.execute("CREATE TABLE activity_task(id BIGINT PRIMARY KEY,plan_id BIGINT NOT NULL,user_id BIGINT NOT NULL,account_id BIGINT NOT NULL,topic_id BIGINT NOT NULL,task_date DATE NOT NULL,action_type VARCHAR(20),action_ordinal INT,due_at TIMESTAMP,status VARCHAR(20),content CLOB,post_id VARCHAR(100),attempts INT,message VARCHAR(1000),started_at TIMESTAMP,finished_at TIMESTAMP,UNIQUE(plan_id,action_type,action_ordinal))");
        jdbc.execute("CREATE TABLE activity_account_pause(account_id BIGINT PRIMARY KEY,user_id BIGINT NOT NULL,reason VARCHAR(1000),created_at TIMESTAMP,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE activity_templates(topic_id BIGINT PRIMARY KEY,user_id BIGINT NOT NULL,post_templates CLOB,reply_templates CLOB,updated_at TIMESTAMP)");
        jdbc.update("INSERT INTO platform_account VALUES (?,?,?)", 10L, 7L, "测试账号");
        jdbc.update("INSERT INTO weibo_super_topic VALUES (?,?)", 20L, "测试超话");
    }

    @AfterEach
    void rollback() {
        if (session != null) {
            session.rollback();
            session.close();
        }
    }

    @Test
    void claimIsAtomicRetryKeepsReservationAndUnknownIsNeverDue() {
        DailyPlan plan = plan();
        assertThat(mapper.insertPlan(plan)).isEqualTo(1);
        assertThat(mapper.insertPlan(plan())).isZero();
        ActivityTask task = task(plan.getId(), 101L, "PENDING", LocalDateTime.of(2026, 10, 22, 9, 0));
        assertThat(mapper.insertTask(task)).isEqualTo(1);

        LocalDateTime now = LocalDateTime.of(2026, 10, 22, 10, 0);
        assertThat(mapper.claim(101L, now)).isEqualTo(1);
        assertThat(mapper.claim(101L, now)).isZero();
        assertThat(mapper.reserve(101L, "post-A")).isEqualTo(1);
        assertThat(mapper.retry(101L, now.plusMinutes(5), "稍后重试")).isEqualTo(1);
        assertThat(mapper.findDue(LocalDate.of(2026, 10, 22), now.plusMinutes(4))).isNull();
        assertThat(mapper.findDue(LocalDate.of(2026, 10, 22), now.plusMinutes(5)).getPostId()).isEqualTo("post-A");
        assertThat(mapper.findTask(101L).getOrdinal()).isEqualTo(101);
        assertThat(mapper.findTask(101L).getStartedAt()).isEqualTo(now);

        assertThat(mapper.claim(101L, now.plusMinutes(5))).isEqualTo(1);
        assertThat(mapper.finish(101L, "UNKNOWN", "待核实", now.plusMinutes(6))).isEqualTo(1);
        assertThat(mapper.findDue(LocalDate.of(2026, 10, 22), now.plusHours(1))).isNull();
    }

    @Test
    void recoversStaleRunningAndExpiresOnlyUnfinishedRows() {
        DailyPlan plan = plan();
        mapper.insertPlan(plan);
        ActivityTask running = task(plan.getId(), 201L, "RUNNING", LocalDateTime.of(2026, 10, 21, 9, 0));
        running.setStartedAt(LocalDateTime.of(2026, 10, 21, 9, 1));
        mapper.insertTask(running);
        mapper.insertTask(task(plan.getId(), 202L, "PENDING", LocalDateTime.of(2026, 10, 21, 10, 0)));
        LocalDateTime now = LocalDateTime.of(2026, 10, 22, 8, 0);

        assertThat(mapper.recoverRunning(now.minusMinutes(10), now)).isEqualTo(1);
        assertThat(mapper.expireBefore(LocalDate.of(2026, 10, 22), now)).isEqualTo(1);
        assertThat(mapper.history(7L)).extracting(ActivityTask::getStatus)
                .containsExactlyInAnyOrder("UNKNOWN", "EXPIRED");
        assertThat(mapper.countPending(LocalDate.of(2026, 10, 22))).isZero();
    }

    @Test
    void templateAndPauseUpsertsRemainScopedAndDurable() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 22, 8, 0);
        assertThat(mapper.pauseAccount(7L, 10L, "需要登录", now)).isPositive();
        assertThat(mapper.pauseAccount(7L, 10L, "需要验证", now.plusMinutes(1))).isPositive();
        assertThat(mapper.isAccountPaused(10L)).isEqualTo(1);
        assertThat(mapper.clearAccountPause(99L, 10L)).isZero();
        assertThat(mapper.clearAccountPause(7L, 10L)).isEqualTo(1);

        ActivityTemplates templates = new ActivityTemplates();
        templates.setUserId(7L);
        templates.setTopicId(20L);
        templates.setPostTemplates("第一条 {topic}");
        templates.setReplyTemplates("回复 {topic}");
        assertThat(mapper.saveTemplates(templates)).isPositive();
        templates.setPostTemplates("第二条 {topic}");
        assertThat(mapper.saveTemplates(templates)).isPositive();
        assertThat(mapper.getTemplates(7L, 20L).getPostTemplates()).isEqualTo("第二条 {topic}");
        assertThat(mapper.getTemplates(99L, 20L)).isNull();
    }

    private DailyPlan plan() {
        DailyPlan plan = new DailyPlan();
        plan.setId(1L);
        plan.setUserId(7L);
        plan.setAccountId(10L);
        plan.setTopicId(20L);
        plan.setTaskDate(LocalDate.of(2026, 10, 22));
        plan.setSignTarget(1);
        plan.setPostTarget(1);
        plan.setReplyTarget(4);
        plan.setForwardTarget(2);
        plan.setViewTarget(40);
        plan.setCreatedAt(LocalDateTime.of(2026, 10, 22, 8, 0));
        return plan;
    }

    private ActivityTask task(Long planId, Long id, String status, LocalDateTime dueAt) {
        ActivityTask task = new ActivityTask();
        task.setId(id);
        task.setPlanId(planId);
        task.setUserId(7L);
        task.setAccountId(10L);
        task.setTopicId(20L);
        task.setTaskDate(dueAt.toLocalDate());
        task.setActionType("reply");
        task.setOrdinal(id.intValue());
        task.setDueAt(dueAt);
        task.setStatus(status);
        task.setAttempts(0);
        return task;
    }
}
