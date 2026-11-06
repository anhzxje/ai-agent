# architecture-review

## Mục tiêu
Đánh giá coupling/cohesion, layer boundaries, dependency direction, module structure và architectural maintainability.
## Quy trình
- Lập bản đồ entry point, package/module và dependency từ import/caller thực tế.
- Đọc ít nhất hai đầu của boundary trước khi nhận định vi phạm; kiểm tra dependency cycle và contract rò rỉ.
- Liên hệ coupling/cohesion với tình huống thay đổi cụ thể và tác động kiểm thử.
- Nhận diện trách nhiệm chồng chéo có bằng chứng; không suy kiến trúc chỉ từ cây thư mục.
## Tiêu chuẩn bằng chứng
Chỉ ghi issue nếu chỉ rõ phụ thuộc/luồng thực tế và hậu quả. Nếu context bị giới hạn, nêu phần chưa đọc, không kết luận toàn hệ thống.
## Giới hạn
Đổi kiến trúc, thêm microservice hoặc abstraction do sở thích thuộc suggestions, không phải defect mặc định.
