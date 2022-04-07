import java.util.Vector;

public class ChatRoom {
    private String id;
    private Vector<User> users;

    public ChatRoom(String id) {
        this.id = id;
        this.users = new Vector<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Vector<User> getUsers() {
        return users;
    }

    public void setUsers(Vector<User> users) {
        this.users = users;
    }

    public void addUser(User user) {
        this.users.add(user);
    }
}
