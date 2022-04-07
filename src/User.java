import java.io.DataOutputStream;

public class User {
    private String id;
    private String username;
    private DataOutputStream client;

    public User(String id, String username, DataOutputStream client) {
        this.id = id;
        this.username = username;
        this.client = client;
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

    public void setUsername(String username) {
        this.username = username;
    }
}
