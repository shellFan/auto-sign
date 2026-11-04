package com.autosign.activity;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ActivityMapper {
    @Insert("INSERT IGNORE INTO daily_activity_plan " +
            "(id,user_id,account_id,topic_id,task_date,sign_target,post_target,reply_target,forward_target,view_target,created_at) " +
            "VALUES (#{id},#{userId},#{accountId},#{topicId},#{taskDate},#{signTarget},#{postTarget},#{replyTarget},#{forwardTarget},#{viewTarget},#{createdAt})")
    int insertPlan(DailyPlan plan);

    @Insert("INSERT IGNORE INTO activity_task " +
            "(id,plan_id,user_id,account_id,topic_id,task_date,action_type,action_ordinal,due_at,status,content,post_id,attempts,message,started_at,finished_at) " +
            "VALUES (#{id},#{planId},#{userId},#{accountId},#{topicId},#{taskDate},#{actionType},#{ordinal},#{dueAt},#{status},#{content},#{postId},#{attempts},#{message},#{startedAt},#{finishedAt})")
    int insertTask(ActivityTask task);

    @Select("SELECT p.*,a.account_name,t.topic_name FROM daily_activity_plan p " +
            "LEFT JOIN platform_account a ON a.id=p.account_id LEFT JOIN weibo_super_topic t ON t.id=p.topic_id " +
            "WHERE p.task_date=#{day} AND p.account_id=#{accountId} AND p.topic_id=#{topicId} LIMIT 1")
    DailyPlan findPlan(@Param("day") LocalDate day, @Param("accountId") Long accountId,
                       @Param("topicId") Long topicId);

    @Select("SELECT p.*,a.account_name,t.topic_name FROM daily_activity_plan p " +
            "LEFT JOIN platform_account a ON a.id=p.account_id LEFT JOIN weibo_super_topic t ON t.id=p.topic_id " +
            "WHERE p.user_id=#{userId} AND p.task_date=#{day} ORDER BY a.account_name,t.topic_name")
    List<DailyPlan> listPlans(@Param("userId") Long userId, @Param("day") LocalDate day);

    @Select("<script>SELECT x.*,x.action_ordinal AS ordinal,a.account_name,t.topic_name FROM activity_task x " +
            "LEFT JOIN platform_account a ON a.id=x.account_id LEFT JOIN weibo_super_topic t ON t.id=x.topic_id " +
            "WHERE x.task_date=#{day}<if test='userId != null'> AND x.user_id=#{userId}</if> " +
            "ORDER BY x.due_at,x.id</script>")
    List<ActivityTask> listTasks(@Param("userId") Long userId, @Param("day") LocalDate day);

    @Select("SELECT x.*,x.action_ordinal AS ordinal,a.account_name,t.topic_name FROM activity_task x " +
            "LEFT JOIN platform_account a ON a.id=x.account_id LEFT JOIN weibo_super_topic t ON t.id=x.topic_id " +
            "WHERE x.user_id=#{userId} AND (x.status <> 'PENDING' OR x.started_at IS NOT NULL) " +
            "ORDER BY CASE WHEN x.status='UNKNOWN' THEN 0 ELSE 1 END," +
            "COALESCE(x.finished_at,x.started_at,x.due_at) DESC,x.id DESC LIMIT 100")
    List<ActivityTask> history(Long userId);

    @Select("SELECT x.*,x.action_ordinal AS ordinal,a.account_name,t.topic_name FROM activity_task x " +
            "LEFT JOIN platform_account a ON a.id=x.account_id LEFT JOIN weibo_super_topic t ON t.id=x.topic_id " +
            "WHERE x.id=#{id} LIMIT 1")
    ActivityTask findTask(Long id);

    @Select("SELECT x.*,x.action_ordinal AS ordinal,a.account_name,t.topic_name FROM activity_task x " +
            "LEFT JOIN platform_account a ON a.id=x.account_id LEFT JOIN weibo_super_topic t ON t.id=x.topic_id " +
            "WHERE x.task_date=#{day} AND x.status='PENDING' AND x.due_at <= #{now} ORDER BY x.due_at,x.id LIMIT 1")
    ActivityTask findDue(@Param("day") LocalDate day, @Param("now") LocalDateTime now);

    @Update("UPDATE activity_task SET status='RUNNING',attempts=attempts+1,started_at=#{now},finished_at=NULL " +
            "WHERE id=#{id} AND status='PENDING'")
    int claim(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Update("UPDATE activity_task SET status=#{status},message=#{message},finished_at=#{now} WHERE id=#{id} AND status='RUNNING'")
    int finish(@Param("id") Long id, @Param("status") String status,
               @Param("message") String message, @Param("now") LocalDateTime now);

    @Update("UPDATE activity_task SET status='PENDING',due_at=#{dueAt},message=#{message},finished_at=NULL " +
            "WHERE id=#{id} AND status='RUNNING'")
    int retry(@Param("id") Long id, @Param("dueAt") LocalDateTime dueAt, @Param("message") String message);

    @Update("UPDATE activity_task SET post_id=#{postId} WHERE id=#{id} AND status='RUNNING' " +
            "AND (post_id IS NULL OR post_id=#{postId})")
    int reserve(@Param("id") Long id, @Param("postId") String postId);

    @Select("SELECT post_id FROM activity_task WHERE account_id=#{accountId} AND topic_id=#{topicId} " +
            "AND task_date=#{taskDate} AND action_type=#{actionType} AND id <> #{id} AND post_id IS NOT NULL")
    List<String> excludedPostIds(ActivityTask task);

    @Select("SELECT MAX(COALESCE(finished_at,started_at)) FROM activity_task")
    LocalDateTime lastActivityAt();

    @Select("SELECT COUNT(*) FROM activity_task WHERE task_date=#{day} AND status='PENDING'")
    int countPending(LocalDate day);

    @Select("SELECT COUNT(*) FROM activity_task WHERE plan_id=#{planId}")
    int countTasks(Long planId);

    @Update("UPDATE activity_task SET status='UNKNOWN',message='待核实',finished_at=#{now} " +
            "WHERE status='RUNNING' AND started_at < #{cutoff}")
    int recoverRunning(@Param("cutoff") LocalDateTime cutoff, @Param("now") LocalDateTime now);

    @Update("UPDATE activity_task SET status='EXPIRED',finished_at=#{now} " +
            "WHERE task_date < #{day} AND status IN ('PENDING','PAUSED')")
    int expireBefore(@Param("day") LocalDate day, @Param("now") LocalDateTime now);

    @Update("UPDATE activity_task SET status='EXPIRED',finished_at=#{now} " +
            "WHERE task_date=#{day} AND status IN ('PENDING','PAUSED')")
    int expireToday(@Param("day") LocalDate day, @Param("now") LocalDateTime now);

    @Update("UPDATE activity_task SET status='EXPIRED',message=#{message},finished_at=#{now} " +
            "WHERE id=#{id} AND status='PENDING'")
    int expireTask(@Param("id") Long id, @Param("message") String message, @Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM activity_account_pause WHERE account_id=#{accountId}")
    int isAccountPaused(Long accountId);

    @Insert("INSERT INTO activity_account_pause(account_id,user_id,reason,created_at,updated_at) " +
            "VALUES(#{accountId},#{userId},#{reason},#{now},#{now}) ON DUPLICATE KEY UPDATE " +
            "user_id=VALUES(user_id),reason=VALUES(reason),updated_at=VALUES(updated_at)")
    int pauseAccount(@Param("userId") Long userId, @Param("accountId") Long accountId,
                     @Param("reason") String reason, @Param("now") LocalDateTime now);

    @Update("UPDATE activity_task SET status='PAUSED',message=#{reason} WHERE account_id=#{accountId} " +
            "AND task_date=#{day} AND status='PENDING'")
    int pauseTasks(@Param("accountId") Long accountId, @Param("day") LocalDate day,
                   @Param("reason") String reason);

    @Delete("DELETE FROM activity_account_pause WHERE user_id=#{userId} AND account_id=#{accountId}")
    int clearAccountPause(@Param("userId") Long userId, @Param("accountId") Long accountId);

    @Update("UPDATE activity_task SET status='PENDING',due_at=#{dueAt} WHERE id=#{id} " +
            "AND status IN ('PENDING','PAUSED')")
    int reschedule(@Param("id") Long id, @Param("dueAt") LocalDateTime dueAt);

    @Select("SELECT topic_id,user_id,post_templates,reply_templates FROM activity_templates " +
            "WHERE user_id=#{userId} AND topic_id=#{topicId} LIMIT 1")
    ActivityTemplates getTemplates(@Param("userId") Long userId, @Param("topicId") Long topicId);

    @Insert("INSERT INTO activity_templates(topic_id,user_id,post_templates,reply_templates,updated_at) " +
            "VALUES(#{topicId},#{userId},#{postTemplates},#{replyTemplates},CURRENT_TIMESTAMP) " +
            "ON DUPLICATE KEY UPDATE user_id=VALUES(user_id),post_templates=VALUES(post_templates)," +
            "reply_templates=VALUES(reply_templates),updated_at=CURRENT_TIMESTAMP")
    int saveTemplates(ActivityTemplates templates);

    @Update("UPDATE activity_task SET content=#{content} WHERE id=#{id} AND status IN ('PENDING','PAUSED')")
    int updateContent(@Param("id") Long id, @Param("content") String content);
}
