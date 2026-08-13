package xin.v5ai.nb.common.auth;

public interface UserCredentialStore {
    String findPasswordHash(String username);
}
