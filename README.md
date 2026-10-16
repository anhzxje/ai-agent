# Soopi - Nền tảng Tác tử AI Đánh giá Mã nguồn (AI Agent Code Reviewer)

> Hệ thống Tác tử AI hỗ trợ tự động rà soát mã nguồn, kiểm tra bảo mật, phát hiện lỗi logic, tối ưu hiệu năng và hội thoại kỹ thuật phần mềm dựa trên **Java 21**, **Spring Boot 3.3.4**, **Spring AI**, **Google Gemini API**, **Spring Security (JWT)** và **MySQL 8.x**.

---

## 1. Giới thiệu tổng quan

**Soopi AI Agent** là giải pháp đánh giá và kiểm thử mã nguồn tự động dành cho lập trình viên và nhóm phát triển. Khác biệt với các ứng dụng chatbot thông thường chỉ đọc văn bản cố định, Soopi hoạt động như một **Autonomous AI Agent** được trang bị khả năng **Tool Calling**:
- Tự động duyệt cấu trúc cây thư mục dự án (`list_directory`).
- Quét và định vị các tệp mã nguồn theo mẫu wildcard (`glob`).
- Đọc nội dung tệp thực tế trên ổ đĩa (`file_system`).
- Tìm kiếm từ khóa, hàm, regex pattern trên toàn bộ codebase (`grep`).
- Phân tích ngữ cảnh, phát hiện các lỗ hổng bảo mật (OWASP, SQL Injection, Path Traversal, Memory Leak...) và đề xuất giải pháp vá lỗi chi tiết kèm mã nguồn bằng tiếng Việt.
- Tương tác hội thoại kỹ thuật đa lượt với hai chế độ: **Hỏi đáp chung** (không gắn dự án) và **Không gian dự án mã nguồn** (Project Workspace).

---

## 2. Kiến trúc & Công nghệ Thực tế

### Công nghệ sử dụng
- **Backend**: Java 21, Spring Boot 3.3.4, Spring Data JPA, Hibernate ORM, Transaction Management.
- **AI Engine**: Spring AI (`spring-ai-starter-model-google-genai`), Google Gemini API với bộ mô hình linh hoạt:
  1. `gemini-3.5-flash-lite` (Mặc định - Phản hồi siêu tốc, tiết kiệm token, tối ưu hội thoại)
  2. `gemini-2.5-flash` (Lập luận sâu, phân tích cấu trúc mã nguồn toàn diện)
  3. `gemini-2.5-pro` (Phân tích chuyên sâu cho các bài toán kiến trúc phức tạp)
  4. `gemini-1.5-flash` & `gemini-1.5-pro` (Tương thích ngược)
- **Bảo mật**: Spring Security 6, JJWT (JSON Web Token), BCrypt Password Encoder, Strict Path Normalization (Anti Path Traversal Sandbox).
- **Cơ sở dữ liệu**: MySQL 8.x với hỗ trợ kiểu dữ liệu JSON (`SqlTypes.JSON`), kiểm tra ping kết nối và đo độ trễ trực tiếp.
- **Giao diện Web Modular (Vanilla JavaScript ES6+)**:
  - Không sử dụng các framework cồng kềnh, phân tách module kiến trúc rõ ràng:
    - `js/modules/chatView.js`: Quản lý hội thoại, hiển thị tin nhắn, typing indicator, đổi mô hình AI.
    - `js/modules/projectView.js`: Quản lý danh mục dự án, thanh điều hướng sidebar, ghim cố định Hỏi đáp chung, cascade xóa dự án.
    - `js/modules/adminView.js`: Bảng điều khiển quản trị trang cuộn đơn (Single-page Continuous Scroll) với thanh điều hướng kính mờ (Frosted Glass Sticky Header).
    - `js/modules/markdownUtils.js`: Định dạng Markdown, làm nổi bật cú pháp khối code (Syntax Highlighting) và Antigravity Slash Chips.
    - `js/api.js`: Tầng kết nối REST API, quản lý token, đồng bộ dữ liệu và cơ chế chuyển đổi dự phòng (Fallback).
    - `js/app.js`: Bộ điều phối trung tâm (Central Controller).

---

