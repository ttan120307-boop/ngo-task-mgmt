# Những gì đã sửa so với bản trước (để ghi vào báo cáo / commit message)

**SQL (`sql/`)**: thay toàn bộ bằng gói v3: `ngo_full_schema_and_data.sql` (chạy 1 lần, chạy lại được), dữ liệu ≥ 15 dòng mỗi bảng với ngày tương đối `CURRENT_DATE`, `estimated_hours`/`due_date` NOT NULL, Leader bằng cột sinh `leader_flag` + UNIQUE, rule 8 theo khoảng thời gian, index `(project_id, status, due_date)`, file DML, 12 query, đại số quan hệ.

**Java**
- `Task.java`, `EditTaskForm.java`, `TaskEditingPanel.java`, `TaskEditingService.java`: hours và due date bắt buộc (BR6).
- `TaskEditingService.update`: không cho đổi Team khi task đã có người được giao (BR24), báo lỗi rõ ràng.
- `DataAccessException.java`: thêm thông báo cho `uq_membership_one_leader`, rule 8 mới, BR24, role id 1, due date task/project.
- `ReportDAO.java` + `ReportForm.java`: thêm 3 báo cáo của Topic 6 (blocked tasks, completed projects, top commenter); `MainFrame` có nút Reports; đóng Report không tắt cả ứng dụng.
- `TaskDAO.searchRows`: sắp theo `due_date` (query 1 của đề).

**Repo**: bỏ `cls/`, `.gitignore` chặn `config/db.properties`, `cls/`, `*.class`; `db.properties.example` không còn mật khẩu; README viết lại.

## Đăng nhập + phân quyền + đồng bộ giao diện
- Thêm bảng ACCOUNT (sql/09, test sql/14), gói `ngo.auth` (Role, AuthService, Authz...), chạy `ngo.App` để vào màn hình đăng nhập.
- 3 vai trò: Admin / Manager / User (xem README mục 6). Quyền được kiểm tra ở DAO/service, không chỉ ẩn nút.
- Mọi màn hình dùng chung `UiStyle` (sidebar theo vai trò, hộp người dùng, đổi mật khẩu, đăng xuất, nút phẳng, bảng, font). Thêm Home, My tasks, Manage.
- Mật khẩu demo: Ngo@2026 (hãy đổi). Không commit config/db.properties.
