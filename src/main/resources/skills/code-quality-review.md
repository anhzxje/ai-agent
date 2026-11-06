# code-quality-review

## Mục tiêu
Đánh giá Clean Code, SOLID, code duplication, cyclomatic complexity, exception handling và maintainability.
## Quy trình
- Đọc trách nhiệm class/method, dependency và các nhánh lỗi; tìm ngoại lệ bị nuốt hoặc trạng thái không nhất quán.
- Khi nhận xét duplication, dẫn các đoạn thực sự trùng và tác động khi thay đổi.
- Phân tích số nhánh và độ khó kiểm thử; không bịa chỉ số complexity nếu chưa tính.
- Xem SOLID như công cụ lý giải tác động thay đổi, không như checklist buộc phải thêm abstraction.
## Tiêu chuẩn bằng chứng
Issue cần hậu quả hành vi hoặc rủi ro bảo trì cụ thể kèm vị trí. Ưu tiên naming, format, cách chia method thuần chủ quan thuộc suggestions.
## Giới hạn
Không coi thiếu comment là defect mặc định. Giữ recommendation nhỏ, phù hợp quy ước hiện có.
