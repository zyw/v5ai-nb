package xin.v5ai.nb.rag.core.chunker;

import xin.v5ai.nb.rag.core.ChunkMode;

/**
 * 单个切片策略的实现契约：除文档切片能力外，声明所支持的切片策略，供注册中心按策略路由。
 *
 * @author ZYW
 * @since 2026-09-08
 */
public interface ChunkingStrategy extends DocumentChunker {

    /**
     * 该实现类支持的切片策略；注册中心按此值路由，同一策略只能有一个实现。
     */
    ChunkMode supportedMode();
}
