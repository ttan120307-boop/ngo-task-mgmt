/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
package ngo.ui.browse;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableModel;
/**
 *
 * @author Phuong Vy
 */
public class SearchForm extends javax.swing.JFrame {
    
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(SearchForm.class.getName());

private static final String[] COLUMNS
            = {"ID", "Title", "Team", "Priority", "Status", "Due date", "Assignee"};
    private static final int[] WIDTHS = {40, 200, 130, 80, 110, 100, 260};
    private static final String[] STATUSES = {"", "TODO", "IN_PROGRESS", "DONE", "CANCELLED"};
    private static final String[] PRIORITIES = {"", "LOW", "MEDIUM", "HIGH", "URGENT"};
    private static final Color BORDER = new Color(0xD3D1C7);

    private JTextField keywordBox;
    private JTable resultsTable;
    private JLabel countLabel;
    private RoundButton priorityBtn;
    private RoundButton searchBtn;
    private RoundButton clearBtn;
    private final Map<String, RoundButton> chips = new LinkedHashMap<>();

    private String selectedStatus = "";
    private String selectedPriority = "";
    private int totalTasks = -1;   // number of all tasks (known after the first unfiltered search)
    private int searchSeq = 0;     // ignore answers of older searches

    public SearchForm() {
        initComponents();                       // phần Design cũ của NetBeans, giữ nguyên
        setTitle("Search tasks");
        setContentPane(new JPanel(new BorderLayout()));   // bỏ giao diện cũ, dựng giao diện mới bằng code
        UiStyle.apply(this, "Search tasks");
        getContentPane().add(buildMainPanel(), BorderLayout.CENTER);
        UiStyle.styleTable(resultsTable);
        updateChips();
        runSearch();
    }

