package de.cesr.crafty.gui.utils.graphical;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

public class MousePressed {
	private static final String DETACHED_MARKER = MousePressed.class.getName() + ".detached";

	/**
	 * @author Mohamed Byari
	 *
	 */

	public static void mouseControle(Pane pane, Node node) {
		mouseControle(pane, node, null, "titel");
	}

	public static void mouseControle(Pane pane, Node node, String titel) {
		mouseControle(pane, node, null, titel);
	}

	public static void mouseControle(Pane box, Node node, HashMap<String, Consumer<String>> othersMenuItems) {
		mouseControle(box, node, othersMenuItems, "titel");
	}

	public static void mouseControle(Pane box, Node node, HashMap<String, Consumer<String>> othersMenuItems,
			String titel) {
		HashMap<String, Consumer<String>> hashAction = new HashMap<>();
		if (othersMenuItems != null) {
			othersMenuItems.forEach((name, action) -> {
				hashAction.put(name, action);
			});
		}

		hashAction.put("Save as PNG", _ -> {
			SaveAs.png(titel, node);

		});
//		hashAction.put("Close", (x) -> {
//			Parent m = node.getParent();
//			((Pane) m).getChildren().remove(node);
//		});
		hashAction.put("Detach", _ -> {
			if (box == null || Boolean.TRUE.equals(node.getProperties().get(DETACHED_MARKER))
					|| !isDescendantOf(node, box)) {
				return;
			}

			List<Integer> findpath = Tools.findIndexPath(node, box);
			if (findpath.isEmpty()) {
				return;
			}

			SizingSnapshot sizing = SizingSnapshot.capture(node);
			Separator placeholder = new Separator();
			Tools.reInsertChildAtIndexPath(placeholder, box, findpath);
			node.getProperties().put(DETACHED_MARKER, Boolean.TRUE);
			sizing.release();

			NewWindow win = new NewWindow();
			win.creatwindows(titel, node);
			win.setMinWidth(360);
			win.setMinHeight(280);

			URL stylesheet = MousePressed.class.getResource("/styles.css");
			if (stylesheet != null && win.getScene() != null) {
				win.getScene().getStylesheets().add(stylesheet.toExternalForm());
			}

			win.setOnCloseRequest(_ -> {
				removeFromCurrentParent(node);
				sizing.restore();
				if (!replacePlaceholder(placeholder, node)) {
					Tools.reInsertChildAtIndexPath(node, box, findpath);
				}
				node.getProperties().remove(DETACHED_MARKER);
				box.requestLayout();
			});
		});
		MousePressed.smartMenu(node, hashAction);
	}

	private static boolean isDescendantOf(Node node, Parent expectedAncestor) {
		Node current = node;
		while (current != null) {
			if (current == expectedAncestor) {
				return true;
			}
			current = current.getParent();
		}
		return false;
	}

	private static void removeFromCurrentParent(Node node) {
		Parent parent = node.getParent();
		if (parent instanceof Pane pane) {
			pane.getChildren().remove(node);
		} else if (parent instanceof Group group) {
			group.getChildren().remove(node);
		}
	}

	private static boolean replacePlaceholder(Node placeholder, Node node) {
		Parent parent = placeholder.getParent();
		if (parent instanceof Pane pane) {
			int index = pane.getChildren().indexOf(placeholder);
			if (index >= 0) {
				pane.getChildren().set(index, node);
				return true;
			}
		} else if (parent instanceof Group group) {
			int index = group.getChildren().indexOf(placeholder);
			if (index >= 0) {
				group.getChildren().set(index, node);
				return true;
			}
		}
		return false;
	}

	private static final class SizingSnapshot {
		private final List<RegionSizing> regions = new ArrayList<>();

		private static SizingSnapshot capture(Node root) {
			SizingSnapshot snapshot = new SizingSnapshot();
			snapshot.captureNode(root);
			return snapshot;
		}

		private void captureNode(Node node) {
			if (node instanceof Region region) {
				regions.add(new RegionSizing(region));
			}

			// A Control manages its own skin children. Only user-created container children
			// should have their fixed sizing released.
			if (node instanceof Parent parent && !(node instanceof Control)) {
				for (Node child : parent.getChildrenUnmodifiable()) {
					captureNode(child);
				}
			}
		}

		private void release() {
			regions.forEach(RegionSizing::release);
		}

		private void restore() {
			for (int i = regions.size() - 1; i >= 0; i--) {
				regions.get(i).restore();
			}
		}
	}

	private static final class RegionSizing {
		private final Region region;
		private final double minWidth;
		private final double prefWidth;
		private final double maxWidth;
		private final double minHeight;
		private final double prefHeight;
		private final double maxHeight;
		private final boolean minWidthBound;
		private final boolean prefWidthBound;
		private final boolean maxWidthBound;
		private final boolean minHeightBound;
		private final boolean prefHeightBound;
		private final boolean maxHeightBound;

		private RegionSizing(Region region) {
			this.region = region;
			minWidth = region.getMinWidth();
			prefWidth = region.getPrefWidth();
			maxWidth = region.getMaxWidth();
			minHeight = region.getMinHeight();
			prefHeight = region.getPrefHeight();
			maxHeight = region.getMaxHeight();
			minWidthBound = region.minWidthProperty().isBound();
			prefWidthBound = region.prefWidthProperty().isBound();
			maxWidthBound = region.maxWidthProperty().isBound();
			minHeightBound = region.minHeightProperty().isBound();
			prefHeightBound = region.prefHeightProperty().isBound();
			maxHeightBound = region.maxHeightProperty().isBound();
		}

		private void release() {
			if (!minWidthBound) {
				region.setMinWidth(0);
			}
			if (!prefWidthBound) {
				region.setPrefWidth(Region.USE_COMPUTED_SIZE);
			}
			if (!maxWidthBound) {
				region.setMaxWidth(Double.MAX_VALUE);
			}
			if (!minHeightBound) {
				region.setMinHeight(0);
			}
			if (!prefHeightBound) {
				region.setPrefHeight(Region.USE_COMPUTED_SIZE);
			}
			if (!maxHeightBound) {
				region.setMaxHeight(Double.MAX_VALUE);
			}
		}

		private void restore() {
			if (!minWidthBound) {
				region.setMinWidth(minWidth);
			}
			if (!prefWidthBound) {
				region.setPrefWidth(prefWidth);
			}
			if (!maxWidthBound) {
				region.setMaxWidth(maxWidth);
			}
			if (!minHeightBound) {
				region.setMinHeight(minHeight);
			}
			if (!prefHeightBound) {
				region.setPrefHeight(prefHeight);
			}
			if (!maxHeightBound) {
				region.setMaxHeight(maxHeight);
			}
		}
	}

	static void smartMenu(Node node, HashMap<String, Consumer<String>> hash) {
		ContextMenu cm = new ContextMenu();

		MenuItem[] item = new MenuItem[hash.size()];
		AtomicInteger i = new AtomicInteger();
		hash.forEach((k, v) -> {
			item[i.get()] = new MenuItem(k);
			cm.getItems().add(item[i.get()]);
			item[i.get()].setOnAction(_ -> {
				v.accept(k);
			});
			i.getAndIncrement();
		});

		node.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
			if (cm.isShowing()) {
				cm.hide();
			}
			if (e.isSecondaryButtonDown()) {
				cm.show((Stage) node.getScene().getWindow(), e.getScreenX(), e.getScreenY());
				e.consume();
			}
		});
	}

}
