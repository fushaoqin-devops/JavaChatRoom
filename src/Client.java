import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.*;
import javafx.util.Callback;
import javafx.util.Pair;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.function.Consumer;

enum ResponseType {
    MESSAGE,
    USERS,
    FILES,
    UPLOAD,
    DOWNLOAD,
    DIRECT_MESSAGE
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
    private HBox root = new HBox();

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
    private ArrayList<String> fileList = null;
    private Boolean loaded = false;

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
                dos.writeInt(RequestType.LOGOUT.ordinal());
                saveUserInfo(roomId);
            } catch (IOException ioe) {
                alert(Alert.AlertType.ERROR, "ERROR", ioe + "");
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
        taInput.setPrefColumnCount(38);
        taInput.setPrefRowCount(3);
        taInput.setWrapText(true);
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
        taInput.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.trim().startsWith("@")) {
                btnSend.setDisable(newValue.trim().split(" ").length <= 1);
            } else {
                btnSend.setDisable(newValue.trim().length() == 0);
            }
        });
        fpBot.getChildren().addAll(taInput, btnSend);
        FlowPane.setMargin(taInput, new Insets(8, 0, 0, 0));

        btnSend.setOnAction(this);

        // Chat room's chat section
        VBox chatSection = new VBox();
        taChat.setEditable(false);
        taChat.setPrefColumnCount(80);
        taChat.setPrefRowCount(80);
        taChat.setWrapText(true);
        taChat.setStyle("-fx-font-family: monospace; -fx-opacity: 1.0");
        chatSection.getChildren().addAll(menuBar, taChat, fpBot);

        // User status section
        listViewUsers.setCellFactory(new Callback<ListView, ListCell<Pair<String, Status>>>() {
            @Override
            public ListCell<Pair<String, Status>> call(ListView listView) {
                ListCell cell = new UserStatusCell();

                // On selection, initiate private message with @ sign
                cell.setOnMouseClicked((event) -> {
                    String clickedUser = cell.getText();
                    if (clickedUser != null) {
                        String tag = "@" + clickedUser + " ";
                        if (!taInput.getText().trim().contains(tag)) {
                            taInput.setText(tag + taInput.getText());
                        } else {
                            // Remove private message tag if clicked again
                            String undoInput = taInput.getText().replaceFirst(tag, "");
                            taInput.setText(undoInput);
                        }
                    }
                });
                return cell;
            }
        });

        root.getChildren().addAll(chatSection, listViewUsers);
        scene = new Scene(root, 700, 500);
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
        taInput.requestFocus();
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
            } catch (IOException ioe) {
                alert(Alert.AlertType.ERROR, "ERROR", ioe.getMessage());
            }
        }, () -> {
            return;
        });
    }

    /**
     * Persist user info between room change and window close
     *
     * @param roomId room id that needs to be persisted
     * @throws IOException
     */
    private void saveUserInfo(String roomId) throws IOException {
        File dir = new File("./TempUser");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File tempUserFile = new File("./TempUser/CurrentUser.txt");
        FileOutputStream tmpFileOutputStream = new FileOutputStream(tempUserFile, false);
        DataOutputStream output = new DataOutputStream(tmpFileOutputStream);
        output.writeUTF(currentUserName);
        output.writeUTF(roomId);
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
                    // Delete temp user file on logout
                    dos.writeInt(RequestType.LOGOUT.ordinal());
                    File tempUserFile = new File("./TempUser/CurrentUser.txt");
                    tempUserFile.delete();
                    disconnectServer();
                    System.exit(0);
                } catch (IOException ioe) {
                    alert(Alert.AlertType.ERROR, "ERROR", ioe.getMessage());
                }
            }
        }, buttons);
    }

    /**
     * Upload a single file or multiple files to server
     *
     * @throws IOException
     */
    private void handleUpload() throws IOException {
        FileChooser fileChooser = new FileChooser();
        List<File> files = fileChooser.showOpenMultipleDialog(stage);
        if (files != null) {
            for (File file : files) {
                uploadFileToServer(file);
            }
        }
    }

    /**
     * Upload a file to server with buffer
     *
     * @param file file being uploaded
     * @throws IOException
     */
    private void uploadFileToServer(File file) throws IOException {
        dos.writeInt(RequestType.UPLOAD.ordinal());
        dos.writeUTF(file.getName());
        dos.writeLong(file.length());
        FileInputStream fis = new FileInputStream(file);
        int bytes;
        byte[] buffer = new byte[(int) file.length()];
        while ((bytes = fis.read(buffer, 0, buffer.length)) > 0) {
            dos.write(buffer, 0, bytes);
        }
        fis.close();
        dos.flush();
    }

    /**
     * Get all files uploaded to server, download selected files to user selected folder
     *
     * @throws IOException
     */
    private void handleDownload() throws IOException {
        // On first interaction, get all files and load to list
        if (fileList == null) {
            dos.writeInt(RequestType.FILES.ordinal());
        }
        while (!loaded) {
            // Wait for server to load file list
        }

        // Display of all files
        ListView<String> listViewFiles = new ListView<String>();
        for (String filename : fileList) {
            listViewFiles.getItems().add(filename);
        }
        listViewFiles.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE); // Support multi-select for downloading multiple files
        Dialog files = new Dialog();
        files.setHeaderText("File on chat room server");
        files.getDialogPane().setContent(listViewFiles);
        ButtonType downloadButtonType = new ButtonType("Download");
        files.getDialogPane().getButtonTypes().addAll(downloadButtonType, ButtonType.CANCEL);
        Button btnDownload = (Button) files.getDialogPane().lookupButton(downloadButtonType);
        btnDownload.setDisable(true);

        // Disable download button if no file selected
        listViewFiles.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observableValue, String s, String t1) {
                if (listViewFiles.getSelectionModel().getSelectedItems().size() == 0) {
                    btnDownload.setDisable(true);
                } else {
                    btnDownload.setDisable(false);
                }
            }
        });
        files.setResultConverter(dialogButton -> {
            if (dialogButton == downloadButtonType) {
                return listViewFiles.getSelectionModel().getSelectedItems();
            }
            return null;
        });
        Optional<ObservableList<String>> result = files.showAndWait();
        result.ifPresent(new Consumer<ObservableList<String>>() {
            @Override
            public void accept(ObservableList<String> list) {
                DirectoryChooser dc = new DirectoryChooser();
                File dir = dc.showDialog(stage);
                if (dir != null) {
                    for (String filename : list) {
                        try {
                            downloadFileFromServer(filename, dir.getAbsolutePath());
                        } catch (IOException ioe) {
                            alert(Alert.AlertType.ERROR, "ERROR", ioe.getMessage());
                        }
                    }
                }
            }
        });
    }

    /**
     * Notify server download request
     *
     * @param filename name of file on server
     * @param path     user selected destination path
     * @throws IOException
     */
    private void downloadFileFromServer(String filename, String path) throws IOException {
        dos.writeInt(RequestType.DOWNLOAD.ordinal());
        dos.writeUTF(filename);
        dos.writeUTF(path);
        dos.flush();
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
            alert(Alert.AlertType.ERROR, "Error", e.getMessage());
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
            dos.flush();
        } catch (IOException ioe) {
            alert(Alert.AlertType.ERROR, "Server Unavailable", ioe.getMessage());
            System.exit(0);
        }
    }

    /**
     * Utility function for showing alert message
     *
     * @param type   alert type
     * @param header header text
     * @param msg    alert message
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
                        case FILES:
                            populateFileList();
                            break;
                        case UPLOAD:
                            updateFileList();
                            break;
                        case DOWNLOAD:
                            downloadFile();
                            break;
                        case DIRECT_MESSAGE:
                            String sender = dis.readUTF();
                            String directMessage = dis.readUTF();
                            directMessage(sender, directMessage);
                            break;
                        default:
                            System.out.println("Unknown response type received");
                            break;
                    }
                } catch (EOFException eof) {
                    log("Server disconnected.");
                    Thread.currentThread().interrupt();
                } catch (SocketException se) {
                    System.out.println(se.getMessage());
                } catch (Exception e) {
                    String methodName = e.getStackTrace()[0].getMethodName();
                    alert(Alert.AlertType.ERROR, "Error", "Error invoking " + methodName + ": " + e.getMessage());
                }
            }
        }

        /**
         * Receive private message
         *
         * @param sender  message sender
         * @param message private message
         */
        private void directMessage(String sender, String message) {
            Platform.runLater(() -> {
                Boolean error = sender.equals("ERROR");
                if (!error) {
                    log(message);
                }

                // Show popup notification
                Popup popup = new Popup();
                FlowPane fp = new FlowPane(8, 8);
                fp.setPadding(new Insets(10, 5, 10, 5));
                fp.setAlignment(Pos.CENTER);
                fp.setStyle("-fx-background-color: #D3D3D3; -fx-font-family: monospace; -fx-border-radius: 30; -fx-background-radius: 30; -fx-opacity: 0.8");
                String notificationMessage = error ? sender + ": " + message : sender + " just sent you a private message";
                Label notification = new Label(notificationMessage);
                notification.setStyle(String.format("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: %s", error ? "red" : "black"));
                fp.getChildren().add(notification);
                popup.getContent().add(fp);
                popup.setAutoHide(true);
                popup.show(stage);
            });
        }

        /**
         * Wrapper for alert function to run in thread
         *
         * @param type   alert type
         * @param header header text
         * @param msg    alert message
         */
        private void alertLater(Alert.AlertType type, String header, String msg) {
            Platform.runLater(() -> {
                alert(type, header, msg);
            });
        }

        /**
         * Handle download response
         *
         * @throws IOException
         */
        private void downloadFile() throws IOException {
            long fileSize = dis.readLong();
            String filePath = dis.readUTF();
            File file = new File(filePath);
            FileOutputStream fos = new FileOutputStream(file);
            if (!(fileSize > 0)) {
                return;
            }
            int bytes;
            byte[] buffer = new byte[(int) fileSize];
            while (fileSize > 0 && (bytes = dis.read(buffer, 0, (int) Math.min(buffer.length, fileSize))) != -1) {
                fos.write(buffer, 0, bytes);
                fileSize -= bytes;
            }
        }

        /**
         * On new file uploaded, update file list
         *
         * @throws IOException
         */
        private void updateFileList() throws IOException {
            String filename = dis.readUTF();
            if (fileList != null) {
                fileList.add(filename); // TODO: use vector or lock
            }
        }

        /**
         * On initialization, populate file list
         *
         * @throws IOException
         */
        private void populateFileList() throws IOException {
            String[] filenames = dis.readUTF().split(",");
            fileList = new ArrayList<>(Arrays.asList(filenames));
            loaded = true;
        }

        /**
         * Populate user status section
         *
         * @throws IOException
         */
        private void loadUsersInChatRoom() throws IOException {
            String username = dis.readUTF();
            Status status = STATUS_TYPES[dis.readInt()];

            Platform.runLater(() -> {
                Pair<String, Status> user = new Pair<String, Status>(username, status);
                // Check if user already in list view
                int idx = listViewUsers.getItems().indexOf(new Pair<String, Status>(username, STATUS_TYPES[1 - status.ordinal()]));
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

        /**
         * Print message in chat section
         *
         * @param message chat message
         */
        private void log(String message) {
            Platform.runLater(() -> taChat.appendText(message + "\n"));
        }
    }
}
