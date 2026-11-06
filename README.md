# Soopi AI Agent

Ứng dụng hỗ trợ hội thoại kỹ thuật và đánh giá mã nguồn bằng AI. Backend dùng Java 21, Spring Boot, Spring AI và MySQL; giao diện HTML/CSS/JavaScript được phục vụ cùng ứng dụng. Hệ thống hỗ trợ quản lý dự án, hội thoại, tác vụ code review, skills và quản trị người dùng.

## Yêu cầu

- Docker và Docker Compose để chạy toàn bộ ứng dụng; hoặc Java 21 và MySQL 8 để chạy backend trực tiếp.
- Google Gemini API key để sử dụng các chức năng AI.
- Node.js 22+ nếu chạy kiểm thử giao diện.

## Chạy bằng Docker Compose

Tạo tệp `.env` tại thư mục gốc (không đưa mật khẩu hoặc API key vào Git):

```dotenv
DB_ROOT_PASSWORD=change-root-password
DB_USERNAME=ai_agent
DB_PASSWORD=change-database-password
GOOGLE_API_KEY=your-gemini-api-key
JWT_SECRET=your-base64-encoded-secret
ADMIN_INITIAL_PASSWORD=change-this-admin-password
```

Tạo `JWT_SECRET` ngẫu nhiên bằng `openssl rand -base64 32`. Mật khẩu admin ban đầu cần ít nhất 12 ký tự. Sau đó chạy:

```bash
docker compose up -d --build
```

Mở <http://localhost:8080>, đăng nhập bằng `admin` và mật khẩu đã đặt. Admin chỉ được tạo khi tài khoản này chưa tồn tại. Kiểm tra trạng thái bằng `docker compose ps` hoặc `curl http://localhost:8080/actuator/health`; dừng bằng `docker compose down` (dữ liệu MySQL được giữ trong volume).

Compose gắn thư mục dự án vào `/workspace` ở chế độ chỉ đọc. Đường dẫn dự án nhập vào ứng dụng phải là đường dẫn bên trong container, ví dụ `/workspace/src/main/java`. Tùy chỉnh mount và `AGENT_ALLOWED_PATH` trong `docker-compose.yml` nếu cần review mã nguồn khác.

## Chạy trực tiếp

Khởi động MySQL và tạo database `ai_agent`, sau đó khai báo các biến môi trường `DB_USERNAME`, `DB_PASSWORD`, `GOOGLE_API_KEY`, `JWT_SECRET`, `ADMIN_INITIAL_PASSWORD` và `AGENT_ALLOWED_PATH` (thư mục được phép đọc mã nguồn). Có thể đặt thêm `DB_HOST` và `DB_PORT`; mặc định là `localhost:3306`. Chạy:

```bash
./mvnw spring-boot:run
```

Hoặc lưu cấu hình vào `.env` và chạy `bash scripts/run-local.sh`. Biến trong `.env` được tập lệnh nạp khi chạy trực tiếp; Docker Compose cũng dùng tệp này để thay thế cấu hình. Hibernate đang dùng `ddl-auto: update`, vì vậy hãy sao lưu và kiểm tra schema trước khi sử dụng với dữ liệu hiện có.

## Cấu trúc

- `src/main/java/prj/anhzxje/aiagent/`: backend, chia theo tính năng như `auth`, `chat`, `projects`, `tasks`, `review`, `skills` và `admin`.
- `src/main/resources/static/`: giao diện web; `skills/` và `prompts/` chứa dữ liệu sử dụng lúc chạy.
- `src/main/resources/db/`: SQL hỗ trợ triển khai thủ công.
- `src/test/`: kiểm thử backend và frontend; `evaluation/` chứa dữ liệu mẫu cần cho kiểm thử đánh giá.
- `pom.xml`, `Dockerfile`, `docker-compose.yml`: cấu hình build và triển khai.

## Kiểm thử

```bash
./mvnw verify
node --test src/test/js/*.test.cjs
```

Kiểm thử kiến trúc dùng `python3 -m unittest discover -s src/test/python -p 'test_*.py'` và `python3 scripts/check-architecture.py --bytecode target/classes`. Kiểm thử trình duyệt cần Chromium: đặt `CHROMIUM_PATH` rồi chạy `node src/test/js/workspace.browser.cjs`. Các kiểm thử backend dùng cơ sở dữ liệu và nhà cung cấp giả lập, không cần gọi Gemini.
