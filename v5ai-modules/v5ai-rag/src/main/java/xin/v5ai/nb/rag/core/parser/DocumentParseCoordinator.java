package xin.v5ai.nb.rag.core.parser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentParseCoordinator {

    private final DocumentParseEngineRegistry engineRegistry;

    public ParsedDocument parse(ParseRequest request) {
        String configuredName = request.params() == null ? null : request.params().getEngine();
        DocumentParseEngine selected = engineRegistry.resolve(configuredName);
        if ("default".equalsIgnoreCase(selected.engine())) {
            return selected.parse(request);
        }

        try {
            ParsedDocument parsed = selected.parse(request);
            if (parsed == null || parsed.isBlank()) {
                log.warn("文档解析引擎返回空结果，降级到 default: engine={}, documentId={}, knowledgeBaseId={}",
                        selected.engine(), request.documentId(), request.knowledgeBaseId());
                return engineRegistry.defaultEngine().parse(request);
            }
            return parsed;
        } catch (Exception ex) {
            log.warn("文档解析引擎不可用，降级到 default: engine={}, documentId={}, knowledgeBaseId={}",
                    selected.engine(), request.documentId(), request.knowledgeBaseId(), ex);
            return engineRegistry.defaultEngine().parse(request);
        }
    }
}
