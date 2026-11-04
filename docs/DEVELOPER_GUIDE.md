# Hướng dẫn Lập trình viên & Phát triển Mở rộng (Developer & Extension Guide)
# Dự án: Soopi AI Agent Platform

> **Phiên bản:** 1.0.0  
> **Dành cho:** Kỹ sư phần mềm, AI Engineer, và Người đóng góp mã nguồn (Contributors).

---

## 1. Thiết lập Môi trường Phát triển (Developer Setup)

### 1.1. Công cụ & Plugin Cần thiết
1. **Java Development Kit (JDK)**: Cài đặt **JDK 21 LTS** (Ví dụ: Eclipse Temurin 21 hoặc Oracle OpenJDK 21).
   - Kiểm tra phiên bản bằng terminal: `java -version`
2. **Integrated Development Environment (IDE)**:
   - **IntelliJ IDEA** (Khuyến nghị): Cần bật plugin **Lombok** và kích hoạt tính năng **Annotation Processing** (*Settings -> Build, Execution, Deployment -> Compiler -> Annotation Processors -> Enable annotation processing*).
   - **Visual Studio Code**: Cần cài đặt bộ extension **Extension Pack for Java**, **Lombok Support for VS Code**, và **Spring Boot Tools**.
3. **Cơ sở dữ liệu**: MySQL Server 8.0 cục bộ hoặc sử dụng Docker container:
   ```bash
   docker run -d --name mysql-dev -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=ai_agent mysql:8.0
   ```
4. **Node.js**: Phiên bản 18+ (Để chạy bộ test JavaScript độc lập `node --test`).

---

## 2. Cấu trúc Mã nguồn & Quy chuẩn Thiết kế

### 2.1. Phân chia Gói (Package Structure)
Toàn bộ mã nguồn Java nằm trong namespace `prj.anhzxje.aiagent`:

```text
src/main/java/prj/anhzxje/aiagent/
├── agent/                  # Lõi Tác tử AI: CodeReviewAgent, SandboxTools
├── config/                 # Cấu hình Spring: SecurityConfig, AgentConfig, DataInitializer, AuditAspect
├── controller/             # Tầng REST API: Auth, User, Project, Task, Chat, Admin, System
├── dto/                    # Data Transfer Objects phân nhóm theo domain:
│   ├── admin/              # DTO thống kê, cấu hình tool, cài đặt admin
│   ├── auth/               # DTO đăng nhập, đăng ký, hồ sơ người dùng
│   ├── chat/               # DTO hội thoại, gửi tin nhắn, cập nhật model
│   ├── common/             # DTO chung (ErrorResponse)
│   ├── project/            # DTO tạo và xem dự án
│   ├── task/               # DTO cấu hình và kết quả tác vụ AI
│   └── user/               # DTO tùy chọn cá nhân (Preferences)
├── entity/                 # Thực thể JPA: User, Project, AgentTask, ChatConversation, ChatMessage...
├── enums/                  # Các hằng số Enum: Role, JobStatus, TaskType, Severity, Category
├── exception/              # Xử lý ngoại lệ tập trung: GlobalExceptionHandler, ResourceNotFoundException...
├── repository/             # Spring Data JPA Repositories
├── security/               # Thành phần JWT: JwtTokenProvider, JwtAuthenticationFilter, CustomUserDetails
└── service/                # Business Logic & Async Task Executor
```

### 2.2. Quy ước Thiết kế & Coding Standards
- **Nguyên lý Single Responsibility**: Các Controller chỉ tiếp nhận yêu cầu, validate và chuyển tiếp cho Service. Mọi logic nghiệp vụ, giao dịch, kiểm tra quyền sở hữu IDOR đều nằm trong Service layer.
- **Xử lý Dữ liệu Nhạy cảm**: Tuyệt đối không lưu mật khẩu dạng plain-text; luôn sử dụng `PasswordEncoder.encode()`. API Key phải được mask trước khi trả về qua API.
- **Tính Bất biến (Immutability)**: Khuyến khích sử dụng Lombok `@Builder` và `@RequiredArgsConstructor` cho Dependency Injection qua Constructor.

---

## 3. Hướng dẫn Mở rộng Tính năng (How-To Guides)

### 3.1. Hướng dẫn: Thêm một Công cụ (Tool) Mới cho AI Agent

