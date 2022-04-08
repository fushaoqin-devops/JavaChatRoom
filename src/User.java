import java.io.DataOutputStream;

enum Status {
    online, offline
}

public class User {
    private String id;
    private String username;
    private DataOutputStream client;
    private Status status;

    public User(String id, String username, DataOutputStream client, Status status) {
        this.id = id;
        this.username = username;
        this.client = client;
        this.status = status;
    }

    public String getId() {
        return id;
    }

    public DataOutputStream getClient() {
        return client;
    }

    public void setClient(DataOutputStream client) {
        this.client = client;
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
