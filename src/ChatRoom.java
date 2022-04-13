import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chat room object. Represents the chat room where user can interact each other
 */
public class ChatRoom implements Serializable {
    private static final long serialVersionUid = 01L;
    private String id;  // Chat room id
    private ConcurrentHashMap<String, User> users;  // All users. Users are mapped to their id for faster access
    private List<String> chatHistory;   // Chat rooms chat history

    public ChatRoom(String id) {
        this.id = id;
        this.users = new ConcurrentHashMap<>();
        this.chatHistory = Collections.synchronizedList(new ArrayList<String>());
    }

    public void addChatHistory(String message) {
        this.chatHistory.add(message);
    }

    public String getId() {
        return id;
    }

    /**
     * Get chat room's history
     *
     * @return chat room history
     */
    public List<String> getChatHistory() {
        return chatHistory;
    }

    /**
     * Get all users in chat room
     *
     * @return all users in chat room
     */
    public Collection<User> getUsers() {
        return users.values();
    }

    /**
     * Get all online users in chat room
     *
     * @return all online users
     */
    public Collection<User> getOnlineUsers() {
        ArrayList<User> onlineUsers = new ArrayList<>();
        for (User user : getUsers()) {
            if (user.getStatus() == Status.online) {
                onlineUsers.add(user);
            }
        }
        return onlineUsers;
    }

    /**
     * Find a user in chat room by id
     *
     * @param id user's id
     * @return User object if found, null otherwise
     */
    public User getUserById(String id) {
        return this.users.get(id);
    }

    /**
     * Add a user to chat room
     *
     * @param id   user id
     * @param user user object
     */
    public void addUser(String id, User user) {
        this.users.put(id, user);
    }

    /**
     * Find a user in chat room by username
     *
     * @param username user's username
     * @return User object if found, null otherwise
     */
    public User getUserByUsername(String username) {
        for (User user : users.values()) {
            if (user.getUsername().equals(username)) {
                return user;
            }
        }
        return null;
    }
}
