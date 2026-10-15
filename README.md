# Soopi - Nền tảng Tác tử AI Đánh giá Mã nguồn (AI Agent Code Reviewer)

> Hệ thống Tác tử AI hỗ trợ tự động rà soát mã nguồn, kiểm tra bảo mật, phát hiện lỗi logic, tối ưu hiệu năng và hội thoại kỹ thuật phần mềm dựa trên **Spring Boot 3.x**, **Spring AI**, **Google Gemini**, **Spring Security (JWT)**, **MySQL** và **Desktop App Windows (.exe)**.

---

## 1. Giới thiệu tổng quan

**Soopi AI Agent** là giải pháp đánh giá và kiểm thử mã nguồn tự động dành cho lập trình viên và nhóm phát triển. Khác biệt với các ứng dụng chatbot thông thường chỉ đọc văn bản cố định, Soopi hoạt động như một **Autonomous AI Agent** được trang bị khả năng **Tool Calling**:
- Tự động duyệt cấu trúc thư mục dự án (`list_directory`).
- Quét và định vị các tệp mã nguồn theo mẫu wildcard (`glob`).
- Đọc nội dung tệp thực tế trên ổ đĩa (`file_system`).
- Tìm kiếm từ khóa, hàm, regex pattern trên toàn bộ codebase (`grep`).
- Phân tích ngữ cảnh, phát hiện các lỗ hổng bảo mật (OWASP, SQL Injection, Path Traversal, Memory Leak...) và đề xuất giải pháp vá lỗi chi tiết kèm mã nguồn bằng tiếng Việt.

---

## 2. Kiến trúc & Công nghệ

### Công nghệ sử dụng
- **Backend**: Java 21, Spring Boot 3.4+, Spring Data JPA, Hibernate ORM.
- **AI Engine**: Spring AI (`spring-ai-starter-model-google-genai`), Google Gemini API với bộ 3 mô hình tối ưu:
  1. `gemini-3.5-flash-lite` (Mặc định - Hạn ngạch 500 RPD, 15 RPM, phản hồi siêu tốc, miễn phí 100%)
  2. `gemini-3.1-flash-lite` (Thế hệ 3.1 mới nhất - Hạn ngạch 500 RPD, 15 RPM, tối ưu code review)
  3. `gemini-2.5-flash-lite` (Hạn ngạch 20 RPD, 10 RPM, ổn định & tiết kiệm token) / `gemini-2.5-flash` (Lập luận sâu)
- **Bảo mật**: Spring Security 6, JJWT (JSON Web Token), BCrypt Password Encoder, Strict Path Normalization (Anti Path Traversal).
- **Cơ sở dữ liệu**: MySQL 8.x với hỗ trợ kiểu dữ liệu JSON (`SqlTypes.JSON`).
- **Giao diện Web**: Vanilla JavaScript (ES6+), Vanilla CSS (Modern Dark/Light SaaS Theme, Micro-animations).
- **Desktop Application**: Electron, Native OS Directory Dialog, Packaged Win32 x64 `Soopi.exe`.

