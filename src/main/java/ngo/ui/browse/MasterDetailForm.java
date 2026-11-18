/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
package ngo.ui.browse;

/**
 *
 * @author Phuong Vy
 */
public class MasterDetailForm extends javax.swing.JFrame {
    
    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(MasterDetailForm.class.getName());
    private final javax.swing.JLabel lblPageTitle =
        new javax.swing.JLabel("Projects and tasks");

    /**
     * Creates new form MasterDetailForm
     */
        public MasterDetailForm() {
        initComponents();
        relayout();
                UiStyle.apply(this, "Projects");
                        lblPageTitle.setFont(new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 24));
        lblPageTitle.setForeground(new java.awt.Color(0x1A1A1A));
        UiStyle.styleTable(tblProjects);
        UiStyle.styleTable(tblTasks);
        setLocationRelativeTo(null);
        tblProjects.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        tblProjects.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadTasksOfSelectedProject();
            }
        });
        loadProjects();
    }
        
private void relayout() {
    jPanel2.removeAll();
    jPanel2.setLayout(new java.awt.GridBagLayout());

    java.awt.GridBagConstraints g =
            new java.awt.GridBagConstraints();

    g.gridx = 0;
    g.weightx = 1;
    g.fill = java.awt.GridBagConstraints.BOTH;

    // 1. Main page title
    lblPageTitle.setFont(
            new java.awt.Font("Segoe UI", java.awt.Font.BOLD, 24));
    lblPageTitle.setForeground(new java.awt.Color(0x1A1A1A));

    g.gridy = 0;
    g.weighty = 0;
    g.insets = new java.awt.Insets(18, 16, 12, 16);
    jPanel2.add(lblPageTitle, g);

    // 2. Projects heading
    g.gridy = 1;
    g.weighty = 0;
    g.insets = new java.awt.Insets(4, 16, 4, 16);
    jPanel2.add(jPanel1, g);

    // 3. Projects table
    g.gridy = 2;
    g.weighty = 0.4;
    g.insets = new java.awt.Insets(4, 16, 12, 16);
    jPanel2.add(jScrollPane1, g);

    // 4. Tasks heading
    g.gridy = 3;
    g.weighty = 0;
    g.insets = new java.awt.Insets(4, 16, 4, 16);
    jPanel2.add(jLabel2, g);

    // 5. Tasks table
    g.gridy = 4;
    g.weighty = 0.6;
    g.insets = new java.awt.Insets(4, 16, 16, 16);
    jPanel2.add(jScrollPane2, g);

    jPanel2.revalidate();
    jPanel2.repaint();
}


    private void showTable(javax.swing.JTable table, String[] columns, java.util.List<Object[]> rows) {
        javax.swing.table.DefaultTableModel model = new javax.swing.table.DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        for (Object[] row : rows) {
            model.addRow(row);
        }
        table.setModel(model);
    }
        private void renameHeaders(javax.swing.JTable table, String... names) {
        if (table.getColumnCount() != names.length) {
            return; // số cột không khớp thì giữ nguyên, không báo lỗi
        }
        for (int i = 0; i < names.length; i++) {
            table.getColumnModel().getColumn(i).setHeaderValue(names[i]);
        }
        table.getTableHeader().repaint();
    }

    private void showError(Exception ex) {
        Throwable t = (ex.getCause() != null) ? ex.getCause() : ex;
        javax.swing.JOptionPane.showMessageDialog(this, t.getMessage(), "Lỗi",
                javax.swing.JOptionPane.ERROR_MESSAGE);
    }

    private void loadProjects() {
        new javax.swing.SwingWorker<java.util.List<Object[]>, Void>() {
            @Override
            protected java.util.List<Object[]> doInBackground() throws Exception {
                return new ngo.dao.ProjectDAO().browseRows();
            }

            @Override
            protected void done() {
                try {
                    showTable(tblProjects, ngo.dao.ProjectDAO.BROWSE_COLUMNS, get());
                    renameHeaders(tblProjects, "ID", "Project name", "Owner", "Start date", "Deadline", "Tasks");
                   
                } catch (Exception ex) {
                    showError(ex);
                }
            }
        }.execute();
    }

    private void loadTasksOfSelectedProject() {
        int row = tblProjects.getSelectedRow();
        if (row < 0) {
            return;
        }
        final int projectId = Integer.parseInt(tblProjects.getValueAt(row, 0).toString());
        new javax.swing.SwingWorker<java.util.List<Object[]>, Void>() {
            @Override
            protected java.util.List<Object[]> doInBackground() throws Exception {
                return new ngo.dao.TaskDAO().browseRows(projectId);
            }

            @Override
            protected void done() {
                try {
                    showTable(tblTasks, ngo.dao.TaskDAO.BROWSE_COLUMNS, get());
              
                    renameHeaders(tblTasks,
        "ID", "Title", "Team", "Priority",
        "Status", "Due date", "Assignee");
                    tblTasks.setRowHeight(34);
                    if (tblTasks.getColumnCount() > 0) {
    tblTasks.getColumnModel().getColumn(0).setPreferredWidth(50);
    tblTasks.getColumnModel().getColumn(0).setMaxWidth(60);
}


if (tblTasks.getColumnCount() >= 7) {
    tblTasks.setAutoResizeMode(javax.swing.JTable.AUTO_RESIZE_ALL_COLUMNS);

    tblTasks.getColumnModel().getColumn(0).setPreferredWidth(40);  // ID
    tblTasks.getColumnModel().getColumn(1).setPreferredWidth(130); // Title
    tblTasks.getColumnModel().getColumn(2).setPreferredWidth(65);  // Team
    tblTasks.getColumnModel().getColumn(3).setPreferredWidth(65);  // Priority
    tblTasks.getColumnModel().getColumn(4).setPreferredWidth(65);  // Status
    tblTasks.getColumnModel().getColumn(5).setPreferredWidth(85);  // Due date
    tblTasks.getColumnModel().getColumn(6).setPreferredWidth(220); // Assignee
}

                  
                } catch (Exception ex) {
                    showError(ex);
                }
            }
        }.execute();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel2 = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jScrollPane1 = new javax.swing.JScrollPane();
        tblProjects = new javax.swing.JTable();
        jScrollPane2 = new javax.swing.JScrollPane();
        tblTasks = new javax.swing.JTable();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jLabel2.setText("Tasks of selected project");

        jLabel1.setText("Projects");

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addContainerGap())
        );

        tblProjects.setModel(new javax.swing.table.DefaultTableModel(
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
        jScrollPane1.setViewportView(tblProjects);

        tblTasks.setModel(new javax.swing.table.DefaultTableModel(
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
        jScrollPane2.setViewportView(tblTasks);

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 388, Short.MAX_VALUE)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addComponent(jLabel2)
                    .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 105, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 10, Short.MAX_VALUE)
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 105, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        getContentPane().add(jPanel2, java.awt.BorderLayout.CENTER);

        pack();
    }// </editor-fold>//GEN-END:initComponents

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
        java.awt.EventQueue.invokeLater(() -> new MasterDetailForm().setVisible(true));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JTable tblProjects;
    private javax.swing.JTable tblTasks;
    // End of variables declaration//GEN-END:variables
}