### Cấu trúc mã nguồn chi tiết
```text
aiagent/
├── pom.xml                     # Cấu hình Maven, Spring Boot 3.3.4 và Spring AI
├── mvnw / mvnw.cmd             # Maven Wrapper khởi chạy ứng dụng
└── src/
    ├── main/
    │   ├── java/prj/anhzxje/aiagent/
    │   │   ├── agent/          # Core Agent: CodeReviewAgent (Reasoning + Dynamic Tool Calling)
    │   │   ├── config/         # SecurityConfig, DataInitializer, AgentConfig, AuditAspect
    │   │   ├── controller/     # REST Controllers: Admin, User, Task, Chat, Project, Auth, System
    │   │   ├── dto/            # Data Transfer Objects cho Request và Response
    │   │   ├── entity/         # JPA Entities: User, Project, AgentTask, AuditLog, ToolConfig, Setting, ChatConversation, ChatMessage
    │   │   ├── enums/          # JobStatus, Role, TaskType, Category, Severity
    │   │   ├── exception/      # GlobalExceptionHandler và các ngoại lệ tùy chỉnh
    │   │   ├── repository/     # Spring Data JPA Repositories
    │   │   ├── security/       # JwtAuthenticationFilter, JwtTokenProvider, CustomUserDetails
    │   │   └── service/        # Business Logic: Task, AuditLog, ToolManager, SystemConfig, Chat, Project, Auth...
    │   └── resources/
    │       ├── application.yml # Cấu hình cổng 8080, datasource MySQL, JWT secret, model Gemini
    │       └── static/         # Giao diện Web Modular (HTML, CSS, JS Modules)
    └── test/                   # Toàn bộ 135 unit tests tự động (Controller, Service, Security, DTO, Repository)
```

---

## 3. Ma trận Phân quyền & Tính năng (Role Matrix)

Hệ thống phân tách nghiêm ngặt giữa hai vai trò: **ADMIN** và **USER**.

| Nhóm tính năng | Chi tiết tính năng | Role USER | Role ADMIN |
|---|---|:---:|:---:|
| **Xác thực** | Đăng ký tài khoản mới & Đăng nhập nhận JWT Token | ✅ | ✅ |
| | Đổi mật khẩu của chính mình (`/api/users/me/password`) | ✅ | ✅ |
| | Đăng xuất an toàn & Ghi vết Audit Log | ✅ | ✅ |
| **Cá nhân hóa** | Tùy chọn giao diện (Dark / Light Theme), Ngôn ngữ, Model AI ưa thích | ✅ | ✅ |
| **Quản lý Dự án** | Tạo, xem, cập nhật, xóa dự án cá nhân | ✅ | ✅ |
| | **Xóa dự án tầng sâu (Cascading Deletion):** Tự động dọn dẹp sạch toàn bộ tác vụ, phiên chat và tin nhắn thuộc dự án | ✅ | ✅ |
| | Chọn thư mục mã nguồn qua hộp thoại Native OS Folder Dialog (Desktop) | ✅ | ✅ |
| **Tác vụ AI** | Giao task cho Agent (Code Review, Security Audit, Bug Fix, Performance...) | ✅ | ✅ |
| | Lựa chọn công cụ Agent được phép dùng (`list_directory`, `glob`, `file_system`, `grep`) | ✅ | ✅ |
| | Lựa chọn mô hình AI thực thi cho từng tác vụ | ✅ | ✅ |
| | Theo dõi tiến độ thời gian thực (`PENDING`, `PROCESSING`, `COMPLETED`, `ERROR`, `CANCELLED`) | ✅ | ✅ |
| | Hủy tác vụ đang chạy (`/api/tasks/{id}/cancel`), Chạy lại tác vụ cũ (`retry`) | ✅ | ✅ |
| | Xóa tác vụ hoặc xóa toàn bộ lịch sử tác vụ của chính mình | ✅ | ✅ |
| | Ghim / Bỏ ghim tác vụ quan trọng lên thanh điều hướng | ✅ | ✅ |
| | **Cách ly dữ liệu:** Người dùng chỉ xem được tác vụ của chính mình (lọc `userId` backend) | ✅ | ✅ |
| **Hội thoại AI** | **Cố định mục Hỏi đáp chung:** Luôn ghim ở đầu thanh bên, trò chuyện kỹ thuật tự do không phụ thuộc dự án | ✅ | ✅ |
| | Phiên hội thoại gắn ngữ cảnh dự án: AI tự động duyệt codebase để giải đáp | ✅ | ✅ |
| | Đổi mô hình AI trực tiếp trong phiên chat (`PATCH /api/chat/conversations/{id}/model`) | ✅ | ✅ |
| | Xóa cuộc hội thoại và làm sạch tin nhắn (`DELETE /api/chat/conversations/{id}`) | ✅ | ✅ |
| **Quản trị người dùng** | Xem toàn bộ danh sách tài khoản trong CSDL | ❌ | ✅ |
| | **Khóa / Mở khóa tài khoản** (`isLocked`) chặn đăng nhập và từ chối token | ❌ | ✅ |
| | Chuyển đổi vai trò tài khoản (`USER` $\leftrightarrow$ `ADMIN`) | ❌ | ✅ |
| | Xóa tài khoản người dùng vi phạm khỏi hệ thống | ❌ | ✅ |
| | Kiểm duyệt chi tiết dự án, tác vụ và lịch sử của bất kỳ thành viên nào | ❌ | ✅ |
| **Bảng Quản trị viên** | **Thiết kế Single-page Continuous Scroll:** Xem trọn vẹn 5 phân khu trên một trang cuộn duy nhất | ❌ | ✅ |
| | **Bật / Tắt công cụ Agent:** Bật/tắt `list_directory`, `glob`, `file_system`, `grep` thời gian thực | ❌ | ✅ |
| | **Cấu hình Model & API Key:** Cập nhật Gemini API Key và chọn mô hình mặc định (có mặt nạ bảo mật) | ❌ | ✅ |
| | **Giám sát tác vụ & Lỗi:** Theo dõi toàn bộ task trong hệ thống và bảng tổng hợp log lỗi | ❌ | ✅ |
| | **Nhật ký Kiểm toán (Audit Log):** Xem lịch sử toàn bộ hành động nhạy cảm với IP, timestamp, action | ❌ | ✅ |

