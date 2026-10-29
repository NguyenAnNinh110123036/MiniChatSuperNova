package common;

public class Protocol {
    public static final int DEFAULT_PORT = 5000;
    public static final String SEP = "\\|";
    public static final String RAW_SEP = "|";

    // Lệnh từ Client -> Server
    public static final String CMD_REGISTER = "REGISTER";
    public static final String CMD_LOGIN = "LOGIN";
    public static final String CMD_LOGOUT = "LOGOUT";
    public static final String CMD_UPDATE_AVATAR = "UPDATE_AVATAR";
    public static final String CMD_FRIEND_REQ = "FRIEND_REQ";
    public static final String CMD_FRIEND_ACCEPT = "FRIEND_ACCEPT";
    public static final String CMD_CREATE_GROUP = "CREATE_GROUP";
    public static final String CMD_ADD_MEMBER = "ADD_MEMBER";
    public static final String CMD_LEAVE_GROUP = "LEAVE_GROUP";
    public static final String CMD_KICK_MEMBER = "KICK_MEMBER";
    public static final String CMD_SEND_MSG = "SEND_MSG";
    public static final String CMD_RECALL_MSG = "RECALL_MSG";
    public static final String CMD_PIN_MSG = "PIN_MSG";
    public static final String CMD_TYPING = "TYPING";
    public static final String CMD_MARK_SEEN = "MARK_SEEN";

    // Phản hồi từ Server -> Client
    public static final String RESP_AUTH_OK = "AUTH_OK";
    public static final String RESP_AUTH_FAIL = "AUTH_FAIL";
    public static final String RESP_REGISTER_OK = "REGISTER_OK";
    public static final String RESP_SYNC_USERS = "SYNC_USERS";
    public static final String RESP_SYNC_GROUPS = "SYNC_GROUPS";
    public static final String RESP_RECEIVE_MSG = "RECEIVE_MSG";
    public static final String RESP_MSG_RECALLED = "MSG_RECALLED";
    public static final String RESP_GROUP_PINNED = "GROUP_PINNED";
    public static final String RESP_TYPING_NOTIFY = "TYPING_NOTIFY";
    public static final String RESP_SEEN_NOTIFY = "SEEN_NOTIFY";
    public static final String RESP_ALERT = "ALERT";

    public static String escape(String text) {
        if (text == null)
            return "";
        return text.replace("|", "&#124;")
                .replace(";", "&#59;")
                .replace("\n", " ")
                .replace("\r", "");
    }

    public static String unescape(String text) {
        if (text == null)
            return "";
        return text.replace("&#124;", "|")
                .replace("&#59;", ";");
    }

    public static String pack(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0)
                sb.append(RAW_SEP);
            sb.append(escape(parts[i]));
        }
        return sb.toString();
    }

    public static String[] unpack(String line) {
        String[] raw = line.split(SEP, -1);
        for (int i = 0; i < raw.length; i++) {
            raw[i] = unescape(raw[i]);
        }
        return raw;
    }
}