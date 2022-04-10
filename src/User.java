import java.io.Serializable;

enum Status {
    online, offline
}

/**
 * User class represents a user from chat room
 */
public class User implements Serializable {
    private static final long serialVersionUid = 02L;
    private String id;  // User id. UUID as string
    private String username;
    private Status status;  // Status keep track if user is online or not

    public User(String id, String username, Status status) {
        this.id = id;
        this.username = username;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
