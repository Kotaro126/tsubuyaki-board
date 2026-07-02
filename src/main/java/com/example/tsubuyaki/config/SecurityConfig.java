package com.example.tsubuyaki.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // 研修アプリの認証要件は増やさず、CSRF 保護だけを有効にする。
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .csrf(Customizer.withDefaults())
                .build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        // permitAll 構成でデフォルトユーザーを使わないため、空のユーザー管理を明示する。
        return new InMemoryUserDetailsManager();
    }
}
