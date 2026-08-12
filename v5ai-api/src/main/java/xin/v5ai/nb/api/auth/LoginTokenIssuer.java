package xin.v5ai.nb.api.auth;

public interface LoginTokenIssuer {
    String issue(String username);

    void logout();
}
