package server;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class UserModel {
    private final String username;
    private final String password;
    private String avatarBase64;
    private boolean online;
    private final Set<String> friends = ConcurrentHashMap.newKeySet();
    private final Set<String> incomingRequests = ConcurrentHashMap.newKeySet();

    public UserModel(String username, String password, String avatarBase64) {
        this.username = username;
        this.password = password;
        this.avatarBase64 = avatarBase64 == null ? "" : avatarBase64;
        this.online = false;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getAvatarBase64() {
        return avatarBase64;
    }

    public void setAvatarBase64(String avatarBase64) {
        this.avatarBase64 = avatarBase64 == null ? "" : avatarBase64;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public Set<String> getFriends() {
        return friends;
    }

    public Set<String> getIncomingRequests() {
        return incomingRequests;
    }
}