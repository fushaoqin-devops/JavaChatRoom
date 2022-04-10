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
    USERS;
}

/**
 * Client for sales associates to place orders and get total number of orders placed
 *
 * @author Shaoqin Fu
 * @version 03/25/2022
 */
public class Client extends Application implements EventHandler<ActionEvent> {
    private Stage stage;
    private Scene scene;
    private HBox root = new HBox(8);
//    private VBox root = new VBox(8);

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
    public static final ResponseType[] RESPONSE_TYPES = ResponseType.values();
    public static final Status[] STATUS_TYPES = Status.values();
    private Socket socket = null;
    private DataOutputStream dos = null;
    private DataInputStream dis = null;
    private Thread messageService = null;
    private String currentUserName = "";

    public static void main(String[] args) {
        launch(args);
    }

    public void start(Stage _stage) {
        stage = _stage;
        stage.setOnCloseRequest((WindowEvent windowEvent) -> {
            disconnectServer();
            System.exit(0);
        });

        menuFile.getItems().addAll(menuItemUpload, menuItemDownload);
        menuUser.getItems().addAll(menuItemChangeRoom, menuItemLogout);
        menuBar.getMenus().addAll(menuFile, menuUser);
        menuItemUpload.setOnAction(this);
        menuItemDownload.setOnAction(this);
        menuItemChangeRoom.setOnAction(this);
        menuItemLogout.setOnAction(this);

        FlowPane fpBot = new FlowPane(8, 8);
        taInput.setPrefColumnCount(30);
        taInput.setPrefRowCount(3);
        taInput.setOnKeyPressed(keyEvent -> {
            if (keyEvent.getCode() == KeyCode.ENTER) {
                if (keyEvent.isShiftDown()) {
                    taInput.appendText("\n");
                } else if (!taInput.getText().trim().equals("")) {
                    handleSend();
                } else {
                    taInput.clear();
                }
            }
        });
        btnSend.setDisable(true);
        taInput.textProperty().addListener((observable, oldValue, newValue) -> btnSend.setDisable(newValue.trim().length() == 0));
        fpBot.getChildren().addAll(taInput, btnSend);

        btnSend.setOnAction(this);

        VBox chatSection = new VBox(8);
        taChat.setEditable(false);
        taChat.setStyle("-fx-font-family: monospace; -fx-opacity: 1.0");
        chatSection.getChildren().addAll(menuBar, taChat, fpBot);

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
        stage.setOnShowing((WindowEvent windowEvent) -> {
            Pair<String, String> prevSessionInfo = loadUserInfo();
            if (prevSessionInfo != null) {
                doConnect(prevSessionInfo.getKey(), prevSessionInfo.getValue());
                mainStage.setTitle("Chat Room - " + prevSessionInfo.getValue());
            } else {
                LoginDialog loginDialog = new LoginDialog();
                Optional<Pair<String, String>> result = loginDialog.showAndWait();
                result.ifPresentOrElse((Pair<String, String> loginCredentials) -> {
                    doConnect(loginCredentials.getKey(), loginCredentials.getValue());
                    mainStage.setTitle("Chat Room - " + loginCredentials.getValue());
                }, () -> System.exit(0));
            }
        });
        stage.show();
    }

    private Pair<String, String> loadUserInfo() {
        try {
            File file = new File("./TempUser/CurrentUser.txt");
            FileInputStream fis = new FileInputStream(file);
            DataInputStream dis = new DataInputStream(fis);
            String username = dis.readUTF();
            String roomId = dis.readUTF();
            return new Pair<String, String>(username, roomId);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Menu item dispatcher
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
        System.out.println(command);
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

    private void handleChangeRoom() throws IOException {

        showDialog("Do you want to join another room?", "Room ID: ", (String id) -> {
            try {
                dos.writeInt(RequestType.LOGOUT.ordinal());
                disconnectServer();
                File dir = new File("./TempUser");
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                File tempUserFile = new File("./TempUser/CurrentUser.txt");
                FileOutputStream tmpFileOutputStream = new FileOutputStream(tempUserFile, false);
                DataOutputStream output = new DataOutputStream(tmpFileOutputStream);
                output.writeUTF(currentUserName);
                output.writeUTF(id);
                ((Stage) scene.getWindow()).close();
                Platform.runLater(() -> {
                    new Client().start(new Stage());
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, () -> {
            return;
        });
    }

    private void handleLogout() {

        ArrayList<ButtonType> buttons = new ArrayList<>();
        ButtonType logoutButton = new ButtonType("Logout", ButtonBar.ButtonData.OK_DONE);
        buttons.add(logoutButton);
        buttons.add(ButtonType.CANCEL);

        showDialog("Logout", "Do you want to logout?", (btnType) -> {
            if (btnType == logoutButton) {
                try {
                    dos.writeInt(RequestType.LOGOUT.ordinal());
                    disconnectServer();
                    System.exit(0);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, buttons);
    }

    private void handleUpload() {
        // TODO: upload file to server
    }

    private void handleDownload() {
        // TODO: download file from server
    }

    private void handleSend() {
        try {
            String message = taInput.getText().trim();
            dos.writeInt(RequestType.MESSAGE.ordinal());
            dos.writeUTF(message);
            dos.flush();
            taInput.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }

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
            dos.writeInt(RequestType.USERS.ordinal());
            System.out.println("here");
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

    private void showDialog(String headerText, String contentText, Consumer<String> action, Runnable emptyAction) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setHeaderText(headerText);
        dialog.setContentText(contentText);

        // handle action
        Optional<String> result = dialog.showAndWait();
        result.ifPresentOrElse(action, emptyAction);
    }

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

        private void loadUsersInChatRoom() throws IOException {
            String username = dis.readUTF();
            System.out.println(username);
            Status status = STATUS_TYPES[dis.readInt()];
            System.out.println(status);
            Platform.runLater(() -> {
                Pair<String, Status> user = new Pair(username, status);
                int idx = listViewUsers.getItems().indexOf(new Pair(username, STATUS_TYPES[1 - status.ordinal()]));
                if (idx == -1) {
                    listViewUsers.getItems().add(user);
                } else {
                    listViewUsers.getItems().set(idx, user);
                }
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

        private void log(String message) {
            Platform.runLater(() -> taChat.appendText(message + "\n"));
        }
    }
}
