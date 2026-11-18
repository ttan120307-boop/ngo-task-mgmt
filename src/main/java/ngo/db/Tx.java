package ngo.db;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Executes a group of SQL statements within a SINGLE transaction:
 * setAutoCommit(false) -> execute work -> COMMIT; on error -> ROLLBACK.
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
            } catch (RuntimeException e) {   // business/validation errors inside work must also ROLLBACK
                rollbackQuietly(c);
                throw e;
            }
        } catch (SQLException e) {           // error opening/closing connection
            throw DataAccessException.from(e);
        }
    }

    private static void rollbackQuietly(Connection c) {
        try { c.rollback(); } catch (SQLException ignored) { }
    }
}
