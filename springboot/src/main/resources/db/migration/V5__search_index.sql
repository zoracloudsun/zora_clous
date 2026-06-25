-- V5__search_index.sql
-- Phase 4: 全文搜索引擎
-- 为 chat_message.content 和 chat_conversation.title 添加 MySQL FULLTEXT 索引
-- 使用 ngram parser 实现中文分词（默认 n=2，双字分词）

ALTER TABLE chat_message
    ADD FULLTEXT INDEX ft_chat_message_content (content) WITH PARSER ngram;

ALTER TABLE chat_conversation
    ADD FULLTEXT INDEX ft_chat_conversation_title (title) WITH PARSER ngram;
