package xin.v5ai.nb.rag.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import xin.v5ai.nb.rag.domain.KnowledgeBase;

/**
 * 知识库列表查询请求体。
 *
 * @author ZYW
 * @since 2026-08-22
 */
@Data
@AutoMapper(target = KnowledgeBase.class, reverseConvertGenerate = false)
public class KnowledgeBaseBo {

    /**
     * 知识库名称（模糊匹配）
     */
    private String name;

    /**
     * 启用状态（ACTIVE / DISABLED）
     */
    private String status;

    /**
     * 知识库描述
     */
    private String description;
}
