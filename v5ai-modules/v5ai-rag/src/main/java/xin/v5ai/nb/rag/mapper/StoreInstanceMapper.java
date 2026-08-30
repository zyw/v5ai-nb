package xin.v5ai.nb.rag.mapper;

import org.apache.ibatis.annotations.Mapper;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.rag.domain.StoreInstance;
import xin.v5ai.nb.rag.domain.vo.StoreInstanceVo;

/**
 * <p>
 * 存储实例 Mapper 接口
 * </p>
 *
 * @author ZYW
 * @since 2026-08-30
 */
@Mapper
public interface StoreInstanceMapper extends BaseMapperPlus<StoreInstance, StoreInstanceVo> {
}
