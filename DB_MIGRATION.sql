-- ==================== 微信扫码登录 — 数据库迁移脚本 ====================
-- 执行前请备份数据库
-- 使用方法：在 MySQL 中执行此脚本

USE springboot_zyt;

-- 1. 为 user 表添加微信相关字段
ALTER TABLE user
    ADD COLUMN openid   VARCHAR(128) NULL COMMENT '微信 openid（唯一标识一个微信用户）' AFTER password,
    ADD COLUMN nickname VARCHAR(64)  NULL COMMENT '微信昵称'                            AFTER openid,
    ADD COLUMN avatar   VARCHAR(512) NULL COMMENT '微信头像 URL'                        AFTER nickname;

-- 2. 为 openid 添加唯一索引（一个微信号只能绑定一个账号）
CREATE UNIQUE INDEX idx_user_openid ON user (openid);
