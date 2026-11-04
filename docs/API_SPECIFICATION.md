# Đặc tả RESTful API (REST API Specification)
# Dự án: Soopi AI Agent Platform

> **Phiên bản API:** v1.0.0  
> **Giao thức:** HTTP/1.1 & HTTPS  
> **Định dạng dữ liệu:** `application/json;charset=UTF-8`  
> **Cơ chế xác thực:** JSON Web Token (JWT) qua tiêu đề `Authorization: Bearer <TOKEN>`  
> **Base URL:** `http://localhost:8080` (hoặc domain production cấu hình)

---

## 1. Quy ước Chung & Tiêu chuẩn Phản hồi

### 1.1. Tiêu đề Yêu cầu (Request Headers)
Mọi yêu cầu gửi đến các endpoint được bảo vệ (Protected Endpoints) bắt buộc phải kèm theo các header sau:

```http
Content-Type: application/json
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### 1.2. Mã Trạng thái HTTP (HTTP Status Codes)
| Mã HTTP | Tên mã | Ý nghĩa |
|---|---|---|
| `200 OK` | Thành công | Yêu cầu được xử lý thành công và có dữ liệu trả về |
| `201 Created` | Đã khởi tạo | Tài nguyên mới đã được tạo thành công (User, Project, Task, Conversation) |
| `204 No Content` | Không có nội dung | Thao tác thành công (thường là lệnh xóa DELETE), không cần trả body |
| `400 Bad Request` | Dữ liệu không hợp lệ | Tham số không hợp lệ, vi phạm ràng buộc validation |
| `401 Unauthorized` | Chưa xác thực | Thiếu token, token không hợp lệ hoặc đã hết hạn |
| `403 Forbidden` | Bị từ chối quyền | Tài khoản bị khóa (`isLocked`), hoặc không đủ quyền (cần quyền ADMIN) |
| `404 Not Found` | Không tìm thấy | Tài nguyên theo ID chỉ định không tồn tại trong hệ thống |
| `500 Internal Error` | Lỗi máy chủ | Lỗi ngoại lệ chưa được xử lý hoặc dịch vụ bên ngoài (LLM) gặp sự cố |

### 1.3. Cấu trúc Phản hồi Lỗi Chuẩn (Standard Error Response)
Khi có lỗi xảy ra, API trả về cấu trúc JSON đồng nhất:

```json
{
  "status": 400,
  "message": "Tên đăng nhập đã tồn tại trong hệ thống",
  "path": "/api/auth/register",
  "timestamp": "2026-10-27T15:30:00"
}
```

---

## 2. Nhóm API Xác thực (Authentication API - `/api/auth`)

### 2.1. Đăng ký Tài khoản Mới
- **Endpoint:** `POST /api/auth/register`
- **Quyền hạn:** Công khai (`Public / PermitAll`)
- **Mô tả:** Đăng ký tài khoản thành viên mới trong hệ thống với vai trò mặc định là `ROLE_USER`.

#### Request Body
```json
{
  "username": "developer01",
  "email": "dev01@example.com",
  "password": "Password123@"
}
```
*Ràng buộc:*
- `username`: Bắt buộc, không trùng lặp, độ dài từ 3 - 50 ký tự.
- `email`: Bắt buộc, định dạng email hợp lệ, không trùng lặp.
- `password`: Bắt buộc, độ dài tối thiểu 6 ký tự.

#### Response: `201 Created`
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "username": "developer01",
  "role": "USER",
  "message": "Đăng ký tài khoản thành công"
}
```

---

### 2.2. Đăng nhập & Lấy JWT Token
- **Endpoint:** `POST /api/auth/login`
- **Quyền hạn:** Công khai (`Public / PermitAll`)
- **Mô tả:** Xác thực thông tin đăng nhập và cấp token JWT nếu tài khoản hợp lệ và không bị khóa.

#### Request Body
```json
{
  "username": "developer01",
  "password": "Password123@"
}
```

