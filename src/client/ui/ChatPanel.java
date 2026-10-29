package client.ui;

import client.ChatMemory;
import client.ClientSocket;
import common.AvatarUtil;
import common.Protocol;
import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class ChatPanel extends JPanel {
    private static final Color ZALO_BLUE = new Color(0, 104, 255);
    private static final Color BG_LIGHT = new Color(244, 246, 248);

    private final MainFrame mainFrame;
    private final ClientSocket socket;
    private final ChatMemory memory;
    private final String myUsername;
    private String myAvatarBase64;

    public static class ClientUserInfo {
        public String username;
        public boolean online;
        public String relation; // FRIEND, REQ_IN, REQ_OUT, STRANGER
        public String avatarBase64;
    }

    public static class ClientGroupInfo {
        public String groupName;
        public String admin;
        public String pinnedMessage;
        public List<String> members = new ArrayList<>();
        public String avatarBase64;
    }

    private final Map<String, ClientUserInfo> usersMap = new LinkedHashMap<>();
    private final Map<String, ClientGroupInfo> groupsMap = new LinkedHashMap<>();
    private final Map<String, Set<String>> typingUsersMap = new HashMap<>();
    // Lưu tin nhắn ghim cục bộ cho cả Server hoặc Chat riêng
    private final Map<String, String> localPinnedMap = new HashMap<>();

    // Thành phần UI bên trái
    private JLabel myAvatarLabel;
    private JTextField sidebarSearchField;
    private String activeFilter = "ALL";
    private DefaultListModel<String> conversationListModel;
    private JList<String> conversationList;

    // Thành phần UI bên phải
    private JLabel chatAvatarLabel;
    private JLabel chatTitleLabel;
    private JLabel chatSubtitleLabel;
    private JTextField msgSearchField;
    private JButton groupInfoBtn;

    // Banner cảnh báo Người lạ & Banner Ghim tin nhắn
    private JPanel strangerBanner;
    private JLabel strangerTextLabel;
    private JButton friendActionBtn;
    private JPanel pinBanner;
    private JLabel pinTextLabel;

    // Khung chứa các bong bóng tin nhắn co giãn vừa khít nội dung
    private JPanel messagesContainer;
    private JScrollPane chatScroll;
    private JLabel typingLabel;
    private JTextField messageInput;
    private Timer typingStopTimer;
    private boolean isCurrentlyTyping = false;

    private String activeTarget = "SERVER";

    public ChatPanel(MainFrame mainFrame, ClientSocket socket, ChatMemory memory, String myUsername,
            String myAvatarBase64) {
        this.mainFrame = mainFrame;
        this.socket = socket;
        this.memory = memory;
        this.myUsername = myUsername;
        this.myAvatarBase64 = myAvatarBase64 == null ? "" : myAvatarBase64;

        setLayout(new BorderLayout());
        initTopBar();
        initMainBody();
        initTypingTimer();

        new Thread(this::listenFromServer).start();
        refreshConversationList();
        updateChatHeaderAndRender();
    }

    // ==================== 1. THANH HEADER TRÊN CÙNG ====================
    private void initTopBar() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setBackground(ZALO_BLUE);
        topBar.setBorder(new EmptyBorder(8, 16, 8, 16));

        JPanel leftUserBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftUserBox.setOpaque(false);
        myAvatarLabel = new JLabel(AvatarUtil.createCircleAvatar(myAvatarBase64, myUsername, 38, true, true));
        JLabel appTitle = new JLabel("Mini Chat  |  " + myUsername);
        appTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        appTitle.setForeground(Color.WHITE);
        leftUserBox.add(myAvatarLabel);
        leftUserBox.add(appTitle);

        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 2));
        rightActions.setOpaque(false);

        JButton changeMyAvatarBtn = new JButton("📷 Đổi Avatar");
        styleTopButton(changeMyAvatarBtn, Color.WHITE, ZALO_BLUE);
        changeMyAvatarBtn.addActionListener(e -> changeMyAvatar());

        JButton createGroupBtn = new JButton("+ Tạo Nhóm");
        styleTopButton(createGroupBtn, new Color(40, 167, 69), Color.WHITE);
        createGroupBtn
                .addActionListener(e -> GroupDialog.showCreateGroupDialog(mainFrame, socket, usersMap, myUsername));

        JButton logoutBtn = new JButton("Đăng xuất");
        styleTopButton(logoutBtn, new Color(229, 57, 53), Color.WHITE);
        logoutBtn.addActionListener(e -> {
            socket.send(Protocol.pack(Protocol.CMD_LOGOUT));
            mainFrame.returnToLogin();
        });

        rightActions.add(changeMyAvatarBtn);
        rightActions.add(createGroupBtn);
        rightActions.add(logoutBtn);

        topBar.add(leftUserBox, BorderLayout.WEST);
        topBar.add(rightActions, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);
    }

    private void styleTopButton(JButton btn, Color bg, Color fg) {
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
    }

    // ==================== 2. THÂN CHÍNH: CỘT TRÁI & KHUNG CHAT PHẢI
    // ====================
    private void initMainBody() {
        JPanel body = new JPanel(new BorderLayout());

        // --- CỘT BÊN TRÁI (SIDEBAR) ---
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(300, 0));
        sidebar.setBackground(Color.WHITE);
        sidebar.setBorder(new MatteBorder(0, 0, 0, 1, new Color(220, 225, 232)));

        JPanel searchAndTabs = new JPanel(new BorderLayout(0, 6));
        searchAndTabs.setBackground(Color.WHITE);
        searchAndTabs.setBorder(new EmptyBorder(10, 10, 8, 10));

        sidebarSearchField = new JTextField();
        sidebarSearchField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        sidebarSearchField.setBorder(BorderFactory.createTitledBorder("🔍 Tìm tên bạn bè, tên nhóm..."));
        sidebarSearchField.getDocument().addDocumentListener(new SimpleDocListener(this::refreshConversationList));

        JPanel tabsPanel = new JPanel(new GridLayout(1, 4, 4, 0));
        tabsPanel.setOpaque(false);
        tabsPanel.add(createFilterButton("Tất cả", "ALL"));
        tabsPanel.add(createFilterButton("Cá nhân", "PRIVATE"));
        tabsPanel.add(createFilterButton("Nhóm", "GROUP"));
        tabsPanel.add(createFilterButton("Server", "SERVER"));

        searchAndTabs.add(sidebarSearchField, BorderLayout.NORTH);
        searchAndTabs.add(tabsPanel, BorderLayout.SOUTH);
        sidebar.add(searchAndTabs, BorderLayout.NORTH);

        conversationListModel = new DefaultListModel<>();
        conversationList = new JList<>(conversationListModel);
        conversationList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        conversationList.setCellRenderer(new ConversationRenderer(this));
        conversationList.setFixedCellHeight(68);
        conversationList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String sel = conversationList.getSelectedValue();
                if (sel != null) {
                    activeTarget = sel;
                    memory.clearUnread(activeTarget);
                    if (activeTarget.startsWith("USER:")) {
                        socket.send(Protocol.pack(Protocol.CMD_MARK_SEEN, activeTarget.substring(5)));
                    }
                    conversationList.repaint();
                    updateChatHeaderAndRender();
                }
            }
        });

        JScrollPane listScroll = new JScrollPane(conversationList);
        listScroll.setBorder(null);
        sidebar.add(listScroll, BorderLayout.CENTER);

        // --- KHUNG CHAT BÊN PHẢI ---
        JPanel chatArea = new JPanel(new BorderLayout());
        chatArea.setBackground(Color.WHITE);

        JPanel northWrapper = new JPanel();
        northWrapper.setLayout(new BoxLayout(northWrapper, BoxLayout.Y_AXIS));
        northWrapper.setBackground(Color.WHITE);

        JPanel chatHeader = new JPanel(new BorderLayout(10, 0));
        chatHeader.setBackground(Color.WHITE);
        chatHeader.setBorder(BorderFactory.createCompoundBorder(
                new MatteBorder(0, 0, 1, 0, new Color(225, 228, 232)),
                new EmptyBorder(10, 15, 10, 15)));

        JPanel titleLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleLeft.setOpaque(false);
        chatAvatarLabel = new JLabel();
        JPanel titleTexts = new JPanel(new GridLayout(2, 1));
        titleTexts.setOpaque(false);
        chatTitleLabel = new JLabel("CHUNG SERVER");
        chatTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        chatSubtitleLabel = new JLabel("Trò chuyện toàn hệ thống");
        chatSubtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        chatSubtitleLabel.setForeground(Color.GRAY);
        titleTexts.add(chatTitleLabel);
        titleTexts.add(chatSubtitleLabel);
        titleLeft.add(chatAvatarLabel);
        titleLeft.add(titleTexts);

        JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        headerRight.setOpaque(false);
        msgSearchField = new JTextField(13);
        msgSearchField.setBorder(BorderFactory.createTitledBorder("🔍 Tìm tin nhắn"));
        msgSearchField.getDocument().addDocumentListener(new SimpleDocListener(this::renderChatMessages));

        groupInfoBtn = new JButton("ⓘ Thông tin nhóm");
        groupInfoBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        groupInfoBtn.setBackground(new Color(230, 240, 255));
        groupInfoBtn.setForeground(ZALO_BLUE);
        groupInfoBtn.setOpaque(true);
        groupInfoBtn.setBorderPainted(false);
        groupInfoBtn.setVisible(false);
        groupInfoBtn.addActionListener(e -> {
            if (activeTarget.startsWith("GROUP:")) {
                ClientGroupInfo g = groupsMap.get(activeTarget.substring(6));
                if (g != null) {
                    GroupDialog.showGroupInfoDialog(mainFrame, socket, g, usersMap, myUsername, myAvatarBase64);
                }
            }
        });

        headerRight.add(msgSearchField);
        headerRight.add(groupInfoBtn);

        chatHeader.add(titleLeft, BorderLayout.WEST);
        chatHeader.add(headerRight, BorderLayout.EAST);

        // Thanh cảnh báo Người lạ
        strangerBanner = new JPanel(new BorderLayout(10, 0));
        strangerBanner.setBackground(new Color(255, 248, 225));
        strangerBanner.setBorder(new EmptyBorder(6, 15, 6, 15));
        strangerTextLabel = new JLabel("⚠️ Người lạ: Hai bạn chưa kết bạn trên hệ thống.");
        strangerTextLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        strangerTextLabel.setForeground(new Color(180, 100, 0));
        friendActionBtn = new JButton("+ Kết bạn ngay");
        friendActionBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        friendActionBtn.addActionListener(e -> handleFriendAction());
        strangerBanner.add(strangerTextLabel, BorderLayout.WEST);
        strangerBanner.add(friendActionBtn, BorderLayout.EAST);
        strangerBanner.setVisible(false);

        // Thanh Ghim thông báo
        pinBanner = new JPanel(new BorderLayout(10, 0));
        pinBanner.setBackground(new Color(235, 245, 255));
        pinBanner.setBorder(new EmptyBorder(6, 15, 6, 15));
        pinTextLabel = new JLabel("📌 Tin nhắn ghim: ");
        pinTextLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        pinTextLabel.setForeground(ZALO_BLUE);
        pinBanner.add(pinTextLabel, BorderLayout.CENTER);
        pinBanner.setVisible(false);

        northWrapper.add(chatHeader);
        northWrapper.add(strangerBanner);
        northWrapper.add(pinBanner);
        chatArea.add(northWrapper, BorderLayout.NORTH);

        // Khung chứa danh sách bong bóng tin nhắn (Co giãn vừa khít từng tin nhắn)
        messagesContainer = new JPanel();
        messagesContainer.setLayout(new BoxLayout(messagesContainer, BoxLayout.Y_AXIS));
        messagesContainer.setBackground(BG_LIGHT);

        JPanel scrollContent = new JPanel(new BorderLayout());
        scrollContent.setBackground(BG_LIGHT);
        scrollContent.setBorder(new EmptyBorder(10, 12, 10, 12));
        scrollContent.add(messagesContainer, BorderLayout.NORTH);

        chatScroll = new JScrollPane(scrollContent);
        chatScroll.setBorder(null);
        chatScroll.getVerticalScrollBar().setUnitIncrement(16);
        chatArea.add(chatScroll, BorderLayout.CENTER);

        // Khu vực nhập tin nhắn phía dưới (Đã lược bỏ các nút thừa vì đã chuyển vào
        // Chuột phải)
        JPanel southWrapper = new JPanel(new BorderLayout());
        southWrapper.setBackground(Color.WHITE);
        southWrapper.setBorder(new MatteBorder(1, 0, 0, 0, new Color(220, 225, 232)));

        typingLabel = new JLabel(" ");
        typingLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        typingLabel.setForeground(ZALO_BLUE);
        typingLabel.setBorder(new EmptyBorder(4, 15, 2, 15));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        toolbar.setBackground(Color.WHITE);

        JButton emojiBtn = new JButton("😊 Emoji & Câu chúc");
        emojiBtn.addActionListener(e -> showEmojiMenu(emojiBtn));

        JButton imgBtn = new JButton("🖼️ Gửi Ảnh");
        imgBtn.addActionListener(e -> sendImageAttachment());

        JButton fileBtn = new JButton("📎 Gửi File");
        fileBtn.addActionListener(e -> sendFileAttachment());

        // JLabel hintLabel = new JLabel("💡 Mẹo: Click chuột phải vào tin nhắn để Copy,
        // Ghim, Tải file hoặc Thu hồi");
        // hintLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        // hintLabel.setForeground(Color.GRAY);

        // toolbar.add(emojiBtn);
        // toolbar.add(imgBtn);
        // toolbar.add(fileBtn);
        // toolbar.add(hintLabel);

        JPanel inputRow = new JPanel(new BorderLayout(8, 0));
        inputRow.setBackground(Color.WHITE);
        inputRow.setBorder(new EmptyBorder(6, 12, 10, 12));

        messageInput = new JTextField();
        messageInput.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        messageInput.addActionListener(e -> sendTextMessage());
        messageInput.getDocument().addDocumentListener(new SimpleDocListener(this::onUserTyping));

        JButton sendBtn = new JButton("GỬI TIN NHẮN");
        sendBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        sendBtn.setBackground(ZALO_BLUE);
        sendBtn.setForeground(Color.WHITE);
        sendBtn.setOpaque(true);
        sendBtn.setBorderPainted(false);
        sendBtn.setFocusPainted(false);
        sendBtn.setPreferredSize(new Dimension(130, 38));
        sendBtn.addActionListener(e -> sendTextMessage());

        inputRow.add(messageInput, BorderLayout.CENTER);
        inputRow.add(sendBtn, BorderLayout.EAST);

        JPanel upperSouth = new JPanel(new BorderLayout());
        upperSouth.setOpaque(false);
        upperSouth.add(typingLabel, BorderLayout.NORTH);
        upperSouth.add(toolbar, BorderLayout.SOUTH);

        southWrapper.add(upperSouth, BorderLayout.NORTH);
        southWrapper.add(inputRow, BorderLayout.SOUTH);
        chatArea.add(southWrapper, BorderLayout.SOUTH);

        body.add(sidebar, BorderLayout.WEST);
        body.add(chatArea, BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);
    }

    private JButton createFilterButton(String label, String filterCode) {
        JButton b = new JButton(label);
        b.setFont(new Font("Segoe UI", Font.BOLD, 11));
        b.setFocusPainted(false);
        b.setMargin(new Insets(4, 2, 4, 2));
        b.addActionListener(e -> {
            activeFilter = filterCode;
            refreshConversationList();
        });
        return b;
    }

    // ==================== 3. HIỆU ỨNG "AI ĐANG NHẬP..." ====================
    private void initTypingTimer() {
        typingStopTimer = new Timer(2200, e -> stopTypingStatus());
        typingStopTimer.setRepeats(false);
    }

    private void onUserTyping() {
        String text = messageInput.getText();
        if (!text.isEmpty()) {
            if (!isCurrentlyTyping) {
                isCurrentlyTyping = true;
                socket.send(Protocol.pack(Protocol.CMD_TYPING, activeTarget, "START"));
            }
            typingStopTimer.restart();
        } else {
            stopTypingStatus();
        }
    }

    private void stopTypingStatus() {
        if (isCurrentlyTyping) {
            isCurrentlyTyping = false;
            typingStopTimer.stop();
            socket.send(Protocol.pack(Protocol.CMD_TYPING, activeTarget, "STOP"));
        }
    }

    // ==================== 4. NHẬN & XỬ LÝ DỮ LIỆU TỪ SERVER ====================
    private void listenFromServer() {
        while (socket.isConnected()) {
            String line = socket.readLine();
            if (line == null)
                break;
            SwingUtilities.invokeLater(() -> processServerPacket(line));
        }
    }

    private void processServerPacket(String line) {
        String[] p = Protocol.unpack(line);
        if (p.length == 0)
            return;
        String type = p[0];

        switch (type) {
            case Protocol.RESP_SYNC_USERS: {
                usersMap.clear();
                if (p.length >= 2 && !p[1].isEmpty()) {
                    for (String token : p[1].split(";")) {
                        String[] f = token.split("~", -1);
                        if (f.length >= 4) {
                            ClientUserInfo u = new ClientUserInfo();
                            u.username = f[0];
                            u.online = "ONLINE".equals(f[1]);
                            u.relation = f[2];
                            u.avatarBase64 = f[3];
                            usersMap.put(u.username, u);
                        }
                    }
                }
                refreshConversationList();
                updateChatHeaderAndRender();
                break;
            }
            case Protocol.RESP_SYNC_GROUPS: {
                groupsMap.clear();
                if (p.length >= 2 && !p[1].isEmpty()) {
                    for (String token : p[1].split(";")) {
                        String[] f = token.split("~", -1);
                        if (f.length >= 5) {
                            ClientGroupInfo g = new ClientGroupInfo();
                            g.groupName = f[0];
                            g.admin = f[1];
                            g.pinnedMessage = f[2];
                            g.members = new ArrayList<>(Arrays.asList(f[3].split(",")));
                            g.avatarBase64 = f[4];
                            groupsMap.put(g.groupName, g);
                        }
                    }
                }
                if (activeTarget.startsWith("GROUP:") && !groupsMap.containsKey(activeTarget.substring(6))) {
                    activeTarget = "SERVER";
                }
                refreshConversationList();
                updateChatHeaderAndRender();
                break;
            }
            case Protocol.RESP_RECEIVE_MSG: {
                if (p.length >= 10) {
                    String targetKey = p[1];
                    String msgId = p[2];
                    String sender = p[3];
                    String msgType = p[4];
                    String content = p[5];
                    String fileName = p[6];
                    String fileBase64 = p[7];
                    long ts = Long.parseLong(p[8]);
                    String seenStatus = p[9];
                    boolean recalled = content.startsWith("🚫");

                    ChatMemory.ChatMessage msg = new ChatMemory.ChatMessage(
                            msgId, sender, msgType, content, fileName, fileBase64, ts, recalled, seenStatus);
                    boolean isNew = memory.addOrUpdateMessage(targetKey, msg);

                    if (targetKey.equals(activeTarget)) {
                        if (activeTarget.startsWith("USER:") && !sender.equals(myUsername)) {
                            socket.send(Protocol.pack(Protocol.CMD_MARK_SEEN, activeTarget.substring(5)));
                        }
                        renderChatMessages();
                    } else if (isNew && !sender.equals(myUsername)) {
                        memory.incrementUnread(targetKey);
                    }
                    conversationList.repaint();
                }
                break;
            }
            case Protocol.RESP_MSG_RECALLED: {
                if (p.length >= 3) {
                    memory.markRecalled(p[1], p[2]);
                    if (p[1].equals(activeTarget))
                        renderChatMessages();
                    conversationList.repaint();
                }
                break;
            }
            case Protocol.RESP_GROUP_PINNED: {
                if (p.length >= 3) {
                    ClientGroupInfo g = groupsMap.get(p[1]);
                    if (g != null)
                        g.pinnedMessage = p[2];
                    if (activeTarget.equals("GROUP:" + p[1]))
                        updateChatHeaderAndRender();
                }
                break;
            }
            case Protocol.RESP_TYPING_NOTIFY: {
                if (p.length >= 4) {
                    String key = p[1];
                    String typer = p[2];
                    String status = p[3];
                    Set<String> set = typingUsersMap.computeIfAbsent(key, k -> new LinkedHashSet<>());
                    if ("START".equals(status))
                        set.add(typer);
                    else
                        set.remove(typer);
                    if (key.equals(activeTarget))
                        updateTypingLabel();
                }
                break;
            }
            case Protocol.RESP_SEEN_NOTIFY: {
                if (p.length >= 2) {
                    String partner = p[1];
                    memory.markConversationSeenByPartner(partner);
                    if (activeTarget.equals("USER:" + partner))
                        renderChatMessages();
                }
                break;
            }
            case Protocol.RESP_ALERT: {
                if (p.length >= 2) {
                    JOptionPane.showMessageDialog(this, p[1], "Thông báo Mini Chat", JOptionPane.INFORMATION_MESSAGE);
                }
                break;
            }
        }
    }

    // ==================== 5. HIỂN THỊ BONG BÓNG CHAT GỌN ĐẸP & MENU CHUỘT PHẢI
    // ====================
    private void refreshConversationList() {
        String query = AvatarUtil.removeAccents(sidebarSearchField.getText().trim());
        String prev = activeTarget;
        conversationListModel.clear();

        if ("ALL".equals(activeFilter) || "SERVER".equals(activeFilter)) {
            if (query.isEmpty() || "chung server".contains(query)) {
                conversationListModel.addElement("SERVER");
            }
        }
        if ("ALL".equals(activeFilter) || "GROUP".equals(activeFilter)) {
            for (String gName : groupsMap.keySet()) {
                if (query.isEmpty() || AvatarUtil.removeAccents(gName).contains(query)) {
                    conversationListModel.addElement("GROUP:" + gName);
                }
            }
        }
        if ("ALL".equals(activeFilter) || "PRIVATE".equals(activeFilter)) {
            for (String uName : usersMap.keySet()) {
                if (query.isEmpty() || AvatarUtil.removeAccents(uName).contains(query)) {
                    conversationListModel.addElement("USER:" + uName);
                }
            }
        }

        if (conversationListModel.contains(prev)) {
            conversationList.setSelectedValue(prev, false);
        } else if (!conversationListModel.isEmpty()) {
            conversationList.setSelectedIndex(0);
        }
    }

    private void updateChatHeaderAndRender() {
        strangerBanner.setVisible(false);
        pinBanner.setVisible(false);
        groupInfoBtn.setVisible(false);

        if ("SERVER".equals(activeTarget)) {
            chatAvatarLabel.setIcon(AvatarUtil.createCircleAvatar("", "S", 42, false, true));
            chatTitleLabel.setText("🌐 CHUNG SERVER");
            chatSubtitleLabel.setText("Tất cả các máy đang kết nối trên Server");
            String pinned = localPinnedMap.get("SERVER");
            if (pinned != null && !pinned.isEmpty()) {
                pinTextLabel.setText("📌 Tin nhắn ghim: " + pinned);
                pinBanner.setVisible(true);
            }
        } else if (activeTarget.startsWith("GROUP:")) {
            String gName = activeTarget.substring(6);
            ClientGroupInfo g = groupsMap.get(gName);
            if (g != null) {
                chatAvatarLabel.setIcon(AvatarUtil.createCircleAvatar(g.avatarBase64, gName, 42, false, true));
                chatTitleLabel.setText("👥 Nhóm: " + gName);
                chatSubtitleLabel.setText("👑 Nhóm trưởng: " + g.admin + "  •  " + g.members.size() + " thành viên");
                groupInfoBtn.setVisible(true);
                if (g.pinnedMessage != null && !g.pinnedMessage.isEmpty()) {
                    pinTextLabel.setText("📌 Tin nhắn ghim: " + g.pinnedMessage);
                    pinBanner.setVisible(true);
                }
            }
        } else if (activeTarget.startsWith("USER:")) {
            String partner = activeTarget.substring(5);
            ClientUserInfo u = usersMap.get(partner);
            boolean online = u != null && u.online;
            String rel = u != null ? u.relation : "STRANGER";
            String av = u != null ? u.avatarBase64 : "";

            chatAvatarLabel.setIcon(AvatarUtil.createCircleAvatar(av, partner, 42, true, online));
            chatTitleLabel.setText(partner + (online ? " (🟢 Đang Online)" : ""));
            chatSubtitleLabel.setText("FRIEND".equals(rel) ? "Bạn bè trên Mini Chat" : "Trò chuyện với Người lạ");

            if (!"FRIEND".equals(rel)) {
                strangerBanner.setVisible(true);
                if ("REQ_IN".equals(rel)) {
                    strangerTextLabel.setText("👋 Người lạ: " + partner + " đã gửi lời mời kết bạn cho bạn!");
                    friendActionBtn.setText("✓ Chấp nhận kết bạn");
                    friendActionBtn.setEnabled(true);
                } else if ("REQ_OUT".equals(rel)) {
                    strangerTextLabel
                            .setText("⚠️ Người lạ: Bạn đã gửi lời mời kết bạn, đang chờ " + partner + " đồng ý.");
                    friendActionBtn.setText("Đã gửi lời mời");
                    friendActionBtn.setEnabled(false);
                } else {
                    strangerTextLabel.setText("⚠️ Người lạ: Hai bạn chưa kết bạn (vẫn có thể nhắn tin riêng).");
                    friendActionBtn.setText("+ Kết bạn ngay");
                    friendActionBtn.setEnabled(true);
                }
            }
            String pinned = localPinnedMap.get(activeTarget);
            if (pinned != null && !pinned.isEmpty()) {
                pinTextLabel.setText("📌 Tin nhắn ghim: " + pinned);
                pinBanner.setVisible(true);
            }
        }

        updateTypingLabel();
        renderChatMessages();
    }

    private void updateTypingLabel() {
        Set<String> typers = typingUsersMap.get(activeTarget);
        if (typers == null || typers.isEmpty()) {
            typingLabel.setText(" ");
        } else {
            typingLabel.setText("✍️ " + String.join(", ", typers) + " đang nhập tin nhắn...");
        }
    }

    private void renderChatMessages() {
        messagesContainer.removeAll();
        List<ChatMemory.ChatMessage> msgs = memory.getMessages(activeTarget);
        String keyword = AvatarUtil.removeAccents(msgSearchField.getText().trim());
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");

        for (ChatMemory.ChatMessage m : msgs) {
            if (!keyword.isEmpty() && !AvatarUtil.removeAccents(m.content).contains(keyword)
                    && !AvatarUtil.removeAccents(m.sender).contains(keyword)) {
                continue;
            }

            String time = sdf.format(new Date(m.timestamp));

            // Tin nhắn thông báo hệ thống căn giữa
            if ("SYSTEM".equals(m.msgType)) {
                JPanel sysRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
                sysRow.setOpaque(false);
                JLabel sysLbl = new JLabel(m.content + " (" + time + ")");
                sysLbl.setFont(new Font("Segoe UI", Font.ITALIC, 11));
                sysLbl.setForeground(new Color(90, 95, 105));
                sysLbl.setOpaque(true);
                sysLbl.setBackground(new Color(226, 230, 234));
                sysLbl.setBorder(new EmptyBorder(4, 12, 4, 12));
                sysRow.add(sysLbl);
                messagesContainer.add(sysRow);
                continue;
            }

            boolean isMine = m.sender.equals(myUsername);
            JPanel row = new JPanel(new FlowLayout(isMine ? FlowLayout.RIGHT : FlowLayout.LEFT, 6, 4));
            row.setOpaque(false);

            // Khung bong bóng chat tự co giãn vừa đúng độ dài nội dung
            JPanel bubble = new JPanel();
            bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
            Color bubbleBg = m.recalled ? new Color(240, 240, 240)
                    : (isMine ? new Color(229, 239, 255) : Color.WHITE);
            Color borderCol = m.recalled ? new Color(210, 210, 210)
                    : (isMine ? new Color(184, 212, 255) : new Color(220, 224, 230));
            bubble.setBackground(bubbleBg);
            bubble.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(borderCol, 1, true),
                    new EmptyBorder(8, 12, 8, 12)));

            // 1. Dòng tiêu đề: Icon đại diện hình tròn kế bên Tên người gửi
            JPanel senderHeader = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            senderHeader.setOpaque(false);
            senderHeader.setAlignmentX(Component.LEFT_ALIGNMENT);

            String senderAvatarB64 = isMine ? myAvatarBase64
                    : (usersMap.containsKey(m.sender) ? usersMap.get(m.sender).avatarBase64 : "");
            ImageIcon smallAvatar = AvatarUtil.createCircleAvatar(senderAvatarB64, m.sender, 24, false, false);
            JLabel avatarIconLbl = new JLabel(smallAvatar);

            JLabel nameLbl = new JLabel(m.sender);
            nameLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
            nameLbl.setForeground(isMine ? new Color(0, 80, 200) : ZALO_BLUE);

            senderHeader.add(avatarIconLbl);
            senderHeader.add(nameLbl);
            bubble.add(senderHeader);
            bubble.add(Box.createVerticalStrut(4));

            // 2. Nội dung tin nhắn (Chữ, Ảnh hoặc Tập tin)
            if (m.recalled) {
                JLabel recalledLbl = new JLabel("🚫 Tin nhắn đã được thu hồi");
                recalledLbl.setFont(new Font("Segoe UI", Font.ITALIC, 13));
                recalledLbl.setForeground(Color.GRAY);
                recalledLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
                bubble.add(recalledLbl);
            } else if ("IMAGE".equals(m.msgType)) {
                BufferedImage img = AvatarUtil.decodeImage(m.fileBase64);
                if (img != null) {
                    JLabel imgLbl = new JLabel(new ImageIcon(img));
                    imgLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
                    bubble.add(imgLbl);
                }
                JLabel capLbl = new JLabel("🖼️ " + m.fileName);
                capLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                capLbl.setForeground(Color.DARK_GRAY);
                capLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
                bubble.add(Box.createVerticalStrut(3));
                bubble.add(capLbl);
            } else if ("FILE".equals(m.msgType)) {
                JPanel fileBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
                fileBox.setBackground(new Color(246, 248, 252));
                fileBox.setBorder(BorderFactory.createLineBorder(new Color(180, 205, 255), 1));
                fileBox.setAlignmentX(Component.LEFT_ALIGNMENT);
                JLabel fileLbl = new JLabel("📎 Tập tin: " + m.fileName);
                fileLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
                fileLbl.setForeground(new Color(20, 40, 80));
                fileBox.add(fileLbl);
                bubble.add(fileBox);
            } else {
                JLabel contentLbl = new JLabel();
                contentLbl.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                contentLbl.setForeground(new Color(20, 20, 20));
                contentLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
                if (m.content.length() > 55) {
                    contentLbl.setText("<html><div style='width:280px;'>" + escapeHtml(m.content) + "</div></html>");
                } else {
                    contentLbl.setText(m.content);
                }
                bubble.add(contentLbl);
            }

            // 3. Dòng thời gian & trạng thái xem (Đã ẩn hoàn toàn Mã #...)
            bubble.add(Box.createVerticalStrut(4));
            String footerText = time;
            if (isMine && activeTarget.startsWith("USER:")) {
                footerText += ("SEEN".equals(m.seenStatus) ? "  •  ✓✓ Đã xem" : "  •  ✓ Đã gửi");
            }
            JLabel timeLbl = new JLabel(footerText);
            timeLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            timeLbl.setForeground(new Color(120, 125, 135));
            timeLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            bubble.add(timeLbl);

            // Gắn sự kiện Click chuột phải cho bong bóng tin nhắn và các thành phần bên
            // trong
            MouseAdapter popupListener = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e) || e.isPopupTrigger()) {
                        showMessageContextMenu(e.getComponent(), e.getX(), e.getY(), m);
                    }
                }
            };
            attachPopupListenerRecursively(bubble, popupListener);

            row.add(bubble);
            messagesContainer.add(row);
        }

        messagesContainer.revalidate();
        messagesContainer.repaint();
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = chatScroll.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    private void attachPopupListenerRecursively(Component comp, MouseAdapter listener) {
        comp.addMouseListener(listener);
        if (comp instanceof Container) {
            for (Component child : ((Container) comp).getComponents()) {
                attachPopupListenerRecursively(child, listener);
            }
        }
    }

    // Menu chuột phải trên từng tin nhắn: Copy, Ghim (ai cũng ghim được), Tải
    // file/ảnh, Thu hồi (chỉ người gửi)
    private void showMessageContextMenu(Component invoker, int x, int y, ChatMemory.ChatMessage m) {
        if (m.recalled)
            return;
        JPopupMenu menu = new JPopupMenu();

        // 1. Copy tin nhắn
        JMenuItem copyItem = new JMenuItem("📋 Copy tin nhắn");
        copyItem.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        copyItem.addActionListener(e -> {
            String textToCopy = ("FILE".equals(m.msgType) || "IMAGE".equals(m.msgType)) ? m.fileName : m.content;
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(textToCopy), null);
        });
        menu.add(copyItem);

        // 2. Ghim tin nhắn (Ai cũng ghim được)
        JMenuItem pinItem = new JMenuItem("📌 Ghim tin nhắn này");
        pinItem.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        pinItem.addActionListener(e -> {
            String pinContent = m.sender + ": "
                    + (("FILE".equals(m.msgType) || "IMAGE".equals(m.msgType)) ? m.fileName : m.content);
            if (activeTarget.startsWith("GROUP:")) {
                socket.send(Protocol.pack(Protocol.CMD_PIN_MSG, activeTarget.substring(6), pinContent));
            } else {
                localPinnedMap.put(activeTarget, pinContent);
                updateChatHeaderAndRender();
            }
        });
        menu.add(pinItem);

        // 3. Tải ảnh hoặc file về máy (Khi click chuột phải vào ảnh/file)
        if (("IMAGE".equals(m.msgType) || "FILE".equals(m.msgType)) && !m.fileBase64.isEmpty()) {
            JMenuItem downloadItem = new JMenuItem(
                    "💾 Tải " + ("IMAGE".equals(m.msgType) ? "ảnh" : "file") + " về máy (" + m.fileName + ")");
            downloadItem.setFont(new Font("Segoe UI", Font.BOLD, 13));
            downloadItem.setForeground(ZALO_BLUE);
            downloadItem.addActionListener(e -> {
                JFileChooser saver = new JFileChooser();
                saver.setSelectedFile(new File(m.fileName));
                if (saver.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                    boolean ok = AvatarUtil.base64ToFile(m.fileBase64, saver.getSelectedFile());
                    JOptionPane.showMessageDialog(this, ok ? "Đã tải về máy thành công!" : "Lỗi khi lưu file!");
                }
            });
            menu.add(downloadItem);
        }

        // 4. Thu hồi tin nhắn (Chỉ hiển thị cho người gửi tin nhắn đó)
        if (m.sender.equals(myUsername)) {
            menu.addSeparator();
            JMenuItem recallItem = new JMenuItem("🚫 Thu hồi tin nhắn");
            recallItem.setFont(new Font("Segoe UI", Font.BOLD, 13));
            recallItem.setForeground(new Color(220, 53, 69));
            recallItem
                    .addActionListener(e -> socket.send(Protocol.pack(Protocol.CMD_RECALL_MSG, activeTarget, m.msgId)));
            menu.add(recallItem);
        }

        menu.show(invoker, x, y);
    }

    // ==================== 6. GỬI TIN NHẮN, ẢNH, FILE & KẾT BẠN
    // ====================
    private void sendTextMessage() {
        String text = messageInput.getText().trim();
        if (text.isEmpty())
            return;
        stopTypingStatus();
        socket.send(Protocol.pack(Protocol.CMD_SEND_MSG, activeTarget, "TEXT", text, "", ""));
        messageInput.setText("");
    }

    private void sendImageAttachment() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Hình ảnh (JPG, PNG)", "jpg", "jpeg", "png"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            String b64 = AvatarUtil.encodeImageFile(f, 280);
            if (!b64.isEmpty()) {
                socket.send(Protocol.pack(Protocol.CMD_SEND_MSG, activeTarget, "IMAGE", "[Hình ảnh] " + f.getName(),
                        f.getName(), b64));
            }
        }
    }

    private void sendFileAttachment() {
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            if (f.length() > 5 * 1024 * 1024) {
                JOptionPane.showMessageDialog(this, "Vui lòng chọn file nhỏ hơn 5MB!");
                return;
            }
            String b64 = AvatarUtil.fileToBase64(f);
            if (!b64.isEmpty()) {
                socket.send(Protocol.pack(Protocol.CMD_SEND_MSG, activeTarget, "FILE", "[Tập tin] " + f.getName(),
                        f.getName(), b64));
            }
        }
    }

    private void showEmojiMenu(Component invoker) {
        JPopupMenu menu = new JPopupMenu();
        String[] items = {
                "😄", "❤️", "👍", "🎉", "🔥", "😂", "😭", "🙏", "💯",
                "👋 Xin chào mọi người nhé!",
                "👍 Đã rõ, cảm ơn bạn!",
                "🎉 Chúc mừng nhé!",
                "⏰ Tối nay mọi người có online không ạ?"
        };
        for (String item : items) {
            JMenuItem mi = new JMenuItem(item);
            mi.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 13));
            mi.addActionListener(e -> {
                messageInput.setText(messageInput.getText() + item);
                messageInput.requestFocus();
            });
            menu.add(mi);
        }
        menu.show(invoker, 0, -menu.getPreferredSize().height);
    }

    private void handleFriendAction() {
        if (!activeTarget.startsWith("USER:"))
            return;
        String partner = activeTarget.substring(5);
        ClientUserInfo u = usersMap.get(partner);
        if (u == null)
            return;
        if ("REQ_IN".equals(u.relation)) {
            socket.send(Protocol.pack(Protocol.CMD_FRIEND_ACCEPT, partner));
        } else if ("STRANGER".equals(u.relation)) {
            socket.send(Protocol.pack(Protocol.CMD_FRIEND_REQ, partner));
        }
    }

    private void changeMyAvatar() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Hình ảnh (JPG, PNG)", "jpg", "jpeg", "png"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            String b64 = AvatarUtil.encodeImageFile(fc.getSelectedFile(), 96);
            if (!b64.isEmpty()) {
                this.myAvatarBase64 = b64;
                myAvatarLabel.setIcon(AvatarUtil.createCircleAvatar(myAvatarBase64, myUsername, 38, true, true));
                socket.send(Protocol.pack(Protocol.CMD_UPDATE_AVATAR, "USER", myUsername, b64));
                renderChatMessages();
            }
        }
    }

    private String escapeHtml(String s) {
        if (s == null)
            return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public ChatMemory getMemory() {
        return memory;
    }

    public ClientUserInfo getUserInfo(String username) {
        return usersMap.get(username);
    }

    public ClientGroupInfo getGroupInfo(String groupName) {
        return groupsMap.get(groupName);
    }

    private static class SimpleDocListener implements DocumentListener {
        private final Runnable callback;

        public SimpleDocListener(Runnable callback) {
            this.callback = callback;
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            callback.run();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            callback.run();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            callback.run();
        }
    }
}