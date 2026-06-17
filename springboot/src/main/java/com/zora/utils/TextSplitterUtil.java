package com.zora.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 文本分割工具类（Phase 2: RAG 知识库）
 * <p>
 * 实现递归字符文本分割，按段落 → 句子 → 字符的优先级切分文本。
 * 保证每个文本块不超过最大字符数，块之间有指定重叠。
 * </p>
 */
public class TextSplitterUtil {

    /** 段落分隔符 */
    private static final Pattern PARAGRAPH_SEP = Pattern.compile("\\n\\s*\\n");
    /** 句子分隔符（中文句号、问号、感叹号、换行 + 英文标点） */
    private static final Pattern SENTENCE_SEP = Pattern.compile(
            "(?<=[。！？\\n.!?])\\s*");

    private TextSplitterUtil() {
    }

    /**
     * 递归分割文本为指定大小的块
     *
     * @param text      原始文本
     * @param chunkSize 最大块大小（字符数）
     * @param overlap   块间重叠字符数
     * @return 分割后的文本块列表
     */
    public static List<String> split(String text, int chunkSize, int overlap) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }
        return splitRecursive(text.trim(), chunkSize, overlap);
    }

    /**
     * 递归分割：先按段落，超长再按句子，最后强制按字符
     */
    private static List<String> splitRecursive(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();

        // 如果文本已经足够短，直接返回
        if (text.length() <= chunkSize) {
            if (!text.isEmpty()) {
                chunks.add(text);
            }
            return chunks;
        }

        // 1. 尝试按段落分割
        String[] paragraphs = PARAGRAPH_SEP.split(text);
        if (paragraphs.length > 1) {
            StringBuilder current = new StringBuilder();
            for (String para : paragraphs) {
                String trimmed = para.trim();
                if (trimmed.isEmpty())
                    continue;

                if (current.length() + trimmed.length() + 2 <= chunkSize) {
                    if (current.length() > 0)
                        current.append("\n\n");
                    current.append(trimmed);
                } else {
                    // 当前累积的段落形成一块
                    if (current.length() > 0) {
                        chunks.add(current.toString());
                        // 重叠：保留最后 overlap 个字符
                        if (overlap > 0 && current.length() > overlap) {
                            current = new StringBuilder(current.substring(current.length() - overlap));
                        } else if (overlap > 0) {
                            current = new StringBuilder(current.toString());
                        } else {
                            current = new StringBuilder();
                        }
                    }
                    // 如果单个段落超长，继续按句子分割
                    if (trimmed.length() > chunkSize) {
                        chunks.addAll(splitRecursive(trimmed, chunkSize, overlap));
                        current = new StringBuilder();
                    } else {
                        current = new StringBuilder(trimmed);
                    }
                }
            }
            if (current.length() > 0) {
                chunks.add(current.toString());
            }
            return chunks;
        }

        // 2. 尝试按句子分割
        String[] sentences = SENTENCE_SEP.split(text);
        if (sentences.length > 1) {
            StringBuilder current = new StringBuilder();
            for (String sentence : sentences) {
                String trimmed = sentence.trim();
                if (trimmed.isEmpty())
                    continue;

                if (current.length() + trimmed.length() + 1 <= chunkSize) {
                    if (current.length() > 0)
                        current.append(" ");
                    current.append(trimmed);
                } else {
                    if (current.length() > 0) {
                        chunks.add(current.toString());
                        current = new StringBuilder();
                    }
                    // 单句超长，强制按字符切分
                    if (trimmed.length() > chunkSize) {
                        chunks.addAll(splitByChar(trimmed, chunkSize, overlap));
                    } else {
                        current = new StringBuilder(trimmed);
                    }
                }
            }
            if (current.length() > 0) {
                chunks.add(current.toString());
            }
            return chunks;
        }

        // 3. 最后兜底：直接按字符切分
        return splitByChar(text, chunkSize, overlap);
    }

    /**
     * 按字符数强制切分（带重叠）
     */
    private static List<String> splitByChar(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            // 尝试在空格或标点处截断，避免截断单词（中英文通用）
            if (end < text.length()) {
                int breakPoint = end;
                for (int i = end; i > start + chunkSize / 2; i--) {
                    char c = text.charAt(i);
                    if (Character.isWhitespace(c) || c == '。' || c == '，' || c == '.' || c == ',') {
                        breakPoint = i + 1;
                        break;
                    }
                }
                end = breakPoint;
            }
            chunks.add(text.substring(start, end).trim());
            start = end - overlap;
            if (start < 0)
                start = 0;
        }
        return chunks;
    }
}