---

## 4. Cơ chế Bảo mật & An toàn Dữ liệu

1. **Bảo vệ đường dẫn (Anti Path Traversal Sandbox)**:
   - Tham số `projectPath` được chuẩn hóa bằng `Path.normalize()`.
   - Hệ thống so sánh với danh mục `agent.allowed-paths` trong [application.yml](file:///d:/AI%20Agent/aiagent/src/main/resources/application.yml) để ngăn chặn hành vi đọc trộm tệp tin hệ điều hành nhạy cảm (`C:\Windows`, `/etc/passwd`).
2. **Cách ly dữ liệu theo `userId`**:
   - Tất cả API của người dùng đều xác thực danh tính trực tiếp từ `SecurityContextHolder` thông qua JWT Token hợp lệ. Người dùng không thể xem, sửa hoặc xóa dữ liệu của người khác bằng cách thay đổi ID trên URL.
3. **Cơ chế Khóa tài khoản (Account Lockout)**:
   - Khi tài khoản bị Admin khóa (`isLocked = true`), `CustomUserDetails.isAccountNonLocked()` trả về `false`.
   - Filter và Authentication Manager lập tức chặn đăng nhập hoặc từ chối mọi yêu cầu gửi đến API với thông báo lỗi rõ ràng.
4. **Bảo vệ Khóa bí mật (Secret Masking)**:
   - API Key được bảo vệ qua biến môi trường hoặc lưu trong CSDL MySQL, không bao giờ bị lộ ra ngoài.
   - Giao diện Admin hiển thị mặt nạ bảo mật (`AIzaSy...4X9Z`).
5. **Nhật ký Kiểm toán Tự động (Audit Trail)**:
   - Mọi hành động quan trọng (đăng nhập, đăng xuất, đổi mật khẩu, khóa user, đổi role, bật/tắt tool, đổi API key, xóa dự án/hội thoại) đều được ghi tự động vào bảng `audit_logs` với IP client, thời gian và chi tiết hành động.

---

## 5. Hướng dẫn Cài đặt & Khởi chạy

### 1. Yêu cầu môi trường
- **Java**: JDK 21 trở lên.
- **Maven**: 3.8+ (đã tích hợp sẵn Maven Wrapper `mvnw.cmd`).
- **MySQL Server**: 8.0 trở lên.
- **Gemini API Key**: Nhận miễn phí từ [Google AI Studio](https://aistudio.google.com/app/apikey).

### 2. Khởi tạo Cơ sở dữ liệu MySQL
Đăng nhập vào MySQL và tạo cơ sở dữ liệu:
```sql
CREATE DATABASE ai_agent CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 3. Cấu hình biến môi trường
Thiết lập Gemini API Key trên máy Windows PowerShell:
```powershell
[System.Environment]::SetEnvironmentVariable('GOOGLE_API_KEY', 'AIzaSy_KEY_CỦA_BẠN', 'User')
```

### 4. Khởi chạy Backend Server
Sử dụng Maven Wrapper để chạy server:
```powershell
.\mvnw.cmd spring-boot:run
```
Server chạy tại địa chỉ chuẩn: **`http://localhost:8080`**

> **Tài khoản khởi tạo tự động (DataInitializer):**
> - **Tài khoản Admin:** `username: admin` | `password: admin`
> - Đăng nhập tài khoản Admin để truy cập toàn bộ các tính năng Quản trị hệ thống, Bật/tắt công cụ, và Nhật ký kiểm toán.

---

## 6. Danh mục REST API chính

| Phương thức | Đường dẫn API | Mô tả chức năng | Quyền hạn |
|:---:|---|---|:---:|
| **POST** | `/api/auth/login` | Đăng nhập và nhận JWT token | Public |
| **POST** | `/api/auth/register` | Đăng ký tài khoản người dùng mới | Public |
| **POST** | `/api/auth/logout` | Đăng xuất và ghi nhật ký kiểm toán | User / Admin |
| **GET** | `/api/system/models` | Lấy danh sách model Gemini được phép dùng | Public |
| **GET** | `/api/system/tools` | Lấy danh sách công cụ Agent đang được bật | Public |
| **GET** | `/api/system/ping-db` | Ping kiểm tra kết nối CSDL MySQL và đo độ trễ | Public |
| **GET** | `/api/users/me` | Lấy hồ sơ tài khoản hiện tại | User / Admin |
| **PUT** | `/api/users/me/password` | Đổi mật khẩu tài khoản của chính mình | User / Admin |
| **GET / PUT** | `/api/users/me/preferences`| Xem và lưu tùy chọn giao diện, ngôn ngữ, model | User / Admin |
| **GET / POST** | `/api/projects` | Xem danh sách và đăng ký dự án mới | User / Admin |
| **GET** | `/api/projects/{id}/summary` | Xem tóm tắt thống kê của một dự án | User / Admin |
| **DELETE** | `/api/projects/{id}` | Xóa vĩnh viễn dự án (Cascade xóa task và chat liên quan) | Owner / Admin |
| **GET / POST** | `/api/tasks` | Xem danh sách task của mình và tạo task AI mới | User / Admin |
| **POST** | `/api/tasks/{id}/cancel` | Hủy tác vụ đang chạy | Owner |
| **POST** | `/api/tasks/{id}/retry` | Chạy lại tác vụ cũ | Owner |
| **DELETE** | `/api/tasks/{id}` | Xóa tác vụ của mình | Owner |
| **GET / POST** | `/api/chat/conversations` | Lấy danh sách và tạo cuộc hội thoại AI mới | User / Admin |
| **GET** | `/api/chat/conversations/{id}` | Lấy chi tiết lịch sử tin nhắn trong phiên | Owner / Admin |
| **DELETE** | `/api/chat/conversations/{id}` | Xóa cuộc hội thoại và toàn bộ tin nhắn liên quan | Owner / Admin |
| **PATCH** | `/api/chat/conversations/{id}/model`| Cập nhật mô hình AI cho cuộc hội thoại | Owner / Admin |
| **POST** | `/api/chat` | Gửi tin nhắn và nhận phản hồi từ AI Agent | User / Admin |
| **GET** | `/api/admin/stats` | Xem số liệu thống kê hệ thống toàn diện | Admin |
| **GET** | `/api/admin/users` | Danh sách người dùng và trạng thái khóa | Admin |
| **PUT** | `/api/admin/users/{id}/lock` | Khóa tài khoản người dùng | Admin |
| **PUT** | `/api/admin/users/{id}/unlock` | Mở khóa tài khoản người dùng | Admin |
| **PUT** | `/api/admin/users/{id}/role` | Phân quyền tài khoản (USER / ADMIN) | Admin |
| **DELETE** | `/api/admin/users/{id}` | Xóa tài khoản người dùng | Admin |
| **GET / PUT** | `/api/admin/tools` | Xem và Bật / Tắt các công cụ của Agent | Admin |
| **GET / PUT** | `/api/admin/settings` | Xem và cập nhật mô hình, Gemini API Key | Admin |
| **GET** | `/api/admin/tasks` | Xem toàn bộ lịch sử tác vụ toàn hệ thống | Admin |
| **GET** | `/api/admin/audit-logs` | Xem nhật ký kiểm toán hệ thống | Admin |

---

## 7. Kiểm thử Tự động (Automated Tests)

Dự án sở hữu bộ kiểm thử tự động toàn diện với **135 test cases** đạt tỉ lệ vượt qua **100%**:
- **Agent Tests**: Kiểm tra cơ chế tự động suy luận (Reasoning) và gọi công cụ (Tool Calling) của `CodeReviewAgent`.
- **Controller Tests**: Đầy đủ ca kiểm thử cho AdminController, ChatController, ProjectController, TaskController, AuthController, UserController, SystemController.
- **Service Tests**: Bao phủ logic xử lý nghiệp vụ, kiểm tra quyền sở hữu IDOR, cascade xóa và rollback transaction.
- **Security Tests**: Kiểm tra phân quyền chặt chẽ giữa Role USER và ADMIN, chặn tài khoản bị khóa (`isLocked`), kiểm tra tính hợp lệ của token JWT.

Chạy toàn bộ kiểm thử:
```powershell
.\mvnw.cmd test
```

---

## Bản quyền & Tác giả
- **Tác giả:** anhzxje
- **Dự án:** Soopi - AI Agent Platform
- **Giấy phép:** MIT License
