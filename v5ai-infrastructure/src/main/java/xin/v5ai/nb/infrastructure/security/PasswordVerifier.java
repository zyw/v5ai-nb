package xin.v5ai.nb.infrastructure.security;

public interface PasswordVerifier {
    boolean matches(String rawPassword, String encodedPassword);
}
