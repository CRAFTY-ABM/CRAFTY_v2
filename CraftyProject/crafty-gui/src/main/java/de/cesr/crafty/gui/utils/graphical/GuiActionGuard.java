package de.cesr.crafty.gui.utils.graphical;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Scene;
import javafx.scene.control.ButtonBase;
import javafx.util.Duration;

/**
 * Prevents a button from dispatching the same action repeatedly while its first
 * action is still being processed by the JavaFX application thread.
 */
public final class GuiActionGuard {

	private static final Duration RELEASE_DELAY = Duration.millis(300);

	private GuiActionGuard() {
	}

	public static void install(Scene scene) {
		Set<Object> activeControls = Collections.newSetFromMap(new IdentityHashMap<>());

		scene.addEventFilter(ActionEvent.ACTION, event -> {
			Object source = event.getSource();
			if (!(source instanceof ButtonBase)) {
				return;
			}

			if (!activeControls.add(source)) {
				event.consume();
				return;
			}

			// Start the release delay only after the current action and any already
			// queued duplicate clicks have had a chance to be processed.
			Platform.runLater(() -> {
				PauseTransition release = new PauseTransition(RELEASE_DELAY);
				release.setOnFinished(_ -> activeControls.remove(source));
				release.play();
			});
		});
	}
}
