package xin.v5ai.nb.runtime.mapper;

import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.runtime.domain.RuntimeMessage;
import xin.v5ai.nb.runtime.domain.vo.RuntimeMessageVo;

@Mapper
public interface RuntimeMessageMapper extends BaseMapperPlus<RuntimeMessage, RuntimeMessageVo> {

    // 会话列表的「首条提问预览」查询（selectFirstUserMessages / DISTINCT ON v5ai_message）已删除：
    // 会话名在创建时就由首条提问写好（见 ConversationNaming），列表只读 v5ai_conversation。
}
