package com.hatodo.config;

import com.hatodo.live.LiveLogoutHandler;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * Rotates the session id on login to defeat session fixation. Safe when no
     * session exists yet - it simply does nothing in that case.
     */
    @Bean
    public SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new ChangeSessionIdAuthenticationStrategy();
    }

    @Bean
    public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                       PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   SecurityContextRepository securityContextRepository,
                                                   LiveLogoutHandler liveLogoutHandler)
            throws Exception {
        // Session cookie auth means CSRF protection stays on. The token goes in a
        // readable cookie so the SPA can echo it back in X-XSRF-TOKEN, which is
        // what axios does by default for same-origin requests.
        CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();

        http
            .securityContext(context -> context.securityContextRepository(securityContextRepository))
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfTokenRepository)
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .authorizeHttpRequests(auth -> auth
                // Without this, a 500 inside a controller is forwarded to /error,
                // blocked by the rule below, and surfaces as a misleading 401.
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                // The sign-in screen is a list of users, so the roster has to be
                // readable before anyone holds a session. The requirement document
                // records that exposure deliberately (requirements/0002).
                .requestMatchers("/api/auth/login", "/api/auth/csrf", "/api/auth/users").permitAll()
                        // The sign-in screen draws each member's picture, and it
                        // draws it before anyone has a session (adr/0019). The
                        // controller still refuses every purpose but AVATAR, which
                        // is what keeps a list's pictures behind its grant.
                        .requestMatchers(HttpMethod.GET, "/api/images/*").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                .anyRequest().authenticated())
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                // Closes this member's live streams. Nothing re-checks
                // authorization on an emitter that is already open, so without
                // this a signed-out tab keeps receiving until its stream times
                // out. See adr/0054.
                .addLogoutHandler(liveLogoutHandler)
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) ->
                    response.setStatus(HttpStatus.NO_CONTENT.value())))
            // An API returns 401 for anonymous callers; it never redirects to a login page.
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable());

        return http.build();
    }
}