Tất cả công cụ mà AI Agent có thể gọi đều được định nghĩa trong [SandboxTools.java](file:///d:/AI%20Agent/aiagent/src/main/java/prj/anhzxje/aiagent/agent/SandboxTools.java).

#### Bước 1: Viết lớp Tool với chú thích `@Tool`
Mở `SandboxTools.java` và bổ sung lớp Inner Tool mới (Ví dụ: `CountLinesTool` - đếm số dòng mã nguồn):

```java
public final class CountLinesTool {
    @Tool(name = "count_lines", description = "Đếm tổng số dòng mã nguồn của một tệp tin hợp lệ bên trong dự án.")
    public String countLines(String path) throws IOException {
        Path target = resolve(path);
        if (!Files.isRegularFile(target)) {
            throw new IllegalArgumentException("Đường dẫn phải là tệp tin");
        }
        long lineCount = Files.lines(target, StandardCharsets.UTF_8).count();
        return "Tệp tin " + relative(target) + " có tổng cộng " + lineCount + " dòng.";
    }
}
```

#### Bước 2: Đăng ký Tool trong switch-case của `SandboxTools`
```java
public Object tool(String name) {
    return switch (name) {
        case "list_directory" -> new DirectoryTool();
        case "glob" -> new GlobTool();
        case "file_system" -> new ReadTool();
        case "grep" -> new SearchTool();
        case "count_lines" -> new CountLinesTool(); // Đăng ký tool mới
        default -> throw new IllegalArgumentException("Công cụ không được hỗ trợ: " + name);
    };
}
```

#### Bước 3: Đăng ký vào Cơ sở dữ liệu qua `DataInitializer.java`
Mở [DataInitializer.java](file:///d:/AI%20Agent/aiagent/src/main/java/prj/anhzxje/aiagent/config/DataInitializer.java) để tự động thêm tool vào bảng `agent_tool_configs` khi khởi động ứng dụng:

```java
registerToolIfAbsent("count_lines", "Đếm số dòng mã nguồn", "Đếm tổng số dòng code của tệp", true, "ANALYSIS");
```

Sau khi hoàn thành 3 bước trên, Quản trị viên có thể Bật/Tắt tool này trên giao diện Admin, và Agent sẽ tự động nhận diện tool trong vòng lặp Function Calling!

---

### 3.2. Hướng dẫn: Thêm một Loại Tác vụ AI Mới (TaskType)

Giả sử bạn muốn bổ sung tác vụ tạo tài liệu tự động (`DOCUMENTATION_GENERATOR`):

#### Bước 1: Mở rộng enum `TaskType`
Mở [TaskType.java](file:///d:/AI%20Agent/aiagent/src/main/java/prj/anhzxje/aiagent/enums/TaskType.java):
```java
public enum TaskType {
    CODE_REVIEW,
    CODE_FIX,
    TEST_GENERATION,
    DOCUMENTATION_GENERATOR // Thêm loại task mới
}
```

#### Bước 2: Thêm Prompt và Luồng Xử lý trong `CodeReviewAgent.java`
Xây dựng prompt chuyên biệt và định nghĩa cấu trúc JSON đầu ra cho task type mới:
```java
public String generateDocumentation(String projectPath, String module) {
    String userPrompt = String.format("""
        Nhiệm vụ của bạn là đọc và tạo tài liệu kỹ thuật Markdown cho dự án tại '%s'.
        Mô-đun cần tạo: %s.
        Hãy sử dụng các công cụ được cấp để đọc mã nguồn và viết tài liệu chi tiết.
        """, projectPath, module);

    return chatClient.prompt()
            .user(userPrompt)
            .tools(new SandboxTools(Paths.get(projectPath)).tool("list_directory"), ...)
            .call()
            .content();
}
```

#### Bước 3: Cập nhật `AgentTaskExecutor.java`
Thêm nhánh xử lý trong worker:
```java
case DOCUMENTATION_GENERATOR -> {
    String output = codeReviewAgent.generateDocumentation(projectPath, inputParams);
    task.setOutput(output);
}
```

---

### 3.3. Hướng dẫn: Thêm một Mô hình AI mới (Gemini Model Variant)

Mô hình AI được quản lý tập trung trong [SystemConfigService.java](file:///d:/AI%20Agent/aiagent/src/main/java/prj/anhzxje/aiagent/service/SystemConfigService.java).

Để thêm mô hình mới (ví dụ: `gemini-1.5-pro` hoặc các bản cập nhật tương lai):
1. Mở `SystemConfigService.java`, bổ sung mã định danh model vào danh sách `DEFAULT_ALLOWED_MODELS`:
   ```java
   public static final List<String> DEFAULT_ALLOWED_MODELS = List.of(
           "gemini-3.5-flash-lite",
           "gemini-2.5-flash",
           "gemini-2.5-pro",
           "gemini-1.5-pro" // Thêm model mới
   );
   ```
2. Cập nhật frontend: Mở [chatView.js](file:///d:/AI%20Agent/aiagent/src/main/resources/static/js/modules/chatView.js) để hiển thị tên thân thiện cho model mới trên dropdown chọn mô hình.

---

## 4. Chiến lược Kiểm thử & Đảm bảo Chất lượng (Testing & QA)

Dự án duy trì bộ kiểm thử tự động toàn diện bao phủ từ Backend đến Frontend.

### 4.1. Chạy Toàn bộ Kiểm thử Backend (Maven)
Dự án có **148 ca kiểm thử tự động** (bao phủ Security, IDOR, Controllers, Services, Agent Tool Calling, Cascading Delete):

```powershell
# Chạy toàn bộ kiểm thử
.\mvnw.cmd test

# Chạy một lớp kiểm thử cụ thể (Ví dụ: ToolManagerServiceTest)
.\mvnw.cmd test -Dtest=ToolManagerServiceTest
```

### 4.2. Chạy Kiểm thử Giao diện Frontend (Node.js)
Giao diện Web Modular có bộ kiểm thử logic độc lập không cần trình duyệt thông qua Node.js test runner:

```bash
node --test src/test/js/frontend.test.cjs
```

### 4.3. Hướng dẫn Viết Ca Kiểm thử Mới (Example Unit Test)
Dưới đây là mẫu kiểm thử một Service với Mockito và JUnit 5:

```java
package prj.anhzxje.aiagent.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import prj.anhzxje.aiagent.entity.User;
import prj.anhzxje.aiagent.repository.UserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("Ném ngoại lệ khi không tìm thấy người dùng theo ID")
    void shouldThrowExceptionWhenUserNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> {
            userService.getUserById(999L);
        });

        verify(userRepository, times(1)).findById(999L);
    }
}
```

---

## 5. Quy chuẩn Đóng gói & Bản phát hành (Build & Packaging)

### 5.1. Đóng gói tệp thực thi JAR
```powershell
.\mvnw.cmd clean package -DskipTests
```
Tệp JAR kết quả sẽ được tạo tại: `target/aiagent-0.0.1-SNAPSHOT.jar`.

### 5.2. Khởi chạy trực tiếp từ tệp JAR
```powershell
java -jar target/aiagent-0.0.1-SNAPSHOT.jar
```
