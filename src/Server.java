import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;
import java.util.Vector;

public class Server {

    public static final int SERVER_PORT = 8080;
    private Vector<ChatRoom> chatRooms = new Vector<>();

    public static void main(String[] args) {
        new Server().execute();
    }

    public void execute() {

        System.out.printf("Accepting Connection on port %d..", SERVER_PORT);
        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Request received from " + clientSocket.getInetAddress().getHostName());
                new ClientThread(clientSocket).start();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

    }

    class ClientThread extends Thread {

        Socket socket;
        String username;
        String roomId;

        public ClientThread(Socket socket) {
            this.socket = socket;
        }

        public void run() {

            try {
                String ip = socket.getInetAddress().getHostName();

                System.out.println("Accepting connection from ip " + ip);

                DataInputStream dis = new DataInputStream(socket.getInputStream());
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                username = dis.readUTF();
                this.username = username;
                User user = new User(UUID.randomUUID().toString(), username, dos);

                roomId = dis.readUTF();
                this.roomId = roomId;
                addUserToChatRoom(user, roomId);
                broadCastMessage(String.format("User: %s logged in", username));

                while (true) {
                    String message = dis.readUTF();
                    broadCastMessage(username + ": " + message);
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        public ChatRoom getExistingChatRoom(String id) {
            for (ChatRoom room : chatRooms) {
                if (room.getId().equals(id)) {
                    return room;
                }
            }
            return null;
        }

        public void addUserToChatRoom(User user, String id) {
            ChatRoom existingChatRoom = getExistingChatRoom(id);
            if (existingChatRoom == null) {
                ChatRoom room = new ChatRoom(roomId);
                room.addUser(user);
                chatRooms.add(room);
            } else {
                existingChatRoom.addUser(user);
            }
        }

        public void broadCastMessage(String message) throws IOException {
            // Could simplify with ConcurrentMap
            for (ChatRoom room : chatRooms) {
                if (room.getId().equals(this.roomId)) {
                    for (User user : room.getUsers()) {
                        user.getClient().writeUTF(message);
                    }
                }
            }
        }
    }
}
