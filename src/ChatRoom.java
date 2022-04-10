import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class ChatRoom implements Serializable {
    private static final long serialVersionUid = 01L;
    private String id;
    private ConcurrentHashMap<String, User> users;
    private List<String> chatHistory;

    public ChatRoom(String id) {
        this.id = id;
        this.users = new ConcurrentHashMap<>();
        this.chatHistory = Collections.synchronizedList(new ArrayList<String>());
    }

    public List<String> getChatHistory() {
        return chatHistory;
    }

    public void addChatHistory(String message) {
        this.chatHistory.add(message);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Collection<User> getUsers() {
        return users.values();
    }

    public Collection<User> getOnlineUsers() {
        ArrayList<User> onlineUsers = new ArrayList<>();
        for (User user : getUsers()) {
            if (user.getStatus() == Status.online) {
                onlineUsers.add(user);
            }
        }
        return onlineUsers;
    }

    public User getUserById(String id) {
        return this.users.get(id);
    }

    public void setUsers(ConcurrentHashMap<String, User> users) {
        this.users = users;
    }

    public void addUser(String id, User user) {
        this.users.put(id, user);
    }

    public User getUserByUsername(String username) {
        for (User user : users.values()) {
            if (user.getUsername().equals(username)) {
                return user;
            }
        }
        return null;
    }
}
