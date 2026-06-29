package com.zora.config.task;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.zora.service.AiChatService;
import com.zora.service.RagService;

/**
 * 定期清理任务
 * <p>
 * 每天凌晨 3 点物理删除超过 30 天的软删除数据：
 * <ul>
 * <li>AI 对话：软删除的对话及其消息</li>
 * <li>RAG 知识库（Phase 2）：软删除的知识库、文档、文本块</li>
 * </ul>
 * </p>
 */
@Component
public class CleanupTask {

    private static final Logger log = LoggerFactory.getLogger(CleanupTask.class);

    @Resource
    private AiChatService aiChatService;

    @Resource
    private RagService ragService;

    /**
     * 每天凌晨 3:00 执行清理
     * cron 秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupDeletedConversations() {
        log.info("[定时任务] 开始清理超过 30 天的软删除数据...");

        // 清理 AI 对话
        try {
            int count = aiChatService.cleanupOldDeletedConversations();
            if (count > 0) {
                log.info("[定时任务] 清理完成，物理删除了 {} 个对话", count);
            } else {
                log.info("[定时任务] 无需清理过期对话");
            }
        } catch (Exception e) {
            log.error("[定时任务] 清理对话失败", e);
        }

        // 清理 RAG 知识库（Phase 2）
        try {
            int count = ragService.cleanupOldDeletedRecords();
            if (count > 0) {
                log.info("[定时任务] 清理完成，物理删除了 {} 个知识库", count);
            } else {
                log.info("[定时任务] 无需清理过期知识库");
            }
        } catch (Exception e) {
            log.error("[定时任务] 清理知识库失败", e);
        }
    }
}
