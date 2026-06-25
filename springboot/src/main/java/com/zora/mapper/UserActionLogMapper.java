package com.zora.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zora.entity.UserActionLog;

/**
 * 用户行为日志 Mapper
 * <p>
 * Phase 4 用户行为分析 —— 继承 BaseMapper 获得自动 CRUD。
 * 行为日志通过 AOP 切面异步写入，查询用于统计分析和仪表盘展示。
 * </p>
 */
public interface UserActionLogMapper extends BaseMapper<UserActionLog> {
}
