package ngo.ui.edit;

import ngo.dao.LookupDAO;
import ngo.dao.MembershipDAO;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Join/leave Team screen backed by MembershipDAO. */
public class MembershipEditingPanel extends JPanel {
    private final MembershipDAO membershipDAO = new MembershipDAO();
    private final LookupDAO lookupDAO = new LookupDAO();
    private final JComboBox<LookupDAO.ComboItem> teamCombo = new JComboBox<>();
    private final JComboBox<LookupDAO.ComboItem> volunteerCombo = new JComboBox<>();
    private final JComboBox<LookupDAO.ComboItem> roleCombo = new JComboBox<>();
    private final JTextField joinedDateField = new JTextField(LocalDate.now().toString(), 12);
    private final DefaultTableModel model = new DefaultTableModel(new Object[]{"Membership ID", "Volunteer", "Role", "Joined"}, 0) {
        @Override public boolean isCellEditable(int row,int col){return false;}
    };
    private final JTable table = new JTable(model);
    private final JButton joinButton = new JButton("Join Team");
    private final JButton leaveButton = new JButton("Leave Team");
    private final JButton refreshButton = new JButton("Refresh members");
    private boolean loading;
    private List<Object[]> currentRows = List.of();

    public MembershipEditingPanel() { buildUi(); bindEvents(); loadLookups(); }

    private void buildUi() {
        setLayout(new BorderLayout(8,8)); setBorder(new EmptyBorder(10,10,10,10));
        JPanel form = new JPanel(new GridBagLayout()); form.setBorder(BorderFactory.createTitledBorder("Team Membership"));
        GridBagConstraints c = new GridBagConstraints(); c.insets=new Insets(4,6,4,6); c.anchor=GridBagConstraints.WEST; c.fill=GridBagConstraints.HORIZONTAL;
        addField(form,c,0,"Team *",teamCombo); addField(form,c,1,"Volunteer *",volunteerCombo); addField(form,c,2,"Team role *",roleCombo);
        addField(form,c,3,"Joined date (yyyy-MM-dd)",joinedDateField);
        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT)); actions.add(joinButton); actions.add(leaveButton); actions.add(refreshButton);
        JPanel top=new JPanel(new BorderLayout()); top.add(form,BorderLayout.CENTER); top.add(actions,BorderLayout.SOUTH);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); table.setAutoCreateRowSorter(true); table.setFillsViewportHeight(true);
        JScrollPane grid=new JScrollPane(table); grid.setBorder(BorderFactory.createTitledBorder("Current members of selected Team – select a row to leave"));
        JSplitPane split=new JSplitPane(JSplitPane.VERTICAL_SPLIT,top,grid); split.setResizeWeight(0.42); add(split,BorderLayout.CENTER);
    }
    private void addField(JPanel p,GridBagConstraints c,int row,String label,Component field){c.gridy=row;c.gridx=0;c.weightx=0;p.add(new JLabel(label),c);c.gridx=1;c.weightx=1;p.add(field,c);}
    private void bindEvents(){
        joinButton.addActionListener(e->joinTeam()); leaveButton.addActionListener(e->leaveTeam()); refreshButton.addActionListener(e->loadMembers());
        teamCombo.addActionListener(e->{if(!loading)loadMembers();});
    }
    private void loadLookups(){setBusy(true);UiSupport.runAsync(this,"load Membership choices",()->new LookupData(lookupDAO.teams(),lookupDAO.volunteers(),lookupDAO.teamRoles()),d->{
        loading=true;teamCombo.removeAllItems();d.teams.forEach(teamCombo::addItem);volunteerCombo.removeAllItems();d.volunteers.forEach(volunteerCombo::addItem);roleCombo.removeAllItems();d.roles.forEach(roleCombo::addItem);loading=false;loadMembers();
    },()->setBusy(false));}
    private void loadMembers(){LookupDAO.ComboItem team=(LookupDAO.ComboItem)teamCombo.getSelectedItem();model.setRowCount(0);if(team==null){currentRows=List.of();return;}setBusy(true);
        UiSupport.runAsync(this,"load current Team members",()->membershipDAO.currentMembers(team.getId()),rows->{currentRows=rows;rows.forEach(model::addRow);},()->setBusy(false));}
    private void joinTeam(){LookupDAO.ComboItem team=(LookupDAO.ComboItem)teamCombo.getSelectedItem(),vol=(LookupDAO.ComboItem)volunteerCombo.getSelectedItem(),role=(LookupDAO.ComboItem)roleCombo.getSelectedItem();
        if(team==null||vol==null||role==null){UiSupport.showError(this,"Select Team, Volunteer and Team role.");return;}
        final LocalDate date;try{date=joinedDateField.getText().trim().isEmpty()?LocalDate.now():LocalDate.parse(joinedDateField.getText().trim());}catch(DateTimeParseException e){UiSupport.showError(this,"Joined date must use yyyy-MM-dd.");return;}
        int teamId=team.getId(),volId=vol.getId(),roleId=role.getId();setBusy(true);
        UiSupport.runAsync(this,"join Team",()->membershipDAO.join(volId,teamId,roleId,date),id->{UiSupport.showInfo(this,"Membership created. ID: "+id);loadMembers();},()->setBusy(false));
    }
    private void leaveTeam(){int view=table.getSelectedRow();if(view<0){UiSupport.showError(this,"Select a current member to leave the Team.");return;}int row=table.convertRowIndexToModel(view);int membershipId=((Number)currentRows.get(row)[0]).intValue();
        int answer=JOptionPane.showConfirmDialog(this,"Confirm that this member leaves the selected Team? The membership record will be kept with a leave date.","Confirm leave Team",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE);if(answer!=JOptionPane.YES_OPTION)return;
        setBusy(true);UiSupport.runAsync(this,"leave Team",()->{membershipDAO.leave(membershipId,LocalDate.now());return null;},ignored->{UiSupport.showInfo(this,"Member has left the Team.");loadMembers();},()->setBusy(false));
    }
    private void setBusy(boolean busy){joinButton.setEnabled(!busy);leaveButton.setEnabled(!busy);refreshButton.setEnabled(!busy);teamCombo.setEnabled(!busy);volunteerCombo.setEnabled(!busy);roleCombo.setEnabled(!busy);}
    private static final class LookupData{final List<LookupDAO.ComboItem> teams,volunteers,roles;LookupData(List<LookupDAO.ComboItem> t,List<LookupDAO.ComboItem> v,List<LookupDAO.ComboItem> r){teams=t;volunteers=v;roles=r;}}
}
