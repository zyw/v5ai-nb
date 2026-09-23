package xin.v5ai.nb.platform.service;

import xin.v5ai.nb.common.core.domain.PageResult;
import xin.v5ai.nb.common.mybatis.core.page.PageQuery;
import xin.v5ai.nb.platform.domain.bo.PlmResourceBo;
import xin.v5ai.nb.platform.domain.vo.PlmResourceVo;

import java.util.Collection;

/**
 * 通用资源存储服务。
 *
 * @author ZYW
 * @since 2026-09-02
 */
public interface IPlmResourceService {

    /**
     * 分页查询资源列表（文件名模糊 / 业务类型 / 创建时间区间）。
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 资源分页结果
     */
    PageResult<PlmResourceVo> queryPageList(PlmResourceBo bo, PageQuery pageQuery);

    /**
     * 查询资源详情。
     *
     * @param id 主键
     * @return 资源详情
     */
    PlmResourceVo queryById(Long id);

    /**
     * 上传文件并登记资源记录。
     *
     * @param originalFilename 原始文件名
     * @param content          文件内容
     * @param contentType      文件 MIME 类型
     * @param bizType          业务类型（AVATAR/ATTACHMENT/DOCUMENT/GENERAL）
     * @param bizId            关联业务ID，可为空
     * @return 上传后的资源信息
     */
    PlmResourceVo upload(String originalFilename, byte[] content, String contentType,
                         String bizType, Long bizId);

    /**
     * 上传文件并登记资源记录，显式指定创建者。
     *
     * <p>与上一个重载的唯一差别是 {@code createdBy} 由调用方传入而不是取当前登录用户：
     * API Key 鉴权路径（门户上传附件）没有 Sa-Token 会话，取不到登录用户，
     * 归属只能来自调用方已经校验过的 Key（见 ADR 0006）。</p>
     *
     * @param originalFilename 原始文件名
     * @param content          文件内容
     * @param contentType      文件 MIME 类型
     * @param bizType          业务类型（AVATAR/ATTACHMENT/DOCUMENT/GENERAL）
     * @param bizId            关联业务ID，可为空
     * @param createdBy        创建者ID，可为空（表示无归属）
     * @return 上传后的资源信息
     */
    PlmResourceVo upload(String originalFilename, byte[] content, String contentType,
                         String bizType, Long bizId, Long createdBy);

    /**
     * 修改资源元数据（业务类型 / 关联业务ID）。
     *
     * @param bo 编辑参数
     * @return 是否成功
     */
    Boolean updateByBo(PlmResourceBo bo);

    /**
     * 校验并批量删除资源（物理删除文件与记录）。
     *
     * @param ids     主键集合
     * @param isValid 是否执行存在性校验
     * @return 是否成功
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);

    /**
     * 读取资源文件内容（预览 / 下载）。
     *
     * @param id 主键
     * @return 文件内容与元数据
     */
    ResourceContent loadContent(Long id);

    /**
     * 资源文件内容与展示元数据。
     *
     * @param bytes        文件字节
     * @param mimeType     MIME 类型
     * @param originalName 原始文件名
     * @param storageType  存储类型
     */
    record ResourceContent(byte[] bytes, String mimeType, String originalName, String storageType) {
    }
}
