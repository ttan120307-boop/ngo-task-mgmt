package ngo.ui.edit;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;

/** Main container for the currently available Editing & Validation screens. */
public class EditingValidationPanel extends JPanel {
    public EditingValidationPanel() {
        super(new BorderLayout());
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Task CRUD", new TaskEditingPanel());
        tabs.addTab("Transfer Assignment", new AssignmentTransferPanel());
        tabs.addTab("Comment & Attachment", new CommentAttachmentPanel());
        tabs.addTab("Task Report", new TaskReportPanel());
        add(tabs, BorderLayout.CENTER);
    }
}
