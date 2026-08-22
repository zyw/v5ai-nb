package xin.v5ai.nb.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import xin.v5ai.nb.platform.domain.User;

/**
 * 用户表（v5ai_user）Mapper：合并原 auth.UserMapper（findByUsername 凭据查询）
 * 与 platform.UserAccountMapper（BaseMapper CRUD）。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    @Select("""
            SELECT id, username, password_hash, status, tenant_id, display_name
            FROM v5ai_user
            WHERE username = #{username}
            """)
    User findByUsername(@Param("username") String username);
}
