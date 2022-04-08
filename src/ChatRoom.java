import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class ChatRoom {
    private String id;
    //    private Vector<User> users;
    private ConcurrentHashMap<String, User> users;

    public ChatRoom(String id) {
        this.id = id;
        this.users = new ConcurrentHashMap<>();
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
}
