package xin.v5ai.nb.platform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import xin.v5ai.nb.common.mybatis.core.mapper.BaseMapperPlus;
import xin.v5ai.nb.platform.domain.User;
import xin.v5ai.nb.platform.domain.vo.UserVo;

/**
 * 用户表（v5ai_user）Mapper：合并原 auth.UserMapper（findByUsername 凭据查询）
 * 与 platform.UserAccountMapper（BaseMapper CRUD）。
 */
@Mapper
public interface UserMapper extends BaseMapperPlus<User, UserVo> {

    User findByUsername(@Param("username") String username);
}
