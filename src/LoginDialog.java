import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.util.Pair;

public class LoginDialog extends Dialog<Pair<String, String>> {
    public LoginDialog() {
        this.setTitle("Welcome");
        this.setHeaderText("Login");
        ButtonType joinButtonType = new ButtonType("Join");
        this.getDialogPane().getButtonTypes().addAll(joinButtonType, ButtonType.CANCEL);
        GridPane content = new GridPane();
        content.setHgap(8);
        content.setVgap(8);
        Label lblUsername = new Label("Username: ");
        Label lblRoomId = new Label("Room ID: ");
        TextField tfUsername = new TextField();
        TextField tfRoomId = new TextField();

        content.addRow(0, lblUsername, tfUsername);
        content.addRow(1, lblRoomId, tfRoomId);

        Button btnJoin = (Button) this.getDialogPane().lookupButton(joinButtonType);
        btnJoin.setDisable(true);
        // Validate username and room id
        tfUsername.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.length() != 0 && !tfRoomId.getText().isEmpty()) {
                btnJoin.setDisable(false);
            } else {
                btnJoin.setDisable(true);
            }
        });
        tfRoomId.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.length() != 0 && !tfUsername.getText().isEmpty()) {
                btnJoin.setDisable(false);
            } else {
                btnJoin.setDisable(true);
            }
        });
        this.getDialogPane().setContent(content);

        setResultConverter(dialogButton -> {
            if (dialogButton == joinButtonType) {
                return new Pair<>(tfUsername.getText(), tfRoomId.getText());
            }
            return null;
        });
    }
}
