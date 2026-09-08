package xin.v5ai.nb.rag.core.chunker;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import xin.v5ai.nb.rag.core.ChunkMode;
import xin.v5ai.nb.rag.core.ChunkingOptions;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 切片策略注册中心：按切片策略路由到对应实现类，对外仍以 {@link DocumentChunker} 暴露，
 * 使注入方无需感知策略拆分（{@code @Primary} 下唯一命中本注册中心）。
 * 新增切片策略只需新增一个 {@link ChunkingStrategy} 实现类，无需改动注册中心。
 *
 * @author ZYW
 * @since 2026-09-08
 */
@Primary
@Component
public class DocumentChunkerRegistry implements DocumentChunker {
    private final Map<ChunkMode, ChunkingStrategy> strategies;
    private final ChunkingStrategy fallback;

    public DocumentChunkerRegistry(List<ChunkingStrategy> strategies) {
        this.strategies = new EnumMap<>(ChunkMode.class);
        for (ChunkingStrategy strategy : strategies) {
            this.strategies.put(strategy.supportedMode(), strategy);
        }
        this.fallback = this.strategies.getOrDefault(ChunkMode.LENGTH,
                strategies.isEmpty() ? null : strategies.get(0));
    }

    @Override
    public List<String> chunk(String text) {
        return fallback.chunk(text);
    }

    @Override
    public List<String> chunk(String text, ChunkingOptions options) {
        ChunkMode mode = options.sliceStrategy();
        ChunkingStrategy strategy = strategies.getOrDefault(mode, fallback);
        return strategy.chunk(text, options);
    }
}
