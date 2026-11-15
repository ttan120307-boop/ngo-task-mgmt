package ngo.db;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Chạy một nhóm lệnh SQL trong MỘT transaction:
 * setAutoCommit(false) -> chạy work -> COMMIT; có lỗi -> ROLLBACK.
 */
public final class Tx {
    @FunctionalInterface
    public interface Work<T> {
        T run(Connection c) throws SQLException;
    }

    private Tx() { }

    public static <T> T run(Work<T> work) {
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException e) {
                rollbackQuietly(c);
                throw DataAccessException.from(e);
            } catch (RuntimeException e) {   // lỗi nghiệp vụ/validate trong work cũng phải ROLLBACK
                rollbackQuietly(c);
                throw e;
            }
        } catch (SQLException e) {           // lỗi mở/đóng kết nối
            throw DataAccessException.from(e);
        }
    }

    private static void rollbackQuietly(Connection c) {
        try { c.rollback(); } catch (SQLException ignored) { }
    }
}
