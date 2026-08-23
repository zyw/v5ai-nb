package xin.v5ai.nb.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import xin.v5ai.nb.platform.domain.User;

/**
 * 用户表（v5ai_user）Mapper：合并原 auth.UserMapper（findByUsername 凭据查询）
 * 与 platform.UserAccountMapper（BaseMapper CRUD）。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    User findByUsername(@Param("username") String username);
}
