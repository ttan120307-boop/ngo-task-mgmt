# NGO Task Management

Ứng dụng desktop (Java Swing + JDBC + MySQL) quản lý công việc tình nguyện cho một tổ chức phi chính phủ: dự án, công việc, đội nhóm, phân công, bình luận và tệp đính kèm.

Môn học: IT079IU.

## Thành viên

| Thành viên | Phần phụ trách |
|---|---|
| Tấn | Backend: kết nối JDBC, DAO, transaction, xử lý lỗi, Browse form |
| Phương Vy | Forms & UI: Search form, Master-Detail form |
| Hà Vy | Editing & Validation: form thêm, sửa, xóa, kiểm tra dữ liệu, hiển thị lỗi |

## Yêu cầu cài đặt

- MySQL 8.0.16 trở lên (cần cho ràng buộc CHECK)
- JDK 17 trở lên
- Maven (hoặc một IDE có hỗ trợ Maven, ví dụ VS Code với Extension Pack for Java, Eclipse)
- Kết nối Internet lần đầu để Maven tải thư viện `mysql-connector-j`

## Cài đặt và chạy

### 1. Nạp cơ sở dữ liệu

Trong MySQL Workbench (hoặc công cụ khác), chạy **lần lượt, đúng thứ tự** ba file trong thư mục `sql`:

1. `01_foundation_schema_and_data.sql`
2. `03_project_task_schema_and_data.sql`
3. `05_interactions_schema_and_data.sql`

Lưu ý: file `01` có lệnh `DROP DATABASE IF EXISTS ngo_task_mgmt`, nên mỗi lần chạy lại `01` sẽ xóa sạch database cũ rồi tạo lại. Cuối file `05` có bảng đếm dòng: `TASK_ASSIGNMENT` 33, `TASK_COMMENT` 13, `ATTACHMENT` 11.

### 2. Cấu hình kết nối

Chép `config/db.properties.example` thành `config/db.properties`, rồi sửa `db.user` và `db.password` theo MySQL của bạn. File `db.properties` đã được chặn trong `.gitignore`, không đưa lên Git.

Chương trình đọc file cấu hình theo đường dẫn `config/db.properties` tính từ thư mục chạy (thư mục gốc của project, nơi có `pom.xml`). Có thể đổi bằng tham số `-Ddb.config=/duong/dan/db.properties`.

### 3. Chạy chương trình

Mở thư mục project trong IDE, chờ Maven tải thư viện, rồi chạy lớp **`ngo.ui.MainFrame`** (cửa sổ chính, từ đó mở các form).

Hoặc chạy bằng dòng lệnh, tại thư mục gốc của project:

```
mvn compile org.codehaus.mojo:exec-maven-plugin:3.1.0:java -Dexec.mainClass=ngo.ui.MainFrame
```

Thanh dưới cùng của cửa sổ chính cho biết đã kết nối được MySQL hay chưa. Nếu hiện lỗi, xem mục "Lỗi thường gặp".

Các form cũng chạy riêng được: `ngo.ui.grid.BrowseForm`, `ngo.ui.browse.MasterDetailForm`, `ngo.ui.browse.SearchForm`, `ngo.ui.edit.EditingValidationDemo`. Trong Editing & Validation có các tab Task CRUD + Labels + Dependencies, Project CRUD, Team Membership, Transfer Assignment, Comment & Attachment và Task Report. Lớp `ngo.Demo` chạy thử backend (thêm, sửa, tìm kiếm, xóa task, transaction COMMIT và ROLLBACK, các lỗi mẫu) và in kết quả ra Terminal.

## Cấu trúc project

```
config/db.properties.example     mẫu cấu hình kết nối
sql/                             ba file tạo bảng và dữ liệu mẫu
src/main/java/ngo/
  db/        DBConnection, Tx (transaction), DataAccessException (dịch lỗi MySQL sang tiếng Việt)
  model/     Task, Project
  dao/       TaskDAO, ProjectDAO, LookupDAO, TaskDependencyDAO, TaskLabelDAO, MembershipDAO
  service/   TaskService (nghiệp vụ nhiều bảng trong một transaction)
  ui/        MainFrame (cửa sổ chính)
    grid/    BrowseForm
    browse/  MasterDetailForm, SearchForm
    edit/    các màn hình Editing & Validation
  Demo.java  chạy thử backend
```

