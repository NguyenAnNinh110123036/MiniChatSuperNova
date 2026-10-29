package client.ui;

import client.ClientSocket;
import common.AvatarUtil;
import common.Protocol;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.*;
import java.util.List;

public class GroupDialog extends JDialog {

    // ==================== 1. HỘP THOẠI TẠO NHÓM MỚI (>= 3 NGƯỜI)
    // ====================
    public static void showCreateGroupDialog(JFrame parent, ClientSocket socket,
            Map<String, ChatPanel.ClientUserInfo> usersMap, String myUsername) {
        JDialog dialog = new JDialog(parent, "Tạo Nhóm Trò Chuyện Mới (>= 3 thành viên)", true);
        dialog.setSize(480, 560);
        dialog.setLocationRelativeTo(parent);

        JPanel main = new JPanel(new BorderLayout(12, 12));
        main.setBorder(new EmptyBorder(16, 16, 16, 16));
        main.setBackground(Color.WHITE);

        JPanel topBox = new JPanel(new BorderLayout(10, 10));
        topBox.setOpaque(false);

        final String[] groupAvatarB64 = { "" };
        JLabel avatarLbl = new JLabel(AvatarUtil.createCircleAvatar("", "G", 58, false, false));
        JButton chooseImgBtn = new JButton("📷 Chọn ảnh đại diện nhóm");
        chooseImgBtn.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        chooseImgBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new FileNameExtensionFilter("Hình ảnh (JPG, PNG)", "jpg", "jpeg", "png"));
            if (fc.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                File f = fc.getSelectedFile();
                groupAvatarB64[0] = AvatarUtil.encodeImageFile(f, 96);
                avatarLbl.setIcon(AvatarUtil.createCircleAvatar(groupAvatarB64[0], "G", 58, false, false));
            }
        });

        JPanel avatarPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        avatarPanel.setOpaque(false);
        avatarPanel.add(avatarLbl);
        avatarPanel.add(chooseImgBtn);

        JTextField nameField = new JTextField();
        nameField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        nameField.setBorder(BorderFactory.createTitledBorder("Nhập tên nhóm mới"));

        topBox.add(avatarPanel, BorderLayout.NORTH);
        topBox.add(nameField, BorderLayout.CENTER);

        // Danh sách lớn hiển thị Avatar tròn + Tên (Click để chọn nhiều người)
        DefaultListModel<ChatPanel.ClientUserInfo> listModel = new DefaultListModel<>();
        for (ChatPanel.ClientUserInfo u : usersMap.values()) {
            listModel.addElement(u);
        }
        Set<String> selectedUsers = new LinkedHashSet<>();

        JList<ChatPanel.ClientUserInfo> userList = new JList<>(listModel);
        userList.setFixedCellHeight(62);
        userList.setCellRenderer(new LargeUserSelectRenderer(selectedUsers, true));
        userList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int idx = userList.locationToIndex(e.getPoint());
                if (idx >= 0) {
                    ChatPanel.ClientUserInfo u = listModel.getElementAt(idx);
                    if (selectedUsers.contains(u.username)) {
                        selectedUsers.remove(u.username);
                    } else {
                        selectedUsers.add(u.username);
                    }
                    userList.repaint();
                }
            }
        });

        JScrollPane scroll = new JScrollPane(userList);
        scroll.setBorder(BorderFactory.createTitledBorder("Click vào người dùng để chọn (Chọn ít nhất 2 người)"));

        JButton createBtn = new JButton("TẠO NHÓM NGAY");
        stylePrimaryButton(createBtn, new Color(0, 104, 255));
        createBtn.addActionListener(e -> {
            String gName = nameField.getText().trim();
            if (gName.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Vui lòng nhập tên nhóm!");
                return;
            }
            if (selectedUsers.size() < 2) {
                JOptionPane.showMessageDialog(dialog,
                        "Nhóm phải có từ 3 thành viên trở lên!\n(Bạn + ít nhất 2 người được chọn trong danh sách)",
                        "Chưa đủ thành viên", JOptionPane.WARNING_MESSAGE);
                return;
            }
            socket.send(Protocol.pack(Protocol.CMD_CREATE_GROUP, gName, groupAvatarB64[0],
                    String.join(",", selectedUsers)));
            dialog.dispose();
        });

        main.add(topBox, BorderLayout.NORTH);
        main.add(scroll, BorderLayout.CENTER);
        main.add(createBtn, BorderLayout.SOUTH);

        dialog.setContentPane(main);
        dialog.setVisible(true);
    }

    // ==================== 2. HỘP THOẠI THÔNG TIN NHÓM & QUẢN TRỊ
    // ====================
    public static void showGroupInfoDialog(JFrame parent, ClientSocket socket, ChatPanel.ClientGroupInfo group,
            Map<String, ChatPanel.ClientUserInfo> usersMap, String myUsername, String myAvatarBase64) {
        JDialog dialog = new JDialog(parent, "Thông tin nhóm: " + group.groupName, true);
        dialog.setSize(500, 580);
        dialog.setLocationRelativeTo(parent);

        boolean amIAdmin = group.admin.equals(myUsername);

        JPanel main = new JPanel(new BorderLayout(12, 12));
        main.setBorder(new EmptyBorder(16, 16, 16, 16));
        main.setBackground(Color.WHITE);

        // Header thông tin nhóm
        JPanel header = new JPanel(new BorderLayout(14, 6));
        header.setOpaque(false);
        JLabel avatarLbl = new JLabel(
                AvatarUtil.createCircleAvatar(group.avatarBase64, group.groupName, 64, false, false));

        JPanel infoText = new JPanel(new GridLayout(3, 1, 0, 2));
        infoText.setOpaque(false);
        JLabel nameLbl = new JLabel("Nhóm: " + group.groupName);
        nameLbl.setFont(new Font("Segoe UI", Font.BOLD, 17));

        JLabel adminLbl = new JLabel("👑 Nhóm trưởng: " + group.admin);
        adminLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        adminLbl.setForeground(new Color(220, 53, 69)); // Màu đỏ cho Nhóm trưởng

        JLabel countLbl = new JLabel("Tổng số thành viên: " + group.members.size() + " người");
        countLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        countLbl.setForeground(Color.DARK_GRAY);

        infoText.add(nameLbl);
        infoText.add(adminLbl);
        infoText.add(countLbl);

        header.add(avatarLbl, BorderLayout.WEST);
        header.add(infoText, BorderLayout.CENTER);

        // Danh sách thành viên với Icon tròn, Nhóm trưởng màu đỏ, Online màu xanh lá,
        // Offline ẩn đi
        DefaultListModel<String> memModel = new DefaultListModel<>();
        for (String m : group.members) {
            memModel.addElement(m);
        }
        JList<String> memList = new JList<>(memModel);
        memList.setFixedCellHeight(62);
        memList.setCellRenderer(new GroupMemberRenderer(group, usersMap, myUsername, myAvatarBase64));

        JScrollPane scroll = new JScrollPane(memList);
        scroll.setBorder(BorderFactory.createTitledBorder("Thành viên trong nhóm"));

        // Các nút chức năng
        JPanel btnPanel = new JPanel(new GridLayout(amIAdmin ? 2 : 1, 2, 10, 10));
        btnPanel.setOpaque(false);

        JButton addMemBtn = new JButton("+ Thêm thành viên");
        addMemBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        addMemBtn.addActionListener(e -> {
            dialog.dispose();
            showAddMemberDialog(parent, socket, group, usersMap);
        });

        JButton leaveBtn = new JButton("🚪 Rời khỏi nhóm");
        leaveBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        leaveBtn.setForeground(new Color(220, 53, 69));
        leaveBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(dialog,
                    "Bạn có chắc chắn muốn rời khỏi nhóm '" + group.groupName + "' không?",
                    "Xác nhận rời nhóm", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                socket.send(Protocol.pack(Protocol.CMD_LEAVE_GROUP, group.groupName));
                dialog.dispose();
            }
        });

        btnPanel.add(addMemBtn);
        btnPanel.add(leaveBtn);

        if (amIAdmin) {
            JButton changeAvatarBtn = new JButton("🖼️ Đổi ảnh nhóm");
            changeAvatarBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
            changeAvatarBtn.addActionListener(e -> {
                JFileChooser fc = new JFileChooser();
                fc.setFileFilter(new FileNameExtensionFilter("Hình ảnh (JPG, PNG)", "jpg", "jpeg", "png"));
                if (fc.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                    String b64 = AvatarUtil.encodeImageFile(fc.getSelectedFile(), 96);
                    socket.send(Protocol.pack(Protocol.CMD_UPDATE_AVATAR, "GROUP", group.groupName, b64));
                    dialog.dispose();
                }
            });

            JButton kickBtn = new JButton("❌ Mời ra khỏi nhóm");
            kickBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
            kickBtn.setForeground(new Color(220, 53, 69));
            kickBtn.addActionListener(e -> {
                dialog.dispose();
                showKickMemberDialog(parent, socket, group, usersMap, myUsername);
            });

            btnPanel.add(changeAvatarBtn);
            btnPanel.add(kickBtn);
        }

        main.add(header, BorderLayout.NORTH);
        main.add(scroll, BorderLayout.CENTER);
        main.add(btnPanel, BorderLayout.SOUTH);

        dialog.setContentPane(main);
        dialog.setVisible(true);
    }

    // ==================== 3. BẢNG CHỌN THÊM THÀNH VIÊN CỠ LỚN ====================
    private static void showAddMemberDialog(JFrame parent, ClientSocket socket, ChatPanel.ClientGroupInfo group,
            Map<String, ChatPanel.ClientUserInfo> usersMap) {
        DefaultListModel<ChatPanel.ClientUserInfo> model = new DefaultListModel<>();
        for (ChatPanel.ClientUserInfo u : usersMap.values()) {
            if (!group.members.contains(u.username)) {
                model.addElement(u);
            }
        }
        if (model.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Tất cả người dùng trên Server đều đã ở trong nhóm này!");
            return;
        }

        JDialog dialog = new JDialog(parent, "Thêm thành viên vào nhóm: " + group.groupName, true);
        dialog.setSize(450, 480);
        dialog.setLocationRelativeTo(parent);

        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBorder(new EmptyBorder(15, 15, 15, 15));
        main.setBackground(Color.WHITE);

        Set<String> selectedUsers = new LinkedHashSet<>();
        JList<ChatPanel.ClientUserInfo> list = new JList<>(model);
        list.setFixedCellHeight(62);
        list.setCellRenderer(new LargeUserSelectRenderer(selectedUsers, true));
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int idx = list.locationToIndex(e.getPoint());
                if (idx >= 0) {
                    String uName = model.getElementAt(idx).username;
                    if (selectedUsers.contains(uName))
                        selectedUsers.remove(uName);
                    else
                        selectedUsers.add(uName);
                    list.repaint();
                }
            }
        });

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createTitledBorder("Click vào người dùng muốn thêm vào nhóm"));

        JButton confirmBtn = new JButton("THÊM VÀO NHÓM");
        stylePrimaryButton(confirmBtn, new Color(40, 167, 69));
        confirmBtn.addActionListener(e -> {
            if (selectedUsers.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Vui lòng chọn ít nhất 1 người dùng!");
                return;
            }
            for (String u : selectedUsers) {
                socket.send(Protocol.pack(Protocol.CMD_ADD_MEMBER, group.groupName, u));
            }
            dialog.dispose();
        });

        main.add(scroll, BorderLayout.CENTER);
        main.add(confirmBtn, BorderLayout.SOUTH);
        dialog.setContentPane(main);
        dialog.setVisible(true);
    }

    // ==================== 4. BẢNG CHỌN MỜI THÀNH VIÊN RA KHỎI NHÓM CỠ LỚN
    // ====================
    private static void showKickMemberDialog(JFrame parent, ClientSocket socket, ChatPanel.ClientGroupInfo group,
            Map<String, ChatPanel.ClientUserInfo> usersMap, String myUsername) {
        DefaultListModel<ChatPanel.ClientUserInfo> model = new DefaultListModel<>();
        for (String mem : group.members) {
            if (!mem.equals(myUsername)) {
                ChatPanel.ClientUserInfo info = usersMap.get(mem);
                if (info == null) {
                    info = new ChatPanel.ClientUserInfo();
                    info.username = mem;
                    info.online = false;
                    info.avatarBase64 = "";
                }
                model.addElement(info);
            }
        }
        if (model.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "Không có thành viên nào khác để mời ra khỏi nhóm!");
            return;
        }

        JDialog dialog = new JDialog(parent, "Mời thành viên ra khỏi nhóm: " + group.groupName, true);
        dialog.setSize(450, 480);
        dialog.setLocationRelativeTo(parent);

        JPanel main = new JPanel(new BorderLayout(10, 10));
        main.setBorder(new EmptyBorder(15, 15, 15, 15));
        main.setBackground(Color.WHITE);

        Set<String> selectedUsers = new LinkedHashSet<>();
        JList<ChatPanel.ClientUserInfo> list = new JList<>(model);
        list.setFixedCellHeight(62);
        list.setCellRenderer(new LargeUserSelectRenderer(selectedUsers, false));
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int idx = list.locationToIndex(e.getPoint());
                if (idx >= 0) {
                    selectedUsers.clear();
                    selectedUsers.add(model.getElementAt(idx).username);
                    list.repaint();
                }
            }
        });

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createTitledBorder("Click chọn thành viên muốn mời ra khỏi nhóm"));

        JButton kickBtn = new JButton("MỜI RA KHỎI NHÓM");
        stylePrimaryButton(kickBtn, new Color(220, 53, 69));
        kickBtn.addActionListener(e -> {
            if (selectedUsers.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Vui lòng chọn 1 thành viên trong danh sách!");
                return;
            }
            String target = selectedUsers.iterator().next();
            socket.send(Protocol.pack(Protocol.CMD_KICK_MEMBER, group.groupName, target));
            dialog.dispose();
        });

        main.add(scroll, BorderLayout.CENTER);
        main.add(kickBtn, BorderLayout.SOUTH);
        dialog.setContentPane(main);
        dialog.setVisible(true);
    }

    private static void stylePrimaryButton(JButton btn, Color bg) {
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setPreferredSize(new Dimension(0, 42));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    // Bộ vẽ dòng chọn thành viên cỡ lớn (Có Avatar tròn + Tên + Trạng thái xanh lá)
    private static class LargeUserSelectRenderer extends JPanel implements ListCellRenderer<ChatPanel.ClientUserInfo> {
        private final Set<String> selectedSet;
        private final JLabel avatarLbl = new JLabel();
        private final JLabel nameLbl = new JLabel();
        private final JLabel statusLbl = new JLabel();
        private final JLabel badgeLbl = new JLabel();

        public LargeUserSelectRenderer(Set<String> selectedSet, boolean multiSelect) {
            this.selectedSet = selectedSet;
            setLayout(new BorderLayout(12, 0));
            setBorder(BorderFactory.createCompoundBorder(
                    new MatteBorder(0, 0, 1, 0, new Color(235, 238, 242)),
                    new EmptyBorder(8, 12, 8, 12)));
            setOpaque(true);

            JPanel center = new JPanel(new GridLayout(2, 1));
            center.setOpaque(false);
            nameLbl.setFont(new Font("Segoe UI", Font.BOLD, 15));
            statusLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
            statusLbl.setForeground(new Color(40, 167, 69));
            center.add(nameLbl);
            center.add(statusLbl);

            badgeLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
            badgeLbl.setOpaque(true);
            badgeLbl.setBorder(new EmptyBorder(4, 10, 4, 10));

            add(avatarLbl, BorderLayout.WEST);
            add(center, BorderLayout.CENTER);
            add(badgeLbl, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends ChatPanel.ClientUserInfo> list,
                ChatPanel.ClientUserInfo u, int index,
                boolean isSelected, boolean cellHasFocus) {
            boolean chosen = selectedSet.contains(u.username);
            avatarLbl.setIcon(AvatarUtil.createCircleAvatar(u.avatarBase64, u.username, 44, true, u.online));
            nameLbl.setText(u.username);
            statusLbl.setText(u.online ? "● Đang Online" : "");

            if (chosen) {
                setBackground(new Color(225, 239, 255));
                badgeLbl.setText("✓ Đã chọn");
                badgeLbl.setBackground(new Color(0, 104, 255));
                badgeLbl.setForeground(Color.WHITE);
            } else {
                setBackground(Color.WHITE);
                badgeLbl.setText("Chọn");
                badgeLbl.setBackground(new Color(240, 242, 245));
                badgeLbl.setForeground(Color.DARK_GRAY);
            }
            return this;
        }
    }

    // Bộ vẽ danh sách thành viên trong Thông tin nhóm (Nhóm trưởng màu đỏ, Online
    // xanh lá, Offline ẩn)
    private static class GroupMemberRenderer extends JPanel implements ListCellRenderer<String> {
        private final ChatPanel.ClientGroupInfo group;
        private final Map<String, ChatPanel.ClientUserInfo> usersMap;
        private final String myUsername;
        private final String myAvatarBase64;

        private final JLabel avatarLbl = new JLabel();
        private final JLabel nameLbl = new JLabel();
        private final JLabel statusLbl = new JLabel();

        public GroupMemberRenderer(ChatPanel.ClientGroupInfo group,
                Map<String, ChatPanel.ClientUserInfo> usersMap,
                String myUsername, String myAvatarBase64) {
            this.group = group;
            this.usersMap = usersMap;
            this.myUsername = myUsername;
            this.myAvatarBase64 = myAvatarBase64;

            setLayout(new BorderLayout(12, 0));
            setBorder(BorderFactory.createCompoundBorder(
                    new MatteBorder(0, 0, 1, 0, new Color(235, 238, 242)),
                    new EmptyBorder(8, 12, 8, 12)));
            setBackground(Color.WHITE);

            JPanel textPanel = new JPanel(new GridLayout(2, 1));
            textPanel.setOpaque(false);
            nameLbl.setFont(new Font("Segoe UI", Font.BOLD, 15));
            statusLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
            statusLbl.setForeground(new Color(40, 167, 69)); // Màu xanh lá cho Online
            textPanel.add(nameLbl);
            textPanel.add(statusLbl);

            add(avatarLbl, BorderLayout.WEST);
            add(textPanel, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list, String memberName,
                int index, boolean isSelected, boolean cellHasFocus) {
            boolean isAdmin = memberName.equals(group.admin);
            boolean isOnline;
            String avatarB64 = "";

            if (memberName.equals(myUsername)) {
                isOnline = true;
                avatarB64 = myAvatarBase64;
            } else {
                ChatPanel.ClientUserInfo u = usersMap.get(memberName);
                isOnline = (u != null && u.online);
                avatarB64 = (u != null) ? u.avatarBase64 : "";
            }

            avatarLbl.setIcon(AvatarUtil.createCircleAvatar(avatarB64, memberName, 44, isOnline, isOnline));

            if (isAdmin) {
                nameLbl.setText(memberName + " (👑 Nhóm trưởng)");
                nameLbl.setForeground(new Color(220, 53, 69)); // Nhóm trưởng màu đỏ
            } else {
                nameLbl.setText(memberName);
                nameLbl.setForeground(new Color(30, 30, 30));
            }

            // Online hiện màu xanh lá, Offline thì ẩn đi
            statusLbl.setText(isOnline ? "● Đang Online" : "");

            return this;
        }
    }
}