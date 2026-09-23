package xin.v5ai.nb.runtime.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xin.v5ai.nb.common.mybatis.core.domain.BaseEntity;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("v5ai_conversation")
public class RuntimeConversation extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id")
    private String id;

    private String agentKey;

    /**
     * 会话归属的 API Key（门户侧租户边界）；调试入口产生的会话为 null。
     */
    private Long apiKeyId;

    /**
     * 会话名称（调用方可改，1-100 字符）；null 表示未命名。
     *
     * <p>新建会话时由首条提问生成（{@code ConversationNaming.fromQuery}），
     * 首轮结束后可能被模型改写成短标题（见 {@code ConversationTitleWriter}）。</p>
     */
    private String name;

    /**
     * 名称来源：{@code AUTO}=系统生成（可被模型改写），{@code USER}=用户改名（永不被覆盖）。
     */
    private String nameSource;

    /**
     * 归档时间；非空表示已归档（列表默认隐藏且禁止继续对话）。
     */
    private OffsetDateTime archivedAt;

    private Long userId;

}