## Thiết kế backend

- **Truy vấn tham số hóa:** mọi câu SQL dùng `PreparedStatement`, kể cả tìm kiếm có lọc động.
- **Transaction (COMMIT/ROLLBACK):** `Tx.run(...)` bật `setAutoCommit(false)`, thành công thì COMMIT, có lỗi thì ROLLBACK. `TaskService` dùng cho:
  - thêm task kèm nhiều người thực hiện (`createTaskWithAssignees`)
  - thêm bình luận kèm tệp đính kèm (`addComment`)
  - chuyển giao việc (`transferAssignment`)
- **Báo lỗi dễ hiểu:** `DataAccessException` đổi lỗi MySQL (ràng buộc CHECK, khóa ngoại, trùng khóa, trigger) thành câu tiếng Việt. Chi tiết kỹ thuật chỉ ghi vào log, không hiện lên giao diện.
- **Kiểm tra hai tầng:** `Task.validate()` và `Project.validate()` bắt lỗi nhập liệu trước khi gọi database; trigger và ràng buộc trong database là lớp kiểm tra cuối.
- **Số thứ tự cục bộ:** `comment_id` đánh số riêng cho từng task, `attachment_id` riêng cho từng bình luận; `addComment` khóa dòng task cha để hai người bình luận cùng lúc không bị trùng khóa.

## Quy tắc nghiệp vụ do database bảo đảm

- Người được giao việc phải là thành viên hiện tại (chưa có ngày rời) của team phụ trách task.
- Task chưa thể bắt đầu hoặc hoàn thành khi còn task tiên quyết chưa xong.
- Phụ thuộc giữa các task không được tạo vòng lặp, không tự phụ thuộc chính nó và phải nằm trong cùng một project.
- Mỗi team chỉ có một Leader hiện tại.
- Xóa project hoặc task sẽ xóa theo (CASCADE) các phân công, bình luận, tệp đính kèm và phụ thuộc liên quan.

Hạn chế đã biết: quy tắc thành viên team chỉ được kiểm tra lúc giao việc. Nếu sau đó một người rời team, các phân công cũ của họ không tự bị gỡ.

## Tình trạng chức năng

| Chức năng | Backend | Giao diện |
|---|---|---|
| Xem danh sách Task (Browse) | Có | Có |
| Master-Detail Project và Task | Có | Có |
| Tìm kiếm Task | Có | Có |
| Thêm, sửa, xóa Task (có xác nhận khi xóa) | Có | Có |
| Giao việc cho nhiều người, chuyển giao việc | Có | Có |
| Bình luận và tệp đính kèm | Có | Có |
| Báo cáo Task | Có | Có |
| Thêm, sửa, xóa Project | Có | Có |
| Tham gia, rời team (Membership) | Có | Có |
| Thêm, gỡ phụ thuộc giữa các task | Có | Có |
| Gắn, gỡ nhãn cho task | Có | Có |

## Lỗi thường gặp

| Thông báo | Cách xử lý |
|---|---|
| Không đọc được file cấu hình CSDL | Tạo `config/db.properties` từ file `.example`, và chạy chương trình từ thư mục gốc của project |
| Sai tên đăng nhập hoặc mật khẩu MySQL | Sửa `db.user` và `db.password` trong `config/db.properties` |
| Chưa có database ngo_task_mgmt | Chạy ba file trong thư mục `sql` theo đúng thứ tự |
| Không kết nối được tới MySQL | Bật MySQL và kiểm tra cổng trong `db.url` (mặc định 3306) |
| No suitable driver found | Maven chưa tải thư viện; reload project Maven và chờ tải xong |
| Báo thiếu bảng LABEL | Đang dùng file `03` cũ; nạp lại `01`, `03`, `05` |
