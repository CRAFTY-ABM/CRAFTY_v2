package de.cesr.crafty.gui.utils.graphical;

import java.util.List;
import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import de.cesr.crafty.gui.main.FxMain;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * @author Mohamed Byari
 *
 */

public class WarningWindowes {
	static String p = "";
	private static final AtomicBoolean LOADING = new AtomicBoolean(false);

	public static String alterErrorNotFileFound(String message, String path) {
		p = path;
		Alert alert = new Alert(AlertType.WARNING);
		ButtonType selectfile = new ButtonType("Select another file", ButtonBar.ButtonData.OK_DONE);
		alert.setTitle("Error");
		alert.setHeaderText(message + " \n" + path);
		System.out.println(message + " \n" + path);
		alert.getButtonTypes().setAll(selectfile, ButtonType.NO);
		alert.showAndWait().ifPresent(response -> {
			if (response == selectfile) {
				FileChooser fileChooser = new FileChooser();
				fileChooser.setTitle("Select a CSV file");
				File selectedFile = fileChooser.showOpenDialog(FxMain.primaryStage);
				if (selectedFile != null) {
					p = selectedFile.getAbsolutePath();
				}
			}
		});
		return p;
	}

	public static void showWarningMessage(String message, String okbuttonName, Consumer<String> Retry,
			String cancelbuttonName, Consumer<String> continuAnyWay) {
		showWarningMessage(message, okbuttonName, Retry, cancelbuttonName, continuAnyWay, new ArrayList<String>());
	}

	public static void showWarningMessage(String message, String okbuttonName, Consumer<String> okbuttonConsumer,
			String cancelbuttonName, Consumer<String> continuAnyWay, List<String> listWarning) {
		ButtonType okButtonType = new ButtonType(okbuttonName, ButtonBar.ButtonData.OK_DONE);
		ButtonType cancelButtonType = new ButtonType(cancelbuttonName, ButtonBar.ButtonData.CANCEL_CLOSE);
		ButtonType customButtonType = new ButtonType("Close CRAFTY");

		Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
		alert.setTitle("Warning");
		alert.setHeaderText(message);
		String str = "";
		for (int i = 0; i < listWarning.size(); i++) {
			str = str + listWarning.get(i) + "\n";
		}
		alert.setContentText(str);

		// Add custom buttons
		alert.getButtonTypes().setAll(okButtonType, cancelButtonType, customButtonType);

		// Handle button actions
		alert.setOnCloseRequest(_ -> {
			ButtonType result = alert.getResult();
			if (result == okButtonType) {
				okbuttonConsumer.accept("");
			} else if (result == cancelButtonType) {
				continuAnyWay.accept("");
			} else if (result == customButtonType) {
				Platform.exit();
			}
		});

		alert.showAndWait();
	}

	public static void showWarningMessage(String message, String okButtonName, Consumer<String> okButtonConsumer) {
		ButtonType okButtonType = new ButtonType(okButtonName, ButtonBar.ButtonData.OK_DONE);
		ButtonType customButtonType = new ButtonType("Close CRAFTY");

		Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
		alert.setTitle("Warning");
		alert.setHeaderText(message);

		// Add custom buttons
		alert.getButtonTypes().setAll(okButtonType, customButtonType);

		// Handle button actions
		alert.setOnCloseRequest(_ -> {
			ButtonType result = alert.getResult();
			if (result == okButtonType) {
				okButtonConsumer.accept("");
			} else if (result == customButtonType) {
				Platform.exit();
			}
		});

		alert.showAndWait();
	}

	public static void showWaitingDialog(Consumer<String> action) {
		if (!Platform.isFxApplicationThread()) {
			Platform.runLater(() -> showWaitingDialog(action));
			return;
		}
		if (!LOADING.compareAndSet(false, true)) {
			return;
		}

		Stage waitingDialog = new Stage();
		if (FxMain.primaryStage != null && FxMain.primaryStage.isShowing()) {
			waitingDialog.initOwner(FxMain.primaryStage);
		}
		waitingDialog.initModality(Modality.APPLICATION_MODAL);
		waitingDialog.initStyle(StageStyle.UNDECORATED);
		Label label = new Label("Please wait...");
		ProgressIndicator progressIndicator = new ProgressIndicator();
		progressIndicator.setCenterShape(true);
		VBox root = new VBox();
		root.setAlignment(Pos.CENTER);
		root.setSpacing(10);
		root.getChildren().addAll(label, progressIndicator);

		Scene scene = new Scene(root, 200, 100);
		waitingDialog.setScene(scene);
		waitingDialog.setOnCloseRequest(event -> {
			if (LOADING.get()) {
				event.consume();
			}
		});
		waitingDialog.show();

		// Defer the work once so the waiting dialog is rendered before loading starts.
		Platform.runLater(() -> {
			try {
				action.accept("");
			} finally {
				LOADING.set(false);
				waitingDialog.close();
			}
		});
	}

}
