package xin.v5ai.nb.infrastructure.auth;

import org.springframework.stereotype.Repository;
import xin.v5ai.nb.common.core.auth.UserCredentialStore;

@Repository
public class MyBatisUserCredentialStore implements UserCredentialStore {
    private final UserMapper userMapper;

    public MyBatisUserCredentialStore(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public String findPasswordHash(String username) {
        UserEntity user = userMapper.findByUsername(username);
        // 账号状态：'0' 正常、'1' 停用（status 为空视为停用，安全兜底）
        if (user == null || !"0".equals(user.getStatus())) {
            return null;
        }
        return user.getPasswordHash();
    }
}
