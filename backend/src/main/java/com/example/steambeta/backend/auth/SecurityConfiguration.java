package com.example.steambeta.backend.auth;

import com.example.steambeta.backend.library.LibraryService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods=false)
@ConditionalOnWebApplication(type=ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfiguration {
    public static void error(HttpServletResponse response,int status,String code) throws IOException {
        response.setStatus(status); response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\""+code+"\"}");
    }
    @Bean SecurityFilterChain security(HttpSecurity http,AccountLookup accounts,PasswordEncoder encoder,
                                      LibraryService library,ObjectMapper json) throws Exception {
        var provider=new DaoAuthenticationProvider(accounts);
        provider.setPasswordEncoder(encoder);
        http.authenticationProvider(provider)
            .csrf(csrf->csrf.csrfTokenRepository(new HttpSessionCsrfTokenRepository()))
            .authorizeHttpRequests(auth->auth
                .requestMatchers("/api/auth/csrf","/api/auth/login").permitAll()
                .anyRequest().authenticated())
            .sessionManagement(session->session.sessionFixation(fixation->fixation.changeSessionId()))
            .requestCache(cache->cache.disable())
            .httpBasic(basic->basic.disable())
            .formLogin(form->form.loginProcessingUrl("/api/auth/login").usernameParameter("login")
                .successHandler((request,response,authentication)->{
                    response.setContentType("application/json;charset=UTF-8");
                    json.writeValue(response.getWriter(),library.me());
                })
                .failureHandler((request,response,exception)->error(response,401,"invalid_credentials")))
            .logout(logout->logout.logoutUrl("/api/auth/logout").invalidateHttpSession(true)
                .clearAuthentication(true).deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request,response,authentication)->response.setStatus(204)))
            .exceptionHandling(errors->errors
                .authenticationEntryPoint((request,response,exception)->error(response,401,"session_required"))
                .accessDeniedHandler((request,response,exception)->error(response,403,"request_rejected")));
        return http.build();
    }
}
