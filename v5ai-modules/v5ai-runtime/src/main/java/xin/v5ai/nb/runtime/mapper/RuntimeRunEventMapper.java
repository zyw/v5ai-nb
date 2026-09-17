package xin.v5ai.nb.runtime.mapper;

import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.runtime.domain.RuntimeRunEvent;
import xin.v5ai.nb.runtime.domain.vo.RuntimeRunEventVo;

@Mapper
public interface RuntimeRunEventMapper extends BaseMapperPlus<RuntimeRunEvent, RuntimeRunEventVo> {

}
