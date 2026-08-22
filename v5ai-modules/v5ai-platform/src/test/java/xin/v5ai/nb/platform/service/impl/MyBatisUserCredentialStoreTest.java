package xin.v5ai.nb.platform.service.impl;

import org.junit.jupiter.api.Test;
import xin.v5ai.nb.platform.domain.User;
import xin.v5ai.nb.platform.mapper.UserMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MyBatisUserCredentialStoreTest {

    private static UserMapper mapperWith(User user) {
        var mapper = mock(UserMapper.class);
        when(mapper.findByUsername(anyString())).thenReturn(user);
        return mapper;
    }

    @Test
    void returnsPasswordHashForActiveUser() {
        var user = new User();
        user.setUsername("alice");
        user.setPasswordHash("hash");
        user.setStatus("0");
        var store = new MyBatisUserCredentialStore(mapperWith(user));

        String passwordHash = store.findPasswordHash("alice");

        assertThat(passwordHash).isEqualTo("hash");
    }

    @Test
    void returnsNullForDisabledUser() {
        var user = new User();
        user.setUsername("alice");
        user.setPasswordHash("hash");
        user.setStatus("1");
        var store = new MyBatisUserCredentialStore(mapperWith(user));

        String passwordHash = store.findPasswordHash("alice");

        assertThat(passwordHash).isNull();
    }

    @Test
    void returnsNullForMissingUser() {
        var store = new MyBatisUserCredentialStore(mapperWith(null));

        String passwordHash = store.findPasswordHash("missing");

        assertThat(passwordHash).isNull();
    }
}
