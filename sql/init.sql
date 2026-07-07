-- 自动签到系统数据库初始化脚本

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS qiandao DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE qiandao;

-- 系统用户表
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
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
DROP TABLE IF EXISTS platform_account;
CREATE TABLE platform_account (
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
DROP TABLE IF EXISTS weibo_super_topic;
CREATE TABLE weibo_super_topic (
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
DROP TABLE IF EXISTS sign_task;
CREATE TABLE sign_task (
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
DROP TABLE IF EXISTS sign_record;
CREATE TABLE sign_record (
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
DROP TABLE IF EXISTS sign_record_detail;
CREATE TABLE sign_record_detail (
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

-- 插入默认管理员用户（密码：admin123，BCrypt加密）
INSERT INTO sys_user (id, username, password, nickname, status) VALUES
(1, 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH', '管理员', 1);
