package xin.v5ai.nb.runtime.mapper;

import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.runtime.domain.RuntimeConversation;
import xin.v5ai.nb.runtime.domain.vo.RuntimeConversationVo;

@Mapper
public interface RuntimeConversationMapper extends BaseMapperPlus<RuntimeConversation, RuntimeConversationVo> {

}
