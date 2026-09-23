package xin.v5ai.nb.rag.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import xin.v5ai.nb.common.mybatis.core.domain.BaseEntity;
import xin.v5ai.nb.rag.core.store.TextArrayTypeHandler;

import java.io.Serial;
import java.io.Serializable;

/**
 * <p>
 * 
 * </p>
 *
 * @author ZYW
 * @since 2026-09-03
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
// autoResultMap 必须开：keyword_tokens 的 TextArrayTypeHandler 只有在这个开关下才会参与结果映射，
// 否则通用查询（selectById / selectList）会把 PG 的 text[] 或 MySQL 的 JSON 列值直接塞给 String[]，
// 类型不匹配报错。切片详情页与知识问答的邻近展开都走通用查询。
@TableName(value = "v5ai_knowledge_chunk", autoResultMap = true)
public class KnowledgeChunk extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long knowledgeBaseId;

    private Long documentId;

    private Integer chunkIndex;

    private String content;

    private Object metadata;

    /**
     * 段落索引
     */
    private Integer paragraphIndex;

    /**
     * 分片token数量
     */
    private Integer tokenCount;

    /**
     * 向量id
     */
    private String vectorId;

    /**
     * chunk内容SHA-256，用于向量去重
     */
    private String contentHash;

    /**
     * chunk来源类型: TEXT=文本 IMAGE=图片
     */
    private String sourceType;

    /**
     * 关键词分词（jieba INDEX 模式，保留重复词；PG 为 {@code text[]}、MySQL 为 {@code JSON} 字符串数组，
     * 两种物理形态由 {@link TextArrayTypeHandler} 按连接自动互转）。
     * <p>
     * 仅服务关键词路 BM25：词频 tf 由数组内出现次数现算，文档长度 dl = 数组长度，
     * df / avgdl 检索时按库集合现算。写入由 Worker 重建与手工切片增改维护。
     */
    @TableField(value = "keyword_tokens", typeHandler = TextArrayTypeHandler.class)
    private String[] keywordTokens;
}
