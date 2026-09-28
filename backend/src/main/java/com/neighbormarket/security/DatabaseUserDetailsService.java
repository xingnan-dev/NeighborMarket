package com.neighbormarket.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.neighbormarket.mapper.AppUserMapper;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final AppUserMapper users;
    public DatabaseUserDetailsService(AppUserMapper users) { this.users = users; }
    @Override
    public UserDetails loadUserByUsername(String username) {
        var user = users.selectOne(Wrappers.lambdaQuery(com.neighbormarket.domain.AppUser.class)
                .eq(com.neighbormarket.domain.AppUser::getUsername, username));
        if (user == null) throw new UsernameNotFoundException("账号或密码错误");
        return User.withUsername(user.getUsername()).password(user.getPasswordHash()).roles("USER").build();
    }
}
