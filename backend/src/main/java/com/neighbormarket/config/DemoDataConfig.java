package com.neighbormarket.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.neighbormarket.domain.AppUser;
import com.neighbormarket.mapper.AppUserMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DemoDataConfig {
    @Bean
    CommandLineRunner seedDemoUsers(AppUserMapper users, PasswordEncoder encoder) {
        return args -> {
            createIfMissing(users, encoder, "neighbor_a", "neighborA123!", "林邻");
            createIfMissing(users, encoder, "neighbor_b", "neighborB123!", "周周");
        };
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