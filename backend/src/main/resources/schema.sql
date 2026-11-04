-- 系统用户表
CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT NOT NULL PRIMARY KEY,
  username VARCHAR(50) NOT NULL,
  password VARCHAR(100) NOT NULL,
  nickname VARCHAR(100) DEFAULT NULL,
  status TINYINT DEFAULT 1 COMMENT '0禁用 1正常',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 平台账号表
CREATE TABLE IF NOT EXISTS platform_account (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  platform VARCHAR(30) NOT NULL COMMENT 'tieba weibo',
  account_name VARCHAR(100) DEFAULT NULL COMMENT '账号显示名',
  remark VARCHAR(200) DEFAULT NULL,
  login_status TINYINT DEFAULT 0 COMMENT '0未登录 1已登录 2已失效 3需验证',
  cookie_path VARCHAR(500) DEFAULT NULL COMMENT '加密cookie文件路径',
  browser_profile_path VARCHAR(500) DEFAULT NULL COMMENT '浏览器用户目录',
  last_login_time DATETIME DEFAULT NULL,
  last_sign_time DATETIME DEFAULT NULL,
  enable_status TINYINT DEFAULT 1 COMMENT '0禁用 1启用',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_user_platform (user_id, platform)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='平台账号表';

-- 微博超话表
CREATE TABLE IF NOT EXISTS weibo_super_topic (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  account_id BIGINT NOT NULL COMMENT '微博账号ID',
  topic_name VARCHAR(100) NOT NULL COMMENT '超话名称',
  topic_url VARCHAR(1000) DEFAULT NULL COMMENT '超话地址',
  enable_status TINYINT DEFAULT 1 COMMENT '0禁用 1启用',
  sort INT DEFAULT 0,
  remark VARCHAR(200) DEFAULT NULL,
  last_sign_date VARCHAR(20) DEFAULT NULL,
  last_sign_status TINYINT DEFAULT 0 COMMENT '0未执行 1成功 2已签到 3失败 4需验证',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_account_id (account_id),
  KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微博超话配置表';

-- 签到任务表
CREATE TABLE IF NOT EXISTS sign_task (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  account_id BIGINT NOT NULL,
  platform VARCHAR(30) NOT NULL COMMENT 'tieba weibo',
  task_name VARCHAR(100) DEFAULT NULL,
  cron_expr VARCHAR(100) DEFAULT NULL,
  enable_status TINYINT DEFAULT 1 COMMENT '0禁用 1启用',
  retry_count INT DEFAULT 0,
  last_run_time DATETIME DEFAULT NULL,
  next_run_time DATETIME DEFAULT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_account_id (account_id),
  KEY idx_platform (platform)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='签到任务表';

-- 签到记录表
CREATE TABLE IF NOT EXISTS sign_record (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  account_id BIGINT NOT NULL,
  task_id BIGINT DEFAULT NULL,
  platform VARCHAR(30) NOT NULL,
  sign_date VARCHAR(20) NOT NULL COMMENT 'yyyy-MM-dd',
  status TINYINT DEFAULT 0 COMMENT '0未执行 1执行中 2成功 3部分成功 4失败 5需重新登录 6需人工验证',
  success_count INT DEFAULT 0,
  fail_count INT DEFAULT 0,
  message VARCHAR(1000) DEFAULT NULL,
  screenshot_path VARCHAR(500) DEFAULT NULL,
  start_time DATETIME DEFAULT NULL,
  end_time DATETIME DEFAULT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_account_date (account_id, sign_date),
  KEY idx_platform_date (platform, sign_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='签到记录表';

-- 签到明细表
CREATE TABLE IF NOT EXISTS sign_record_detail (
  id BIGINT NOT NULL PRIMARY KEY,
  record_id BIGINT NOT NULL,
  target_type VARCHAR(30) DEFAULT NULL COMMENT 'tieba super_topic task',
  target_name VARCHAR(200) DEFAULT NULL COMMENT '贴吧名/超话名/任务名',
  target_url VARCHAR(1000) DEFAULT NULL,
  status TINYINT DEFAULT 0 COMMENT '0失败 1成功 2已签到 3跳过 4需人工',
  message VARCHAR(1000) DEFAULT NULL,
  screenshot_path VARCHAR(500) DEFAULT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_record_id (record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='签到明细表';

-- 文章管理表
CREATE TABLE IF NOT EXISTS article (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  title VARCHAR(200) NOT NULL COMMENT '文章标题',
  content TEXT DEFAULT NULL COMMENT '文章内容(HTML)',
  summary VARCHAR(500) DEFAULT NULL COMMENT '文章摘要',
  cover_image VARCHAR(500) DEFAULT NULL COMMENT '封面图片URL',
  status TINYINT DEFAULT 0 COMMENT '0草稿 1已发布 2已推送公众号',
  wechat_media_id VARCHAR(200) DEFAULT NULL COMMENT '微信公众号素材ID',
  publish_time DATETIME DEFAULT NULL COMMENT '发布时间',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_user_id (user_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文章管理表';

-- 微博每日活动计划：目标一经生成即保持不变
CREATE TABLE IF NOT EXISTS daily_activity_plan (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  account_id BIGINT NOT NULL,
  topic_id BIGINT NOT NULL,
  task_date DATE NOT NULL,
  sign_target INT NOT NULL,
  post_target INT NOT NULL,
  reply_target INT NOT NULL,
  forward_target INT NOT NULL,
  view_target INT NOT NULL,
  created_at DATETIME NOT NULL,
  UNIQUE KEY uk_activity_plan (task_date, account_id, topic_id),
  KEY idx_activity_plan_user_date (user_id, task_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微博每日活动计划';

-- 每行代表一次可独立领取和恢复的活动动作
CREATE TABLE IF NOT EXISTS activity_task (
  id BIGINT NOT NULL PRIMARY KEY,
  plan_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  account_id BIGINT NOT NULL,
  topic_id BIGINT NOT NULL,
  task_date DATE NOT NULL,
  action_type VARCHAR(20) NOT NULL,
  action_ordinal INT NOT NULL,
  due_at DATETIME NOT NULL,
  status VARCHAR(20) NOT NULL,
  content TEXT DEFAULT NULL,
  post_id VARCHAR(100) DEFAULT NULL,
  attempts INT NOT NULL DEFAULT 0,
  message VARCHAR(1000) DEFAULT NULL,
  started_at DATETIME DEFAULT NULL,
  finished_at DATETIME DEFAULT NULL,
  UNIQUE KEY uk_activity_action (plan_id, action_type, action_ordinal),
  KEY idx_activity_due (task_date, status, due_at),
  KEY idx_activity_user_date (user_id, task_date),
  KEY idx_activity_target (account_id, topic_id, task_date, action_type, post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微博活动动作队列';

-- 超话发帖和评论模板，使用换行分隔候选文案
CREATE TABLE IF NOT EXISTS activity_templates (
  topic_id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  post_templates TEXT NOT NULL,
  reply_templates TEXT NOT NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_activity_template_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微博超话活动模板';

-- 登录失效或验证时持久暂停整个账号
CREATE TABLE IF NOT EXISTS activity_account_pause (
  account_id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  reason VARCHAR(1000) DEFAULT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  KEY idx_activity_pause_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='微博活动账号暂停标记';
