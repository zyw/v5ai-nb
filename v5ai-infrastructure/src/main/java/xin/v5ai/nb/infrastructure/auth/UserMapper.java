package xin.v5ai.nb.infrastructure.auth;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {
    @Select("""
            SELECT id, username, password_hash, enabled, tenant_id, display_name
            FROM v5ai_user
            WHERE username = #{username}
            """)
    UserEntity findByUsername(@Param("username") String username);
}
