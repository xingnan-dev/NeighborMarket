package com.neighbormarket.web;

import com.neighbormarket.domain.AppUser;
import com.neighbormarket.mapper.AppUserMapper;
import com.neighbormarket.web.dto.AuthModels;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionFixationProtectionStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthControllerSessionTest {
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void successfulLoginRotatesExistingSessionAndPersistsAuthentication() {
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        AppUserMapper users = mock(AppUserMapper.class);
        var authenticated = UsernamePasswordAuthenticationToken.authenticated(
                "neighbor_a", "", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(authenticationManager.authenticate(any())).thenReturn(authenticated);

        AppUser user = new AppUser();
        user.setId(1L);
        user.setUsername("neighbor_a");
        user.setNickname("林邻");
        when(users.selectOne(any())).thenReturn(user);

        var repository = new HttpSessionSecurityContextRepository();
        var controller = new AuthController(
                authenticationManager,
                users,
                new SessionFixationProtectionStrategy(),
                repository);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        String sessionIdBeforeLogin = request.getSession(true).getId();

        AuthModels.UserResponse result = controller.login(
                new AuthModels.LoginRequest("neighbor_a", "configured-secret"),
                request,
                response);

        assertEquals(1L, result.id());
        assertNotEquals(sessionIdBeforeLogin, request.getSession(false).getId());
        Object stored = request.getSession(false)
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertInstanceOf(SecurityContext.class, stored);
        assertEquals("neighbor_a", ((SecurityContext) stored).getAuthentication().getName());
    }
}
