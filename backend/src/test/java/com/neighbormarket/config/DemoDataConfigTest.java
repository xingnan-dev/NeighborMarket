package com.neighbormarket.config;

import com.neighbormarket.domain.AppUser;
import com.neighbormarket.mapper.AppUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DemoDataConfigTest {
    @Test
    void demoSeederIsAbsentWithoutDemoProfile() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(DemoDataConfig.class);
            context.refresh();

            assertEquals(0, context.getBeansOfType(CommandLineRunner.class).size());
        }
    }

    @Test
    void demoSeederUsesConfiguredPasswords() throws Exception {
        AppUserMapper users = mock(AppUserMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(users.selectCount(any())).thenReturn(0L);
        when(encoder.encode("configured-a")).thenReturn("hash-a");
        when(encoder.encode("configured-b")).thenReturn("hash-b");

        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("demo");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "app.demo.user-a.password", "configured-a",
                    "app.demo.user-b.password", "configured-b")));
            context.registerBean(AppUserMapper.class, () -> users);
            context.registerBean(PasswordEncoder.class, () -> encoder);
            context.register(DemoDataConfig.class);
            context.refresh();

            context.getBean(CommandLineRunner.class).run();
        }

        verify(encoder).encode("configured-a");
        verify(encoder).encode("configured-b");
        verify(users, times(2)).insert(any(AppUser.class));
    }

    @Test
    void demoSeederFailsFastWhenPasswordIsMissing() {
        AppUserMapper users = mock(AppUserMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        CommandLineRunner runner = new DemoDataConfig().seedDemoUsers(users, encoder, "", "configured-b");

        assertThrows(IllegalStateException.class, () -> runner.run());
        verifyNoInteractions(users, encoder);
    }
}
