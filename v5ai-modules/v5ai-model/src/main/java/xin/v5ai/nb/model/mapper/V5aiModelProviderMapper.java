package xin.v5ai.nb.model.mapper;

import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.model.domain.V5aiModelProvider;
import xin.v5ai.nb.model.domain.vo.V5aiModelProviderVo;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author ZYW
 * @since 2026-08-20
 */
@Mapper
public interface V5aiModelProviderMapper extends BaseMapperPlus<V5aiModelProvider, V5aiModelProviderVo> {

}
