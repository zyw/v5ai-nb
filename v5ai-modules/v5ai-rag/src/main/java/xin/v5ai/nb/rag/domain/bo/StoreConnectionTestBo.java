package xin.v5ai.nb.rag.domain.bo;

/**
 * 存储实例连接测试请求体。
 *
 * @param type   类型: 1-PG_VECTOR 2-MILVUS 3-ELASTICSEARCH 4-PG_FULLTEXT
 * @param config 连接参数 JSON（未保存的表单值）
 * @param id     编辑态传入已存在实例 id，用于合并库中脱敏的敏感字段；新建态为 null
 */
public record StoreConnectionTestBo(Integer type, String config, Long id) {
}
