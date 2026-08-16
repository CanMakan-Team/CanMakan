package com.canmakan.backend.shared.security;

import com.canmakan.backend.auth.RefreshTokenProperties;
import com.canmakan.backend.family.InviteProperties;
import com.canmakan.backend.family.ResendProperties;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/** Spring Security authentication foundation for UC19. */
@Configuration
@EnableConfigurationProperties({
    JwtProperties.class,
    RefreshTokenProperties.class,
    InviteProperties.class,
    ResendProperties.class
})
public class SecurityConfig {

    private static final int BCRYPT_STRENGTH = 10;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    public AuthenticationProvider authenticationProvider(
            AuthUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        // Validate the secret before exposing the AC3 suspended-account distinction.
        provider.setPreAuthenticationChecks(user -> { });
        provider.setPostAuthenticationChecks(SecurityConfig::rejectUnavailableAccount);
        return provider;
    }

    /**
     * Runs after the password matches. Locked, disabled, expired-account and expired-credential
     * users all fail here so a wrong password still looks like generic bad credentials.
     */
    static void rejectUnavailableAccount(UserDetails user) {
        if (!user.isAccountNonLocked()) {
            throw new LockedException("Account is unavailable");
        }
        if (!user.isEnabled()) {
            throw new DisabledException("Account is unavailable");
        }
        if (!user.isAccountNonExpired()) {
            throw new AccountExpiredException("Account is unavailable");
        }
        if (!user.isCredentialsNonExpired()) {
            throw new CredentialsExpiredException("Account is unavailable");
        }
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationProvider authenticationProvider) {
        return new ProviderManager(authenticationProvider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler,
            CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
            .formLogin(formLogin -> formLogin.disable())
            .httpBasic(httpBasic -> httpBasic.disable())
            .logout(logout -> logout.disable())
            .requestCache(requestCache -> requestCache.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/invitations/*/preview").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/api/auth/account").authenticated()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/families/**").authenticated()
                .requestMatchers("/api/invitations/**").authenticated()
                .requestMatchers("/api/notifications/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/scan/assess").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/scan/validate").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/scan/history/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/profiles/me").hasRole("USER")
                .requestMatchers(HttpMethod.GET, "/api/profiles/me").hasRole("USER")
                .requestMatchers(HttpMethod.PUT, "/api/profiles/me").hasRole("USER")
                .requestMatchers("/api/profiles/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/restrictions").authenticated()
                .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                .requestMatchers("/api/**").authenticated()
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .anyRequest().denyAll())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
