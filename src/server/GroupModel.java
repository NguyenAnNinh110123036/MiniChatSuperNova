package server;

import java.util.LinkedHashSet;
import java.util.Set;

public class GroupModel {
    private final String groupName;
    private String admin;
    private String avatarBase64;
    private String pinnedMessage;
    private final Set<String> members = new LinkedHashSet<>();

    public GroupModel(String groupName, String admin, String avatarBase64) {
        this.groupName = groupName;
        this.admin = admin;
        this.avatarBase64 = avatarBase64 == null ? "" : avatarBase64;
        this.pinnedMessage = "";
        this.members.add(admin);
    }

    public String getGroupName() {
        return groupName;
    }

    public String getAdmin() {
        return admin;
    }

    public void setAdmin(String admin) {
        this.admin = admin;
    }

    public String getAvatarBase64() {
        return avatarBase64;
    }

    public void setAvatarBase64(String avatarBase64) {
        this.avatarBase64 = avatarBase64 == null ? "" : avatarBase64;
    }

    public String getPinnedMessage() {
        return pinnedMessage;
    }

    public void setPinnedMessage(String pinnedMessage) {
        this.pinnedMessage = pinnedMessage == null ? "" : pinnedMessage;
    }

    public synchronized Set<String> getMembers() {
        return members;
    }

    public synchronized boolean removeMember(String user) {
        boolean removed = members.remove(user);
        if (removed && user.equals(admin) && !members.isEmpty()) {
            admin = members.iterator().next(); // Chuyển quyền nhóm trưởng nếu admin rời nhóm
        }
        return removed;
    }
}