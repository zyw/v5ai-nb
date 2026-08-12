package xin.v5ai.nb.api.auth;

public interface UserCredentialStore {
    String findPasswordHash(String username);
}
