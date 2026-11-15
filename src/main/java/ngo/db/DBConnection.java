package ngo.db;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** Đọc cấu hình từ file ngoài (config/db.properties) và cấp Connection mới. */
public final class DBConnection {
    // Cho phép đổi đường dẫn khi chạy: java -Ddb.config=D:/cfg/db.properties ...
    private static final String CONFIG_PATH = System.getProperty("db.config", "config/db.properties");
    private static final Properties PROPS = load();

    private DBConnection() { }

    private static Properties load() {
        Properties p = new Properties();
        try (InputStream in = new FileInputStream(CONFIG_PATH)) {
            p.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được file cấu hình CSDL: " + CONFIG_PATH, e);
        }
        return p;
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                PROPS.getProperty("db.url"),
                PROPS.getProperty("db.user"),
                PROPS.getProperty("db.password"));
    }
}