### Cấu trúc mã nguồn chi tiết
```text
aiagent/
├── build-exe.bat               # Script tự động đóng gói ứng dụng desktop Soopi.exe
├── run-desktop.bat             # Script chạy nhanh ứng dụng desktop Soopi.exe
├── desktop/                    # Mã nguồn Electron Desktop App
│   ├── app/                    # Bản build giao diện tĩnh đóng gói sẵn (Offline & Fallback)
│   ├── main.js                 # Electron Main Process (quản lý window, server check, dialog)
│   ├── preload.js              # IPC Bridge (mở hộp thoại chọn thư mục gốc hệ điều hành)
│   └── package.json            # Cấu hình electron và electron-packager
├── pom.xml                     # Cấu hình Maven, các dependencies Spring Boot và Spring AI
└── src/
    ├── main/
    │   ├── java/prj/anhzxje/aiagent/
    │   │   ├── agent/          # Core Agent: CodeReviewAgent (Reasoning + Dynamic Tool Calling)
    │   │   ├── config/         # Cấu hình SecurityConfig, DataInitializer, AgentConfig
    │   │   ├── controller/     # REST Controllers: Admin, User, Task, Chat, Project, Auth, System
    │   │   ├── dto/            # Data Transfer Objects cho Request và Response
    │   │   ├── entity/         # JPA Entities: User, Project, AgentTask, AuditLog, ToolConfig, Setting...
    │   │   ├── enums/          # JobStatus, Role, TaskType, Category, Severity
    │   │   ├── exception/      # GlobalExceptionHandler và các custom exceptions
    │   │   ├── repository/     # Spring Data JPA Repositories
    │   │   ├── security/       # JwtAuthenticationFilter, JwtTokenProvider, CustomUserDetails
    │   │   └── service/        # Các tầng nghiệp vụ: Task, AuditLog, ToolManager, SystemConfig, Chat...
    │   └── resources/
    │       ├── application.yml # Cấu hình cổng 8081, datasource MySQL, JWT secret, model Gemini
    │       └── static/         # Giao diện Web (index.html, css/style.css, js/app.js, js/api.js)
    └── test/                   # Toàn bộ 71 unit test tự động (Controller, Service, Security, DTO)
```

---

## 3. Ma trận Phân quyền & Tính năng (Role Matrix)

Hệ thống phân tách nghiêm ngặt giữa hai vai trò: **ADMIN** và **USER**.

| Nhóm tính năng | Chi tiết tính năng | Role USER | Role ADMIN |
|---|---|:---:|:---:|
| **Xác thực** | Đăng ký tài khoản mới & Đăng nhập cấp JWT Token | ✅ | ✅ |
| | Đổi mật khẩu của chính mình (`/api/users/me/password`) | ✅ | ✅ |
| | Đăng xuất an toàn & Xóa phiên | ✅ | ✅ |
| **Cá nhân hóa** | Cài đặt tùy chọn: Giao diện (Dark/Light), Ngôn ngữ, Model ưa thích | ✅ | ✅ |
| **Quản lý Dự án** | Tạo, xem, cập nhật, xóa dự án cá nhân | ✅ | ✅ |
| | Chọn thư mục mã nguồn qua hộp thoại Native OS Folder Dialog | ✅ | ✅ |
| **Tác vụ AI** | Giao task cho Agent (chọn loại task, trọng tâm phân tích) | ✅ | ✅ |
| | Tùy chọn công cụ Agent được phép dùng (`list_directory`, `glob`, `file_system`, `grep`) | ✅ | ✅ |
| | Lựa chọn mô hình AI (`gemini-2.5-flash-lite`, `gemini-2.0-flash`, `gemini-2.5-pro`...) | ✅ | ✅ |
| | Theo dõi tiến độ thời gian thực (`PENDING`, `PROCESSING`, `COMPLETED`, `ERROR`, `CANCELLED`) | ✅ | ✅ |
| | Hủy tác vụ đang chạy (`/api/tasks/{id}/cancel`) | ✅ | ✅ |
| | Chạy lại tác vụ cũ (`retry`) | ✅ | ✅ |
| | Xóa tác vụ hoặc xóa toàn bộ lịch sử tác vụ của chính mình | ✅ | ✅ |
| | Ghim/bỏ ghim tác vụ quan trọng lên thanh điều hướng | ✅ | ✅ |
| | **Cách ly dữ liệu:** Người dùng chỉ xem được tác vụ của chính mình (lọc `userId` backend) | ✅ | ✅ |
| **Hội thoại AI** | Tạo các phiên chat tương tác đa lượt với AI Agent | ✅ | ✅ |
| | Lưu trữ lịch sử hội thoại và xem lại các phiên chat cũ | ✅ | ✅ |
| **Quản trị người dùng** | Xem toàn bộ danh sách tài khoản trong CSDL | ❌ | ✅ |
| | **Khóa / Mở khóa tài khoản** (`isLocked`) ngăn chặn đăng nhập | ❌ | ✅ |
| | Chuyển đổi vai trò tài khoản (`USER` $\leftrightarrow$ `ADMIN`) | ❌ | ✅ |
| | Xóa tài khoản người dùng vi phạm khỏi hệ thống | ❌ | ✅ |
| | Kiểm duyệt chi tiết dự án, tác vụ và lịch sử của bất kỳ thành viên nào | ❌ | ✅ |
| **Quản trị công cụ** | **Bật / Tắt các tool của Agent** (`list_directory`, `glob`, `file_system`, `grep`) | ❌ | ✅ |
| **Cấu hình hệ thống** | Cấu hình mô hình Gemini mặc định toàn hệ thống | ❌ | ✅ |
| | Cập nhật Google Gemini API Key mới (lưu CSDL, hiển thị che mặt nạ bảo mật) | ❌ | ✅ |
| **Giám sát & Nhật ký** | Xem lịch sử toàn bộ tác vụ của tất cả thành viên trong CSDL | ❌ | ✅ |
| | Xem bảng tổng hợp log lỗi hệ thống | ❌ | ✅ |
| | Xem **Bảng Nhật ký Kiểm toán (Audit Log)** ghi vết các hành động quan trọng | ❌ | ✅ |

