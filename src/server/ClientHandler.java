package server;

import common.Protocol;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ServerCore core;
    private BufferedReader in;
    private PrintWriter out;
    private String username;

    public ClientHandler(Socket socket, ServerCore core) {
        this.socket = socket;
        this.core = core;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void send(String packet) {
        if (out != null) {
            out.println(packet);
        }
    }

    public void forceClose() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void run() {
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            String line;
            while ((line = in.readLine()) != null) {
                String[] p = Protocol.unpack(line);
                if (p.length == 0)
                    continue;
                String cmd = p[0];

                switch (cmd) {
                    case Protocol.CMD_REGISTER:
                        if (p.length >= 4)
                            core.handleRegister(p[1].trim(), p[2].trim(), p[3], this);
                        break;
                    case Protocol.CMD_LOGIN:
                        if (p.length >= 3)
                            core.handleLogin(p[1].trim(), p[2].trim(), this);
                        break;
                    case Protocol.CMD_LOGOUT:
                        core.handleLogout(username, this);
                        username = null;
                        break;
                    case Protocol.CMD_UPDATE_AVATAR:
                        if (username != null && p.length >= 4)
                            core.handleUpdateAvatar(username, p[1], p[2], p[3]);
                        break;
                    case Protocol.CMD_FRIEND_REQ:
                        if (username != null && p.length >= 2)
                            core.handleFriendRequest(username, p[1]);
                        break;
                    case Protocol.CMD_FRIEND_ACCEPT:
                        if (username != null && p.length >= 2)
                            core.handleFriendAccept(username, p[1]);
                        break;
                    case Protocol.CMD_CREATE_GROUP:
                        if (username != null && p.length >= 4)
                            core.handleCreateGroup(username, p[1].trim(), p[2], p[3]);
                        break;
                    case Protocol.CMD_ADD_MEMBER:
                        if (username != null && p.length >= 3)
                            core.handleAddMemberToGroup(username, p[1], p[2]);
                        break;
                    case Protocol.CMD_LEAVE_GROUP:
                        if (username != null && p.length >= 2)
                            core.handleLeaveGroup(username, p[1]);
                        break;
                    case Protocol.CMD_KICK_MEMBER:
                        if (username != null && p.length >= 3)
                            core.handleKickMember(username, p[1], p[2]);
                        break;
                    case Protocol.CMD_SEND_MSG:
                        if (username != null && p.length >= 6) {
                            core.handleSendMessage(username, p[1], p[2], p[3], p[4], p[5]);
                        }
                        break;
                    case Protocol.CMD_RECALL_MSG:
                        if (username != null && p.length >= 3)
                            core.handleRecallMessage(username, p[1], p[2]);
                        break;
                    case Protocol.CMD_PIN_MSG:
                        if (username != null && p.length >= 3)
                            core.handlePinMessage(username, p[1], p[2]);
                        break;
                    case Protocol.CMD_TYPING:
                        if (username != null && p.length >= 3)
                            core.handleTyping(username, p[1], p[2]);
                        break;
                    case Protocol.CMD_MARK_SEEN:
                        if (username != null && p.length >= 2)
                            core.handleMarkSeen(username, p[1]);
                        break;
                }
            }
        } catch (IOException ignored) {
        } finally {
            core.handleLogout(username, this);
            forceClose();
        }
    }
}