    // ---------- building the screen ----------
    private JPanel buildMainPanel() {
        JLabel title = new JLabel("Search tasks");
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(UiStyle.TEXT);

        clearBtn = new RoundButton("Clear", 10);
        searchBtn = new RoundButton("Search", 10);
        searchBtn.filled();
        clearBtn.addActionListener(e -> clearFilters());
        searchBtn.addActionListener(e -> runSearch());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setBackground(Color.WHITE);
        buttons.add(clearBtn);
        buttons.add(searchBtn);

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setBackground(Color.WHITE);
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(buttons, BorderLayout.EAST);

        // keyword box with a grey hint
        keywordBox = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setColor(new Color(0x9A9A96));
                    g2.setFont(getFont());
                    Insets in = getInsets();
                    int y = (getHeight() + g2.getFontMetrics().getAscent()
                            - g2.getFontMetrics().getDescent()) / 2;
                    g2.drawString("Search tasks by keyword", in.left, y);
                    g2.dispose();
                }
            }
        };
        keywordBox.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        keywordBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                BorderFactory.createEmptyBorder(0, 12, 0, 12)));
        keywordBox.setPreferredSize(new Dimension(100, 38));
        keywordBox.addActionListener(e -> runSearch());   // Enter key

        // priority dropdown (button + popup menu)
        priorityBtn = new RoundButton("Priority: All ", 10);
        priorityBtn.setPreferredSize(new Dimension(160, 38));
        JPopupMenu menu = new JPopupMenu();
        for (String p : PRIORITIES) {
            JMenuItem item = new JMenuItem(p.isEmpty() ? "All" : p);
            item.setFont(UiStyle.FONT);
            item.addActionListener(e -> selectPriority(p));
            menu.add(item);
        }
        priorityBtn.addActionListener(e -> menu.show(priorityBtn, 0, priorityBtn.getHeight()));

        JPanel searchRow = new JPanel(new BorderLayout(10, 0));
        searchRow.setBackground(Color.WHITE);
        searchRow.add(keywordBox, BorderLayout.CENTER);
        searchRow.add(priorityBtn, BorderLayout.EAST);

        // status chips
        JPanel chipRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        chipRow.setBackground(Color.WHITE);
        for (String s : STATUSES) {
            RoundButton chip = new RoundButton(s.isEmpty() ? "All" : s, 40);
            chip.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            chip.setBorder(BorderFactory.createEmptyBorder(5, 14, 5, 14));
            chip.addActionListener(e -> selectStatus(s));
            chips.put(s, chip);
            chipRow.add(chip);
        }

        // results table
        resultsTable = new JTable(new DefaultTableModel(COLUMNS, 0));
        JScrollPane scroll = new JScrollPane(resultsTable);
        scroll.setBorder(BorderFactory.createLineBorder(UiStyle.LINE));
        scroll.getViewport().setBackground(Color.WHITE);

        countLabel = new JLabel(" ");
        countLabel.setFont(UiStyle.FONT);
        countLabel.setForeground(UiStyle.MUTED);

        JPanel main = new JPanel(new GridBagLayout());
        main.setBackground(Color.WHITE);
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.weightx = 1;
        g.fill = GridBagConstraints.BOTH;

        g.gridy = 0;
        g.weighty = 0;
        g.insets = new Insets(18, 16, 12, 16);
        main.add(titleRow, g);

        g.gridy = 1;
        g.insets = new Insets(4, 16, 10, 16);
        main.add(searchRow, g);

        g.gridy = 2;
        g.insets = new Insets(0, 16, 12, 16);
        main.add(chipRow, g);

        g.gridy = 3;
        g.weighty = 1;
        g.insets = new Insets(4, 16, 4, 16);
        main.add(scroll, g);

        g.gridy = 4;
        g.weighty = 0;
        g.insets = new Insets(4, 16, 12, 16);
        main.add(countLabel, g);
        return main;
    }

    // ---------- user actions ----------
    private void selectStatus(String status) {
        selectedStatus = status;
        updateChips();
        runSearch();
    }

    private void selectPriority(String priority) {
        selectedPriority = priority;
        showPriorityText();
        runSearch();
    }

    private void clearFilters() {
        keywordBox.setText("");
        selectedStatus = "";
        selectedPriority = "";
        showPriorityText();
        updateChips();
        runSearch();
    }

    private void showPriorityText() {
        priorityBtn.setText("Priority: " + (selectedPriority.isEmpty() ? "All" : selectedPriority) + " ▾");
    }

    private void updateChips() {
        for (Map.Entry<String, RoundButton> e : chips.entrySet()) {
            if (e.getKey().equals(selectedStatus)) {
                e.getValue().filled();
            } else {
                e.getValue().outline();
            }
        }
    }

    // ---------- database call ----------
    private void runSearch() {
        final String keyword = keywordBox.getText().trim();
        final String status = selectedStatus;
        final String priority = selectedPriority;
        final int mySeq = ++searchSeq;
        new SwingWorker<List<Object[]>, Void>() {
            @Override
            protected List<Object[]> doInBackground() throws Exception {
                return new ngo.dao.TaskDAO().searchRows(null, keyword, status, priority);
            }

            @Override
            protected void done() {
                if (mySeq != searchSeq) {
                    return;   // a newer search has been started
                }
                try {
                    List<Object[]> rows = get();
                    showRows(rows);
                    int shown = rows.size();
                    if (keyword.isEmpty() && status.isEmpty() && priority.isEmpty()) {
                        totalTasks = shown;
                        chips.get("").setText("All " + totalTasks);
                    }
                    int total = (totalTasks < 0) ? shown : totalTasks;
                    countLabel.setText("Showing " + shown + " of " + total + " tasks");
                } catch (Exception ex) {
                    Throwable t = (ex.getCause() != null) ? ex.getCause() : ex;
                    JOptionPane.showMessageDialog(SearchForm.this, t.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void showRows(List<Object[]> rows) {
        DefaultTableModel model = new DefaultTableModel(ngo.dao.TaskDAO.BROWSE_COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        for (Object[] row : rows) {
            model.addRow(row);
        }
        resultsTable.setModel(model);
        if (resultsTable.getColumnCount() == COLUMNS.length) {
            resultsTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
            for (int i = 0; i < COLUMNS.length; i++) {
                resultsTable.getColumnModel().getColumn(i).setHeaderValue(COLUMNS[i]);
                resultsTable.getColumnModel().getColumn(i).setPreferredWidth(WIDTHS[i]);
            }
            resultsTable.getColumnModel().getColumn(0).setMaxWidth(60);
            resultsTable.getTableHeader().repaint();
        }
    }

    // ---------- rounded button used for Clear / Search / Priority / chips ----------
    private static class RoundButton extends JButton {

        private final int radius;
        private Color fill = Color.WHITE;
        private Color line = BORDER;

        RoundButton(String text, int radius) {
            super(text);
            this.radius = radius;
            setFocusPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(7, 16, 7, 16));
            setFont(UiStyle.FONT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            outline();
        }

        final void outline() {
            fill = Color.WHITE;
            line = BORDER;
            setForeground(UiStyle.TEXT);
            repaint();
        }

        final void filled() {
            fill = new Color(0x111111);
            line = fill;
            setForeground(Color.WHITE);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.setColor(line);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }
    private void renameHeaders(javax.swing.JTable table, String... names) {
        if (table.getColumnCount() != names.length) {
            return;
        }
        for (int i = 0; i < names.length; i++) {
            table.getColumnModel().getColumn(i).setHeaderValue(names[i]);
        }
        table.getTableHeader().repaint();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        txtKeyword = new javax.swing.JTextField();
        jLabel2 = new javax.swing.JLabel();
        jLabel3 = new javax.swing.JLabel();
        btnSearch = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        tblResults = new javax.swing.JTable();
        cboStatus = new javax.swing.JComboBox<>();
        cboPriority = new javax.swing.JComboBox<>();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jLabel1.setText("Keyword");

        jLabel2.setText("Status");

        jLabel3.setText("Priority");

        btnSearch.setText("Search");
        btnSearch.addActionListener(this::btnSearchActionPerformed);

        tblResults.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Title 1", "Title 2", "Title 3", "Title 4"
            }
        ));
        jScrollPane1.setViewportView(tblResults);

        cboStatus.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        cboPriority.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(txtKeyword)
                                .addGap(18, 18, 18))
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGap(14, 14, 14)
                                .addComponent(jLabel1)
                                .addGap(106, 106, 106)))
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(cboStatus, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                                .addComponent(jLabel2)
                                .addGap(30, 30, 30)))
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(cboPriority, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(btnSearch))
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addGap(9, 9, 9)
                                .addComponent(jLabel3)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 6, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(jLabel2)
                    .addComponent(jLabel3))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtKeyword, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnSearch)
                    .addComponent(cboStatus, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(cboPriority, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 21, Short.MAX_VALUE)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 222, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void btnSearchActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSearchActionPerformed
                String keyword = txtKeyword.getText().trim();
        String status = String.valueOf(cboStatus.getSelectedItem());
        String priority = String.valueOf(cboPriority.getSelectedItem());
        btnSearch.setEnabled(false);
        new javax.swing.SwingWorker<java.util.List<Object[]>, Void>() {
            @Override
            protected java.util.List<Object[]> doInBackground() throws Exception {
                return new ngo.dao.TaskDAO().searchRows(null, keyword, status, priority);
            }

            @Override
            protected void done() {
                try {
                    javax.swing.table.DefaultTableModel model
                            = new javax.swing.table.DefaultTableModel(ngo.dao.TaskDAO.BROWSE_COLUMNS, 0) {
                        @Override
                        public boolean isCellEditable(int r, int c) {
                            return false;
                        }
                    };
                    for (Object[] row : get()) {
                        model.addRow(row);
                    }
                    tblResults.setModel(model);
                    renameHeaders(tblResults, "ID", "Title", "Label", "Priority", "Status", "Due date", "Assignee");
                } catch (Exception ex) {
                    Throwable t = (ex.getCause() != null) ? ex.getCause() : ex;
                    javax.swing.JOptionPane.showMessageDialog(SearchForm.this, t.getMessage(),
                            "Lỗi", javax.swing.JOptionPane.ERROR_MESSAGE);
                } finally {
                    btnSearch.setEnabled(true);
                }
            }
        }.execute();      // TODO add your handling code here:
    }//GEN-LAST:event_btnSearchActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
            logger.log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(() -> new SearchForm().setVisible(true));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnSearch;
    private javax.swing.JComboBox<String> cboPriority;
    private javax.swing.JComboBox<String> cboStatus;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTable tblResults;
    private javax.swing.JTextField txtKeyword;
    // End of variables declaration//GEN-END:variables
}
