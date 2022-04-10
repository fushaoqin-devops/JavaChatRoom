import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

enum RequestType {
    MESSAGE,
    UPLOAD,
    DOWNLOAD,
    LOGOUT,
    USERS;
}

public class Server {
    public static final int SERVER_PORT = 8080;
    public static final RequestType[] REQUEST_TYPES = RequestType.values();
    private ConcurrentHashMap<String, ChatRoom> chatRooms = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, ConcurrentHashMap<String, DataOutputStream>> onlineClientsWithRoomId = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        Server server = new Server();
        server.loadPrevSessionInfo();
        server.execute();
    }

    private void saveCurrentSessionInfo() {
        if (!chatRooms.isEmpty()) {

            for (ChatRoom room : chatRooms.values()) {
                try {
                    saveChatRoomHistory(room);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void saveChatRoomHistory(ChatRoom room) throws IOException {
        File dir = new File("./ChatRooms");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File("./ChatRooms/ChatRoom_" + room.getId() + ".obj");
        FileOutputStream fos = new FileOutputStream(file, false);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(room);
        fos.close();
        oos.close();
    }

    private void loadPrevSessionInfo() {
        File[] files = new File("./ChatRooms").listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.getName().toLowerCase().endsWith(".obj");
            }
        });

        if (files == null) return;

        for (File file : files) {
            try {
                FileInputStream fis = new FileInputStream(file);
                ObjectInputStream ois = new ObjectInputStream(fis);
                ChatRoom room = (ChatRoom) ois.readObject();
                // On server start, reset all users to offline
                for (User user : room.getUsers()) {
                    user.setStatus(Status.offline);
                }
                chatRooms.put(room.getId(), room);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
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
        ConcurrentHashMap<String, DataOutputStream> onlineClients;

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
                roomId = dis.readUTF();

                User user = getExistingUserByUsername(username);

                onlineClients = onlineClientsWithRoomId.getOrDefault(roomId, new ConcurrentHashMap<String, DataOutputStream>());

                if (user != null) {
                    userId = user.getId();
                    user.setStatus(Status.online);
                    onlineClients.put(user.getId(), dos);
                } else {
                    userId = UUID.randomUUID().toString();
                    user = new User(userId, username, Status.online);
                    onlineClients.put(userId, dos);
                }

                onlineClientsWithRoomId.put(roomId, onlineClients);

                addUserToChatRoom(user, roomId);
                loadChatHistory(dos);
                broadCastMessage(String.format("%s joined", username), true);

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
                            broadCastMessage(username + ": " + message, false);
                            break;
                        case UPLOAD:
                            break;
                        case DOWNLOAD:
                            break;
                        case LOGOUT:
                            logoutUser();
                            break;
                        case USERS:
                            loadAllUsersInChatRoom();
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

        private User getExistingUserByUsername(String username) {
            ChatRoom existingChatRoom = chatRooms.getOrDefault(roomId, null);
            return existingChatRoom != null ? chatRooms.get(roomId).getUserByUsername(username) : null;
        }

        private void loadAllUsersInChatRoom() throws IOException {
            ChatRoom room = chatRooms.get(roomId);
            DataOutputStream dos = onlineClients.get(userId);
            for (User user : room.getUsers()) {
                dos.writeInt(ResponseType.USERS.ordinal());
                dos.writeUTF(user.getUsername());
                dos.writeInt(user.getStatus().ordinal());
            }
            updateAllOnlineClients(Status.online.ordinal());
        }

        private void updateAllOnlineClients(int status) {
            onlineClients.forEach((key, value) -> {
                if (!key.equals(userId)) {
                    try {
                        value.writeInt(ResponseType.USERS.ordinal());
                        value.writeUTF(chatRooms.get(roomId).getUserById(userId).getUsername());
                        value.writeInt(status);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        private void loadChatHistory(DataOutputStream client) throws IOException {
            if (chatRooms.containsKey(roomId)) {
                for (String message : chatRooms.get(roomId).getChatHistory()) {
                    client.writeInt(ResponseType.MESSAGE.ordinal());
                    client.writeUTF(message);
                }
            }
        }

        private void logoutUser() throws IOException {
            ChatRoom currentChatRoom = getCurrentChatRoom(roomId);
            User currentUser = currentChatRoom.getUserById(userId);
            currentUser.setStatus(Status.offline);
            onlineClients.remove(userId);
            broadCastMessage(String.format("%s left", currentUser.getUsername()), true);
            updateAllOnlineClients(Status.offline.ordinal());
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

        public void broadCastMessage(String message, Boolean isSystemMessage) throws IOException {
            ChatRoom currentChatRoom = getCurrentChatRoom(roomId);
            if (!isSystemMessage) {
                currentChatRoom.addChatHistory(message);
            }
            for (DataOutputStream client : onlineClients.values()) {
                client.writeInt(ResponseType.MESSAGE.ordinal());
                client.writeUTF(message);
            }
            saveCurrentSessionInfo();
        }
    }
}
