import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Callback;
import javafx.util.Pair;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Consumer;

enum ResponseType {
    MESSAGE,
    USERS
}

/**
 * Client user interface for the chat room
 * Support file transfer as well as message exchange
 *
 * @author Shaoqin Fu
 * @version 03/25/2022
 */
public class Client extends Application implements EventHandler<ActionEvent> {
    private Stage stage;
    private Scene scene;
    private HBox root = new HBox(8);

    // UI Components
    private MenuBar menuBar = new MenuBar();
    private Menu menuFile = new Menu("File");
    private Menu menuUser = new Menu("User");
    private MenuItem menuItemDownload = new MenuItem("Download File");
    private MenuItem menuItemUpload = new MenuItem("Upload File");
    private MenuItem menuItemLogout = new MenuItem("Logout");
    private MenuItem menuItemChangeRoom = new MenuItem("Change Room");
    private TextArea taChat = new TextArea();
    private TextArea taInput = new TextArea();
    private Button btnSend = new Button("Send");
    private ListView listViewUsers = new ListView();

    // Other attributes
    public static final int SERVER_PORT = 8080;
    protected static final ResponseType[] RESPONSE_TYPES = ResponseType.values();  // All server response types
    protected static final Status[] STATUS_TYPES = Status.values();    // ONLINE/OFFLINE
    private Socket socket = null;
    private DataOutputStream dos = null;
    private DataInputStream dis = null;
    private Thread messageService = null;
    private String currentUserName = "";
    private String roomId = "";

    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Initialize stage
     *
     * @param _stage primary stage
     */
    public void start(Stage _stage) {
        stage = _stage;
        stage.setOnCloseRequest((WindowEvent windowEvent) -> {
            try {
                saveUserInfo(roomId);
            } catch (IOException e) {
                e.printStackTrace();
            }
            disconnectServer();
            System.exit(0);
        });

        // Menu section
        menuFile.getItems().addAll(menuItemUpload, menuItemDownload);
        menuUser.getItems().addAll(menuItemChangeRoom, menuItemLogout);
        menuBar.getMenus().addAll(menuFile, menuUser);
        menuItemUpload.setOnAction(this);
        menuItemDownload.setOnAction(this);
        menuItemChangeRoom.setOnAction(this);
        menuItemLogout.setOnAction(this);

        // User input section
        FlowPane fpBot = new FlowPane(8, 8);
        taInput.setPrefColumnCount(30);
        taInput.setPrefRowCount(3);
        taInput.setOnKeyPressed(keyEvent -> {
            // Enter key press will send the message, Shift + Enter will start on new line
            if (keyEvent.getCode() == KeyCode.ENTER) {
                if (keyEvent.isShiftDown()) {
                    taInput.appendText("\n");
                } else if (!taInput.getText().trim().equals("")) {
                    try {
                        handleSend();
                    } catch (IOException ioe) {
                        alert(Alert.AlertType.ERROR, "ERROR", ioe + "");
                    }
                } else {
                    taInput.clear();
                }
            }
        });
        btnSend.setDisable(true);

        // Button is disabled if no input
        taInput.textProperty().addListener((observable, oldValue, newValue) -> btnSend.setDisable(newValue.trim().length() == 0));
        fpBot.getChildren().addAll(taInput, btnSend);

        btnSend.setOnAction(this);

        // Chat room's chat section
        VBox chatSection = new VBox(8);
        taChat.setEditable(false);
        taChat.setStyle("-fx-font-family: monospace; -fx-opacity: 1.0");
        chatSection.getChildren().addAll(menuBar, taChat, fpBot);

        // User status section
        listViewUsers.setCellFactory(new Callback<ListView, ListCell<Pair<String, Status>>>() {
            @Override
            public ListCell<Pair<String, Status>> call(ListView listView) {
                return new UserStatusCell();
            }
        });


        root.getChildren().addAll(chatSection, listViewUsers);
        scene = new Scene(root, 500, 300);
        stage.setScene(scene);
        final Stage mainStage = stage;

        // On stage start, check if temp user file exists
        stage.setOnShowing((WindowEvent windowEvent) -> {
            Pair<String, String> prevSessionInfo = loadUserInfo();
            if (prevSessionInfo != null) {
                // If temp user file exists, directly send user to chat room
                roomId = prevSessionInfo.getValue();
                doConnect(prevSessionInfo.getKey(), roomId);
                mainStage.setTitle("Chat Room - " + roomId);
            } else {
                // If temp user file does not exist, prompt user for username and room id
                LoginDialog loginDialog = new LoginDialog();
                Optional<Pair<String, String>> result = loginDialog.showAndWait();
                result.ifPresentOrElse((Pair<String, String> loginCredentials) -> {
                    roomId = loginCredentials.getValue();
                    doConnect(loginCredentials.getKey(), roomId);
                    mainStage.setTitle("Chat Room - " + roomId);
                }, () -> System.exit(0));
            }
        });
        stage.show();
    }

