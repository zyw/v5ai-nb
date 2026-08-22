package xin.v5ai.nb.platform.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "用户名不能为空")
        @Size(max = 100, message = "用户名长度不能超过100")
        String username,
        @NotBlank(message = "密码不能为空")
        @Size(max = 20, message = "密码长度不能超过20")
        String password
) {
}
