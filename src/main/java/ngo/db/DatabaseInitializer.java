package ngo.db;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.List;

public final class DatabaseInitializer {

    private DatabaseInitializer() {}

    public static void ensureInitialized() {
        try (Connection c = DBConnection.getConnection();
             Statement st = c.createStatement()) {

            // Kiểm tra nếu bảng ACCOUNT đã tồn tại và có dữ liệu thì bỏ qua
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM TASK")) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return;
                }
            } catch (Exception ignored) {
                // Bảng chưa tồn tại -> tiến hành khởi tạo toàn bộ
            }

            // 1. Nạp các file schema & data cốt lõi (01, 03, 05, 07)
            List<String> sqlFiles = List.of(
                "sql/01_foundation_schema_and_data.sql",
                "sql/03_project_task_schema_and_data.sql",
                "sql/05_interactions_schema_and_data.sql",
                "sql/07_label_schema_and_data.sql"
            );

            for (String file : sqlFiles) {
                runSqlFile(st, Path.of(file));
            }

            // 2. Tạo bảng ACCOUNT (lấy 47 dòng đầu của file 09)
            List<String> lines09 = Files.readAllLines(Path.of("sql/09_account_schema_and_data.sql"), StandardCharsets.UTF_8);
            String ddl09 = String.join("\n", lines09.subList(0, Math.min(47, lines09.size())));
            executeSqlBlock(st, ddl09);

            // 3. Sinh 16 tài khoản từ bảng VOLUNTEER trực tiếp bằng Java (tránh lỗi hàm MD5 của MySQL)
            try (ResultSet rs = st.executeQuery("SELECT volunteer_id, email FROM VOLUNTEER ORDER BY volunteer_id");
                 Statement insertSt = c.createStatement()) {
                while (rs.next()) {
                    int vid = rs.getInt("volunteer_id");
                    String email = rs.getString("email");
                    String username = email.split("@")[0];
                    String salt = hashHex("MD5", "ngo-demo-salt-" + vid);
                    String passHash = hashHex("SHA-256", salt + "Ngo@2026");
                    int isActive = (vid == 16) ? 0 : 1;

                    String insertSql = String.format(
                        "INSERT IGNORE INTO ACCOUNT (volunteer_id, username, salt, password_hash, is_active) " +
                        "VALUES (%d, '%s', '%s', '%s', %d)",
                        vid, username, salt, passHash, isActive
                    );
                    insertSt.executeUpdate(insertSql);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void runSqlFile(Statement st, Path path) throws Exception {
        if (!Files.exists(path)) return;
        String content = Files.readString(path, StandardCharsets.UTF_8);
        executeSqlBlock(st, content);
    }

    private static void executeSqlBlock(Statement st, String sqlContent) throws Exception {
        // Loại bỏ comment dòng -- và tách lệnh theo dấu ;
        String cleaned = sqlContent.replaceAll("(?m)^\\s*--.*$", "");
        for (String stmt : cleaned.split(";")) {
            String trimmed = stmt.trim();
            if (!trimmed.isEmpty() && !trimmed.toUpperCase().startsWith("SELECT ")) {
                st.execute(trimmed);
            }
        }
    }

    private static String hashHex(String algorithm, String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance(algorithm);
        byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}