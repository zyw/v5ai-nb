package xin.v5ai.nb.runtime.core.service;

import xin.v5ai.nb.runtime.core.domain.ConversationDTO;

import java.util.List;
import java.util.Optional;

/**
 * 会话仓储端口：会话记录的持久化与门户侧的会话管理。
 *
 * <p>归属判定一律以 {@code apiKeyId} 为准（ADR 0006），{@code userId} 只是冗余展示字段。</p>
 */
public interface ConversationService {

    /**
     * 确保会话存在，并做归属与状态断言：
     * <ul>
     *   <li>不存在：按传入的 {@code apiKeyId} / {@code userId} 创建；</li>
     *   <li>已存在且请求来自 API Key：归属不匹配（包括对方是调试入口产生的无主会话）→ 404，
     *       防止 Key B 猜到会话 ID 就写进别人的会话；</li>
     *   <li>已存在且已归档：继续对话 → 409（归档是收尾态，不是隐藏）；</li>
     *   <li>请求不带 API Key（管理端调试）：不做归属断言，仅保留归档检查。</li>
     * </ul>
     *
     * <p>新建时会把 {@code conversation.name()} 写进 {@code name} 列（首条提问的兜底名），
     * 并把来源标记为 {@code AUTO}；已存在的会话**绝不改写名称**——用户改过的名字必须留住。</p>
     *
     * @param conversation 要保证存在的会话（必须带 conversationId 与 agentKey）
     * @return true 表示本次真的新建了会话（调用方据此决定要不要在首轮结束后改写标题）
     */
    boolean ensureConversation(ConversationDTO conversation);

    /**
     * 按会话 ID 读取会话。
     *
     * @param conversationId 会话 ID
     * @return 会话；不存在时为空
     */
    Optional<ConversationDTO> findById(String conversationId);

    /**
     * 列出会话：限「本 Key + 本 Agent」，按最近活跃倒序。
     *
     * @param agentKey  Agent 标识
     * @param apiKeyId  归属的 API Key
     * @param archived  true 列出已归档，false 列出未归档
     * @return 会话列表（无匹配时为空列表）
     */
    List<ConversationDTO> list(String agentKey, Long apiKeyId, boolean archived);

    /**
     * 用户改名：同时把名称来源标记为 {@code USER}，此后任何自动命名都不再覆盖它。
     *
     * @param conversationId 会话 ID
     * @param name           新名称（已由调用方校验长度与空白）
     * @return 是否命中一行
     */
    boolean rename(String conversationId, String name);

    /**
     * 自动命名回写：**仅当名称来源仍是 {@code AUTO}** 时才改（用户改过名的会话改不动）。
     *
     * <p>条件写在 UPDATE 的 WHERE 里而不是先查后写：用户改名与异步标题生成可能并发，
     * 由数据库做这一次判定，天然没有竞态窗口。</p>
     *
     * @param conversationId 会话 ID
     * @param name           自动生成的名称（已由调用方清洗与截断）
     * @return 是否命中一行（false = 用户已改名或会话不存在，两种情况都不该重试）
     */
    boolean renameIfAutoNamed(String conversationId, String name);

    /**
     * 归档 / 取消归档。
     *
     * @param conversationId 会话 ID
     * @param archived       true 归档（写入当前时间），false 取消归档（清空）
     * @return 是否命中一行
     */
    boolean setArchived(String conversationId, boolean archived);
}
