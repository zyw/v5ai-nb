package xin.v5ai.nb.api.auth;

import org.springframework.stereotype.Service;
import xin.v5ai.nb.common.auth.UserCredentialStore;
import xin.v5ai.nb.infrastructure.security.PasswordVerifier;

@Service
public class V5aiLoginService {
    private final UserCredentialStore credentialStore;
    private final PasswordVerifier passwordVerifier;
    private final LoginTokenIssuer tokenIssuer;

    public V5aiLoginService(UserCredentialStore credentialStore, PasswordVerifier passwordVerifier,
                            LoginTokenIssuer tokenIssuer) {
        this.credentialStore = credentialStore;
        this.passwordVerifier = passwordVerifier;
        this.tokenIssuer = tokenIssuer;
    }

    public LoginResponse login(LoginRequest request) {
        var passwordHash = credentialStore.findPasswordHash(request.username());
        if (passwordHash == null || !passwordVerifier.matches(request.password(), passwordHash)) {
            throw new IllegalArgumentException("invalid credentials");
        }
        return new LoginResponse("Bearer", tokenIssuer.issue(request.username()));
    }

    public void logout() {
        tokenIssuer.logout();
    }
}
