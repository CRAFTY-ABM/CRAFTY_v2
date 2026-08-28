package de.cesr.crafty.gui.main;

import java.net.URL;

import de.cesr.crafty.gui.logging.GuiLogAppender;
import de.cesr.crafty.gui.utils.graphical.CraftyLogoNode;
import de.cesr.crafty.gui.utils.graphical.GuiActionGuard;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/*
 * @author Mohamed Byari
 *
 */

public class FxMain extends Application {

	public static Stage primaryStage;
	public static Scene scene;
	public static VBox topLevelBox = new VBox();
	public static BorderPane anchor = new BorderPane();
	public static ImageView logo;

	@Override
	public void start(Stage primaryStage) throws Exception {
		System.out.println("--Starting CRAFTY execution--");
		FxMain.primaryStage = primaryStage;
		URL fxml = FxMain.class.getResource("/fxmlControllers/MenuBar.fxml");
		topLevelBox.getChildren().add(FXMLLoader.load(fxml));
		topLevelBox.getChildren().add(anchor);
		VBox.setVgrow(anchor, Priority.ALWAYS);
		anchor.setMinSize(0, 0);
		addLogo();
		scene = new Scene(topLevelBox, 1280, 800);
		scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
		GuiActionGuard.install(scene);
		primaryStage.setTitle("CRAFTY User Interface");
		primaryStage.setScene(scene);
		primaryStage.setMinWidth(900);
		primaryStage.setMinHeight(650);
		primaryStage.setMaximized(true);
		primaryStage.show();
		GuiScaler.reScale(primaryStage);
		GuiLogAppender.install(primaryStage);
		primaryStage.setOnCloseRequest(_ -> Platform.exit());
	}

	@Override
	public void stop() {
		GuiLogAppender.uninstall();
	}

	private void addLogo() {
		CraftyLogoNode logoD = new CraftyLogoNode();
		logoD.setManaged(true);
		logoD.playEntry();
		logoD.playLoading();
		GuiScaler.scaleLogoD(logoD);

		// Scale the wrapper instead of the animated logo. The entry animation changes
		// the logo's own scale, while this wrapper keeps the final display at 50%.
		Group logoWrapper = new Group(logoD);
		logoWrapper.setScaleX(0.7);
		logoWrapper.setScaleY(0.7);
		StackPane welcomePane = new StackPane(logoWrapper);
		welcomePane.getStyleClass().add("welcome-pane");
		anchor.setCenter(welcomePane);
	}

	public static void main(String[] args) {
		launch(args);
	}

}
