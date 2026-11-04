# Hướng dẫn Đóng góp Mã nguồn (Contributing Guidelines)
# Dự án: Soopi AI Agent Platform

Cảm ơn bạn đã quan tâm và muốn đóng góp cho dự án **Soopi AI Agent Platform**!  
Để đảm bảo chất lượng mã nguồn, tính nhất quán kiến trúc và an toàn bảo mật, vui lòng tuân thủ các quy tắc dưới đây.

---

## 1. Quy trình Phát triển & Quản lý Nhánh (Branching Model)

Dự án áp dụng mô hình phân nhánh tiêu chuẩn:
- **`main`**: Nhánh chính thức, chứa mã nguồn ổn định sẵn sàng cho Production.
- **`develop`**: Nhánh tích hợp các tính năng chuẩn bị phát hành.
- **`feature/<ten-tinh-nang>`**: Nhánh phát triển tính năng mới (Ví dụ: `feature/count-lines-tool`).
- **`bugfix/<ten-loi>`**: Nhánh sửa lỗi chức năng.
- **`hotfix/<ten-loi-gap>`**: Nhánh vá lỗi bảo mật khẩn cấp từ `main`.

---

## 2. Tiêu chuẩn Thông điệp Commit (Conventional Commits)

Thông điệp commit phải tuân thủ định dạng:
```text
<loai>(<pham-vi>): <mo-ta-ngan-gon-bang-tieng-viet-hoac-tieng-anh>

[Mo-ta-chi-tiet-neu-co]
```

### Các tiền tố `<loai>` được chấp thuận:
- **`feat`**: Bổ sung một tính năng hoặc API mới.
- **`fix`**: Sửa một lỗi logic hoặc ngoại lệ.
- **`docs`**: Cập nhật hoặc bổ sung tài liệu kỹ thuật, ADRs, README.
- **`style`**: Định dạng code, dấu cách, thụt dòng (không thay đổi logic code).
- **`refactor`**: Tái cấu trúc mã nguồn (không sửa lỗi cũng không thêm tính năng).
- **`perf`**: Tối ưu hóa hiệu năng, giảm độ trễ, tối ưu bộ nhớ.
- **`test`**: Bổ sung hoặc chỉnh sửa các ca kiểm thử tự động.
- **`chore`**: Cập nhật file cấu hình Maven, Dockerfile, scripts, dependencies.

**Ví dụ:**
```text
feat(agent): thêm công cụ count_lines đếm dòng mã nguồn
fix(security): ngăn chặn đọc tệp ngoài projectRoot trong SandboxTools
docs(api): cập nhật đặc tả endpoint /api/tasks/{id}/cancel
```

---

## 3. Quy chuẩn Mã nguồn (Coding Standards)

1. **Ngôn ngữ Backend**:
   - Sử dụng các tính năng hiện đại của **Java 21** (Pattern Matching, Switch Expressions, Records).
   - Đảm bảo toàn bộ mã nguồn sử dụng mã hóa **UTF-8**.
   - Khởi tạo đối tượng DTO qua `@Builder` hoặc Constructor được tiêm phụ thuộc tường minh (`@RequiredArgsConstructor`).
2. **Ngôn ngữ Frontend**:
   - Viết bằng chuẩn **ES6+ Modules** thuần (Vanilla JS), không sử dụng thêm thư viện bên ngoài nếu không có sự phê duyệt kiến trúc.
   - Luôn sử dụng `async/await` và `try...catch` cho các thao tác mạng (`fetch`).
3. **Bảo mật & Sandbox**:
   - Bất kỳ công cụ mới nào được tạo trong `SandboxTools` đều phải gọi hàm `resolve(path)` để bảo vệ chống Path Traversal.
   - Tuyệt đối không hardcode mật khẩu, token, khóa API vào file code; luôn đọc từ biến môi trường.

---

## 4. Danh sách Kiểm tra trước khi Tạo Pull Request (PR Checklist)

Trước khi gửi Pull Request, hãy đảm bảo bạn đã hoàn thành các bước kiểm tra sau:

- [ ] Toàn bộ bài kiểm thử Maven backend chạy thành công:
  ```powershell
  .\mvnw.cmd test
  ```
- [ ] Bài kiểm thử giao diện Frontend chạy thành công:
  ```bash
  node --test src/test/js/frontend.test.cjs
  ```
- [ ] Đã viết thêm Unit Test hoặc Integration Test cho tính năng/lỗi vừa bổ sung.
- [ ] Không có tệp tin tạm, mã nguồn bị comment thừa hoặc tệp nhạy cảm (`.env`, khóa bí mật) được commit.
- [ ] Đã cập nhật tài liệu kỹ thuật tương ứng trong thư mục `docs/` nếu có thay đổi về API hoặc Kiến trúc.
- [ ] Mã nguồn được định dạng sạch sẽ, rõ ràng.
