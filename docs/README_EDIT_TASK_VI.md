# Màn hình Edit Task – hướng dẫn tích hợp

## Mục tiêu

Đây là màn hình Edit Task mới, viết độc lập và **không sử dụng lại giao diện cũ `ngo.ui.edit.TaskEditingPanel`** trong ZIP hiện tại. Nó dùng lại các lớp xử lý dữ liệu mà nhóm đã có: `TaskDAO`, `LookupDAO`, `Task`, `DBConnection`, `Tx`, `DataAccessException`.

Thiết kế dựa theo output Projects mà nhóm gửi: sidebar trái rộng 140px, nền sidebar `#F7F7F5`, nền nội dung trắng, font Segoe UI, bảng có header xám nhạt, đường kẻ mảnh, hàng được chọn xanh nhạt, trạng thái Task có màu tương ứng. Mình không truy cập được canvas Figma từ link chia sẻ trong phiên này, do đó màn hình được căn theo ảnh output và `UiStyle` raw đã gửi; chưa thể xác nhận từng trường/khoảng cách đúng với Figma.

## File được thêm

```text
src/main/java/ngo/ui/browse/UiStyle.java
src/main/java/ngo/ui/edit/EditTaskForm.java
src/main/java/ngo/dao/TaskAssignmentReadDAO.java
src/main/java/ngo/service/TaskEditingService.java
```

Không có file nào trong `ngo.ui.browse.SearchForm`, `MasterDetailForm`, `ProjectDAO`, `TaskDAO`, `Task.java`, hoặc các phần Search/Projects bị ghi đè.

## Chức năng của màn hình

- Hiển thị danh sách Task ở bảng phía dưới.
- Chọn một Task để nạp dữ liệu vào form.
- Add Task mới; Task ID do MySQL `AUTO_INCREMENT` sinh ra.
- Edit thông tin Task; Assignment hiện tại được giữ nguyên và hiển thị dạng chỉ đọc. Chức năng transfer/assignment vẫn thuộc module Assignment hiện có.
- Delete Task với hộp thoại xác nhận.
- Chọn Project và Team từ dữ liệu thật trong database.
- Tạo Task mới có thể chọn nhiều volunteer đang là thành viên hiện tại của Team.
- Kiểm tra title, Project, Team, status, priority, estimated hours, định dạng due date và due date của Task không vượt deadline của Project.
- Nếu đổi Team của Task đang tồn tại, Service kiểm tra mọi volunteer đã được giao Task có phải thành viên hiện tại của Team mới hay không.
- Quy tắc dependency/status còn lại do các trigger MySQL hiện có thực thi; lỗi được chuyển qua `DataAccessException` để hiển thị thân thiện.

## Cách cài

1. Giải nén ZIP patch tại thư mục gốc repository — thư mục chứa `pom.xml`.
2. Không giải nén ZIP cũ có UI bị leader từ chối đè lên phần này.
3. Giữ nguyên các DAO/model/service hiện có của nhóm.
4. Chạy:

```bash
mvn clean compile
```

5. Chạy class `ngo.ui.edit.EditTaskForm` trong VS Code hoặc IntelliJ để mở màn hình độc lập.

Màn hình cũng được mở khi chọn **Edit task** trên sidebar của `UiStyle`.

## Gắn sidebar vào SearchForm (chỉ khi SearchForm chưa có sidebar)

`UiStyle.apply()` đã hỗ trợ cả form NetBeans đang dùng GroupLayout. Nếu SearchForm của nhóm hiện chưa có sidebar và nhóm muốn đồng nhất, trong constructor `SearchForm`, ngay sau `initComponents()`, có thể thêm:

```java
UiStyle.apply(this, "Search tasks");
UiStyle.styleTable(tblResults);
```

Không thêm lần nữa nếu SearchForm đã gọi `UiStyle.apply()`.

`MasterDetailForm` raw mà nhóm gửi đã gọi `UiStyle.apply(this, "Projects")`; không cần thêm lần thứ hai. Nếu `MasterDetailForm.java` trong repository vẫn là bản cũ trong ZIP (không có `UiStyle.apply`), dùng raw mới của teammate hoặc thống nhất với bạn ấy trước khi thay thế.

## Nghiệp vụ và dữ liệu

- `TaskEditingService.create()` chạy INSERT Task và các INSERT Assignment ban đầu trong cùng transaction `Tx.run`. Nếu một volunteer không thuộc Team, trigger `trg_assignment_bi` từ SQL module 05 sẽ làm transaction rollback.
- `TaskEditingService.update()` dùng `TaskDAO.update(Connection, Task)` trong transaction. Trigger `trg_task_bu` của SQL module 03 tiếp tục kiểm tra chuyển Project, prerequisite và mở lại Task DONE.
- Khi đổi Team, service kiểm tra assignment hiện có trước khi cập nhật để không để người được giao nằm ngoài Team mới.
- `TaskEditingService.delete()` dùng transaction; FK CASCADE trong schema xử lý Assignment, Comment, Attachment và Dependency.
- Quy tắc `Task.due_date <= Project.due_date` được kiểm tra trong Java service vì ba SQL module được gửi chưa có trigger tương ứng.

## Cách kiểm thử thủ công

1. Mở Edit task, xác nhận Project/Team/Task được tải từ MySQL.
2. Chọn một Task; kiểm tra ID, Project, Team, title, description, priority, status, hours, due date và danh sách assignee hiển thị đúng.
3. Sửa title rồi Save changes; kiểm tra lại bằng MySQL hoặc Browse.
4. Thử để trống title; form phải từ chối.
5. Thử Priority/Status hợp lệ và không hợp lệ; ComboBox chỉ cho chọn giá trị trong CHECK.
6. Thử `estimated hours = 0`, số âm, số có 3 chữ số thập phân, hoặc quá 9999.99; form phải báo lỗi.
7. Thử ngày sai format; form phải báo `yyyy-MM-dd`.
8. Thử due date Task sau deadline Project; form phải từ chối.
9. Thử chuyển Task sang IN_PROGRESS khi prerequisite chưa DONE; trigger MySQL phải từ chối.
10. Tạo Task mới, chọn một vài volunteer đang thuộc Team; xác nhận Task và Assignment cùng được thêm.
11. Xóa một Task thử nghiệm; xác nhận hộp thoại xuất hiện và dữ liệu liên quan được xử lý theo FK CASCADE.

## Trạng thái xác minh

- Đã biên dịch các source Java trong ZIP nhóm gửi cùng 4 source mới bằng `javac --release 17`.
- Chưa chạy GUI/MySQL thực tế trong môi trường tạo patch; cần chạy trên máy có MySQL, `config/db.properties` hợp lệ và đã chạy SQL 01, 03, 05.
