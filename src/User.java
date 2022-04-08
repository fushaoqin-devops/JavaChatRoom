import java.io.Serializable;

enum Status {
    online, offline
}

public class User implements Serializable {
    private static final long serialVersionUid = 02L;
    private String id;
    private String username;
    private Status status;

    public User(String id, String username, Status status) {
        this.id = id;
        this.username = username;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public void setUsername(String username) {
        this.username = username;
    }
}
