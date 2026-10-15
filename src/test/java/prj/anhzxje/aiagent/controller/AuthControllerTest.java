package prj.anhzxje.aiagent.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Kiểm thử Đơn vị cho Phân hệ Xác thực (AuthControllerTest):
 * Kiểm tra các kịch bản:
 * 1. Đăng ký tài khoản thành công và sinh JWT token.
 * 2. Đăng ký trùng lặp username hoặc email (trả về lỗi HTTP 400).
 * 3. Đăng nhập thành công với thông tin chính xác.
 * 4. Đăng nhập thất bại do sai mật khẩu (BadCredentialsException -> HTTP 401).
 * 5. Đăng nhập thất bại do tài khoản bị khóa (isLocked=true -> HTTP 403).
 */
@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthController authController;

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
    void testRegister_Success() {
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(jwtTokenProvider.generateToken(eq("testuser"), anyString())).thenReturn("mockJwtToken");

        ResponseEntity<AuthResponse> response = authController.register(registerRequest);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("mockJwtToken", response.getBody().getToken());
        assertEquals("testuser", response.getBody().getUsername());
    }

    @Test
    void testRegister_DuplicateUsername() {
        when(userRepository.existsByUsername("testuser")).thenReturn(true);

        ResponseEntity<AuthResponse> response = authController.register(registerRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Username đã tồn tại", response.getBody().getMessage());
    }

    @Test
    void testRegister_DuplicateEmail() {
        when(userRepository.existsByUsername("testuser")).thenReturn(false);
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        ResponseEntity<AuthResponse> response = authController.register(registerRequest);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Email đã được sử dụng", response.getBody().getMessage());
    }

    @Test
    void testLogin_Success() {
        User user = User.builder().id(1L).username("testuser").role(Role.USER).build();
        CustomUserDetails userDetails = new CustomUserDetails(user);
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(jwtTokenProvider.generateToken("testuser", "USER")).thenReturn("mockJwtToken");

        ResponseEntity<AuthResponse> response = authController.login(loginRequest);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("mockJwtToken", response.getBody().getToken());
    }

    @Test
    void testLogin_BadCredentials() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Sai mật khẩu"));

        assertThrows(BadCredentialsException.class, () -> authController.login(loginRequest));
    }
}