    /**
     * Get user info from temp user file saved locally
     *
     * @return pair of username and room id
     */
    private Pair<String, String> loadUserInfo() {
        try (DataInputStream outTmp = new DataInputStream(new FileInputStream(new File("./TempUser/CurrentUser.txt")))) {
            String username = outTmp.readUTF();
            String roomId = outTmp.readUTF();
            return new Pair<>(username, roomId);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Action dispatcher
     *
     * @param ae triggered action event
     */
    public void handle(ActionEvent ae) {
        Object event = ae.getSource();
        String command = "";
        if (event instanceof Button btn) {
            command = btn.getText();
        } else if (event instanceof MenuItem menuItem) {
            command = menuItem.getText();
        }

        // Handle action
        try {
            switch (command) {
                case "Send":
                    handleSend();
                    break;
                case "Upload File":
                    handleUpload();
                    break;
                case "Download File":
                    handleDownload();
                    break;
                case "Change Room":
                    handleChangeRoom();
                    break;
                case "Logout":
                    handleLogout();
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            String methodName = e.getStackTrace()[0].getMethodName();
            alert(Alert.AlertType.ERROR, "Error", "Error invoking " + methodName + ": " + e.getMessage());
        }
    }

    /**
     * Log out user from current room and start a session in new chat room
     */
    private void handleChangeRoom() {
        showDialog("Do you want to join another room?", "Room ID: ", (String id) -> {
            try {
                // Logout
                dos.writeInt(RequestType.LOGOUT.ordinal());
                disconnectServer();

                // Save username and room id in temp user file locally
                saveUserInfo(id);

                // Close current stage and initialize a new stage
                ((Stage) scene.getWindow()).close();
                Platform.runLater(() -> new Client().start(new Stage()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, () -> {
            return;
        });
    }

    private void saveUserInfo(String id) throws IOException {
        File dir = new File("./TempUser");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File tempUserFile = new File("./TempUser/CurrentUser.txt");
        FileOutputStream tmpFileOutputStream = new FileOutputStream(tempUserFile, false);
        DataOutputStream output = new DataOutputStream(tmpFileOutputStream);
        output.writeUTF(currentUserName);
        output.writeUTF(id);

        output.close();
    }

    /**
     * Log out user
     */
    private void handleLogout() {
        ArrayList<ButtonType> buttons = new ArrayList<>();
        ButtonType logoutButton = new ButtonType("Logout", ButtonBar.ButtonData.OK_DONE);
        buttons.add(logoutButton);
        buttons.add(ButtonType.CANCEL);

        showDialog("Logout", "Do you want to logout?", btnType -> {
            if (btnType == logoutButton) {
                try {
                    dos.writeInt(RequestType.LOGOUT.ordinal());
                    File tempUserFile = new File("./TempUser/CurrentUser.txt");
                    tempUserFile.delete();
                    disconnectServer();
                    System.exit(0);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, buttons);
    }

    private void handleUpload() throws IOException {
        FileChooser fileChooser = new FileChooser();
        File file = fileChooser.showOpenDialog(stage);
        if (file == null) return;
        dos.writeInt(RequestType.UPLOAD.ordinal());
        dos.writeUTF(file.getName());
        dos.writeLong(file.length());
        FileInputStream fis = new FileInputStream(file);
        int bytes;
        byte[] buffer = new byte[(int) file.length()];
        while ((bytes = fis.read(buffer, 0, buffer.length)) > 0) {
            dos.write(buffer, 0, bytes);
        }
    }

    private void handleDownload() {
        // TODO: download file from server
    }

    /**
     * Send message to current chat room
     *
     * @throws IOException
     */
    private void handleSend() throws IOException {
        String message = taInput.getText().trim();
        dos.writeInt(RequestType.MESSAGE.ordinal());
        dos.writeUTF(message);
        dos.flush();
        taInput.clear();
    }

    /**
     * Disconnect from server
     */
    private void disconnectServer() {
        try {
            messageService.interrupt();
            dis.close();
            dos.close();
            socket.close();
        } catch (Exception e) {
            alert(Alert.AlertType.ERROR, "Error", e + "\n");
        }
    }

    /**
     * Connect socket server
     */
    private void doConnect(String username, String roomId) {
        try {
            socket = new Socket("localhost", SERVER_PORT);
            dos = new DataOutputStream(socket.getOutputStream());
            dis = new DataInputStream(socket.getInputStream());
            dos.writeUTF(username);
            dos.writeUTF(roomId);
            messageService = new ProcessThread(dis);
            messageService.start();
            currentUserName = username;

            // Broadcast user login to all online clients
            dos.writeInt(RequestType.USERS.ordinal());
        } catch (IOException ioe) {
            alert(Alert.AlertType.ERROR, "Server Unavailable", ioe + "");
            System.exit(0);
        }
    }

    /**
     * Utility function for showing alert message
     *
     * @param type alert type
     * @param msg  alert message
     */
    private void alert(Alert.AlertType type, String header, String msg) {
        Alert alert = new Alert(type, msg);
        alert.setHeaderText(header);
        alert.showAndWait();
    }

    /**
     * Utility function to show simple dialog
     *
     * @param headerText  header text
     * @param contentText prompt text
     * @param action      action on button click
     * @param buttons     action buttons
     */
    private void showDialog(String headerText, String contentText, Consumer<ButtonType> action, ArrayList<ButtonType> buttons) {
        Dialog dialog = new Dialog();
        dialog.setHeaderText(headerText);
        dialog.setContentText(contentText);

        for (ButtonType btnType : buttons) {
            dialog.getDialogPane().getButtonTypes().add(btnType);
        }

        // handle action
        Optional<ButtonType> result = dialog.showAndWait();
        result.ifPresent(action);
    }

    /**
     * Utility function to show text input dialog
     *
     * @param headerText  header text
     * @param contentText prompt text
     * @param action      action on user input
     * @param emptyAction action on cancel
     */
    private void showDialog(String headerText, String contentText, Consumer<String> action, Runnable emptyAction) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setHeaderText(headerText);
        dialog.setContentText(contentText);

        // handle action
        Optional<String> result = dialog.showAndWait();
        result.ifPresentOrElse(action, emptyAction);
    }

    /**
     * Thread class to process server response
     */
    class ProcessThread extends Thread {
        private DataInputStream dis;

        public ProcessThread(DataInputStream dis) {
            this.dis = dis;
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    int method = dis.readInt();
                    if (method < 0 || method >= RESPONSE_TYPES.length) {
                        System.out.println("Invalid response type");
                        System.exit(0);
                    }

                    ResponseType responseType = RESPONSE_TYPES[method];
                    switch (responseType) {
                        case MESSAGE:
                            String message = dis.readUTF();
                            log(message);
                            break;
                        case USERS:
                            loadUsersInChatRoom();
                            break;
                        default:
                            System.out.println("Unknown response type received");
                            break;
                    }
                } catch (EOFException eof) {
                    log("Server disconnected.");
                    Thread.currentThread().interrupt();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        /**
         * Populate user status section
         *
         * @throws IOException
         */
        private void loadUsersInChatRoom() throws IOException {
            String username = dis.readUTF();
            Status status = STATUS_TYPES[dis.readInt()];
            System.out.println(username);
            System.out.println(status);

            Platform.runLater(() -> {
                Pair<String, Status> user = new Pair<String, Status>(username, status);
                // Check if user already in list view
                int idx = listViewUsers.getItems().indexOf(new Pair<String, Status>(username, STATUS_TYPES[1 - status.ordinal()]));
                System.out.println(idx);
                if (idx == -1) {
                    listViewUsers.getItems().add(user);
                } else {
                    listViewUsers.getItems().set(idx, user);
                }

                // Sort user based on online status
                listViewUsers.getItems().sort(new Comparator<Pair<String, Status>>() {
                    @Override
                    public int compare(Pair<String, Status> o1, Pair<String, Status> o2) {
                        if (o1.getValue() == o2.getValue()) {
                            return 0;
                        } else if (o1.getValue() == Status.online) {
                            return -1;
                        } else {
                            return 1;
                        }
                    }
                });
            });
        }

        // Print message in chat
        private void log(String message) {
            Platform.runLater(() -> taChat.appendText(message + "\n"));
        }
    }
}
