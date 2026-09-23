package team.inreok.poppyserver.global.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.csrf.CsrfFilter
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher
import org.springframework.security.web.util.matcher.AndRequestMatcher
import org.springframework.security.web.util.matcher.NegatedRequestMatcher
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class SecurityConfig {

    @Bean
    fun securityFilterChain(http: HttpSecurity, adminCorsConfigurationSource: CorsConfigurationSource): SecurityFilterChain {
        val adminRequestMatcher = PathPatternRequestMatcher.pathPattern("/api/v1/admin/**")
        val adminLoginRequestMatcher = PathPatternRequestMatcher.pathPattern("/api/v1/admin/auth/login")

        http {
            cors {
                configurationSource = adminCorsConfigurationSource
            }
            csrf {
                requireCsrfProtectionMatcher = AndRequestMatcher(
                    CsrfFilter.DEFAULT_CSRF_MATCHER,
                    adminRequestMatcher,
                    NegatedRequestMatcher(adminLoginRequestMatcher),
                )
            }
            authorizeHttpRequests {
                authorize("/actuator/health/**", permitAll)
                authorize("/actuator/**", denyAll)
                authorize(anyRequest, permitAll)
            }
        }
        return http.build()
    }

    @Bean
    fun adminCorsConfigurationSource(
        @Value("\${poppy.admin.allowed-origins:}") allowedOrigins: String,
    ): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOrigins = allowedOrigins.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        configuration.allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
        configuration.allowedHeaders = listOf("Content-Type", "X-CSRF-TOKEN")
        configuration.allowCredentials = true

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/api/v1/admin/**", configuration)
        return source
    }

    @Bean
    fun userDetailsService(): UserDetailsService = UserDetailsService {
        throw UsernameNotFoundException("User details are not supported")
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
