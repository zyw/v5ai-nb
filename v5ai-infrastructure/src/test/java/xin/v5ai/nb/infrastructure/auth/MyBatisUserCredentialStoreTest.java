package xin.v5ai.nb.infrastructure.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MyBatisUserCredentialStoreTest {
    @Test
    void returnsPasswordHashForActiveUser() {
        var mapper = new RecordingUserMapper();
        mapper.user = new UserEntity();
        mapper.user.setUsername("alice");
        mapper.user.setPasswordHash("hash");
        mapper.user.setEnabled(true);
        var store = new MyBatisUserCredentialStore(mapper);

        String passwordHash = store.findPasswordHash("alice");

        assertThat(passwordHash).isEqualTo("hash");
    }

    @Test
    void returnsNullForDisabledUser() {
        var mapper = new RecordingUserMapper();
        mapper.user = new UserEntity();
        mapper.user.setUsername("alice");
        mapper.user.setPasswordHash("hash");
        mapper.user.setEnabled(false);
        var store = new MyBatisUserCredentialStore(mapper);

        String passwordHash = store.findPasswordHash("alice");

        assertThat(passwordHash).isNull();
    }

    @Test
    void returnsNullForMissingUser() {
        var store = new MyBatisUserCredentialStore(new RecordingUserMapper());

        String passwordHash = store.findPasswordHash("missing");

        assertThat(passwordHash).isNull();
    }

    private static final class RecordingUserMapper implements UserMapper {
        private UserEntity user;

        @Override
        public UserEntity findByUsername(String username) {
            return user;
        }
    }
}
