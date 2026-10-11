package com.neighbormarket.web;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.neighbormarket.domain.AppUser;
import com.neighbormarket.mapper.AppUserMapper;
import com.neighbormarket.web.dto.AuthModels;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final AppUserMapper users;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(
            AuthenticationManager authenticationManager,
            AppUserMapper users,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            SecurityContextRepository securityContextRepository) {
        this.authenticationManager = authenticationManager;
        this.users = users;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
    }
    @PostMapping("/login")
    public AuthModels.UserResponse login(
            @Valid @RequestBody AuthModels.LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        Authentication auth = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(body.username(), body.password()));
        sessionAuthenticationStrategy.onAuthentication(auth, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        return response(auth.getName());
    }
    @GetMapping("/me")
    public AuthModels.UserResponse me(Authentication auth) { return response(auth.getName()); }

    @GetMapping("/csrf")
    public AuthModels.CsrfResponse csrf(CsrfToken token) {
        return new AuthModels.CsrfResponse(token.getToken());
    }

    private AuthModels.UserResponse response(String username) {
        AppUser user = users.selectOne(Wrappers.lambdaQuery(AppUser.class).eq(AppUser::getUsername, username));
        return new AuthModels.UserResponse(user.getId(), user.getUsername(), user.getNickname());
    }
}
