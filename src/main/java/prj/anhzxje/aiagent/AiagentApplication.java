package prj.anhzxje.aiagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ứng dụng chính của hệ thống Soopi AI Agent Platform.
 * 
 * Hệ thống kết hợp:
 * - Spring Boot 3 & Java 21 (Backend REST API, Security, JPA/Hibernate, MySQL)
 * - Spring AI & Google Gemini LLM (Tool Calling, Phân tích mã nguồn tự động)
 * - Frontend Single Page Application (HTML5, Vanilla CSS, JS chuẩn tương thích Electron Desktop)
 */
@SpringBootApplication
public class AiagentApplication {

	/**
	 * Điểm khởi chạy (entry point) của toàn bộ ứng dụng Spring Boot.
	 * Khởi tạo Spring IoC Container, cấu hình JPA, nạp DataInitializer và kích hoạt web server.
	 */
	public static void main(String[] args) {
		SpringApplication.run(AiagentApplication.class, args);
	}

}
