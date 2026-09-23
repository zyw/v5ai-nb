package xin.v5ai.nb.runtime.core.resolver;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.common.agentscope.core.service.AttachmentContentProvider;
import xin.v5ai.nb.platform.api.ResourceContentPort;

/**
 * {@link AttachmentContentProvider} 的实现：委托通用资源存储（{@code plm_resource}）读取附件字节。
 *
 * <p>common-agentscope 不依赖资源存储模块，因此端口声明在那边、实现落在运行时模块。</p>
 */
@Component
@RequiredArgsConstructor
public class ResourceAttachmentContentProvider implements AttachmentContentProvider {

    private final ResourceContentPort resourceContentPort;

    @Override
    public AttachmentContent read(Long resourceId) {
        var content = resourceContentPort.read(resourceId);
        return new AttachmentContent(content.bytes(), content.mimeType());
    }
}
