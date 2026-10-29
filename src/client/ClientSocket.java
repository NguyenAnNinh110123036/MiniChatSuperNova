package client;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientSocket {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private boolean connected = false;

    public boolean connect(String host, int port) {
        disconnect();
        try {
            socket = new Socket(host, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            connected = true;
            return true;
        } catch (IOException e) {
            connected = false;
            return false;
        }
    }

    public void send(String packet) {
        if (out != null) {
            out.println(packet);
        }
    }

    public String readLine() {
        try {
            if (in != null)
                return in.readLine();
        } catch (IOException ignored) {
        }
        return null;
    }

    public void disconnect() {
        connected = false;
        try {
            if (socket != null && !socket.isClosed())
                socket.close();
        } catch (IOException ignored) {
        }
    }

    public boolean isConnected() {
        return connected;
    }
}