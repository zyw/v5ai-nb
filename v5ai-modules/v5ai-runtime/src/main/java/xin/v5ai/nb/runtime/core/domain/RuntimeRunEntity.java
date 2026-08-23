package xin.v5ai.nb.runtime.core.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

@Data
@TableName("v5ai_run")
public class RuntimeRunEntity implements Serializable {
    @TableId
    private String id;
    private String conversationId;
    private String agentKey;
    private String status;
    @TableField("error_message")
    private String errorMessage;
}
