package xin.v5ai.nb.rag.core.parser;

/**
 * 结构化文档解析引擎端口。
 */
public interface DocumentParseEngine {

    ParsedDocument parse(ParseRequest request);

    String engine();
}