#### Response: `200 OK`
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "username": "developer01",
  "role": "USER",
  "message": "Đăng nhập thành công"
}
```

#### Response Lỗi: `403 Forbidden` (Khi tài khoản bị khóa)
```json
{
  "message": "Tài khoản của bạn đã bị khóa bởi Quản trị viên"
}
```

---

### 2.3. Đăng xuất
- **Endpoint:** `POST /api/auth/logout`
- **Quyền hạn:** `USER` hoặc `ADMIN` (Yêu cầu JWT Token)
- **Mô tả:** Hủy phiên làm việc và ghi nhận vào nhật ký kiểm toán hệ thống.

#### Response: `200 OK`
```json
{
  "message": "Đăng xuất thành công"
}
```

---

## 3. Nhóm API Quản lý Người dùng (User API - `/api/users`)

### 3.1. Lấy Thông tin Hồ sơ Bản thân
- **Endpoint:** `GET /api/users/me`
- **Quyền hạn:** `USER` hoặc `ADMIN`
- **Mô tả:** Lấy thông tin tài khoản hiện tại từ phiên đăng nhập.

#### Response: `200 OK`
```json
{
  "id": 1,
  "username": "developer01",
  "email": "dev01@example.com",
  "role": "USER",
  "createdAt": "2026-10-25T08:00:00"
}
```

---

### 3.2. Đổi Mật khẩu Bản thân
- **Endpoint:** `PUT /api/users/me/password` *(Hỗ trợ alias: `POST /api/users/change-password`)*
- **Quyền hạn:** `USER` hoặc `ADMIN`
- **Mô tả:** Người dùng tự cập nhật mật khẩu đăng nhập của chính mình.

#### Request Body
```json
{
  "oldPassword": "Password123@",
  "newPassword": "NewSecurePassword456@"
}
```

#### Response: `200 OK`
```json
{
  "message": "Đổi mật khẩu thành công"
}
```

---

### 3.3. Xem & Cập nhật Tùy chọn Cá nhân (Preferences)
- **Endpoint:** `GET /api/users/me/preferences`
- **Quyền hạn:** `USER` hoặc `ADMIN`
- **Mô tả:** Lấy cấu hình giao diện (theme sáng/tối), ngôn ngữ hiển thị và model ưa thích.

#### Response: `200 OK`
```json
{
  "language": "vi",
  "theme": "dark",
  "preferredModel": "gemini-3.5-flash-lite"
}
```

- **Endpoint:** `PUT /api/users/me/preferences`
- **Quyền hạn:** `USER` hoặc `ADMIN`

#### Request Body
```json
{
  "language": "vi",
  "theme": "dark",
  "preferredModel": "gemini-2.5-flash"
}
```

#### Response: `200 OK`
```json
{
  "language": "vi",
  "theme": "dark",
  "preferredModel": "gemini-2.5-flash"
}
```

---

## 4. Nhóm API Quản lý Dự án (Project API - `/api/projects`)

### 4.1. Lấy Danh sách Dự án của Tôi
- **Endpoint:** `GET /api/projects`
- **Quyền hạn:** `USER` hoặc `ADMIN`
- **Mô tả:** Trả về danh sách dự án do người dùng hiện tại sở hữu, sắp xếp theo thời gian tạo mới nhất.

#### Response: `200 OK`
```json
[
  {
    "id": 10,
    "name": "Backend E-Commerce",
    "description": "Dự án Java Spring Boot microservice",
    "path": "D:/Projects/ecommerce-api",
    "language": "Java",
    "ownerUsername": "developer01",
    "createdAt": "2026-10-26T10:00:00",
    "updatedAt": "2026-10-26T10:00:00"
  }
]
```

---

### 4.2. Xem Chi tiết một Dự án
- **Endpoint:** `GET /api/projects/{id}`
- **Quyền hạn:** Chủ sở hữu dự án (`Owner`) hoặc `ADMIN`
- **Mô tả:** Lấy thông tin dự án theo ID. Tự động kiểm tra quyền sở hữu chống IDOR.

#### Response: `200 OK`
```json
{
  "id": 10,
  "name": "Backend E-Commerce",
  "description": "Dự án Java Spring Boot microservice",
  "path": "D:/Projects/ecommerce-api",
  "language": "Java",
  "ownerUsername": "developer01",
  "createdAt": "2026-10-26T10:00:00",
  "updatedAt": "2026-10-26T10:00:00"
}
```

---

### 4.3. Đăng ký Tạo mới Dự án
- **Endpoint:** `POST /api/projects`
- **Quyền hạn:** `USER` hoặc `ADMIN`
- **Mô tả:** Đăng ký đường dẫn thư mục mã nguồn vào hệ thống để AI Agent phân tích.

#### Request Body
```json
{
  "name": "Backend E-Commerce",
  "description": "Dự án Java Spring Boot microservice",
  "path": "D:/Projects/ecommerce-api",
  "language": "Java"
}
```

#### Response: `201 Created`
*(Cấu trúc tương tự ProjectResponse ở trên)*

---

### 4.4. Cập nhật Dự án
- **Endpoint:** `PUT /api/projects/{id}`
- **Quyền hạn:** Chủ sở hữu dự án (`Owner`) hoặc `ADMIN`

#### Request Body
```json
{
  "name": "Backend E-Commerce (Refactored)",
  "description": "Dự án cập nhật lên Java 21",
  "path": "D:/Projects/ecommerce-api",
  "language": "Java"
}
```

#### Response: `200 OK`
*(Cấu trúc tương tự ProjectResponse sau khi cập nhật)*

---

### 4.5. Xóa Dự án Tầng Sâu (Cascading Delete)
- **Endpoint:** `DELETE /api/projects/{id}`
- **Quyền hạn:** Chủ sở hữu dự án (`Owner`) hoặc `ADMIN`
- **Mô tả:** Xóa vĩnh viễn dự án kèm toàn bộ tác vụ AI (`agent_tasks`), cuộc hội thoại (`chat_conversations`) và tin nhắn liên quan.

#### Response: `204 No Content`

---

## 5. Nhóm API Tác vụ AI (Agent Task API - `/api/tasks`)

### 5.1. Khởi tạo Tác vụ AI Mới
- **Endpoint:** `POST /api/tasks`
- **Quyền hạn:** `USER` hoặc `ADMIN`
- **Mô tả:** Tạo tác vụ mới, kích hoạt worker chạy ngầm trên ThreadPool riêng biệt. Trả về ngay lập tức với trạng thái `PENDING`.

#### Request Body
```json
{
  "projectId": 10,
  "type": "CODE_REVIEW",
  "input": "{\"focus\": \"Security & Performance\", \"model\": \"gemini-2.5-flash\", \"selectedTools\": [\"list_directory\", \"glob\", \"file_system\", \"grep\"]}"
}
```
*Ghi chú:* `type` chấp nhận một trong các giá trị enum: `CODE_REVIEW`, `CODE_FIX`, `TEST_GENERATION`.

#### Response: `201 Created`
```json
{
  "id": 105,
  "projectId": 10,
  "projectName": "Backend E-Commerce",
  "type": "CODE_REVIEW",
  "status": "PENDING",
  "input": "{\"focus\": \"Security & Performance\", \"model\": \"gemini-2.5-flash\", \"selectedTools\": [\"list_directory\", \"glob\", \"file_system\", \"grep\"]}",
  "output": null,
  "errorMessage": null,
  "createdAt": "2026-10-27T10:15:00",
  "completedAt": null,
  "isPinned": false
}
```

---

### 5.2. Lấy Danh sách Tác vụ của Bản thân
- **Endpoint:** `GET /api/tasks`
- **Quyền hạn:** `USER` hoặc `ADMIN`
- **Mô tả:** Trả về toàn bộ danh sách tác vụ của người dùng hiện tại, tự động ưu tiên các task được ghim (`isPinned = true`) và sắp xếp giảm dần theo thời gian tạo.

#### Response: `200 OK`
```json
[
  {
    "id": 105,
    "projectId": 10,
    "projectName": "Backend E-Commerce",
    "type": "CODE_REVIEW",
    "status": "COMPLETED",
    "input": "{\"focus\": \"Security\"}",
    "output": "{\"summary\": \"Phân tích phát hiện 2 lỗi bảo mật nghiêm trọng...\", \"issues\": [...]}",
    "errorMessage": null,
    "createdAt": "2026-10-27T10:15:00",
    "completedAt": "2026-10-27T10:16:30",
    "isPinned": true
  }
]
```

---

### 5.3. Xem Chi tiết một Tác vụ
- **Endpoint:** `GET /api/tasks/{id}`
- **Quyền hạn:** Chủ sở hữu task hoặc `ADMIN`

#### Response: `200 OK`
*(Cấu trúc tương tự AgentTaskResponse)*

---

### 5.4. Lọc Tác vụ theo Dự án
- **Endpoint:** `GET /api/tasks/project/{projectId}`
- **Quyền hạn:** Chủ sở hữu dự án hoặc `ADMIN`

#### Response: `200 OK`
*(Danh sách các AgentTaskResponse thuộc dự án)*

---

### 5.5. Ghim / Bỏ ghim Tác vụ
- **Endpoint:** `PATCH /api/tasks/{id}/pin`
- **Quyền hạn:** Chủ sở hữu task hoặc `ADMIN`
- **Mô tả:** Đảo ngược trạng thái ghim (`isPinned: true/false`) lên đầu danh sách thanh điều hướng.

#### Response: `200 OK`
```json
{
  "id": 105,
  "isPinned": true,
  "status": "COMPLETED"
}
```

---

### 5.6. Hủy Tác vụ Đang Chạy
- **Endpoint:** `POST /api/tasks/{id}/cancel`
- **Quyền hạn:** Chủ sở hữu task
- **Mô tả:** Hủy tác vụ khi đang ở trạng thái `PENDING` hoặc `PROCESSING`. Chuyển trạng thái sang `CANCELLED`.

#### Response: `200 OK`
```json
{
  "id": 105,
  "status": "CANCELLED",
  "errorMessage": "Tác vụ đã được hủy bởi người dùng"
}
```

---

### 5.7. Chạy lại Tác vụ Thất bại (Retry)
- **Endpoint:** `POST /api/tasks/{id}/retry`
- **Quyền hạn:** Chủ sở hữu task
- **Mô tả:** Khởi động lại tác vụ cũ với cấu hình tương tự, gán worker mới và chuyển status về `PENDING`.

#### Response: `200 OK`
```json
{
  "id": 105,
  "status": "PENDING",
  "errorMessage": null
}
```

---

### 5.8. Xóa Tác vụ
- **Endpoint:** `DELETE /api/tasks/{id}` (Xóa một tác vụ cụ thể)
- **Endpoint:** `DELETE /api/tasks` (Xóa toàn bộ lịch sử tác vụ của người dùng hiện tại)
- **Quyền hạn:** Chủ sở hữu task

#### Response: `200 OK`
```json
{
  "message": "Đã xóa tác vụ #105 thành công"
}
```

---

## 6. Nhóm API Hội thoại Trực tiếp (Chat API - `/api/chat`)

### 6.1. Lấy Danh sách Cuộc hội thoại
- **Endpoint:** `GET /api/chat/conversations`
- **Quyền hạn:** `USER` hoặc `ADMIN`
- **Mô tả:** Lấy tất cả phiên trò chuyện của người dùng, sắp xếp theo thời điểm cập nhật gần nhất.

#### Response: `200 OK`
```json
[
  {
    "id": 20,
    "title": "Hỏi đáp chung về tối ưu SQL",
    "model": "gemini-3.5-flash-lite",
    "projectId": null,
    "projectName": null,
    "createdAt": "2026-10-27T09:00:00",
    "updatedAt": "2026-10-27T09:30:00"
  }
]
```

---

### 6.2. Tạo Cuộc hội thoại Mới
- **Endpoint:** `POST /api/chat/conversations`
- **Quyền hạn:** `USER` hoặc `ADMIN`

#### Request Body
```json
{
  "title": "Tra cứu hàm xử lý JWT trong dự án",
  "model": "gemini-2.5-flash",
  "projectId": 10
}
```
*Ghi chú:* Nếu `projectId` là `null`, cuộc hội thoại thuộc chế độ **Hỏi đáp chung** (General Chat).

#### Response: `201 Created`
*(Cấu trúc ChatConversationResponse)*

---

### 6.3. Xem Chi tiết & Lịch sử Tin nhắn trong Phiên
- **Endpoint:** `GET /api/chat/conversations/{id}`
- **Quyền hạn:** Chủ sở hữu phiên chat hoặc `ADMIN`

#### Response: `200 OK`
```json
{
  "id": 20,
  "title": "Hỏi đáp chung về tối ưu SQL",
  "model": "gemini-3.5-flash-lite",
  "projectId": null,
  "messages": [
    {
      "id": 301,
      "sender": "USER",
      "content": "Làm thế nào để tránh lỗi N+1 query trong Spring Boot?",
      "createdAt": "2026-10-27T09:00:05"
    },
    {
      "id": 302,
      "sender": "AGENT",
      "content": "Để khắc phục lỗi N+1 query trong Spring Data JPA, bạn có thể áp dụng các giải pháp sau:\n1. Sử dụng `@EntityGraph`...\n2. Sử dụng câu lệnh JPQL với `JOIN FETCH`...",
      "createdAt": "2026-10-27T09:00:10"
    }
  ]
}
```

---

### 6.4. Đổi Model AI cho Cuộc hội thoại
- **Endpoint:** `PATCH /api/chat/conversations/{id}/model`
- **Quyền hạn:** Chủ sở hữu phiên chat hoặc `ADMIN`

#### Request Body
```json
{
  "model": "gemini-2.5-pro"
}
```

#### Response: `200 OK`
```json
{
  "id": 20,
  "model": "gemini-2.5-pro"
}
```

---

### 6.5. Gửi Tin nhắn Trò chuyện
- **Endpoint:** `POST /api/chat` *(hoặc `POST /api/chat/conversations/{id}/messages`)*
- **Quyền hạn:** `USER` hoặc `ADMIN`
- **Mô tả:** Gửi câu hỏi đến AI Agent. Tự động khởi tạo phiên chat mới nếu `conversationId` không được cung cấp.

#### Request Body
```json
{
  "conversationId": 20,
  "message": "Hãy cho tôi ví dụ minh họa bằng code Java"
}
```

#### Response: `200 OK`
```json
{
  "id": 303,
  "sender": "AGENT",
  "content": "Dưới đây là ví dụ minh họa sử dụng `@EntityGraph` trên Repository:\n```java\n@EntityGraph(attributePaths = {\"author\"})\nList<Book> findAll();\n```",
  "createdAt": "2026-10-27T09:00:15"
}
```

---

## 7. Nhóm API Hệ thống Công khai (System API - `/api/system`)

### 7.1. Lấy Danh sách Mô hình AI Khả dụng
- **Endpoint:** `GET /api/system/models`
- **Quyền hạn:** Công khai (`PermitAll`)
- **Mô tả:** Lấy model mặc định đang hoạt động và danh sách các model mà Admin cho phép người dùng chọn trên giao diện.

#### Response: `200 OK`
```json
{
  "activeModel": "gemini-3.5-flash-lite",
  "allowedModels": [
    "gemini-3.5-flash-lite",
    "gemini-2.5-flash",
    "gemini-2.5-pro"
  ]
}
```

---

### 7.2. Lấy Danh sách Công cụ Đang Bật
- **Endpoint:** `GET /api/system/tools`
- **Quyền hạn:** Công khai (`PermitAll`)
- **Mô tả:** Trả về danh sách các công cụ mà Admin đang kích hoạt (`enabled = true`). Người dùng chỉ được chọn các công cụ này khi chạy tác vụ.

#### Response: `200 OK`
```json
[
  {
    "toolName": "list_directory",
    "displayName": "Duyệt cây thư mục",
    "description": "Duyệt cấu trúc tệp tin và thư mục dự án",
    "enabled": true,
    "category": "CORE"
  },
  {
    "toolName": "glob",
    "displayName": "Tìm kiếm File (Glob)",
    "description": "Tìm kiếm tệp theo mẫu wildcard tương đối",
    "enabled": true,
    "category": "CORE"
  },
  {
    "toolName": "file_system",
    "displayName": "Đọc nội dung mã nguồn",
    "description": "Đọc tệp tin nguồn có đánh số dòng",
    "enabled": true,
    "category": "CORE"
  },
  {
    "toolName": "grep",
    "displayName": "Tìm kiếm từ khóa (Grep)",
    "description": "Tìm chuỗi văn bản chính xác trên toàn bộ dự án",
    "enabled": true,
    "category": "CORE"
  }
]
```

---

## 8. Nhóm API Quản trị Hệ thống (Admin API - `/api/admin`)

> **Yêu cầu bảo mật:** Bắt buộc token phải có vai trò `ROLE_ADMIN` (`@PreAuthorize("hasRole('ADMIN')")`).

### 8.1. Thống kê Toàn diện (Dashboard Stats)
- **Endpoint:** `GET /api/admin/stats`

#### Response: `200 OK`
```json
{
  "totalUsers": 25,
  "totalAdmins": 2,
  "totalRegularUsers": 23,
  "totalProjects": 40,
  "totalTasks": 180,
  "completedTasks": 150,
  "pendingOrProcessingTasks": 5,
  "failedTasks": 25,
  "reviewTasksCount": 120,
  "fixTasksCount": 35,
  "testGenTasksCount": 25
}
```

---

### 8.2. Quản lý Danh sách Người dùng
- **Endpoint:** `GET /api/admin/users`

#### Response: `200 OK`
```json
[
  {
    "id": 1,
    "username": "admin",
    "email": "admin@soopi.ai",
    "role": "ADMIN",
    "isLocked": false,
    "createdAt": "2026-10-24T00:00:00",
    "projectCount": 5,
    "taskCount": 20
  },
  {
    "id": 2,
    "username": "developer01",
    "email": "dev01@example.com",
    "role": "USER",
    "isLocked": false,
    "createdAt": "2026-10-25T08:00:00",
    "projectCount": 2,
    "taskCount": 10
  }
]
```

---

### 8.3. Khóa / Mở khóa Tài khoản Người dùng
- **Endpoint:** `PUT /api/admin/users/{userId}/status?locked=true`
- **Endpoint:** `PUT /api/admin/users/{userId}/lock` (Toggle khóa)
- **Mô tả:** Khóa hoặc mở khóa tài khoản. Khi tài khoản bị khóa, hệ thống lập tức từ chối mọi yêu cầu tiếp theo của tài khoản đó. Ngăn Admin tự khóa chính mình.

#### Response: `200 OK`
```json
{
  "id": 2,
  "username": "developer01",
  "isLocked": true
}
```

---

### 8.4. Phân Quyền Tài khoản
- **Endpoint:** `PUT /api/admin/users/{userId}/role?role=ADMIN`
- **Mô tả:** Chuyển đổi vai trò tài khoản (`USER` $\leftrightarrow$ `ADMIN`). Ngăn Admin tự giáng quyền của chính mình.

#### Response: `200 OK`
```json
{
  "id": 2,
  "username": "developer01",
  "role": "ADMIN"
}
```

---

### 8.5. Xóa Vĩnh viễn Tài khoản (Cascade Delete)
- **Endpoint:** `DELETE /api/admin/users/{userId}`
- **Mô tả:** Xóa vĩnh viễn người dùng cùng toàn bộ dự án, tác vụ, phiên chat liên quan và ghi vết kiểm toán.

#### Response: `200 OK`
```json
{
  "message": "Đã xóa vĩnh viễn người dùng #2 và dọn dẹp sạch toàn bộ dữ liệu phụ thuộc thành công"
}
```

---

### 8.6. Bật / Tắt Công cụ Agent (Tool Management)
- **Endpoint:** `GET /api/admin/tools` (Xem toàn bộ công cụ)
- **Endpoint:** `PUT /api/admin/tools/{toolName}?enabled=false` (Cập nhật trạng thái)
- **Endpoint:** `PUT /api/admin/tools/{toolName}/toggle` (Đảo trạng thái bật/tắt)

#### Response: `200 OK`
```json
{
  "toolName": "grep",
  "displayName": "Tìm kiếm từ khóa (Grep)",
  "enabled": false
}
```

---

### 8.7. Cấu hình Model Mặc định & Gemini API Key
- **Endpoint:** `GET /api/admin/settings` (Xem cấu hình với API Key đã che mặt nạ)
- **Endpoint:** `PUT /api/admin/settings` (Cập nhật model hoặc API Key mới)

#### Request Body (`PUT`)
```json
{
  "model": "gemini-2.5-flash",
  "apiKey": "AIzaSy_NEW_SECRET_KEY_HERE"
}
```

#### Response: `200 OK`
```json
{
  "model": "gemini-2.5-flash",
  "apiKey": "AIzaSy...HERE",
  "allowedModels": [
    "gemini-3.5-flash-lite",
    "gemini-2.5-flash",
    "gemini-2.5-pro"
  ],
  "updatedAt": "2026-10-27T11:00:00"
}
```

---

### 8.8. Giám sát Tác vụ & Lỗi Toàn Hệ thống
- **Endpoint:** `GET /api/admin/tasks?status=ERROR&page=0&size=50` (Xem tất cả task có phân trang và lọc)
- **Endpoint:** `GET /api/admin/tasks/errors` (Xem nhanh danh sách các task gặp lỗi)

#### Response: `200 OK`
```json
[
  {
    "id": 98,
    "userId": 2,
    "username": "developer01",
    "userEmail": "dev01@example.com",
    "projectId": 10,
    "projectName": "Backend E-Commerce",
    "type": "CODE_REVIEW",
    "status": "ERROR",
    "errorMessage": "Gemini API Quota Exceeded (429 Too Many Requests)",
    "createdAt": "2026-10-27T08:00:00"
  }
]
```

---

### 8.9. Xem Nhật ký Kiểm toán (Audit Logs)
- **Endpoint:** `GET /api/admin/audit-logs`
- **Mô tả:** Lấy danh sách 100 nhật ký kiểm toán gần nhất phục vụ thanh tra an ninh.

#### Response: `200 OK`
```json
[
  {
    "id": 501,
    "username": "admin",
    "action": "UPDATE_SYSTEM_SETTINGS",
    "details": "Đã đổi model mặc định sang gemini-2.5-flash",
    "ipAddress": "192.168.1.50",
    "createdAt": "2026-10-27T11:00:00"
  },
  {
    "id": 500,
    "username": "admin",
    "action": "LOCK_USER",
    "details": "Đã khóa tài khoản user id: 4",
    "ipAddress": "192.168.1.50",
    "createdAt": "2026-10-27T10:45:00"
  }
]
```

---

## 9. Kiểm tra Trạng thái Sức khỏe (Health Check - `/actuator/health`)

- **Endpoint:** `GET /actuator/health`
- **Quyền hạn:** Công khai (`PermitAll`)
- **Mô tả:** Được sử dụng bởi Docker Compose Healthcheck và hệ thống giám sát tải (Load Balancer) để kiểm tra tình trạng ứng dụng.

#### Response: `200 OK`
```json
{
  "status": "UP"
}
```
