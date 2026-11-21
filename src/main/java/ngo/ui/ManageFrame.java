package ngo.ui;

import ngo.auth.Authz;
import ngo.ui.browse.UiStyle;
import ngo.ui.edit.EditingValidationPanel;

import javax.swing.JFrame;

/** "Manage" screen (Manager/Admin): task, project, membership, assignment, comment and report tabs inside the common layout. */
public class ManageFrame extends JFrame {
    public ManageFrame() {
        Authz.requireStaff();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        add(new EditingValidationPanel(), java.awt.BorderLayout.CENTER);
        UiStyle.apply(this, "Manage");
    }
}
