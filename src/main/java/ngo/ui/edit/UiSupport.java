package ngo.ui.edit;

import ngo.db.DataAccessException;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import java.awt.Component;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/** Shared Swing helpers: background database calls and user-friendly errors. */
final class UiSupport {
    private UiSupport() { }

    static <T> void runAsync(Component parent, String operation,
                             Callable<T> work, Consumer<T> onSuccess,
                             Runnable onFinished) {
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.call();
            }

            @Override
            protected void done() {
                try {
                    T result = get();
                    if (onSuccess != null) onSuccess.accept(result);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showError(parent, "Thao tác bị gián đoạn. Vui lòng thử lại.");
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof DataAccessException || cause instanceof IllegalArgumentException) {
                        showError(parent, cause.getMessage());
                    } else if (cause instanceof Exception) {
                        showError(parent, "Không thể " + operation + ". Vui lòng thử lại.");
                    } else {
                        showError(parent, "Đã xảy ra lỗi không mong muốn.");
                    }
                } finally {
                    if (onFinished != null) onFinished.run();
                }
            }
        }.execute();
    }

    static void showError(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Thông báo lỗi", JOptionPane.ERROR_MESSAGE);
    }

    static void showInfo(Component parent, String message) {
        JOptionPane.showMessageDialog(parent, message, "Thành công", JOptionPane.INFORMATION_MESSAGE);
    }
}
