package xin.v5ai.nb.rag.core.tokenizer;

import java.util.List;

/**
 * 关键词路文本分词端口（PG BM25 索引/查询共用）。
 * <p>
 * 与具体分词器解耦：上游 jieba-analysis 已停更，替换分词器只需另实现本接口
 * （备选：jieba master/JitPack 或 HanLP），索引与查询两侧必须使用同一实现。
 * <p>
 * 两侧口径不同：<b>索引</b>侧保留重复词（词频 tf 由数组内出现次数现算）、
 * <b>查询</b>侧按词项去重（同一查询词不重复计分）。
 *
 * @author ZYW
 * @since 2026-09-09
 */
public interface TextTokenizer {

    /**
     * 索引侧分词：用于写入 {@code v5ai_knowledge_chunk.keyword_tokens}（保留重复词）。
     *
     * @param text 原文（可为空）
     * @return 小写化、已过滤停用词与纯标点的词项列表（保留顺序与重复）
     */
    List<String> tokenizeForIndex(String text);

    /**
     * 查询侧分词：用于关键词检索的查询词项（去重）。
     *
     * @param text 查询文本（可为空）
     * @return 去重后的词项列表；全为停用词/标点时为空列表（调用方按「无有效词项」处理）
     */
    List<String> tokenizeForSearch(String text);
}
