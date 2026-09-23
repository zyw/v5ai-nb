package xin.v5ai.nb.platform.api;

/**
 * 通用资源存储（plm_resource）的读/写/删端口。
 *
 * <p>知识库文档的源文件统一写入资源存储（biz_type=DOCUMENT），
 * rag 模块通过本端口读写文件字节，避免 rag 与 platform 模块直接依赖。
 * 端口接口落在 common-core，实现由 platform（{@code plm_resource}）提供。</p>
 */
public interface ResourceContentPort {

    /**
     * 上传文件并登记资源记录。
     *
     * @param originalName 原始文件名
     * @param bytes        文件字节
     * @param contentType  MIME 类型
     * @param bizType      业务类型（知识库文档固定为 DOCUMENT）
     * @param bizId        关联业务 ID（知识库文档 id）
     * @return 资源 id
     */
    Long save(String originalName, byte[] bytes, String contentType, String bizType, Long bizId);

    /**
     * 上传文件并登记资源记录，显式指定创建者。
     *
     * <p>与上一个重载的唯一差别是 {@code createdBy} 由调用方传入而不是取当前登录用户：
     * API Key 鉴权路径（门户上传附件）没有 Sa-Token 会话，归属只能来自调用方
     * 已经校验过的 Key（见 ADR 0006）。</p>
     *
     * @param originalName 原始文件名
     * @param bytes        文件字节
     * @param contentType  MIME 类型
     * @param bizType      业务类型（门户附件固定为 ATTACHMENT）
     * @param bizId        关联业务 ID，可为空
     * @param createdBy    创建者 ID（API Key 路径传 Key 的归属用户），可为空
     * @return 资源 id
     */
    Long save(String originalName, byte[] bytes, String contentType, String bizType, Long bizId,
              Long createdBy);

    /**
     * 读取资源文件内容；资源不存在时抛异常（调用方据此提示「源文件已不存在」）。
     */
    Content read(Long resourceId);

    /**
     * 删除资源（幂等、尽力而为）：资源已不存在或删除失败均不再向外抛异常，保证调用方事务不被污染。
     */
    void delete(Long resourceId);

    /**
     * 资源文件内容与展示元数据。
     *
     * @param bytes        文件字节
     * @param mimeType     MIME 类型
     * @param originalName 原始文件名
     */
    record Content(byte[] bytes, String mimeType, String originalName) {
    }
}