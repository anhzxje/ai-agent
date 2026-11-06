package fixture.directory;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
@Configuration
class AccessConfiguration {
    @Bean SecurityFilterChain access(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated()).httpBasic(basic -> {}).build();
    }
}
