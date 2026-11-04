# Hướng dẫn Triển khai & Vận hành Hệ thống (Deployment & Operations Guide)
# Dự án: Soopi AI Agent Platform

> **Phiên bản:** 1.0.0  
> **Áp dụng cho:** Môi trường Local Development, Máy chủ On-Premises, Máy chủ Đám mây (VPS/Cloud) và Docker Container.

---

## 1. Yêu cầu Hệ thống (System Requirements)

### 1.1. Yêu cầu Phần cứng (Hardware Requirements)
| Thành phần | Khuyến nghị Tối thiểu (Dev) | Khuyến nghị Sản xuất (Production) |
|---|---|---|
| **CPU** | 2 vCPU (x86_64 hoặc ARM64) | 4 vCPU trở lên |
| **RAM** | 4 GB | 8 GB trở lên (Tối ưu cho JVM heap 4GB và MySQL) |
| **Ổ cứng** | 10 GB SSD còn trống | 50 GB NVMe SSD (Lưu trữ CSDL, Audit Logs, Logs) |
| **Băng thông** | Kết nối Internet ổn định | Kết nối ổn định tới Google Gemini API (`generativelanguage.googleapis.com`) |

### 1.2. Yêu cầu Phần mềm (Software Prerequisites)
- **Hệ điều hành**: Windows 10/11, Ubuntu 20.04/22.04 LTS, Debian 11/12 hoặc macOS Sonoma+.
- **Java Development Kit (JDK)**: **Java 21 LTS** (Eclipse Temurin hoặc Oracle OpenJDK).
- **Maven**: 3.8+ (Dự án đã tích hợp sẵn Maven Wrapper `mvnw` / `mvnw.cmd`).
- **Cơ sở dữ liệu**: **MySQL 8.0** trở lên (Hỗ trợ kiểu dữ liệu JSON).
- **Docker & Docker Compose** *(nếu chạy container)*: Docker Engine 24.0+ và Docker Compose v2.20+.
- **Google Gemini API Key**: Đăng ký và lấy key miễn phí từ [Google AI Studio](https://aistudio.google.com/app/apikey).

---

## 2. Bảng Tham chiếu Biến Môi trường (Environment Variables)

Hệ thống được cấu hình hoàn toàn thông qua các biến môi trường chuẩn Twelve-Factor App:

| Tên biến môi trường | Bắt buộc | Giá trị mặc định | Mô tả chi tiết & Hướng dẫn |
|---|:---:|---|---|
| `GOOGLE_API_KEY` | **Có** | `AIzaSyPlaceholder...` | API Key truy cập Google Gemini API. |
| `ADMIN_INITIAL_PASSWORD` | **Có** | *(Trống)* | Mật khẩu khởi tạo cho tài khoản `admin` trong lần chạy đầu tiên. **Tối thiểu 12 ký tự**. |
| `JWT_SECRET` | Khuyến nghị | `404E6352...` *(Test key)* | Khóa bí mật HMAC-SHA256 (tối thiểu 256 bits). Phải tạo chuỗi ngẫu nhiên khi đưa lên Production. |
| `DB_USERNAME` | Tùy chọn | `root` | Tên người dùng kết nối MySQL. Khi dùng Docker Compose mặc định là `ai_agent`. |
| `DB_PASSWORD` | Tùy chọn | `root` | Mật khẩu kết nối MySQL. |
| `DB_ROOT_PASSWORD` | Khi dùng Docker | *(Trống)* | Mật khẩu tài khoản root của MySQL container. |
| `SPRING_DATASOURCE_URL` | Tùy chọn | `jdbc:mysql://localhost:3306/ai_agent...` | Chuỗi kết nối JDBC tới máy chủ CSDL MySQL. |
| `AGENT_ALLOWED_PATH` | Khuyến nghị | `D:/AI Agent` hoặc `/workspace` | Danh sách các thư mục an toàn mà AI Agent được phép đọc mã nguồn (Chống Path Traversal). |
| `CORS_ALLOWED_ORIGINS` | Tùy chọn | `http://localhost:8080,http://127.0.0.1:8080` | Danh sách domain được phép gửi request đến API (phân cách bởi dấu phẩy). |

---

## 3. Triển khai Môi trường Phát triển (Local Development)

### Bước 1: Khởi tạo Cơ sở dữ liệu MySQL
Mở MySQL Workbench hoặc terminal MySQL và thực thi câu lệnh tạo database:

```sql
CREATE DATABASE ai_agent CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### Bước 2: Thiết lập Biến Môi trường

#### Trên Windows (PowerShell):
```powershell
$env:GOOGLE_API_KEY = "AIzaSy_KEY_CỦA_BẠN_TỪ_GOOGLE_AI_STUDIO"
$env:ADMIN_INITIAL_PASSWORD = "AdminSecurePassword2026!#"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "mat_khau_mysql_cua_ban"
$env:AGENT_ALLOWED_PATH = "D:/AI Agent"
```

#### Trên Linux / macOS (Bash / Zsh):
```bash
export GOOGLE_API_KEY="AIzaSy_KEY_CỦA_BẠN_TỪ_GOOGLE_AI_STUDIO"
export ADMIN_INITIAL_PASSWORD="AdminSecurePassword2026!#"
export DB_USERNAME="root"
export DB_PASSWORD="mat_khau_mysql_cua_ban"
export AGENT_ALLOWED_PATH="$(pwd)"
```

### Bước 3: Khởi chạy Ứng dụng Backend

#### Chạy trên Windows:
```powershell
.\mvnw.cmd spring-boot:run
```

#### Chạy trên Linux / macOS:
```bash
chmod +x ./mvnw
./mvnw spring-boot:run
```

### Bước 4: Kiểm tra Trạng thái Hoạt động
1. Mở trình duyệt và truy cập: **`http://localhost:8080`**
2. Đăng nhập tài khoản Quản trị viên:
   - **Tài khoản:** `admin`
   - **Mật khẩu:** Giá trị bạn đã thiết lập trong biến `ADMIN_INITIAL_PASSWORD`.
3. Kiểm tra endpoint sức khỏe: `http://localhost:8080/actuator/health` (Phải trả về `{"status":"UP"}`).

---

## 4. Triển khai Container hóa với Docker & Docker Compose

Docker Compose là phương án triển khai chuẩn hóa, an toàn và dễ dàng nhất cho môi trường Server và Production.

### 4.1. Cấu trúc Multi-stage Dockerfile
Dự án sử dụng cơ chế build 2 giai đoạn:
1. **Stage 1 (Builder)**: Sử dụng `eclipse-temurin:21-jdk-alpine`, cache dependencies Maven và đóng gói file JAR.
2. **Stage 2 (Runtime)**: Sử dụng `eclipse-temurin:21-jre-alpine` siêu nhẹ, loại bỏ toàn bộ công cụ biên dịch để tối ưu kích thước image (< 200 MB) và tăng cường bảo mật.

### 4.2. Khởi tạo tệp `.env`
Tạo một tệp `.env` nằm cùng thư mục với `docker-compose.yml`:

```ini
# Cấu hình CSDL MySQL
DB_ROOT_PASSWORD=RootSuperSecurePassword2026!
DB_USERNAME=ai_agent
DB_PASSWORD=AgentUserSecurePassword2026!

# Khóa Gemini LLM
GOOGLE_API_KEY=AIzaSy_YOUR_REAL_GEMINI_API_KEY

# Khóa bí mật JWT (Tạo chuỗi ngẫu nhiên 256-bit)
JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970

# Mật khẩu khởi tạo Quản trị viên
ADMIN_INITIAL_PASSWORD=AdminStrongMasterPassword2026!

# Cấu hình CORS
CORS_ALLOWED_ORIGINS=http://localhost:8080,http://127.0.0.1:8080,https://soopi.yourdomain.com
```

### 4.3. Khởi chạy Dịch vụ (Docker Compose)

Thực thi lệnh sau tại thư mục gốc của dự án:

```bash
docker compose up -d --build
```

Kiểm tra trạng thái các container:
```bash
docker compose ps
```

Xem log ứng dụng theo thời gian thực:
```bash
docker compose logs -f app
```

Dừng và hạ các dịch vụ:
```bash
docker compose down
```

> **Lưu ý về Thư mục Mã nguồn Sandbox:**  
> Trong `docker-compose.yml`, thư mục mã nguồn được mount vào container tại đường dẫn `/workspace:ro` (Chế độ **Chỉ đọc - Read-Only**). AI Agent chỉ có thể phân tích các dự án nằm trong thư mục này, đảm bảo cách ly tuyệt đối với hệ điều hành máy chủ.

---

## 5. Hướng dẫn Tối ưu & Tăng cường Bảo mật Production (Hardening Checklist)

Khi đưa ứng dụng lên máy chủ Internet công khai (Production), hãy thực hiện đầy đủ checklist bảo mật sau:

### 5.1. Tạo chuỗi bí mật JWT ngẫu nhiên
Không sử dụng chuỗi bí mật mẫu trong mã nguồn. Hãy tạo một chuỗi ngẫu nhiên 64 byte bằng OpenSSL:
```bash
openssl rand -hex 32
```
Gán giá trị sinh ra vào biến `JWT_SECRET`.

### 5.2. Cấu hình Reverse Proxy Nginx & Chứng chỉ SSL (HTTPS)
Khuyến nghị đặt ứng dụng phía sau Nginx Reverse Proxy để bật HTTPS và quản lý chứng chỉ Let's Encrypt:

```nginx
server {
    listen 80;
    server_name soopi.yourdomain.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name soopi.yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/soopi.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/soopi.yourdomain.com/privkey.pem;

    # Cấu hình bảo mật HTTP Headers
    add_header X-Frame-Options "SAMEORIGIN";
    add_header X-XSS-Protection "1; mode=block";
    add_header X-Content-Type-Options "nosniff";

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # Hỗ trợ timeout dài cho các tác vụ AI phân tích sâu
        proxy_read_timeout 300s;
        proxy_connect_timeout 300s;
    }
}
```

### 5.3. Chiến lược Sao lưu Dữ liệu MySQL (Automated Backup)
Thiết lập cronjob hàng ngày để sao lưu dữ liệu `ai_agent` trên máy chủ:

```bash
#!/bin/bash
BACKUP_DIR="/var/backups/mysql"
DATE=$(date +%Y%m%d_%H%M%S)
mkdir -p $BACKUP_DIR

# Sao lưu database qua mysqldump
docker exec $(docker compose ps -q mysql) mysqldump -u ai_agent -p'AgentUserSecurePassword2026!' ai_agent | gzip > "$BACKUP_DIR/ai_agent_$DATE.sql.gz"

# Giữ lại bản sao lưu trong 30 ngày gần nhất
find $BACKUP_DIR -type f -name "*.sql.gz" -mtime +30 -exec rm {} \;
```

---

## 6. Hướng dẫn Xử lý Sự cố Thường gặp (Troubleshooting)

### 6.1. Sự cố: Không thể kết nối tới MySQL (`Communications link failure`)
- **Nguyên nhân:** Dịch vụ MySQL chưa khởi động xong hoặc sai địa chỉ máy chủ / tài khoản kết nối.
- **Khắc phục:**
  - Kiểm tra trạng thái MySQL bằng lệnh: `docker compose ps` hoặc `systemctl status mysql`.
  - Nếu chạy Docker, kiểm tra block `healthcheck` của dịch vụ `mysql` trong `docker-compose.yml`. Ứng dụng `app` chỉ khởi động khi MySQL đã sẵn sàng (`service_healthy`).

### 6.2. Sự cố: Lỗi `429 Too Many Requests` từ Google Gemini API
- **Nguyên nhân:** Bạn đang sử dụng API Key miễn phí và đã chạm hạn mức số lượng yêu cầu mỗi phút (RPM) hoặc số lượng yêu cầu mỗi ngày (RPD).
- **Khắc phục:**
  - Truy cập mục **Quản trị viên -> Cấu hình Hệ thống** trên giao diện Web (`/api/admin/settings`).
  - Chuyển sang model nhẹ hơn như `gemini-3.5-flash-lite` (hạn mức cao và tiêu tốn ít quota).
  - Hoặc cập nhật API Key mới / nâng cấp tài khoản Google Cloud sang gói Pay-as-you-go.

### 6.3. Sự cố: Lỗi `SecurityException: Đường dẫn không được cấp phép` (Path Traversal Blocked)
- **Nguyên nhân:** Người dùng đăng ký một dự án nằm ngoài danh sách thư mục được khai báo trong `agent.allowed-paths`.
- **Khắc phục:**
  - Kiểm tra biến môi trường `AGENT_ALLOWED_PATH`.
  - Đảm bảo đường dẫn dự án được đăng ký nằm hoàn toàn bên trong thư mục cho phép (Ví dụ: `D:/AI Agent/...` trên Windows hoặc `/workspace/...` trên Docker).

### 6.4. Sự cố: Không thể đăng nhập bằng tài khoản `admin`
- **Nguyên nhân:** Biến `ADMIN_INITIAL_PASSWORD` chưa được cung cấp hoặc ngắn hơn 12 ký tự trong lần khởi chạy đầu tiên.
- **Khắc phục:**
  - Đặt biến `ADMIN_INITIAL_PASSWORD` với chuỗi dài từ 12 ký tự trở lên.
  - Nếu CSDL đã được tạo nhưng chưa có mật khẩu admin, bạn có thể xóa volume CSDL cũ (trong môi trường dev: `docker compose down -v`) hoặc cập nhật trường `password` trong bảng `users` bằng chuỗi băm BCrypt mới.