---

## 4. Cơ chế Bảo mật & An toàn Dữ liệu

1. **Bảo vệ đường dẫn (Anti Path Traversal)**:
   - Tham số `projectPath` được chuẩn hóa bằng `Path.normalize()`.
   - Hệ thống so sánh với danh sách `agent.allowed-paths` trong [application.yml](file:///d:/AI%20Agent/aiagent/aiagent/src/main/resources/application.yml) để chặn hoàn toàn hành vi đọc trộm tệp tin hệ thống nhạy cảm (như `C:\Windows`, `/etc/passwd`).
2. **Cách ly dữ liệu theo `userId`**:
   - Tất cả API của người dùng đều lấy danh tính trực tiếp từ `SecurityContextHolder` thông qua JWT Token hợp lệ. Người dùng không thể xem, sửa hoặc xóa dữ liệu của người khác bằng cách đổi ID trên URL.
3. **Cơ chế Khóa tài khoản (Account Lockout)**:
   - Khi tài khoản bị Admin khóa (`isLocked = true`), `CustomUserDetails.isAccountNonLocked()` trả về `false`.
   - Filter và Authentication Manager lập tức chặn đăng nhập hoặc từ chối mọi yêu cầu gửi đến API.
4. **Bảo vệ Khóa bí mật khi Push Git (Secret Protection)**:
   - API Key được tách biệt qua biến môi trường hoặc lưu trong CSDL MySQL, không bao giờ bị hardcode lên GitHub.
   - Giao diện Admin chỉ hiển thị mặt nạ bảo mật (`AIzaSy...4X9Z`).
5. **Nhật ký Kiểm toán (Audit Trail)**:
   - Mọi hành động nhạy cảm (đổi mật khẩu, khóa user, đổi vai trò, bật/tắt tool, đổi API key) đều được ghi tự động vào bảng `audit_logs` với IP client, thời gian và chi tiết hành động.

---

## 5. Hướng dẫn Cài đặt & Khởi chạy

### 1. Yêu cầu môi trường
- **Java**: JDK 21 trở lên.
- **Maven**: 3.8+ (đã tích hợp sẵn Maven Wrapper `mvnw`).
- **MySQL Server**: 8.0 trở lên.
- **Node.js**: Phiên bản 18+ (nếu muốn build ứng dụng Desktop).
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
Server sẽ chạy tại địa chỉ: **`http://localhost:8081`**

> **Tài khoản khởi tạo tự động (DataInitializer):**
> - **Tài khoản Admin:** `username: admin` | `password: admin`
> - Đăng nhập tài khoản Admin để truy cập toàn bộ các tính năng Quản trị hệ thống, Bật/tắt công cụ, và Nhật ký kiểm toán.

---

## 6. Ứng dụng Desktop Windows (.exe)

Soopi cung cấp sẵn bộ đóng gói ứng dụng máy tính dành riêng cho hệ điều hành Windows:

### 1. Chạy nhanh ứng dụng Desktop
Nhấp đúp vào file:
👉 **`run-desktop.bat`**
- Mở trực tiếp cửa sổ ứng dụng Soopi Native Win32 x64.
- Tự động kết nối với backend Spring Boot tại cổng 8081.

### 2. Tự động đóng gói lại file .exe
Khi bạn cập nhật giao diện, chỉ cần nhấp đúp vào file:
👉 **`build-exe.bat`**
Script sẽ tự động:
1. Tắt các tiến trình `Soopi.exe` đang chạy để tránh lỗi khóa tệp.
2. Đồng bộ mã nguồn giao diện mới nhất từ `src/main/resources/static/` vào `desktop/app/`.
3. Chạy `electron-packager` để đóng gói ra file thực thi:
   📍 **`desktop\dist\Soopi-win32-x64\Soopi.exe`**

---

## 7. Danh mục REST API chính

| Phương thức | Đường dẫn API | Mô tả chức năng | Quyền hạn |
|:---:|---|---|:---:|
| **POST** | `/api/auth/login` | Đăng nhập và nhận JWT token | Public |
| **POST** | `/api/auth/register` | Đăng ký tài khoản người dùng mới | Public |
| **POST** | `/api/auth/logout` | Đăng xuất và ghi nhật ký kiểm toán | User / Admin |
| **GET** | `/api/system/models` | Lấy danh sách model được phép sử dụng | Public |
| **GET** | `/api/system/tools` | Lấy danh sách công cụ Agent đang được bật | Public |
| **GET** | `/api/users/me` | Lấy hồ sơ tài khoản hiện tại | User / Admin |
| **PUT** | `/api/users/me/password` | Đổi mật khẩu tài khoản của chính mình | User / Admin |
| **GET / PUT** | `/api/users/me/preferences`| Xem và lưu tùy chọn giao diện, ngôn ngữ, model | User / Admin |
| **GET / POST** | `/api/projects` | Xem danh sách và tạo dự án mới | User / Admin |
| **GET / POST** | `/api/tasks` | Xem danh sách task của mình và tạo task AI mới | User / Admin |
| **POST** | `/api/tasks/{id}/cancel` | Hủy tác vụ đang chạy | Owner |
| **POST** | `/api/tasks/{id}/retry` | Chạy lại tác vụ cũ | Owner |
| **DELETE** | `/api/tasks/{id}` | Xóa tác vụ của mình | Owner |
| **GET / POST** | `/api/chat/conversations` | Quản lý các cuộc hội thoại AI | User / Admin |
| **POST** | `/api/chat` | Gửi tin nhắn và nhận phản hồi từ AI Agent | User / Admin |
| **GET** | `/api/admin/stats` | Xem số liệu thống kê hệ thống toàn diện | Admin |
| **GET** | `/api/admin/users` | Danh sách tài khoản người dùng và trạng thái khóa | Admin |
| **PUT** | `/api/admin/users/{id}/lock` | Khóa tài khoản người dùng | Admin |
| **PUT** | `/api/admin/users/{id}/unlock` | Mở khóa tài khoản người dùng | Admin |
| **GET / PUT** | `/api/admin/tools` | Xem và Bật / Tắt các công cụ của Agent | Admin |
| **GET / PUT** | `/api/admin/settings` | Xem và cập nhật mô hình, Gemini API Key | Admin |
| **GET** | `/api/admin/tasks` | Xem toàn bộ lịch sử tác vụ toàn hệ thống | Admin |
| **GET** | `/api/admin/audit-logs` | Xem nhật ký kiểm toán hệ thống | Admin |

---

## 8. Kiểm thử Tự động (Automated Tests)

Dự án có bộ kiểm thử tự động toàn diện với **71 test cases** bao phủ:
- Unit tests cho các Agent, Service, DTO Deserializer.
- Security tests kiểm tra phân quyền chặt chẽ giữa Role USER và ADMIN.
- Kiểm tra tính toàn vẹn của mã hóa mật khẩu và xác thực JWT.

Chạy kiểm thử:
```powershell
.\mvnw.cmd test
```

---

## Bản quyền & Tác giả
- **Tác giả:** anhzxje
- **Dự án:** Soopi - AI Agent Platform
- **Giấy phép:** MIT License
