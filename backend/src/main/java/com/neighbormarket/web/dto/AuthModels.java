package com.neighbormarket.web.dto;

import jakarta.validation.constraints.NotBlank;

public final class AuthModels {
    private AuthModels() {
    }

    public record LoginRequest(
            @NotBlank(message = "请输入账号") String username,
            @NotBlank(message = "请输入密码") String password) {
    }

    public record UserResponse(Long id, String username, String nickname) {
    }

    public record CsrfResponse(String token) {
    }
}
