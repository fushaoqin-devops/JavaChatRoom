import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

enum RequestType {
    MESSAGE,
    UPLOAD,
    DOWNLOAD,
    LOGOUT;
}

public class Server {
    public static final int SERVER_PORT = 8080;
    public static final RequestType[] REQUEST_TYPES = RequestType.values();
    private ConcurrentHashMap<String, ChatRoom> chatRooms = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        new Server().execute();
    }

    public void execute() {

        System.out.printf("Accepting Connection on port %d..", SERVER_PORT);
        try (ServerSocket serverSocket = new ServerSocket(SERVER_PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Request received from " + clientSocket.getInetAddress().getHostName());
                new ClientThread(clientSocket).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    class ClientThread extends Thread {

        Socket socket;
        String userId;
        String roomId;

        public ClientThread(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {

            try {
                String ip = socket.getInetAddress().getHostName();
                System.out.println("Accepting connection from ip " + ip);

                DataInputStream dis = new DataInputStream(socket.getInputStream());
                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                String username = dis.readUTF();
                userId = UUID.randomUUID().toString();
                User user = new User(userId, username, dos, Status.online);

                roomId = dis.readUTF();
                addUserToChatRoom(user, roomId);
                broadCastMessage(String.format("User: %s logged in", username));

                while (!isInterrupted()) {
                    int method = dis.readInt();
                    if (method < 0 || method >= REQUEST_TYPES.length) {
                        System.out.println("Invalid request type");
                        System.exit(0);
                    }
                    RequestType requestType = REQUEST_TYPES[method];
                    switch (requestType) {
                        case MESSAGE:
                            String message = dis.readUTF();
                            broadCastMessage(username + ": " + message);
                            break;
                        case UPLOAD:
                            break;
                        case DOWNLOAD:
                            break;
                        case LOGOUT:
                            logoutUser();
                            break;
                        default:
                            System.out.println("Unknown request type received");
                            break;
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                interrupt();
            }
        }

        private void logoutUser() {
            ChatRoom currentChatRoom = getCurrentChatRoom(roomId);
            User currentUser = currentChatRoom.getUserById(userId);
            currentUser.setStatus(Status.offline);
            interrupt();
        }

        public ChatRoom getCurrentChatRoom(String id) {
            return chatRooms.getOrDefault(id, new ChatRoom(roomId));
        }

        public void addUserToChatRoom(User user, String roomId) {
            ChatRoom existingChatRoom = getCurrentChatRoom(roomId);
            existingChatRoom.addUser(user.getId(), user);
            chatRooms.put(existingChatRoom.getId(), existingChatRoom);
        }

        public void broadCastMessage(String message) throws IOException {
            ChatRoom currentChatRoom = getCurrentChatRoom(roomId);
            for (User user : currentChatRoom.getOnlineUsers()) {
                user.getClient().writeUTF(message);
            }
        }


    }
}
