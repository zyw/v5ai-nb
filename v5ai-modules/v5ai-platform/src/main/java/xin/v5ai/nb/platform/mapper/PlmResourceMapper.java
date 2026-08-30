package xin.v5ai.nb.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.platform.domain.PlmResource;
import xin.v5ai.nb.platform.domain.vo.PlmResourceVo;

/**
 * <p>
 * 通用资源存储 Mapper 接口
 * </p>
 *
 * @author ZYW
 * @since 2026-09-02
 */
@Mapper
public interface PlmResourceMapper extends BaseMapperPlus<PlmResource, PlmResourceVo> {
}
