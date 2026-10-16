package prj.anhzxje.aiagent.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import prj.anhzxje.aiagent.dto.auth.AuthResponse;
import prj.anhzxje.aiagent.dto.auth.LoginRequest;
import prj.anhzxje.aiagent.dto.auth.RegisterRequest;
import prj.anhzxje.aiagent.entity.User;
import prj.anhzxje.aiagent.enums.Role;
import prj.anhzxje.aiagent.repository.UserRepository;
import prj.anhzxje.aiagent.security.CustomUserDetails;
import prj.anhzxje.aiagent.security.JwtTokenProvider;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử Đơn vị cho AuthService (AuthServiceTest):
 * Kiểm tra các tính năng cốt lõi:
 * 1. Đăng ký tài khoản thành công và sinh JWT token.
 * 2. Đăng ký trùng lặp username hoặc email.
 * 3. Đăng nhập thành công với thông tin chính xác.
 * 4. Đăng nhập tài khoản bị khóa.
 * 5. Đăng nhập sai mật khẩu (BadCredentialsException).
 * 6. Đăng xuất ghi nhật ký kiểm toán.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setUsername("testuser");
        registerRequest.setEmail("test@example.com");
        registerRequest.setPassword("password123");

        loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("password123");
    }

    @Test
    void testRegisterUser_Success() {
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPass");
        when(jwtTokenProvider.generateToken(eq("testuser"), anyString())).thenReturn("mockToken");

        AuthResponse res = authService.registerUser(registerRequest);

        assertNotNull(res);
        assertEquals("mockToken", res.getToken());
        assertEquals("testuser", res.getUsername());
        assertEquals("Đăng ký thành công", res.getMessage());
        verify(userRepository, times(1)).save(any(User.class));
        verify(auditLogService, times(1)).log(eq("testuser"), eq("REGISTER"), anyString());
    }

    @Test
    void testRegisterUser_DuplicateUsername() {
        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        AuthResponse res = authService.registerUser(registerRequest);

        assertNotNull(res);
        assertNull(res.getToken());
        assertEquals("Username đã tồn tại", res.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testRegisterUser_DuplicateEmail() {
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        AuthResponse res = authService.registerUser(registerRequest);

        assertNotNull(res);
        assertNull(res.getToken());
        assertEquals("Email đã được sử dụng", res.getMessage());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testLoginUser_Success() {
        User user = User.builder().id(1L).username("testuser").role(Role.USER).isLocked(false).build();
        CustomUserDetails userDetails = new CustomUserDetails(user);
        Authentication auth = new UsernamePasswordAuthenticationToken(userDetails, null);

        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);
        when(jwtTokenProvider.generateToken("testuser", "USER")).thenReturn("mockToken");

        AuthResponse res = authService.loginUser(loginRequest);

        assertNotNull(res);
        assertEquals("mockToken", res.getToken());
        assertEquals("Đăng nhập thành công", res.getMessage());
        verify(auditLogService, times(1)).log(eq("testuser"), eq("LOGIN"), anyString());
    }

    @Test
    void testLoginUser_LockedAccount() {
        User user = User.builder().id(1L).username("testuser").role(Role.USER).isLocked(true).build();
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));

        AuthResponse res = authService.loginUser(loginRequest);

        assertNotNull(res);
        assertNull(res.getToken());
        assertEquals("Tài khoản của bạn đã bị khóa bởi Quản trị viên", res.getMessage());
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void testLoginUser_BadCredentials() {
        User user = User.builder().id(1L).username("testuser").role(Role.USER).isLocked(false).build();
        when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Sai mật khẩu"));

        assertThrows(BadCredentialsException.class, () -> authService.loginUser(loginRequest));
    }

    @Test
    void testLogoutUser() {
        User user = User.builder().id(1L).username("testuser").role(Role.USER).build();
        CustomUserDetails userDetails = new CustomUserDetails(user);

        Map<String, String> res = authService.logoutUser(userDetails);

        assertEquals("Đăng xuất thành công", res.get("message"));
        verify(auditLogService, times(1)).log(eq("testuser"), eq("LOGOUT"), anyString());
    }
}
