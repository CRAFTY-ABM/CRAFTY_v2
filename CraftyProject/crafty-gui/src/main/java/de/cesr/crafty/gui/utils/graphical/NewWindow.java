package de.cesr.crafty.gui.utils.graphical;

import java.util.function.Consumer;

import de.cesr.crafty.gui.main.GuiScaler;
//import de.cesr.crafty.gui.utils.camera.Camera;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.SubScene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.scene.layout.BorderPane;

/**
 * @author Mohamed Byari
 *
 */

public class NewWindow extends Stage {

	public void creatwindows(String name, double Width, double Height, Node... nodes) {

		Scene scene;
		StackPane rootPane = new StackPane();

		rootPane.getChildren().addAll(nodes);

		scene = new Scene(rootPane, GuiScaler.lastScreen.getBounds().getWidth() * Width,
				GuiScaler.lastScreen.getBounds().getHeight() * Height);

		setTitle(name);
		setScene(scene);
		setAlwaysOnTop(true);
		show();
	}

	public static Stage createWin(String name,  Node... nodes) {
		Stage newWin = new Stage();
		Scene scene;
		StackPane rootPane = new StackPane();

		rootPane.getChildren().addAll(nodes);

		scene = new Scene(rootPane);

		newWin.setTitle(name);
		newWin.setScene(scene);
		newWin.setAlwaysOnTop(true);
		newWin.show();
		return newWin;
	}

	public void creatwindows(String name, Node... nodes) {
		Scene scene;
		StackPane rootPane = new StackPane();
		rootPane.getChildren().addAll(nodes);
		scene = new Scene(rootPane);

		setTitle(name);
		setScene(scene);
		setAlwaysOnTop(true);
		show();
	}

	public void creatwindows(String name, Node nodes, Consumer<Stage> action) {
		action.accept(this);
		creatwindows(name, nodes);
	}

	public SubScene subSceneWithCamera(BorderPane rootPane, Node... nodes) {

		Group root = new Group();
		SubScene subScene;
//		Camera camera = new Camera();

		subScene = new SubScene(root, GuiScaler.lastScreen.getBounds().getWidth() * 0.5,
				GuiScaler.lastScreen.getBounds().getHeight() * .8);
		subScene.setFocusTraversable(true);
		// subScene.widthProperty().bind(rootPane.widthProperty());
		// subScene.heightProperty().bind(rootPane.heightProperty());
//		subScene.setCamera(camera);
		root.getChildren().addAll(nodes);
		// camera.defaultcamera(root,subScene);
		// camera.adjustCamera(root,subScene);

		return subScene;
	}
}
