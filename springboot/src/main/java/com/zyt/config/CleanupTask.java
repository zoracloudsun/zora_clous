package com.zyt.config;

import com.zyt.service.AiChatService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定期清理任务
 * 每天凌晨 3 点物理删除超过 30 天的软删除对话和消息
 */
@Component
public class CleanupTask {

    private static final Logger log = LoggerFactory.getLogger(CleanupTask.class);

    @Resource
    private AiChatService aiChatService;

    /**
     * 每天凌晨 3:00 执行清理
     * cron 秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupDeletedConversations() {
        log.info("[定时任务] 开始清理超过 30 天的软删除对话...");
        try {
            int count = aiChatService.cleanupOldDeletedConversations();
            if (count > 0) {
                log.info("[定时任务] 清理完成，物理删除了 {} 个对话", count);
            } else {
                log.info("[定时任务] 无需清理，回收站为空");
            }
        } catch (Exception e) {
            log.error("[定时任务] 清理失败", e);
        }
    }
}
