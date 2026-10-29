package client;

import common.AvatarUtil;
import common.Protocol;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ChatMemory {
    private final String username;
    private final File historyFile;
    private final File mediaCacheDir;

    private final Map<String, List<ChatMessage>> conversations = new HashMap<>();
    private final Map<String, Integer> unreadCounts = new HashMap<>();

    public static class ChatMessage {
        public String msgId;
        public String sender;
        public String msgType; // TEXT, IMAGE, FILE, SYSTEM
        public String content;
        public String fileName;
        public String fileBase64;
        public long timestamp;
        public boolean recalled;
        public String seenStatus; // SENT, SEEN

        public ChatMessage(String msgId, String sender, String msgType, String content,
                String fileName, String fileBase64, long timestamp,
                boolean recalled, String seenStatus) {
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

    public ChatMemory(String username) {
        this.username = username;
        this.historyFile = new File("client_history_" + username + ".txt");
        this.mediaCacheDir = new File("media_cache_" + username);
        if (!mediaCacheDir.exists())
            mediaCacheDir.mkdirs();
        loadFromDisk();
    }

    private synchronized void loadFromDisk() {
        if (!historyFile.exists())
            return;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(historyFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] p = Protocol.unpack(line);
                if (p.length >= 10) {
                    String targetKey = p[0];
                    ChatMessage m = new ChatMessage(
                            p[1], p[2], p[3], p[4], p[5], p[6],
                            Long.parseLong(p[7]), Boolean.parseBoolean(p[8]), p[9]);
                    conversations.computeIfAbsent(targetKey, k -> new ArrayList<>()).add(m);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public synchronized void saveToDisk() {
        try (PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(new FileOutputStream(historyFile), StandardCharsets.UTF_8))) {
            for (Map.Entry<String, List<ChatMessage>> entry : conversations.entrySet()) {
                String key = entry.getKey();
                for (ChatMessage m : entry.getValue()) {
                    pw.println(Protocol.pack(
                            key, m.msgId, m.sender, m.msgType, m.content,
                            m.fileName, m.fileBase64, String.valueOf(m.timestamp),
                            String.valueOf(m.recalled), m.seenStatus));
                }
            }
        } catch (Exception ignored) {
        }
    }

    // Trả về true nếu là tin nhắn mới hoàn toàn (chưa có trong lịch sử)
    public synchronized boolean addOrUpdateMessage(String targetKey, ChatMessage incoming) {
        List<ChatMessage> list = conversations.computeIfAbsent(targetKey, k -> new ArrayList<>());
        for (ChatMessage existing : list) {
            if (existing.msgId.equals(incoming.msgId)) {
                existing.content = incoming.content;
                existing.recalled = incoming.recalled;
                existing.seenStatus = incoming.seenStatus;
                saveToDisk();
                return false;
            }
        }
        list.add(incoming);
        saveToDisk();
        return true;
    }

    public synchronized void markRecalled(String targetKey, String msgId) {
        List<ChatMessage> list = conversations.get(targetKey);
        if (list != null) {
            for (ChatMessage m : list) {
                if (m.msgId.equals(msgId)) {
                    m.recalled = true;
                    m.content = "🚫 Tin nhắn đã được thu hồi";
                    m.fileBase64 = "";
                    break;
                }
            }
            saveToDisk();
        }
    }

    public synchronized void markConversationSeenByPartner(String partner) {
        List<ChatMessage> list = conversations.get("USER:" + partner);
        if (list != null) {
            for (ChatMessage m : list) {
                if (m.sender.equals(username)) {
                    m.seenStatus = "SEEN";
                }
            }
            saveToDisk();
        }
    }

    public synchronized List<ChatMessage> getMessages(String targetKey) {
        return new ArrayList<>(conversations.getOrDefault(targetKey, Collections.emptyList()));
    }

    public synchronized ChatMessage getLastMessage(String targetKey) {
        List<ChatMessage> list = conversations.get(targetKey);
        if (list == null || list.isEmpty())
            return null;
        return list.get(list.size() - 1);
    }

    public void incrementUnread(String targetKey) {
        unreadCounts.put(targetKey, getUnread(targetKey) + 1);
    }

    public void clearUnread(String targetKey) {
        unreadCounts.remove(targetKey);
    }

    public int getUnread(String targetKey) {
        return unreadCounts.getOrDefault(targetKey, 0);
    }

    // Lưu ảnh tạm để hiển thị bằng thẻ <img> trong khung chat HTML
    public File getCachedImageFile(String msgId, String base64) {
        File f = new File(mediaCacheDir, "img_" + msgId + ".jpg");
        if (!f.exists() && base64 != null && !base64.isEmpty()) {
            AvatarUtil.base64ToFile(base64, f);
        }
        return f;
    }
}