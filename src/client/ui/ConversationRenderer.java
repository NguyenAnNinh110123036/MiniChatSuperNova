package client.ui;

import client.ChatMemory;
import common.AvatarUtil;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class ConversationRenderer extends JPanel implements ListCellRenderer<String> {
    private final ChatPanel chatPanel;
    private final JLabel avatarLabel = new JLabel();
    private final JLabel titleLabel = new JLabel();
    private final JLabel subtitleLabel = new JLabel();
    private final JLabel tagLabel = new JLabel();
    private final JLabel unreadBadge = new JLabel();

    public ConversationRenderer(ChatPanel chatPanel) {
        this.chatPanel = chatPanel;
        setLayout(new BorderLayout(10, 4));
        setBorder(new EmptyBorder(8, 10, 8, 10));
        setOpaque(true);

        JPanel leftBox = new JPanel(new BorderLayout(0, 2));
        leftBox.setOpaque(false);
        avatarLabel.setHorizontalAlignment(SwingConstants.CENTER);
        tagLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        tagLabel.setHorizontalAlignment(SwingConstants.CENTER);
        tagLabel.setOpaque(true);
        leftBox.add(avatarLabel, BorderLayout.CENTER);
        leftBox.add(tagLabel, BorderLayout.SOUTH);

        JPanel centerBox = new JPanel(new GridLayout(2, 1, 0, 2));
        centerBox.setOpaque(false);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitleLabel.setForeground(new Color(100, 105, 115));
        centerBox.add(titleLabel);
        centerBox.add(subtitleLabel);

        JPanel rightBox = new JPanel(new GridBagLayout());
        rightBox.setOpaque(false);
        unreadBadge.setFont(new Font("Segoe UI", Font.BOLD, 11));
        unreadBadge.setForeground(Color.WHITE);
        unreadBadge.setBackground(new Color(229, 57, 53));
        unreadBadge.setOpaque(true);
        unreadBadge.setBorder(new EmptyBorder(2, 7, 2, 7));
        rightBox.add(unreadBadge);

        add(leftBox, BorderLayout.WEST);
        add(centerBox, BorderLayout.CENTER);
        add(rightBox, BorderLayout.EAST);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends String> list, String key,
            int index, boolean isSelected, boolean cellHasFocus) {
        ChatMemory.ChatMessage lastMsg = chatPanel.getMemory().getLastMessage(key);
        String preview = (lastMsg != null)
                ? (lastMsg.sender + ": " + lastMsg.content)
                : "Chưa có tin nhắn";
        if (preview.length() > 30)
            preview = preview.substring(0, 30) + "...";

        if ("SERVER".equals(key)) {
            avatarLabel.setIcon(AvatarUtil.createCircleAvatar("", "S", 44, false, true));
            tagLabel.setText("SERVER");
            tagLabel.setBackground(new Color(0, 104, 255));
            tagLabel.setForeground(Color.WHITE);
            titleLabel.setText("CHUNG SERVER 🌐");
            subtitleLabel.setText(lastMsg != null ? preview : "Kênh chat chung toàn hệ thống");
        } else if (key.startsWith("GROUP:")) {
            String gName = key.substring(6);
            ChatPanel.ClientGroupInfo gInfo = chatPanel.getGroupInfo(gName);
            String avatarB64 = gInfo != null ? gInfo.avatarBase64 : "";
            avatarLabel.setIcon(AvatarUtil.createCircleAvatar(avatarB64, gName, 44, false, true));
            tagLabel.setText("NHÓM");
            tagLabel.setBackground(new Color(40, 167, 69));
            tagLabel.setForeground(Color.WHITE);
            titleLabel.setText(gName + " 👥");
            subtitleLabel.setText(lastMsg != null ? preview : "Trò chuyện nhóm");
        } else if (key.startsWith("USER:")) {
            String uName = key.substring(5);
            ChatPanel.ClientUserInfo uInfo = chatPanel.getUserInfo(uName);
            boolean online = uInfo != null && uInfo.online;
            String rel = uInfo != null ? uInfo.relation : "STRANGER";
            String avatarB64 = uInfo != null ? uInfo.avatarBase64 : "";

            avatarLabel.setIcon(AvatarUtil.createCircleAvatar(avatarB64, uName, 44, true, online));
            if ("FRIEND".equals(rel)) {
                tagLabel.setText("Bạn bè");
                tagLabel.setBackground(new Color(220, 235, 255));
                tagLabel.setForeground(new Color(0, 104, 255));
            } else {
                tagLabel.setText("Người lạ");
                tagLabel.setBackground(new Color(255, 236, 179));
                tagLabel.setForeground(new Color(180, 100, 0));
            }
            titleLabel.setText(uName + (online ? " 🟢" : " ⚪"));
            subtitleLabel.setText(lastMsg != null ? preview : (online ? "Đang Online" : "Đang Offline"));
        }

        int unread = chatPanel.getMemory().getUnread(key);
        if (unread > 0) {
            unreadBadge.setText(unread > 99 ? "99+" : String.valueOf(unread));
            unreadBadge.setVisible(true);
            subtitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
            subtitleLabel.setForeground(new Color(20, 20, 20));
        } else {
            unreadBadge.setVisible(false);
            subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            subtitleLabel.setForeground(new Color(100, 105, 115));
        }

        setBackground(isSelected ? new Color(229, 239, 255) : Color.WHITE);
        return this;
    }
}