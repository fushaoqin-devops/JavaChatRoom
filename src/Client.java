import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
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

    public void start(Stage _stage) {
        stage = _stage;
        stage.setTitle("Chat Server");
        stage.setOnCloseRequest(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent windowEvent) {
                disconnectServer();
                System.exit(0);
            }
        });

        FlowPane fpBot = new FlowPane(8, 8);
        taInput.setPrefColumnCount(30);
        taInput.setPrefRowCount(3);
        btnSend.setDisable(true);
        taInput.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.length() != 0) {
                btnSend.setDisable(false);
            } else {
                btnSend.setDisable(true);
            }
        });
        fpBot.getChildren().addAll(taInput, btnSend);

        btnSend.setOnAction(this);

        taChat.setDisable(true);
        root.getChildren().addAll(taChat, fpBot);
        scene = new Scene(root, 500, 300);
        stage.setScene(scene);
        stage.setOnShowing(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent windowEvent) {
//                showDialog("User Login", "Please enter your username: ", new Consumer<String>() {
//                    @Override
//                    public void accept(String username) {
//                        doConnect(username);
//                    }
//                });
                LoginDialog loginDialog = new LoginDialog();
                Optional<Pair<String, String>> result = loginDialog.showAndWait();
                result.ifPresentOrElse(new Consumer<Pair<String, String>>() {
                    @Override
                    public void accept(Pair<String, String> loginCredentials) {
                        doConnect(loginCredentials.getKey(), loginCredentials.getValue());
                    }
                }, new Runnable() {
                    @Override
                    public void run() {
                        System.exit(0);
                    }
                });
            }
        });
        stage.show();
    }

    /**
     * Menu item dispatcher
     *
     * @param ae triggered action event
     */
    public void handle(ActionEvent ae) {
        Button btn = (Button) ae.getSource();

        switch (btn.getText()) {
            case "Send":
                handleSend();
                break;
        }
    }

    private void handleSend() {
        try {
            String message = taInput.getText();
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
            dos.writeUTF(new String("Disconnect"));
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
            alert(Alert.AlertType.ERROR, "Error", "IO Exception: " + ioe + "\n");
            return;
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
        result.ifPresentOrElse(action, new Runnable() {
            @Override
            public void run() {
                System.exit(0);
            }
        });
    }

    class ProcessThread extends Thread {
        public DataInputStream dis;
        public boolean serverRunning;

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
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    taChat.appendText(message + "\n");
                }
            });
        }
    }
}
