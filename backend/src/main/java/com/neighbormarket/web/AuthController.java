package com.neighbormarket.web;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.neighbormarket.domain.AppUser;
import com.neighbormarket.mapper.AppUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final AppUserMapper users;
    public AuthController(AuthenticationConfiguration configuration, AppUserMapper users) throws Exception {
        this.authenticationManager = configuration.getAuthenticationManager();
        this.users = users;
    }
    @PostMapping("/login")
    public UserResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        Authentication auth = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(body.username(), body.password()));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        request.getSession(true).setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return response(auth.getName());
    }
    @GetMapping("/me")
    public UserResponse me(Authentication auth) { return response(auth.getName()); }
    private UserResponse response(String username) {
        AppUser user = users.selectOne(Wrappers.lambdaQuery(AppUser.class).eq(AppUser::getUsername, username));
        return new UserResponse(user.getId(), user.getUsername(), user.getNickname());
    }
    public record LoginRequest(@NotBlank(message="请输入账号") String username, @NotBlank(message="请输入密码") String password) {}
    public record UserResponse(Long id, String username, String nickname) {}
}
