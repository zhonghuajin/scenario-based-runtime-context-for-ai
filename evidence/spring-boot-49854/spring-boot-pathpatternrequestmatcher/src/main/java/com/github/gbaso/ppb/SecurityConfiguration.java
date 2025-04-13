package com.github.gbaso.ppb;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import static org.springframework.security.config.Customizer.withDefaults;

class SecurityConfiguration {

	@Configuration(proxyBeanMethods = false)
	static class WithHttpSecurityCustomizer {

		@Bean
		Customizer<HttpSecurity> securityCustomizer(PathPatternRequestMatcher.Builder mvc) {
			return http -> http
				.authorizeHttpRequests(auth -> auth
					.requestMatchers(mvc.matcher("/hello")).permitAll());
		}

	}

	@Configuration(proxyBeanMethods = false)
	static class WithSecurityFilterChain {

		@Bean
		SecurityFilterChain securityFilterChain(HttpSecurity http, PathPatternRequestMatcher.Builder mvc) {
			return http
				.authorizeHttpRequests(auth -> auth
					.requestMatchers(mvc.matcher("/hello")).permitAll()
					.anyRequest().authenticated())
				.formLogin(withDefaults())
				.httpBasic(withDefaults())
				.build();
		}

	}

}
