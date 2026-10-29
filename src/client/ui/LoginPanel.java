package client.ui;

import client.ClientSocket;
import common.AvatarUtil;
import common.Protocol;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;

public class LoginPanel extends JPanel {
    private final MainFrame mainFrame;
    private JTextField hostField;
    private JTextField portField;
    private JTextField userField;
    private JPasswordField passField;
    private JLabel avatarPreview;
    private String selectedAvatarBase64 = "";

    public LoginPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new GridBagLayout());
        setBackground(new Color(235, 242, 252));

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(215, 222, 232), 1),
                new EmptyBorder(25, 35, 25, 35)));
        card.setPreferredSize(new Dimension(400, 490));

        JLabel title = new JLabel("Mini Chat", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 30));
        title.setForeground(new Color(0, 104, 255));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel sub = new JLabel("Kết nối bạn bè, nhóm & toàn Server", SwingConstants.CENTER);
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        sub.setForeground(Color.GRAY);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Khu vực chọn Avatar hình tròn
        avatarPreview = new JLabel(AvatarUtil.createCircleAvatar("", "U", 68, false, false));
        avatarPreview.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton chooseAvatarBtn = new JButton("📷 Chọn ảnh đại diện (Khi đăng ký)");
        chooseAvatarBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        chooseAvatarBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        chooseAvatarBtn.addActionListener(e -> chooseAvatar());

        userField = new JTextField();
        userField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        userField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        userField.setBorder(BorderFactory.createTitledBorder("Tên đăng nhập"));

        passField = new JPasswordField();
        passField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        passField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        passField.setBorder(BorderFactory.createTitledBorder("Mật khẩu"));

        JPanel netPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        netPanel.setBackground(Color.WHITE);
        netPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        hostField = new JTextField("localhost");
        hostField.setBorder(BorderFactory.createTitledBorder("IP Máy Server"));
        portField = new JTextField(String.valueOf(Protocol.DEFAULT_PORT));
        portField.setBorder(BorderFactory.createTitledBorder("Port"));
        netPanel.add(hostField);
        netPanel.add(portField);

        JButton loginBtn = new JButton("ĐĂNG NHẬP");
        styleButton(loginBtn, new Color(0, 104, 255), Color.WHITE);
        loginBtn.addActionListener(e -> doAuth(true));
        passField.addActionListener(e -> doAuth(true));

        JButton regBtn = new JButton("ĐĂNG KÝ TÀI KHOẢN MỚI");
        styleButton(regBtn, new Color(40, 167, 69), Color.WHITE);
        regBtn.addActionListener(e -> doAuth(false));

        card.add(title);
        card.add(Box.createVerticalStrut(4));
        card.add(sub);
        card.add(Box.createVerticalStrut(12));
        card.add(avatarPreview);
        card.add(Box.createVerticalStrut(6));
        card.add(chooseAvatarBtn);
        card.add(Box.createVerticalStrut(14));
        card.add(userField);
        card.add(Box.createVerticalStrut(10));
        card.add(passField);
        card.add(Box.createVerticalStrut(10));
        card.add(netPanel);
        card.add(Box.createVerticalStrut(18));
        card.add(loginBtn);
        card.add(Box.createVerticalStrut(8));
        card.add(regBtn);

        add(card);
    }

    private void styleButton(JButton btn, Color bg, Color fg) {
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
    }

    private void chooseAvatar() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Hình ảnh (JPG, PNG)", "jpg", "jpeg", "png"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            selectedAvatarBase64 = AvatarUtil.encodeImageFile(file, 96);
            String name = userField.getText().trim().isEmpty() ? "U" : userField.getText().trim();
            avatarPreview.setIcon(AvatarUtil.createCircleAvatar(selectedAvatarBase64, name, 68, false, false));
        }
    }

    private void doAuth(boolean isLogin) {
        String username = userField.getText().trim();
        String password = new String(passField.getPassword()).trim();
        String host = hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Port phải là số nguyên!");
            return;
        }

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập đầy đủ Tên đăng nhập và Mật khẩu!");
            return;
        }

        ClientSocket socket = mainFrame.getClientSocket();
        if (!socket.connect(host, port)) {
            JOptionPane.showMessageDialog(this,
                    "Không thể kết nối tới Server tại " + host + ":" + port + "\nHãy kiểm tra Server đã bật chưa!",
                    "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (isLogin) {
            socket.send(Protocol.pack(Protocol.CMD_LOGIN, username, password));
        } else {
            socket.send(Protocol.pack(Protocol.CMD_REGISTER, username, password, selectedAvatarBase64));
        }

        String resp = socket.readLine();
        if (resp == null) {
            JOptionPane.showMessageDialog(this, "Mất kết nối với Server!");
            return;
        }

        String[] p = Protocol.unpack(resp);
        if (p[0].equals(Protocol.RESP_AUTH_OK)) {
            String myName = p[1];
            String myAvatar = p.length >= 3 ? p[2] : "";
            mainFrame.openChatSession(myName, myAvatar);
        } else if (p[0].equals(Protocol.RESP_REGISTER_OK)) {
            JOptionPane.showMessageDialog(this, p[1], "Thành công", JOptionPane.INFORMATION_MESSAGE);
            socket.disconnect();
        } else if (p[0].equals(Protocol.RESP_AUTH_FAIL)) {
            JOptionPane.showMessageDialog(this, p[1], "Thông báo", JOptionPane.WARNING_MESSAGE);
            socket.disconnect();
        }
    }
}