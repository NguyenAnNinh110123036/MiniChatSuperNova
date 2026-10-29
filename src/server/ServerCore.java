package server;

import common.Protocol;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServerCore {
    private static final String USERS_FILE = "server_users.txt";
    private static final String GROUPS_FILE = "server_groups.txt";
    private static final String HISTORY_FILE = "server_history.txt";

    private final Map<String, UserModel> usersDb = new ConcurrentHashMap<>();
    private final Map<String, GroupModel> groupsDb = new ConcurrentHashMap<>();
    private final Map<String, ClientHandler> onlineHandlers = new ConcurrentHashMap<>();
    private final List<StoredMessage> messageHistory = new CopyOnWriteArrayList<>();

    private final ServerUI ui;

    public static class StoredMessage {
        public String scope; // SERVER, GROUP:gName, hoặc PRIVATE:u1:u2
        public String msgId;
        public String sender;
        public String msgType; // TEXT, IMAGE, FILE, SYSTEM
        public String content;
        public String fileName;
        public String fileBase64;
        public long timestamp;
        public boolean recalled;
        public String seenStatus;

        public StoredMessage(String scope, String msgId, String sender, String msgType,
                String content, String fileName, String fileBase64,
                long timestamp, boolean recalled, String seenStatus) {
            this.scope = scope;
            this.msgId = msgId;
            this.sender = sender;
            this.msgType = msgType;
            this.content = content;
            this.fileName = fileName;
            this.fileBase64 = fileBase64;
            this.timestamp = timestamp;
            this.recalled = recalled;
            this.seenStatus = seenStatus;
        }
    }

    public ServerCore(ServerUI ui) {
        this.ui = ui;
        loadUsers();
        loadGroups();
        loadHistory();
    }

    public void startServer(int port) {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                log("Server đã khởi động tại cổng " + port + ". Sẵn sàng nhận kết nối...");
                refreshUI();
                while (true) {
                    Socket socket = serverSocket.accept();
                    new Thread(new ClientHandler(socket, this)).start();
                }
            } catch (IOException e) {
                log("Lỗi khởi chạy Server: " + e.getMessage());
            }
        }).start();
    }

    public void log(String msg) {
        if (ui != null)
            ui.appendLog(msg);
    }

    public void refreshUI() {
        if (ui != null)
            ui.updateLists(usersDb.values(), groupsDb.values());
    }

    // ==================== LƯU TRỮ BỀN VỮNG TRÊN SERVER ====================
    private synchronized void loadUsers() {
        File f = new File(USERS_FILE);
        if (!f.exists())
            return;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = Protocol.unpack(line);
                if (p.length >= 5) {
                    UserModel u = new UserModel(p[0], p[1], p[2]);
                    if (!p[3].isEmpty())
                        u.getFriends().addAll(Arrays.asList(p[3].split(",")));
                    if (!p[4].isEmpty())
                        u.getIncomingRequests().addAll(Arrays.asList(p[4].split(",")));
                    usersDb.put(u.getUsername(), u);
                }
            }
        } catch (Exception e) {
            log("Lỗi đọc file user: " + e.getMessage());
        }
    }

    public synchronized void saveUsers() {
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(USERS_FILE), StandardCharsets.UTF_8))) {
            for (UserModel u : usersDb.values()) {
                pw.println(Protocol.pack(
                        u.getUsername(),
                        u.getPassword(),
                        u.getAvatarBase64(),
                        String.join(",", u.getFriends()),
                        String.join(",", u.getIncomingRequests())));
            }
        } catch (Exception e) {
            log("Lỗi lưu file user: " + e.getMessage());
        }
    }

    private synchronized void loadGroups() {
        File f = new File(GROUPS_FILE);
        if (!f.exists())
            return;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = Protocol.unpack(line);
                if (p.length >= 5) {
                    GroupModel g = new GroupModel(p[0], p[1], p[2]);
                    g.setPinnedMessage(p[3]);
                    for (String m : p[4].split(",")) {
                        if (!m.isEmpty())
                            g.getMembers().add(m);
                    }
                    groupsDb.put(g.getGroupName(), g);
                }
            }
        } catch (Exception e) {
            log("Lỗi đọc file group: " + e.getMessage());
        }
    }

    public synchronized void saveGroups() {
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(GROUPS_FILE), StandardCharsets.UTF_8))) {
            for (GroupModel g : groupsDb.values()) {
                pw.println(Protocol.pack(
                        g.getGroupName(),
                        g.getAdmin(),
                        g.getAvatarBase64(),
                        g.getPinnedMessage(),
                        String.join(",", g.getMembers())));
            }
        } catch (Exception e) {
            log("Lỗi lưu file group: " + e.getMessage());
        }
    }

    private synchronized void loadHistory() {
        File f = new File(HISTORY_FILE);
        if (!f.exists())
            return;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = Protocol.unpack(line);
                if (p.length >= 10) {
                    messageHistory.add(new StoredMessage(
                            p[0], p[1], p[2], p[3], p[4], p[5], p[6],
                            Long.parseLong(p[7]), Boolean.parseBoolean(p[8]), p[9]));
                }
            }
        } catch (Exception e) {
            log("Lỗi đọc lịch sử chat: " + e.getMessage());
        }
    }

    public synchronized void saveHistory() {
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(HISTORY_FILE), StandardCharsets.UTF_8))) {
            for (StoredMessage m : messageHistory) {
                pw.println(Protocol.pack(
                        m.scope, m.msgId, m.sender, m.msgType, m.content,
                        m.fileName, m.fileBase64, String.valueOf(m.timestamp),
                        String.valueOf(m.recalled), m.seenStatus));
            }
        } catch (Exception e) {
            log("Lỗi lưu lịch sử chat: " + e.getMessage());
        }
    }

    // ==================== ĐĂNG KÝ, ĐĂNG NHẬP & ĐỒNG BỘ ====================
    public synchronized void handleRegister(String username, String password, String avatarBase64,
            ClientHandler handler) {
        if (username.isEmpty() || password.isEmpty() || username.contains(",") || username.contains("=")) {
            handler.send(Protocol.pack(Protocol.RESP_AUTH_FAIL, "Tên tài khoản không hợp lệ!"));
            return;
        }
        if (usersDb.containsKey(username)) {
            handler.send(Protocol.pack(Protocol.RESP_AUTH_FAIL, "Tài khoản '" + username + "' đã tồn tại!"));
            return;
        }
        UserModel newUser = new UserModel(username, password, avatarBase64);
        usersDb.put(username, newUser);
        saveUsers();
        log("Đăng ký mới: " + username);
        refreshUI();
        handler.send(Protocol.pack(Protocol.RESP_REGISTER_OK, "Đăng ký thành công! Bạn có thể đăng nhập ngay."));
    }

    public synchronized void handleLogin(String username, String password, ClientHandler handler) {
        UserModel user = usersDb.get(username);
        if (user == null || !user.getPassword().equals(password)) {
            handler.send(Protocol.pack(Protocol.RESP_AUTH_FAIL, "Sai tên tài khoản hoặc mật khẩu!"));
            return;
        }
        ClientHandler oldHandler = onlineHandlers.get(username);
        if (oldHandler != null && oldHandler != handler) {
            oldHandler.send(Protocol.pack(Protocol.RESP_ALERT, "Tài khoản của bạn đã đăng nhập ở một cửa sổ khác!"));
            oldHandler.forceClose();
        }

        user.setOnline(true);
        handler.setUsername(username);
        onlineHandlers.put(username, handler);
        log("User '" + username + "' đã Online.");

        handler.send(Protocol.pack(Protocol.RESP_AUTH_OK, username, user.getAvatarBase64()));
        broadcastStateToAll();
        syncHistoryForUser(username, handler);
    }

    public synchronized void handleLogout(String username, ClientHandler handler) {
        if (username != null && onlineHandlers.get(username) == handler) {
            onlineHandlers.remove(username);
            UserModel u = usersDb.get(username);
            if (u != null)
                u.setOnline(false);
            log("User '" + username + "' đã Offline.");
            broadcastStateToAll();
        }
    }

    // Gửi danh sách User (Online/Offline, Bạn bè, Avatar) và Nhóm (chỉ nhóm mà user
    // là thành viên)
    public void broadcastStateToAll() {
        for (Map.Entry<String, ClientHandler> entry : onlineHandlers.entrySet()) {
            String targetUser = entry.getKey();
            ClientHandler handler = entry.getValue();
            UserModel me = usersDb.get(targetUser);
            if (me == null)
                continue;

            // 1. Đóng gói danh sách toàn bộ User trên Server
            List<String> userTokens = new ArrayList<>();
            for (UserModel u : usersDb.values()) {
                if (u.getUsername().equals(targetUser))
                    continue;
                boolean isFriend = me.getFriends().contains(u.getUsername());
                boolean reqFromThem = me.getIncomingRequests().contains(u.getUsername());
                boolean reqSentByMe = u.getIncomingRequests().contains(targetUser);
                String rel = isFriend ? "FRIEND" : (reqFromThem ? "REQ_IN" : (reqSentByMe ? "REQ_OUT" : "STRANGER"));
                String token = u.getUsername() + "~" + (u.isOnline() ? "ONLINE" : "OFFLINE") + "~" + rel + "~"
                        + u.getAvatarBase64();
                userTokens.add(token);
            }
            handler.send(Protocol.pack(Protocol.RESP_SYNC_USERS, String.join(";", userTokens)));

            // 2. Đóng gói danh sách Nhóm (Ẩn nhóm với người chưa tham gia)
            List<String> groupTokens = new ArrayList<>();
            for (GroupModel g : groupsDb.values()) {
                if (g.getMembers().contains(targetUser)) {
                    String membersStr = String.join(",", g.getMembers());
                    String token = g.getGroupName() + "~" + g.getAdmin() + "~" + g.getPinnedMessage() + "~" + membersStr
                            + "~" + g.getAvatarBase64();
                    groupTokens.add(token);
                }
            }
            handler.send(Protocol.pack(Protocol.RESP_SYNC_GROUPS, String.join(";", groupTokens)));
        }
        refreshUI();
    }

    // Đồng bộ lại các đoạn chat cũ khi User đăng nhập lại
    private void syncHistoryForUser(String username, ClientHandler handler) {
        for (StoredMessage m : messageHistory) {
            String contentToSend = m.recalled ? "🚫 Tin nhắn đã được thu hồi" : m.content;
            if (m.scope.equals("SERVER")) {
                handler.send(Protocol.pack(Protocol.RESP_RECEIVE_MSG, "SERVER", m.msgId, m.sender,
                        m.msgType, contentToSend, m.fileName, m.recalled ? "" : m.fileBase64,
                        String.valueOf(m.timestamp), m.seenStatus));
            } else if (m.scope.startsWith("GROUP:")) {
                String gName = m.scope.substring(6);
                GroupModel g = groupsDb.get(gName);
                if (g != null && g.getMembers().contains(username)) {
                    handler.send(Protocol.pack(Protocol.RESP_RECEIVE_MSG, "GROUP:" + gName, m.msgId, m.sender,
                            m.msgType, contentToSend, m.fileName, m.recalled ? "" : m.fileBase64,
                            String.valueOf(m.timestamp), m.seenStatus));
                }
            } else if (m.scope.startsWith("PRIVATE:")) {
                String[] parts = m.scope.split(":", 3);
                if (parts.length == 3 && (parts[1].equals(username) || parts[2].equals(username))) {
                    String partner = parts[1].equals(username) ? parts[2] : parts[1];
                    handler.send(Protocol.pack(Protocol.RESP_RECEIVE_MSG, "USER:" + partner, m.msgId, m.sender,
                            m.msgType, contentToSend, m.fileName, m.recalled ? "" : m.fileBase64,
                            String.valueOf(m.timestamp), m.seenStatus));
                }
            }
        }
    }

    // ==================== KẾT BẠN & CẬP NHẬT AVATAR ====================
    public synchronized void handleUpdateAvatar(String requester, String targetType, String targetName,
            String avatarBase64) {
        if ("USER".equals(targetType)) {
            UserModel u = usersDb.get(requester);
            if (u != null) {
                u.setAvatarBase64(avatarBase64);
                saveUsers();
                broadcastStateToAll();
            }
        } else if ("GROUP".equals(targetType)) {
            GroupModel g = groupsDb.get(targetName);
            if (g != null && g.getAdmin().equals(requester)) {
                g.setAvatarBase64(avatarBase64);
                saveGroups();
                broadcastStateToAll();
                sendSystemGroupMsg(targetName, "👑 Nhóm trưởng " + requester + " đã cập nhật ảnh đại diện nhóm.");
            }
        }
    }

    public synchronized void handleFriendRequest(String fromUser, String toUser) {
        UserModel target = usersDb.get(toUser);
        UserModel sender = usersDb.get(fromUser);
        if (target == null || sender == null)
            return;
        if (sender.getFriends().contains(toUser))
            return;

        target.getIncomingRequests().add(fromUser);
        saveUsers();
        broadcastStateToAll();

        ClientHandler targetHandler = onlineHandlers.get(toUser);
        if (targetHandler != null) {
            targetHandler
                    .send(Protocol.pack(Protocol.RESP_ALERT, "👋 " + fromUser + " vừa gửi cho bạn lời mời kết bạn!"));
        }
    }

    public synchronized void handleFriendAccept(String accepter, String requester) {
        UserModel uAccept = usersDb.get(accepter);
        UserModel uReq = usersDb.get(requester);
        if (uAccept == null || uReq == null)
            return;

        uAccept.getIncomingRequests().remove(requester);
        uReq.getIncomingRequests().remove(accepter);
        uAccept.getFriends().add(requester);
        uReq.getFriends().add(accepter);
        saveUsers();
        broadcastStateToAll();

        ClientHandler reqHandler = onlineHandlers.get(requester);
        if (reqHandler != null) {
            reqHandler.send(Protocol.pack(Protocol.RESP_ALERT, "🎉 " + accepter + " đã đồng ý kết bạn với bạn!"));
        }
    }

    // ==================== QUẢN LÝ NHÓM (>= 3 NGƯỜI, NHÓM TRƯỞNG, RỜI NHÓM)
    // ====================
    public synchronized void handleCreateGroup(String creator, String groupName, String avatarBase64,
            String membersCsv) {
        ClientHandler creatorHandler = onlineHandlers.get(creator);
        if (groupName.isEmpty() || groupName.contains("~") || groupName.contains(";")) {
            if (creatorHandler != null)
                creatorHandler.send(Protocol.pack(Protocol.RESP_ALERT, "Tên nhóm không hợp lệ!"));
            return;
        }
        if (groupsDb.containsKey(groupName)) {
            if (creatorHandler != null)
                creatorHandler.send(Protocol.pack(Protocol.RESP_ALERT, "Tên nhóm '" + groupName + "' đã tồn tại!"));
            return;
        }

        Set<String> initialMembers = new LinkedHashSet<>();
        initialMembers.add(creator);
        for (String m : membersCsv.split(",")) {
            if (!m.trim().isEmpty() && usersDb.containsKey(m.trim())) {
                initialMembers.add(m.trim());
            }
        }

        if (initialMembers.size() < 3) {
            if (creatorHandler != null) {
                creatorHandler
                        .send(Protocol.pack(Protocol.RESP_ALERT, "Nhóm chỉ được tạo khi có từ 3 thành viên trở lên!"));
            }
            return;
        }

        GroupModel group = new GroupModel(groupName, creator, avatarBase64);
        group.getMembers().addAll(initialMembers);
        groupsDb.put(groupName, group);
        saveGroups();

        log("Nhóm '" + groupName + "' được tạo bởi " + creator + " (" + initialMembers.size() + " TV).");
        broadcastStateToAll();
        sendSystemGroupMsg(groupName, "🎉 Nhóm '" + groupName + "' đã được tạo bởi Nhóm trưởng " + creator + ".");
    }

    public synchronized void handleAddMemberToGroup(String inviter, String groupName, String newMember) {
        GroupModel g = groupsDb.get(groupName);
        if (g == null || !g.getMembers().contains(inviter))
            return;
        if (!usersDb.containsKey(newMember))
            return;

        if (g.getMembers().add(newMember)) {
            saveGroups();
            broadcastStateToAll();
            sendSystemGroupMsg(groupName, "📢 " + inviter + " đã thêm " + newMember + " vào nhóm.");
        }
    }

    public synchronized void handleLeaveGroup(String username, String groupName) {
        GroupModel g = groupsDb.get(groupName);
        if (g == null || !g.getMembers().contains(username))
            return;

        String oldAdmin = g.getAdmin();
        g.removeMember(username);

        if (g.getMembers().isEmpty()) {
            groupsDb.remove(groupName);
        } else {
            String msg = "📢 " + username + " đã rời khỏi nhóm.";
            if (username.equals(oldAdmin)) {
                msg += " Quyền Nhóm trưởng được chuyển cho " + g.getAdmin() + ".";
            }
            sendSystemGroupMsg(groupName, msg);
        }
        saveGroups();
        broadcastStateToAll();
    }

    public synchronized void handleKickMember(String adminUser, String groupName, String targetUser) {
        GroupModel g = groupsDb.get(groupName);
        if (g == null || !g.getAdmin().equals(adminUser) || adminUser.equals(targetUser))
            return;

        if (g.getMembers().remove(targetUser)) {
            saveGroups();
            broadcastStateToAll();
            sendSystemGroupMsg(groupName, "⚠️ Nhóm trưởng " + adminUser + " đã mời " + targetUser + " ra khỏi nhóm.");
            ClientHandler kicked = onlineHandlers.get(targetUser);
            if (kicked != null) {
                kicked.send(Protocol.pack(Protocol.RESP_ALERT,
                        "Bạn đã bị Nhóm trưởng đưa ra khỏi nhóm '" + groupName + "'."));
            }
        }
    }

    // ==================== GỬI TIN NHẮN, HÌNH ẢNH, FILE, THU HỒI & GHIM
    // ====================
    public synchronized void handleSendMessage(String sender, String targetKey, String msgType,
            String content, String fileName, String fileBase64) {
        String msgId = UUID.randomUUID().toString().substring(0, 8);
        long ts = System.currentTimeMillis();

        if (targetKey.equals("SERVER")) {
            StoredMessage sm = new StoredMessage("SERVER", msgId, sender, msgType, content, fileName, fileBase64, ts,
                    false, "SENT");
            messageHistory.add(sm);
            saveHistory();

            for (ClientHandler ch : onlineHandlers.values()) {
                ch.send(Protocol.pack(Protocol.RESP_RECEIVE_MSG, "SERVER", msgId, sender,
                        msgType, content, fileName, fileBase64, String.valueOf(ts), "SENT"));
            }
        } else if (targetKey.startsWith("GROUP:")) {
            String gName = targetKey.substring(6);
            GroupModel g = groupsDb.get(gName);
            if (g == null || !g.getMembers().contains(sender))
                return;

            StoredMessage sm = new StoredMessage("GROUP:" + gName, msgId, sender, msgType, content, fileName,
                    fileBase64, ts, false, "SENT");
            messageHistory.add(sm);
            saveHistory();

            for (String member : g.getMembers()) {
                ClientHandler ch = onlineHandlers.get(member);
                if (ch != null) {
                    ch.send(Protocol.pack(Protocol.RESP_RECEIVE_MSG, "GROUP:" + gName, msgId, sender,
                            msgType, content, fileName, fileBase64, String.valueOf(ts), "SENT"));
                }
            }
        } else if (targetKey.startsWith("USER:")) {
            String receiver = targetKey.substring(5);
            String scope = canonicalPrivateScope(sender, receiver);

            StoredMessage sm = new StoredMessage(scope, msgId, sender, msgType, content, fileName, fileBase64, ts,
                    false, "SENT");
            messageHistory.add(sm);
            saveHistory();

            ClientHandler senderHandler = onlineHandlers.get(sender);
            if (senderHandler != null) {
                senderHandler.send(Protocol.pack(Protocol.RESP_RECEIVE_MSG, "USER:" + receiver, msgId, sender,
                        msgType, content, fileName, fileBase64, String.valueOf(ts), "SENT"));
            }
            ClientHandler receiverHandler = onlineHandlers.get(receiver);
            if (receiverHandler != null) {
                receiverHandler.send(Protocol.pack(Protocol.RESP_RECEIVE_MSG, "USER:" + sender, msgId, sender,
                        msgType, content, fileName, fileBase64, String.valueOf(ts), "SENT"));
            }
        }
    }

    private void sendSystemGroupMsg(String groupName, String text) {
        String msgId = UUID.randomUUID().toString().substring(0, 8);
        long ts = System.currentTimeMillis();
        StoredMessage sm = new StoredMessage("GROUP:" + groupName, msgId, "Hệ thống", "SYSTEM", text, "", "", ts, false,
                "SENT");
        messageHistory.add(sm);
        saveHistory();

        GroupModel g = groupsDb.get(groupName);
        if (g == null)
            return;
        for (String member : g.getMembers()) {
            ClientHandler ch = onlineHandlers.get(member);
            if (ch != null) {
                ch.send(Protocol.pack(Protocol.RESP_RECEIVE_MSG, "GROUP:" + groupName, msgId, "Hệ thống",
                        "SYSTEM", text, "", "", String.valueOf(ts), "SENT"));
            }
        }
    }

    public synchronized void handleRecallMessage(String requester, String targetKey, String msgId) {
        for (StoredMessage m : messageHistory) {
            if (m.msgId.equals(msgId) && m.sender.equals(requester)) {
                m.recalled = true;
                m.content = "🚫 Tin nhắn đã được thu hồi";
                m.fileBase64 = "";
                saveHistory();

                if (targetKey.equals("SERVER")) {
                    for (ClientHandler ch : onlineHandlers.values()) {
                        ch.send(Protocol.pack(Protocol.RESP_MSG_RECALLED, "SERVER", msgId));
                    }
                } else if (targetKey.startsWith("GROUP:")) {
                    String gName = targetKey.substring(6);
                    GroupModel g = groupsDb.get(gName);
                    if (g != null) {
                        for (String mem : g.getMembers()) {
                            ClientHandler ch = onlineHandlers.get(mem);
                            if (ch != null)
                                ch.send(Protocol.pack(Protocol.RESP_MSG_RECALLED, "GROUP:" + gName, msgId));
                        }
                    }
                } else if (targetKey.startsWith("USER:")) {
                    String partner = targetKey.substring(5);
                    ClientHandler ch1 = onlineHandlers.get(requester);
                    ClientHandler ch2 = onlineHandlers.get(partner);
                    if (ch1 != null)
                        ch1.send(Protocol.pack(Protocol.RESP_MSG_RECALLED, "USER:" + partner, msgId));
                    if (ch2 != null)
                        ch2.send(Protocol.pack(Protocol.RESP_MSG_RECALLED, "USER:" + requester, msgId));
                }
                break;
            }
        }
    }

    public synchronized void handlePinMessage(String requester, String groupName, String pinnedText) {
        GroupModel g = groupsDb.get(groupName);
        if (g == null || !g.getMembers().contains(requester))
            return;

        g.setPinnedMessage(pinnedText);
        saveGroups();
        broadcastStateToAll();

        for (String mem : g.getMembers()) {
            ClientHandler ch = onlineHandlers.get(mem);
            if (ch != null) {
                ch.send(Protocol.pack(Protocol.RESP_GROUP_PINNED, groupName, pinnedText));
            }
        }
        sendSystemGroupMsg(groupName, "📌 " + requester + " đã ghim một thông báo: \"" + pinnedText + "\"");
    }

    public void handleTyping(String sender, String targetKey, String status) {
        if (targetKey.equals("SERVER")) {
            for (Map.Entry<String, ClientHandler> entry : onlineHandlers.entrySet()) {
                if (!entry.getKey().equals(sender)) {
                    entry.getValue().send(Protocol.pack(Protocol.RESP_TYPING_NOTIFY, "SERVER", sender, status));
                }
            }
        } else if (targetKey.startsWith("GROUP:")) {
            String gName = targetKey.substring(6);
            GroupModel g = groupsDb.get(gName);
            if (g != null) {
                for (String mem : g.getMembers()) {
                    if (!mem.equals(sender)) {
                        ClientHandler ch = onlineHandlers.get(mem);
                        if (ch != null)
                            ch.send(Protocol.pack(Protocol.RESP_TYPING_NOTIFY, "GROUP:" + gName, sender, status));
                    }
                }
            }
        } else if (targetKey.startsWith("USER:")) {
            String partner = targetKey.substring(5);
            ClientHandler ch = onlineHandlers.get(partner);
            if (ch != null) {
                ch.send(Protocol.pack(Protocol.RESP_TYPING_NOTIFY, "USER:" + sender, sender, status));
            }
        }
    }

    public synchronized void handleMarkSeen(String reader, String partner) {
        String scope = canonicalPrivateScope(reader, partner);
        boolean changed = false;
        for (StoredMessage m : messageHistory) {
            if (m.scope.equals(scope) && m.sender.equals(partner) && !"SEEN".equals(m.seenStatus)) {
                m.seenStatus = "SEEN";
                changed = true;
            }
        }
        if (changed)
            saveHistory();

        ClientHandler partnerHandler = onlineHandlers.get(partner);
        if (partnerHandler != null) {
            partnerHandler.send(Protocol.pack(Protocol.RESP_SEEN_NOTIFY, reader));
        }
    }

    private String canonicalPrivateScope(String u1, String u2) {
        return u1.compareTo(u2) < 0 ? ("PRIVATE:" + u1 + ":" + u2) : ("PRIVATE:" + u2 + ":" + u1);
    }
}