CREATE DATABASE IF NOT EXISTS springboot_zyt DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE springboot_zyt;

CREATE TABLE IF NOT EXISTS `user` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `email` VARCHAR(100) NOT NULL UNIQUE,
    `password` VARCHAR(255) NOT NULL,
    `openid` VARCHAR(128) NULL COMMENT '微信 openid',
    `nickname` VARCHAR(64) NULL COMMENT '微信昵称',
    `avatar` VARCHAR(512) NULL COMMENT '微信头像 URL',
    `role` VARCHAR(20) NOT NULL DEFAULT 'user' COMMENT '用户角色：user | admin'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
