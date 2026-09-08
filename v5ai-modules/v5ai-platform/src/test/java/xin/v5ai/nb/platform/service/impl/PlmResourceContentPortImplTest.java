package xin.v5ai.nb.platform.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xin.v5ai.nb.common.core.exception.ServiceException;
import xin.v5ai.nb.platform.domain.vo.PlmResourceVo;
import xin.v5ai.nb.platform.service.IPlmResourceService;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PlmResourceContentPortImpl} 的端口映射单测：save/read/delete 委托 + 删除幂等。
 */
class PlmResourceContentPortImplTest {

    private IPlmResourceService resourceService;
    private PlmResourceContentPortImpl port;

    @BeforeEach
    void setUp() {
        resourceService = mock(IPlmResourceService.class);
        port = new PlmResourceContentPortImpl(resourceService);
    }

    @Test
    void saveDelegatesAndReturnsResourceId() {
        var vo = new PlmResourceVo();
        vo.setId(42L);
        var bytes = "doc".getBytes(StandardCharsets.UTF_8);
        when(resourceService.upload("guide.md", bytes, "text/markdown", "DOCUMENT", 10L)).thenReturn(vo);

        assertThat(port.save("guide.md", bytes, "text/markdown", "DOCUMENT", 10L)).isEqualTo(42L);
    }

    @Test
    void readMapsContentMetadata() {
        var content = new IPlmResourceService.ResourceContent(
                "abc".getBytes(StandardCharsets.UTF_8), "text/plain", "a.txt", "LOCAL");
        when(resourceService.loadContent(7L)).thenReturn(content);

        var read = port.read(7L);

        assertThat(read.bytes()).isEqualTo("abc".getBytes(StandardCharsets.UTF_8));
        assertThat(read.mimeType()).isEqualTo("text/plain");
        assertThat(read.originalName()).isEqualTo("a.txt");
    }

    @Test
    void deleteIsIdempotentWhenResourceMissing() {
        // 资源已不存在：queryById 抛异常 → 幂等跳过，不再调用删除
        when(resourceService.queryById(5L)).thenThrow(new ServiceException("资源不存在: 5"));

        assertThatCode(() -> port.delete(5L)).doesNotThrowAnyException();
        verify(resourceService).queryById(5L);
        verify(resourceService, never()).deleteWithValidByIds(anyList(), any());
    }

    @Test
    void deleteIgnoresDeleteFailureAfterExistenceCheck() {
        // 资源存在但删除抛异常：仅告警、不向外抛
        when(resourceService.queryById(5L)).thenReturn(new PlmResourceVo());
        when(resourceService.deleteWithValidByIds(List.of(5L), true))
                .thenThrow(new ServiceException("删除失败"));

        assertThatCode(() -> port.delete(5L)).doesNotThrowAnyException();
        verify(resourceService).deleteWithValidByIds(List.of(5L), true);
    }

    @Test
    void deleteDelegatesOnSuccess() {
        when(resourceService.queryById(5L)).thenReturn(new PlmResourceVo());
        when(resourceService.deleteWithValidByIds(List.of(5L), true)).thenReturn(true);

        port.delete(5L);

        verify(resourceService).deleteWithValidByIds(List.of(5L), true);
    }
}