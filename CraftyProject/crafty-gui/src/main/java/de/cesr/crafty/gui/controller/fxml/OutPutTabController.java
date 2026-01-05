package de.cesr.crafty.gui.controller.fxml;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.VBox;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import de.cesr.crafty.core.dataLoader.ProjectLoader;
import de.cesr.crafty.core.dataLoader.serivces.ServiceSet;
import de.cesr.crafty.gui.canvasFx.CellsCanvas;
import de.cesr.crafty.gui.main.FxMain;
import de.cesr.crafty.gui.main.GuiScaler;
import de.cesr.crafty.gui.utils.analysis.NonGraphic;
import de.cesr.crafty.gui.utils.graphical.FileTreeView;
import de.cesr.crafty.gui.utils.graphical.NewWindow;
import de.cesr.crafty.gui.utils.graphical.WarningWindowes;

public class OutPutTabController {
	@FXML
	private TabPane tabpane;
	@FXML
	private Tab addTab;
	@FXML
	private Button selecserivce;
	@FXML
	private VBox fileTreeView;

	NewWindow colorbox = new NewWindow();
	public static RadioButton[] radioColor;
	TreeView<Path> tree;

	private static OutPutTabController instance;

	public OutPutTabController() {
		instance = this;
	}

	public static OutPutTabController getInstance() {
		return instance;
	}

	public void initialize() {
		radioColor = new RadioButton[ServiceSet.getServicesList().size() + 1];
		ToggleGroup radiosgroup = new ToggleGroup();
		for (int i = 0; i < radioColor.length; i++) {
			if (i < ServiceSet.getServicesList().size()) {
				radioColor[i] = new RadioButton(ServiceSet.getServicesList().get(i));

			} else if (i == ServiceSet.getServicesList().size()) {
				radioColor[i] = new RadioButton("AFT");
			}
			radioColor[i].setToggleGroup(radiosgroup);
			int k = i;
			radioColor[i].setOnAction(_ -> {
				CellsCanvas.colorMap(radioColor[k].getText());
			});

		}

		treeFiles();
//		Scale scaleTransform = new Scale(1/FxMain.graphicScaleX, 1/FxMain.graphicScaleY, 0, 0);
//		tree.getChildrenUnmodifiable().forEach(e->{
//			e.getTransforms().add(scaleTransform);
//		});
		
		
	}
	
	@FXML 
	public void reload(){
		fileTreeView.getChildren().remove(tree);
		treeFiles();
	}
	
	private void treeFiles() {
		Path output = Paths.get(ProjectLoader.getProjectPath() + File.separator + "output");
		tree = FileTreeView.build(output, null, 1);
		fileTreeView.getChildren().add(tree);
		double scaleY = GuiScaler.lastScreen.getBounds().getHeight() / (1.2 * FxMain.graphicScaleY);
		tree.setMaxHeight(scaleY);
		tree.setMinHeight(scaleY);
		mouseTreeFiles(tree);
	}
	

	private void mouseTreeFiles(TreeView<Path> tree) {
		tree.setOnMouseClicked(evt -> {
			// react only to a double–click (use 1 for single-click)
			if (evt.getClickCount() == 2) {
				TreeItem<Path> selected = tree.getSelectionModel().getSelectedItem();
				if (selected != null) {
					WarningWindowes.showWaitingDialog(_ -> {
						Path path = selected.getValue();
						OutPuterController.outputpath = path.toAbsolutePath();
						if (NonGraphic.checkDirectFiles(path, "Total-AggregateServiceDemand.csv")) {
							createNewTab(path.getFileName().toString());
						}
					});
				}
			}
		});
	}

	@FXML
	void selecserivce() {
		if (!colorbox.isShowing()) {
			VBox g = new VBox();
			g.getChildren().addAll(radioColor);
			colorbox.creatwindows("Display Services and AFT distribution", g);
		}
	}

	public void createNewTab(String name) {
		Tab tab = new Tab(name);
		try {
			tab.setContent(FXMLLoader.load(getClass().getResource("/fxmlControllers/OutPuter.fxml")));
		} catch (IOException e) {
		}
		tabpane.getTabs().add(tabpane.getTabs().indexOf(addTab), tab);
		tabpane.getSelectionModel().select(tab);
		tabpane.setTabClosingPolicy(TabPane.TabClosingPolicy.ALL_TABS);
		removeTabIfIsEmpty(tab);
	}

	private void removeTabIfIsEmpty(Tab tab) {
		try {
			((VBox) ((VBox) tab.getContent()).getChildren().iterator().next()).getChildren().forEach(s -> {
				if (s instanceof ChoiceBox) {
					if (((ChoiceBox<?>) s).getItems().size() == 0) {
						tabpane.getTabs().remove(tabpane.getTabs().indexOf(addTab) - 1);
					}
				}
			});
		} catch (NullPointerException e) {
			tabpane.getTabs().remove(tabpane.getTabs().indexOf(addTab) - 1);
		}
	}

}
