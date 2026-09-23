package xin.v5ai.nb.common.agentscope.core;

/**
 * Stdio 命令执行策略：Stdio 传输受命令白名单限制，禁止任意命令与路径穿越。
 * 命令可以是 PATH 上的纯可执行文件名，也可以是可执行文件路径（如 Windows 下的 node.exe 绝对路径）。
 * 实现方提供白名单来源（如配置文件）。
 */
public interface StdioCommandPolicy {

    /** 校验命令是否允许执行；不允许时抛出 {@link IllegalArgumentException}。 */
    void validate(String command);
}
