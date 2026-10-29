package server;

import common.Protocol;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;

public class ServerUI extends JFrame {
    private JTextArea logArea;
    private DefaultListModel<String> userListModel;
    private DefaultListModel<String> groupListModel;

    public ServerUI() {
        setTitle("MiniChat Server - Quản Trị Hệ Thống");
        setSize(760, 500);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        initUI();

        ServerCore core = new ServerCore(this);
        core.startServer(Protocol.DEFAULT_PORT);
    }

    private void initUI() {
        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBackground(new Color(244, 246, 248));
        main.setBorder(new EmptyBorder(10, 10, 10, 10));

        String localIp = "localhost";
        try {
            localIp = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ignored) {
        }

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(0, 104, 255));
        header.setBorder(new EmptyBorder(12, 16, 12, 16));
        JLabel title = new JLabel("MINICHAT SERVER | IP LAN: " + localIp + " | PORT: " + Protocol.DEFAULT_PORT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 15));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.WEST);
        main.add(header, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Nhật ký hoạt động (Server Logs)"));
        main.add(logScroll, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new GridLayout(2, 1, 8, 8));
        rightPanel.setPreferredSize(new Dimension(260, 0));
        rightPanel.setOpaque(false);

        userListModel = new DefaultListModel<>();
        JList<String> userList = new JList<>(userListModel);
        userList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        JScrollPane uScroll = new JScrollPane(userList);
        uScroll.setBorder(BorderFactory.createTitledBorder("Danh sách User (Online / Offline)"));

        groupListModel = new DefaultListModel<>();
        JList<String> groupList = new JList<>(groupListModel);
        groupList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        JScrollPane gScroll = new JScrollPane(groupList);
        gScroll.setBorder(BorderFactory.createTitledBorder("Danh sách Nhóm trên Server"));

        rightPanel.add(uScroll);
        rightPanel.add(gScroll);
        main.add(rightPanel, BorderLayout.EAST);

        setContentPane(main);
    }

    public void appendLog(String msg) {
        SwingUtilities.invokeLater(() -> {
            String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
            logArea.append("[" + time + "] " + msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public void updateLists(Collection<UserModel> users, Collection<GroupModel> groups) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            for (UserModel u : users) {
                String status = u.isOnline() ? "🟢 Online" : "⚪ Offline";
                userListModel.addElement(u.getUsername() + " (" + status + ")");
            }
            groupListModel.clear();
            for (GroupModel g : groups) {
                groupListModel.addElement(
                        "👥 " + g.getGroupName() + " [👑 " + g.getAdmin() + " | " + g.getMembers().size() + " TV]");
            }
        });
    }
}