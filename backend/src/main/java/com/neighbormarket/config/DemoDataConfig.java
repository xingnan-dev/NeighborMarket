package com.neighbormarket.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.neighbormarket.domain.AppUser;
import com.neighbormarket.mapper.AppUserMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;

@Configuration
public class DemoDataConfig {
    @Bean
    @Profile("demo")
    CommandLineRunner seedDemoUsers(
            AppUserMapper users,
            PasswordEncoder encoder,
            @Value("${app.demo.user-a.password:}") String userAPassword,
            @Value("${app.demo.user-b.password:}") String userBPassword) {
        return args -> {
            requirePassword("DEMO_USER_A_PASSWORD", userAPassword);
            requirePassword("DEMO_USER_B_PASSWORD", userBPassword);
            createIfMissing(users, encoder, "neighbor_a", userAPassword, "林邻");
            createIfMissing(users, encoder, "neighbor_b", userBPassword, "周周");
        };
    }

    private void requirePassword(String environmentName, String password) {
        if (!StringUtils.hasText(password)) {
            throw new IllegalStateException("demo profile requires " + environmentName);
        }
    }

    private void createIfMissing(AppUserMapper users, PasswordEncoder encoder, String username, String password, String nickname) {
        Long count = users.selectCount(Wrappers.lambdaQuery(AppUser.class).eq(AppUser::getUsername, username));
        if (count == 0) {
            var user = new AppUser();
            user.setUsername(username);
            user.setPasswordHash(encoder.encode(password));
            user.setNickname(nickname);
            users.insert(user);
        }
    }
}
