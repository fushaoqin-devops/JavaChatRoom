import javafx.scene.control.ListCell;
import javafx.scene.paint.Color;
import javafx.util.Pair;

/**
 * Utility class to change list view text color based on user status
 */
public class UserStatusCell extends ListCell<Pair<String, Status>> {
    @Override
    protected void updateItem(Pair<String, Status> user, boolean b) {
        super.updateItem(user, b);
        if (user != null) {
            setText(user.getKey());
            if (user.getValue() == Status.online) {
                setTextFill(Color.GREEN);
            } else {
                setTextFill(Color.GRAY);
            }
        }
    }
}

