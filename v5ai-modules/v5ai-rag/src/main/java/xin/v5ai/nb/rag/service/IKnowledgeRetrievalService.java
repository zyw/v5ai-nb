package xin.v5ai.nb.rag.service;

import xin.v5ai.nb.rag.domain.bo.KbRetrieveBo;
import xin.v5ai.nb.rag.domain.vo.KbHitVo;

import java.util.List;

/**
 * 知识库检索调试：向量 + 关键词按融合策略召回、阈值过滤、可选问题改写。
 * <p>
 * 空参数按「知识库已存配置（RagConfigDO.SearchParams）→ 默认值」补齐；
 * 本页参数仅调试生效、不回写。
 *
 * @author ZYW
 * @since 2026-09-06
 */
public interface IKnowledgeRetrievalService {

    /**
     * 对单个知识库执行一次调试检索。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param request         检索参数（空字段走知识库配置/默认值）
     * @return 按相关性降序的命中列表
     */
    List<KbHitVo> retrieve(Long knowledgeBaseId, KbRetrieveBo request);
}
