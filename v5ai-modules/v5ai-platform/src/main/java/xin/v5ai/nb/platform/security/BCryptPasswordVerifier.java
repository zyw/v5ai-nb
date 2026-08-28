//package xin.v5ai.nb.platform.security;
//
//import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
//
//public final class BCryptPasswordVerifier implements PasswordVerifier {
//    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
//
//    @Override
//    public boolean matches(String rawPassword, String encodedPassword) {
//        return encoder.matches(rawPassword, encodedPassword);
//    }
//}
