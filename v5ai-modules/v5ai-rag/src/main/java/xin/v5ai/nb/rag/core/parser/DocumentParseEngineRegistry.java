package xin.v5ai.nb.rag.core.parser;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DocumentParseEngineRegistry {

    private final Map<String, DocumentParseEngine> engines;

    public DocumentParseEngineRegistry(List<DocumentParseEngine> candidates) {
        Map<String, DocumentParseEngine> registered = new LinkedHashMap<>();
        for (DocumentParseEngine candidate : candidates) {
            if (candidate == null || candidate.engine() == null || candidate.engine().isBlank()) {
                throw new IllegalArgumentException("文档解析引擎名称不能为空");
            }
            String name = normalize(candidate.engine());
            if (registered.putIfAbsent(name, candidate) != null) {
                throw new IllegalArgumentException("重复注册文档解析引擎: " + name);
            }
        }
        this.engines = Map.copyOf(registered);
    }

    public DocumentParseEngine resolve(String configuredEngine) {
        String name = configuredEngine == null || configuredEngine.isBlank()
                ? "default" : normalize(configuredEngine);
        DocumentParseEngine engine = engines.get(name);
        if (engine == null) {
            throw new IllegalArgumentException("不支持的文档解析引擎: " + configuredEngine);
        }
        return engine;
    }

    public DocumentParseEngine defaultEngine() {
        DocumentParseEngine engine = engines.get("default");
        if (engine == null) {
            throw new IllegalStateException("未注册 default 文档解析引擎");
        }
        return engine;
    }

    private String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
