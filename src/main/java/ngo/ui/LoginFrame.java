package ngo.ui;

import ngo.auth.AuthService;
import ngo.auth.AuthUser;
import ngo.db.DataAccessException;
import ngo.ui.browse.UiStyle;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;

/** Log-in screen: the first window of the application. */
public class LoginFrame extends JFrame {
    private final JTextField txtUser = new JTextField();
    private final JPasswordField txtPass = new JPasswordField();
    private final JCheckBox chkShow = new JCheckBox("Show password");
    private final JButton btnLogin = new JButton("Sign in");
    private final JLabel lblMsg = new JLabel(" ");
    private int failures;

    public LoginFrame() {
        super("NGO Tasks - Sign in");
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiStyle.LINE),
                BorderFactory.createEmptyBorder(28, 32, 24, 32)));

        JLabel title = new JLabel("NGO Tasks");
        title.setFont(UiStyle.FONT_TITLE);
        title.setForeground(UiStyle.TEXT);
        JLabel sub = new JLabel("Volunteer task management - please sign in");
        sub.setFont(UiStyle.FONT);
        sub.setForeground(UiStyle.MUTED);

        card.add(left(title));
        card.add(left(sub));
        card.add(Box.createVerticalStrut(22));
        card.add(left(label("Username")));
        card.add(field(txtUser));
        card.add(Box.createVerticalStrut(12));
        card.add(left(label("Password")));
        card.add(field(txtPass));
        card.add(Box.createVerticalStrut(6));
        chkShow.setFont(UiStyle.FONT);
        chkShow.setBackground(Color.WHITE);
        chkShow.addActionListener(e -> txtPass.setEchoChar(chkShow.isSelected() ? (char) 0 : '•'));
        card.add(left(chkShow));
        card.add(Box.createVerticalStrut(10));
        lblMsg.setFont(UiStyle.FONT);
        lblMsg.setForeground(UiStyle.DANGER);
        card.add(left(lblMsg));
        card.add(Box.createVerticalStrut(8));
        UiStyle.styleButton(btnLogin, false);
        btnLogin.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnLogin.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        btnLogin.addActionListener(e -> doLogin());
        card.add(btnLogin);

        JPanel outer = new JPanel(new java.awt.GridBagLayout());
        outer.setBackground(UiStyle.SIDEBAR_BG);
        outer.add(card);
        card.setPreferredSize(new Dimension(380, 400));
        setContentPane(outer);
        getRootPane().setDefaultButton(btnLogin);
        setSize(600, 520);
        setLocationRelativeTo(null);
    }

    private static JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 12));
        l.setForeground(UiStyle.TEXT);
        return l;
    }

    private static JPanel left(Component c) {
        JPanel p = new JPanel(new java.awt.BorderLayout());
        p.setOpaque(false);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        p.add(c, java.awt.BorderLayout.WEST);
        return p;
    }

    private static JTextField field(JTextField f) {
        f.setFont(UiStyle.FONT);
        f.setAlignmentX(Component.LEFT_ALIGNMENT);
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        f.setPreferredSize(new Dimension(300, 36));
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiStyle.LINE), BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        return f;
    }

    private void doLogin() {
        String user = txtUser.getText();
        char[] pw = txtPass.getPassword();
        String password = new String(pw);
        java.util.Arrays.fill(pw, '\0');
        btnLogin.setEnabled(false);
        lblMsg.setText(" ");
        new SwingWorker<AuthUser, Void>() {
            @Override protected AuthUser doInBackground() throws Exception {
                try {
                    return AuthService.login(user, password);
                } catch (IllegalArgumentException ex) {
                    Thread.sleep(500);          // slows down password guessing
                    throw ex;
                }
            }
            @Override protected void done() {
                try {
                    get();
                    new MainFrame().setVisible(true);
                    dispose();
                } catch (java.util.concurrent.ExecutionException ex) {
                    Throwable t = ex.getCause();
                    failures++;
                    lblMsg.setText(t instanceof IllegalArgumentException || t instanceof DataAccessException
                            ? t.getMessage() : "Cannot sign in: " + (t.getMessage() == null ? t : t.getMessage()));
                    txtPass.setText("");
                    txtPass.requestFocus();
                    if (failures >= 5) {
                        lblMsg.setText("Too many attempts. Please wait 10 seconds.");
                        new javax.swing.Timer(10_000, e -> { failures = 0; btnLogin.setEnabled(true); lblMsg.setText(" "); }) {{
                            setRepeats(false); start();
                        }};
                        return;
                    }
                    btnLogin.setEnabled(true);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }
}
