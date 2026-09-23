package xin.v5ai.nb.platform.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import xin.v5ai.nb.platform.api.ResourceContentPort;
import xin.v5ai.nb.platform.service.IPlmResourceService;

import java.util.List;

/**
 * {@link ResourceContentPort} 的 plm_resource 实现：委托 {@link IPlmResourceService}。
 *
 * <p>{@code delete} 为「幂等 + 尽力而为」：先非事务读取判断资源是否存在（不存在则静默跳过，
 * 不触碰任何事务），存在时再委托删除；删除异常仅告警、绝不向外抛出，保证不反噬调用方
 * （知识库文档/知识库删除）事务。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlmResourceContentPortImpl implements ResourceContentPort {

    private final IPlmResourceService resourceService;

    @Override
    public Long save(String originalName, byte[] bytes, String contentType, String bizType, Long bizId) {
        return resourceService.upload(originalName, bytes, contentType, bizType, bizId).getId();
    }

    @Override
    public Long save(String originalName, byte[] bytes, String contentType, String bizType, Long bizId,
                     Long createdBy) {
        return resourceService.upload(originalName, bytes, contentType, bizType, bizId, createdBy).getId();
    }

    @Override
    public Content read(Long resourceId) {
        var content = resourceService.loadContent(resourceId);
        return new Content(content.bytes(), content.mimeType(), content.originalName());
    }

    @Override
    public void delete(Long resourceId) {
        try {
            // 非事务读取：不存在即幂等跳过（queryById 抛异常，不参与/不污染任何事务）
            resourceService.queryById(resourceId);
        } catch (Exception missing) {
            return;
        }
        try {
            resourceService.deleteWithValidByIds(List.of(resourceId), true);
        } catch (Exception exception) {
            log.warn("删除关联资源失败（忽略）: resourceId={}, reason={}", resourceId, exception.getMessage());
        }
    }
}