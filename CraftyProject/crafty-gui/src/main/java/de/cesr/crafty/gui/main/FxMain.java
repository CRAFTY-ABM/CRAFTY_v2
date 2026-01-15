package de.cesr.crafty.gui.main;

import java.io.InputStream;
import java.net.URL;

import de.cesr.crafty.gui.utils.graphical.CraftyLogoNode;
import de.cesr.crafty.gui.utils.graphical.Tools;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
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
		addLogo();
		scene = new Scene(new Group(topLevelBox));
		scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
//		new GuiScaler(primaryStage, anchor);
		primaryStage.setTitle(" CRAFTY User Interface ");
		primaryStage.setScene(scene);
		primaryStage.setMaximized(true);
		primaryStage.show();
		primaryStage.setOnCloseRequest(_ -> Platform.exit());
	}

	private void addLogo() {
		InputStream imageStream = getClass().getResourceAsStream("/graphic/CRAFTY_logo_modern3.png");
		logo = Tools.logo(imageStream, 1);
		CraftyLogoNode logoD = new CraftyLogoNode();
		logoD.playEntry();
		logoD.playLoading();
		GuiScaler.scaleLogoD(logoD);
		anchor.getChildren().add(logoD);
	}

	public static void main(String[] args) {
		launch(args);
	}

}
