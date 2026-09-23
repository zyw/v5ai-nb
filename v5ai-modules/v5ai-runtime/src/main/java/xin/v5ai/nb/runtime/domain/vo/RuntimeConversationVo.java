package xin.v5ai.nb.runtime.domain.vo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import xin.v5ai.nb.common.mybatis.core.domain.BaseEntity;
import xin.v5ai.nb.runtime.domain.RuntimeConversation;

import java.io.Serial;
import java.io.Serializable;

@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target= RuntimeConversation.class)
public class RuntimeConversationVo extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;

    private String agentKey;

    private Long userId;

}
