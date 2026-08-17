package xin.v5ai.nb.common.core.auth;

public interface UserCredentialStore {
    String findPasswordHash(String username);
}
