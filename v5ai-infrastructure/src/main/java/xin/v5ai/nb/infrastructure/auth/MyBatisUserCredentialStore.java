package xin.v5ai.nb.infrastructure.auth;

import org.springframework.stereotype.Repository;
import xin.v5ai.nb.common.auth.UserCredentialStore;

@Repository
public class MyBatisUserCredentialStore implements UserCredentialStore {
    private final UserMapper userMapper;

    public MyBatisUserCredentialStore(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public String findPasswordHash(String username) {
        UserEntity user = userMapper.findByUsername(username);
        if (user == null || !Boolean.TRUE.equals(user.getEnabled())) {
            return null;
        }
        return user.getPasswordHash();
    }
}
