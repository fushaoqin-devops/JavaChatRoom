import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Pair;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Client for sales associates to place orders and get total number of orders placed
 *
 * @author Shaoqin Fu
 * @version 03/25/2022
 */
public class Client extends Application implements EventHandler<ActionEvent> {
    private Stage stage;
    private Scene scene;
    private VBox root = new VBox(8);

    // UI Components
    private MenuBar menuBar = new MenuBar();
    private Menu menu = new Menu("Options");
    private MenuItem menuItemUpload = new MenuItem("Upload File");
    private MenuItem menuItemLogout = new MenuItem("Logout");
    private TextArea taChat = new TextArea();
    private TextArea taInput = new TextArea();
    private Button btnSend = new Button("Send");

    // Other attributes
    public static final int SERVER_PORT = 8080;
    private Socket socket = null;
    private DataOutputStream dos = null;
    private DataInputStream dis = null;
    private Thread messageService = null;

    public static void main(String[] args) {
        launch(args);
    }

    public void start(Stage stage) {
        stage = stage;
        stage.setOnCloseRequest((WindowEvent windowEvent) -> {
            disconnectServer();
            System.exit(0);
        });

        menu.getItems().addAll(menuItemUpload, menuItemLogout);
        menuBar.getMenus().addAll(menu);
        menuItemUpload.setOnAction(this);
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

        taChat.setDisable(true);
        taChat.setStyle("-fx-font-family: monospace; -fx-opacity: 1.0");
        root.getChildren().addAll(menuBar, taChat, fpBot);
        scene = new Scene(root, 500, 300);
        stage.setScene(scene);
        final Stage mainStage = stage;
        stage.setOnShowing((WindowEvent windowEvent) -> {
            LoginDialog loginDialog = new LoginDialog();
            Optional<Pair<String, String>> result = loginDialog.showAndWait();
            result.ifPresentOrElse((Pair<String, String> loginCredentials) -> {
                doConnect(loginCredentials.getKey(), loginCredentials.getValue());
                mainStage.setTitle("Chat Room - " + loginCredentials.getValue());
            }, () -> System.exit(0));
        });
        stage.show();
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
        switch (command) {
            case "Send":
                handleSend();
                break;
            case "Upload File":
                handleUpload();
                break;
            case "Logout":
                handleLogout();
                break;
            default:
                break;
        }
    }

    private void handleLogout() {
//        dos.writeInt(RequestType.LOGOUT.ordinal());
//        dos.close();
//        dis.close();
//        socket.close();
//        stage.close();
//        Platform.runLater(() -> new Client().start);
    }

    private void handleUpload() {
        // TODO: upload file to server
    }

    private void handleSend() {
        try {
            String message = taInput.getText();
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
            dos.writeUTF("Disconnect");
            dis.close();
            dos.close();
            socket.close();
            messageService.interrupt();
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
     * Dialog utility function
     *
     * @param headerText  header text
     * @param contentText prompt text
     * @param action      Consumer action for handling yes
     */
    private void showDialog(String headerText, String contentText, Consumer<String> action) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setHeaderText(headerText);
        dialog.setContentText(contentText);

        // handle action
        Optional<String> result = dialog.showAndWait();
        result.ifPresentOrElse(action, () -> System.exit(0));
    }

    class ProcessThread extends Thread {
        private DataInputStream dis;
        private boolean serverRunning;

        public ProcessThread(DataInputStream dis) {
            this.dis = dis;
            serverRunning = true;
        }

        @Override
        public void run() {
            while (serverRunning) {
                try {
                    String message = dis.readUTF();
                    log(message + "\n");
                } catch (EOFException eof) {
                    log("Server disconnected.");
                    serverRunning = false;
                    Thread.currentThread().interrupt();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void log(String message) {
            Platform.runLater(() -> taChat.appendText(message + "\n"));
        }
    }
}
