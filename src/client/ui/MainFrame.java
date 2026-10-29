package client.ui;

import client.ChatMemory;
import client.ClientSocket;
import javax.swing.*;
import java.awt.*;

public class MainFrame extends JFrame {
    private final CardLayout cardLayout;
    private final JPanel rootPanel;
    private final ClientSocket clientSocket;
    private ChatPanel chatPanel;

    public MainFrame() {
        setTitle("Mini Chat - Zalo Style");
        setSize(980, 640);
        setMinimumSize(new Dimension(850, 540));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        clientSocket = new ClientSocket();
        cardLayout = new CardLayout();
        rootPanel = new JPanel(cardLayout);

        LoginPanel loginPanel = new LoginPanel(this);
        rootPanel.add(loginPanel, "LOGIN");

        setContentPane(rootPanel);
        cardLayout.show(rootPanel, "LOGIN");
    }

    public void openChatSession(String username, String myAvatarBase64) {
        if (chatPanel != null) {
            rootPanel.remove(chatPanel);
        }
        ChatMemory memory = new ChatMemory(username);
        chatPanel = new ChatPanel(this, clientSocket, memory, username, myAvatarBase64);
        rootPanel.add(chatPanel, "CHAT");
        cardLayout.show(rootPanel, "CHAT");
        setTitle("Mini Chat - Đang đăng nhập: " + username);
    }

    public void returnToLogin() {
        clientSocket.disconnect();
        setTitle("Mini Chat - Zalo Style");
        cardLayout.show(rootPanel, "LOGIN");
    }

    public ClientSocket getClientSocket() {
        return clientSocket;
    }
}