package com.neighbormarket.security;

import com.neighbormarket.domain.AppUser;
import com.neighbormarket.mapper.AppUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DatabaseUserDetailsServiceTest {
    @Test
    void loadsStoredCredentialsAndUserRole() {
        AppUserMapper users = mock(AppUserMapper.class);
        AppUser user = new AppUser();
        user.setUsername("neighbor_a");
        user.setPasswordHash("{bcrypt}stored-hash");
        when(users.selectOne(any())).thenReturn(user);

        var details = new DatabaseUserDetailsService(users).loadUserByUsername("neighbor_a");

        assertEquals("neighbor_a", details.getUsername());
        assertEquals("{bcrypt}stored-hash", details.getPassword());
        assertTrue(details.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_USER")));
    }

    @Test
    void unknownAccountUsesGenericAuthenticationMessage() {
        AppUserMapper users = mock(AppUserMapper.class);
        when(users.selectOne(any())).thenReturn(null);

        var error = assertThrows(UsernameNotFoundException.class,
                () -> new DatabaseUserDetailsService(users).loadUserByUsername("missing"));

        assertEquals("账号或密码错误", error.getMessage());
    }
}
