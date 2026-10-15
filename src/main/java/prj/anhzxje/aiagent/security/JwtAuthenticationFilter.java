package prj.anhzxje.aiagent.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter trích xuất JWT từ header Authorization,
 * validate token, load UserDetails, và set SecurityContext.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService userDetailsService;

    /**
     * Phương thức lọc từng yêu cầu HTTP đến ứng dụng:
     * 1. Đọc Authorization header dạng 'Bearer <token>'
     * 2. Giải mã và kiểm tra tính hợp lệ của JWT (chữ ký, hạn sử dụng)
     * 3. Kiểm tra trạng thái tài khoản có bị Quản trị viên khóa (isLocked) hay không
     * 4. Nạp quyền hạn (Role USER/ADMIN) vào SecurityContextHolder
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        try {
            // Bước 1: Trích xuất token từ Header
            String jwt = getJwtFromRequest(request);

            // Bước 2: Kiểm tra token có tồn tại và hợp lệ không
            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
                String username = jwtTokenProvider.getUsername(jwt);

                // Bước 3: Nạp thông tin người dùng từ CSDL
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                // Bước 4: Kiểm tra trạng thái khóa tài khoản
                if (!userDetails.isAccountNonLocked()) {
                    log.warn("Tài khoản [{}] đã bị khóa, từ chối phiên xác thực", username);
                } else {
                    // Bước 5: Thiết lập phiên xác thực hợp lệ vào Security Context
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());

                    authentication.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception e) {
            log.error("Không thể xác thực người dùng: {}", e.getMessage());
        }

        // Chuyển tiếp request đến filter tiếp theo trong chuỗi FilterChain
        filterChain.doFilter(request, response);
    }

    /**
     * Trích xuất chuỗi JWT token từ HTTP header 'Authorization' (bỏ tiền tố 'Bearer ').
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
