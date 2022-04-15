import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enum of client's request types
 */
enum RequestType {
    MESSAGE,
    UPLOAD,
    DOWNLOAD,
    FILES,
    LOGOUT,
    USERS;
}

/**
 * Server of the chat room app
 */
public class Server {
    public static final int SERVER_PORT = 8080;
    public static final RequestType[] REQUEST_TYPES = RequestType.values(); // Constant array of all request type
    private ConcurrentHashMap<String, ChatRoom> chatRooms = new ConcurrentHashMap<>();  // Each chat room object is mapped with their id
    private ConcurrentHashMap<String, ConcurrentHashMap<String, DataOutputStream>> onlineClientsWithRoomId = new ConcurrentHashMap<>(); // List of online clients is mapped with chat room id

    public static void main(String[] args) {
        Server server = new Server();
        server.loadPrevSessionInfo();
        server.execute();
    }

    /**
     * Save all chat rooms locally
     */
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

    /**
     * Save the chat room object
     *
     * @param room the chat room instance
     * @throws IOException
     */
    private void saveChatRoomHistory(ChatRoom room) throws IOException {
        // Chatroom objects are saved inside ChatRooms folder
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

    /**
     * On server start, load locally saved chat rooms objects
     */
    private void loadPrevSessionInfo() {
        File[] files = new File("./ChatRooms").listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.getName().toLowerCase().endsWith(".obj");
            }
        });

        // In case of a fresh start
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

    /**
     * Start server socket and listen for client connections
     */
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

    /**
     * Client thread to handle each client request
     */
    class ClientThread extends Thread {
        Socket socket;
        String userId;
        String roomId;
        DataInputStream dis;
        DataOutputStream dos;
        ConcurrentHashMap<String, DataOutputStream> onlineClients;  // Keep the record of all online clients in the current room

        public ClientThread(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                String ip = socket.getInetAddress().getHostName();
                System.out.println("Accepting connection from ip " + ip);

                dis = new DataInputStream(socket.getInputStream());
                dos = new DataOutputStream(socket.getOutputStream());
                String username = dis.readUTF();
                roomId = dis.readUTF();

                // Check if user is an existing user in current chat room
                User user = getExistingUserByUsername(username);
                onlineClients = onlineClientsWithRoomId.getOrDefault(roomId, new ConcurrentHashMap<String, DataOutputStream>());

                // Adding/updating online clients
                if (user != null) {
                    // If existing user, then update the status
                    userId = user.getId();
                    user.setStatus(Status.online);
                    onlineClients.put(user.getId(), dos);
                } else {
                    // If new user, create new user and assign id
                    userId = UUID.randomUUID().toString();
                    user = new User(userId, username, Status.online);
                    onlineClients.put(userId, dos);
                }

                // Map current room's clients to room id
                onlineClientsWithRoomId.put(roomId, onlineClients);

                addUserToChatRoom(user, roomId);
                loadChatHistory(dos);
                broadCastMessage(String.format("%s joined", username), true);

                while (!isInterrupted()) {
                    int method = dis.readInt();
                    // Handle invalid request
                    if (method < 0 || method >= REQUEST_TYPES.length) {
                        System.out.println("Invalid request type");
                        System.exit(0);
                    }

                    RequestType requestType = REQUEST_TYPES[method];
                    switch (requestType) {
                        case MESSAGE:
                            sendMessage(username);
                            break;
                        case UPLOAD:
                            uploadFile();
                            break;
                        case FILES:
                            getAllFiles();
                            break;
                        case DOWNLOAD:
                            downloadFile();
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

        /**
         * Send chat messages to all online clients
         *
         * @param username current username
         * @throws IOException
         */
        private void sendMessage(String username) throws IOException {
            String message = dis.readUTF();
            broadCastMessage(username + ": " + message, false);
        }

        /**
         * Send file to client for download
         *
         * @throws IOException
         */
        private void downloadFile() throws IOException {
            String filename = dis.readUTF();
            String path = dis.readUTF();
            File file = new File("./Files/" + roomId + "/" + filename);

            dos.writeInt(ResponseType.DOWNLOAD.ordinal());
            dos.writeLong(file.length());
            dos.writeUTF(path + "/" + filename);
            FileInputStream fis = new FileInputStream(file);
            int bytes;
            byte[] buffer = new byte[(int) file.length()];
            while ((bytes = fis.read(buffer, 0, buffer.length)) > 0) {
                dos.write(buffer, 0, bytes);
            }
            dos.writeInt(ResponseType.MESSAGE.ordinal());
            dos.writeUTF(filename + " downloaded successfully");
        }

        /**
         * Get all files inside current chat room
         *
         * @throws IOException
         */
        private void getAllFiles() throws IOException {
            ChatRoom currentChatRoom = getCurrentChatRoom(roomId);
            File folder = new File("./Files/" + roomId);
            if (!folder.exists()) {
                folder.mkdirs();
            }
            StringBuilder filenames = new StringBuilder();
            File[] files = folder.listFiles();
            for (File f : files) {
                filenames.append(filenames.isEmpty() ? f.getName() : "," + f.getName());
            }
            dos.writeInt(ResponseType.FILES.ordinal());
            dos.writeUTF(filenames.toString());
        }

        /**
         * Receive file from client and download to server folder
         *
         * @throws IOException
         */
        private void uploadFile() throws IOException {
            String filePath = "./Files/" + roomId;
            File dir = new File(filePath);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String filename = dis.readUTF();
            File file = new File(filePath + "/" + filename);
            FileOutputStream fos = new FileOutputStream(file);
            long fileSize = dis.readLong();
            if (!(fileSize > 0)) {
                return;
            }
            int bytes;
            byte[] buffer = new byte[(int) fileSize];
            while (fileSize > 0 && (bytes = dis.read(buffer, 0, (int) Math.min(buffer.length, fileSize))) != -1) {
                fos.write(buffer, 0, bytes);
                fileSize -= bytes;
            }
            ChatRoom currentChatRoom = getCurrentChatRoom(roomId);
            User currentUser = currentChatRoom.getUserById(userId);
            broadCastMessage(filename + " uploaded by " + currentUser.getUsername(), true);
            updateUploadedFile(filename);
        }

        /**
         * Notify online clients of uploaded file
         *
         * @param filename uploaded file name
         */
        private void updateUploadedFile(String filename) {
            onlineClients.forEach((key, value) -> {
                try {
                    value.writeInt(ResponseType.UPLOAD.ordinal());
                    value.writeUTF(filename);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

        /**
         * Check if user exists in current chat room
         *
         * @param username current user's username
         * @return
         */
        private User getExistingUserByUsername(String username) {
            ChatRoom existingChatRoom = chatRooms.getOrDefault(roomId, null);
            return existingChatRoom != null ? chatRooms.get(roomId).getUserByUsername(username) : null;
        }

        /**
         * Get all users' username and status from room and send to clients
         *
         * @throws IOException
         */
        private void loadAllUsersInChatRoom() throws IOException {
            ChatRoom room = chatRooms.get(roomId);
            DataOutputStream dos = onlineClients.get(userId);
            // Update current user's client with all user information
            for (User user : room.getUsers()) {
                dos.writeInt(ResponseType.USERS.ordinal());
                dos.writeUTF(user.getUsername());
                dos.writeInt(user.getStatus().ordinal());
            }
            // Update all other clients about current user
            updateAllOnlineClients(Status.online.ordinal());
        }

        /**
         * Send current user information to other clients in this chat room
         *
         * @param status current user's status
         */
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

        /**
         * Get chat history of current chat room and send to current user's client
         *
         * @param client current user's client
         * @throws IOException
         */
        private void loadChatHistory(DataOutputStream client) throws IOException {
            if (chatRooms.containsKey(roomId)) {
                for (String message : chatRooms.get(roomId).getChatHistory()) {
                    client.writeInt(ResponseType.MESSAGE.ordinal());
                    client.writeUTF(message);
                }
            }
        }

        /**
         * Remove user from online clients. Note user is still considered a member of this chat room
         *
         * @throws IOException
         */
        private void logoutUser() throws IOException {
            ChatRoom currentChatRoom = getCurrentChatRoom(roomId);
            User currentUser = currentChatRoom.getUserById(userId);
            currentUser.setStatus(Status.offline);
            onlineClients.remove(userId);

            // Update all other clients that current user left this chat room
            broadCastMessage(String.format("%s left", currentUser.getUsername()), true);

            // Change current user's status from all online clients
            updateAllOnlineClients(Status.offline.ordinal());
            interrupt();
        }

        /**
         * Get current chat room
         *
         * @param id chat room id
         * @return
         */
        public ChatRoom getCurrentChatRoom(String id) {
            return chatRooms.getOrDefault(id, new ChatRoom(roomId));
        }

        /**
         * Add current user to current chat room
         *
         * @param user   current user object
         * @param roomId current room id
         */
        public void addUserToChatRoom(User user, String roomId) {
            ChatRoom existingChatRoom = getCurrentChatRoom(roomId);
            existingChatRoom.addUser(user.getId(), user);
            chatRooms.put(existingChatRoom.getId(), existingChatRoom);
        }

        /**
         * Sync messages to all online clients
         *
         * @param message         chat message
         * @param isSystemMessage true if message is system purposes (i.e. inform user about who joined and who left)
         * @throws IOException
         */
        public void broadCastMessage(String message, Boolean isSystemMessage) throws IOException {
            ChatRoom currentChatRoom = getCurrentChatRoom(roomId);
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            String messageWithTimeStamp = "[" + timestamp + "] " + message;

            // Do not save system messages in chat room history
            if (!isSystemMessage) {
                currentChatRoom.addChatHistory(messageWithTimeStamp);
            }

            // Send message to all online clients in this chat room
            for (DataOutputStream client : onlineClients.values()) {
                client.writeInt(ResponseType.MESSAGE.ordinal());
                client.writeUTF(isSystemMessage ? message : messageWithTimeStamp);
            }

            // Update local chat room object
            saveCurrentSessionInfo();
        }
    }
}
