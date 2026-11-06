# Soopi AI Agent

Soopi AI Agent là ứng dụng desktop Linux hỗ trợ hội thoại kỹ thuật và rà soát mã nguồn bằng AI. Ứng dụng có cửa sổ riêng, hộp thoại chọn thư mục và các chức năng quản lý dự án, tác vụ, hội thoại, skills và quản trị người dùng.

Cửa sổ dùng GTK và WebKitGTK để hiển thị giao diện HTML/CSS/JavaScript. Backend Java 21, Spring Boot và Spring AI chạy trên máy cục bộ; MySQL chạy qua Docker Compose. Giao tiếp giữa cửa sổ và backend dùng HTTP trên `127.0.0.1:18080`. Tính năng AI cần kết nối đến nhà cung cấp mô hình theo cấu hình.

## Yêu cầu

- Linux có môi trường đồ họa, Java 21, Python 3, GTK 3 và WebKitGTK 4.1; trên Ubuntu có thể cài `python3-gi`, `gir1.2-gtk-3.0`, `gir1.2-webkit2-4.1`.
- Docker đang chạy và Docker Compose để khởi động MySQL.
- Google Gemini API key để sử dụng các chức năng AI.
- Node.js 22+ nếu chạy kiểm thử giao diện JavaScript.

## Cấu hình lần đầu

Tạo tệp `.env` tại thư mục gốc; tệp này được nạp bởi Bash nên đường dẫn có khoảng trắng cần đặt trong dấu nháy. Không đưa mật khẩu hoặc API key vào Git.

```dotenv
DB_ROOT_PASSWORD=change-root-password
DB_USERNAME=ai_agent
DB_PASSWORD=change-database-password
DB_PORT=3307
SPRING_DATASOURCE_URL='jdbc:mysql://127.0.0.1:3307/ai_agent?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true'
GOOGLE_API_KEY=your-gemini-api-key
JWT_SECRET=your-base64-encoded-secret
ADMIN_INITIAL_PASSWORD=change-this-admin-password
AGENT_ALLOWED_PATH='/duong/dan/thu-muc-duoc-phep-doc'
```

`DB_PORT` và cổng trong `SPRING_DATASOURCE_URL` phải trùng nhau. Chọn `AGENT_ALLOWED_PATH` bao gồm các thư mục dự án cần rà soát. Tạo `JWT_SECRET` bằng `openssl rand -base64 32`; mật khẩu admin ban đầu cần ít nhất 12 ký tự. Admin chỉ được tạo khi tài khoản chưa tồn tại: thay đổi biến này sau đó không đổi mật khẩu trong cơ sở dữ liệu hiện có.

## Build và chạy ứng dụng desktop

Từ thư mục dự án, đóng gói backend và mở cửa sổ:

```bash
./mvnw -DskipTests package
bash scripts/soopi-desktop.sh
```

Cửa sổ sẽ khởi động MySQL qua Docker Compose, sau đó chạy tệp `target/aiagent-0.0.1-SNAPSHOT.jar` trên máy. Đăng nhập bằng tài khoản đã có hoặc dùng nút đăng nhập admin cục bộ khi mật khẩu admin trong `.env` khớp cơ sở dữ liệu. Dùng **Chọn thư mục** để chọn dự án nằm trong `AGENT_ALLOWED_PATH`.

Trên máy đã thiết lập mục ứng dụng, có thể mở **Soopi AI Agent** từ danh sách ứng dụng. Tệp `Soopi.desktop` chứa đường dẫn tuyệt đối của máy này; khi chuyển dự án sang vị trí hoặc máy khác, cần chỉnh `Exec` và `Path` trước khi cài mục ứng dụng. Đây là trình mở dựa trên mã nguồn và JAR, chưa phải gói cài đặt desktop độc lập.

Đóng cửa sổ sẽ yêu cầu dừng backend desktop; MySQL và dữ liệu của nó được giữ lại. Dùng `docker compose down` để dừng các dịch vụ Compose mà không xóa volume. Nhật ký khởi động backend desktop nằm tại `.desktop-runtime/backend.log`. Nếu cổng 18080 đang được dùng, đóng phiên Soopi desktop khác trước khi mở lại.

## Chạy backend bằng Docker Compose

Ngoài cách chạy desktop, có thể chạy backend và MySQL trong container bằng `docker compose up -d --build`. Backend khi đó mở cổng 8080 và gắn thư mục dự án tại `/workspace` ở chế độ chỉ đọc; đường dẫn gửi tới backend trong chế độ này là đường dẫn bên trong container. Đây là cấu hình vận hành backend riêng, không tự mở cửa sổ desktop. Dừng bằng `docker compose down`.

## Cấu trúc

- `scripts/desktop.py`, `scripts/soopi-desktop.sh`, `Soopi.desktop`: cửa sổ và trình mở desktop Linux.
- `src/main/java/prj/anhzxje/aiagent/`: backend, chia theo tính năng như `auth`, `chat`, `projects`, `tasks`, `review`, `skills` và `admin`.
- `src/main/resources/static/`: giao diện HTML/CSS/JavaScript hiển thị trong cửa sổ desktop; `skills/` và `prompts/` chứa dữ liệu sử dụng lúc chạy.
- `src/main/resources/db/`: SQL hỗ trợ triển khai thủ công.
- `src/test/`, `evaluation/`: kiểm thử và dữ liệu đánh giá; [evaluation/README.md](evaluation/README.md) hướng dẫn framework đánh giá độc lập.
- `pom.xml`, `Dockerfile`, `docker-compose.yml`: cấu hình build và dịch vụ.

## Kiểm thử

```bash
./mvnw verify
node --test src/test/js/*.test.cjs
python3 -m unittest discover -s src/test/python -p 'test_*.py'
```

Kiểm tra kiến trúc bằng `python3 scripts/check-architecture.py --bytecode target/classes`. Kiểm thử giao diện bằng Chromium dùng `CHROMIUM_PATH` và `node src/test/js/workspace.browser.cjs`; kiểm thử đó không thay thế kiểm tra cửa sổ GTK/WebKit. Các kiểm thử backend dùng cơ sở dữ liệu và nhà cung cấp giả lập, không cần gọi Gemini.
