package de.cesr.crafty.gui.utils.graphical;
import javafx.animation.*;
import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Translate;
import javafx.util.Duration;

public final class CraftyLogoNode extends Group {

    // Palette
    private static final Color ORANGE = Color.web("#fc9a40ff");
    private static final Color TEAL   = Color.web("#204c53ff");
    private static final Color GREEN  = Color.web("#10B981");//"#"
    private static final Color INK    = Color.web("#111827");
    private static final Color SLATE  = Color.web("#334155");

    // Nodes we animate
    private SVGPath outerRing;
    private SVGPath middleRing;
    private SVGPath innerRing;

    private SVGPath cellGreen;
    private SVGPath cellOrange;
    private SVGPath cellTeal;

    private Animation loadingAnim;
    private Animation entryAnim;

    public CraftyLogoNode() {
        build();
        setCache(true);
        setCacheHint(CacheHint.SPEED);
    }

    // --- Public API ----------------------------------------------------------

    /** Looping animation: rings rotate + grid colours pulse. */
    public void playLoading() {
        if (loadingAnim == null) loadingAnim = createLoadingAnimation();
        loadingAnim.play();
    }

    public void stopLoading(boolean resetToZero) {
        if (loadingAnim != null) loadingAnim.stop();
        if (resetToZero) {
            outerRing.setRotate(0);
            middleRing.setRotate(0);
            innerRing.setRotate(0);
        }
        // restore base colors
        cellOrange.setFill(ORANGE);
        cellOrange.setStroke(ORANGE);
        cellGreen.setFill(GREEN);
        cellGreen.setStroke(GREEN);
        cellTeal.setFill(TEAL);
        cellTeal.setStroke(TEAL);
    }

    /** One-shot “entry” animation (nice when logo first appears). */
    public void playEntry() {
        if (entryAnim == null) entryAnim = createEntryAnimation();
        entryAnim.playFromStart();
    }

    // --- Build nodes (from your SVG) ----------------------------------------

    private void build() {
        setManaged(false);

        Group mark = new Group();
        mark.getTransforms().add(new Translate(-129.82867, -27.986963));

        // Small colored squares near center (your path958, path954, path883)
        cellGreen = svgPath("M 360.97378 245.27933 v -9.5021 h 9.85169 9.85173 v 9.5021 9.50216 h -9.85173 -9.85169 z");
        cellGreen.setFill(GREEN);
        cellGreen.setStroke(GREEN);
        cellGreen.setStrokeWidth(0.180019);

        cellOrange = svgPath("M 337.8733 245.27933 v -9.5021 h 9.85168 9.85167 v 9.5021 9.50216 h -9.85167 -9.85168 z");
        cellOrange.setFill(ORANGE);
        cellOrange.setStroke(ORANGE);
        cellOrange.setStrokeWidth(0.180019);

        cellTeal = svgPath("M 314.97176 224.17444 c 0 -6.8873 0.13778 -8.65736 0.74087 -9.51839"
                + " c 0.72125 -1.02974 0.97951 -1.05774 9.75635 -1.05774"
                + " h 9.01548 v 9.51839 9.51839 h -9.75635 -9.75635 z");
        cellTeal.setFill(TEAL);
        cellTeal.setStroke(TEAL);
        cellTeal.setStrokeWidth(0.899375);

        // Rings (real C arcs)
        outerRing = svgPath("M 486.89 359.7 a 180 180 0 1 1 0 -231.4");
        outerRing.setFill(Color.TRANSPARENT);
        outerRing.setStroke(ORANGE);
        outerRing.setStrokeWidth(44);
        outerRing.setStrokeLineCap(StrokeLineCap.ROUND);
        outerRing.getStrokeDashArray().setAll(92.0, 46.0);
        outerRing.setStrokeDashOffset(8);

        middleRing = svgPath("M 452.42 330.78 a 135 135 0 1 1 0 -173.56");
        middleRing.setFill(Color.TRANSPARENT);
        middleRing.setStroke(TEAL);
        middleRing.setStrokeWidth(38);
        middleRing.setStrokeLineCap(StrokeLineCap.ROUND);
        middleRing.getStrokeDashArray().setAll(78.0, 54.0);
        middleRing.setStrokeDashOffset(10);

        innerRing = svgPath("M 417.94 301.85 a 90 90 0 1 1 0 -115.7");
        innerRing.setFill(Color.TRANSPARENT);
        innerRing.setStroke(GREEN);
        innerRing.setStrokeWidth(34);
        innerRing.setStrokeLineCap(StrokeLineCap.ROUND);
        innerRing.getStrokeDashArray().setAll(62.0, 50.0);
        innerRing.setStrokeDashOffset(12);

        // g25 group (scaled “cell + text” block from your svg)
        Group g25 = new Group();
        g25.getTransforms().add(new Affine(
                3.8240078, 0, 167.72609,
                0, 3.699599, -9.1771109
        ));

        Rectangle cell = new Rectangle(38.092041, 59.784199, 18, 18);
        cell.setArcWidth(0.91527015 * 2);
        cell.setArcHeight(1.0797421 * 2);
        cell.setFill(Color.TRANSPARENT);
        cell.setStroke(Color.web("#111827"));
        cell.setStrokeWidth(0.8);

        SVGPath miniGrid = svgPath("M 44.092044 59.784193 v 18 "
                + "M 50.092044 59.784193 v 18 "
                + "M 38.092044 65.784193 h 18 "
                + "M 38.092044 71.784193 h 18");
        miniGrid.setFill(Color.TRANSPARENT);
        miniGrid.setStroke(Color.web("#111827"));
        miniGrid.setStrokeWidth(0.8);
        miniGrid.setOpacity(0.55);

        Text tCrafty = new Text("CRAFTY");
        tCrafty.setX(-10);
        tCrafty.setY(145);
        tCrafty.setFill(TEAL);
        tCrafty.setFont(Font.font("Space Grotesk", FontWeight.BOLD, 30));
//        tCrafty.setScaleX(0.96696546);
//        tCrafty.setScaleY(1.0341631);

        Text tLine1 = new Text("humans shaping land");
        tLine1.setX(-5);
        tLine1.setY(157.25967);
        tLine1.setFill(SLATE);
        tLine1.setFont(Font.font("Inter", FontWeight.NORMAL, 11));
//        tLine1.setScaleX(0.96696546);
//        tLine1.setScaleY(1.0341631);

        Text tLine2 = new Text("through decisions");
        tLine2.setX(-5);
        tLine2.setY(168.33743);
        tLine2.setFill(SLATE);
        tLine2.setFont(Font.font("Inter", FontWeight.NORMAL, 11));

        g25.getChildren().addAll(cell, miniGrid, tCrafty, tLine1, tLine2);

        mark.getChildren().addAll(
                // order matters for appearance
                outerRing, middleRing, innerRing,
                cellTeal, cellOrange, cellGreen,
                g25
        );

        getChildren().add(mark);
    }

    private static SVGPath svgPath(String d) {
        SVGPath p = new SVGPath();
        p.setContent(d);
        return p;
    }

    // --- Animations ----------------------------------------------------------

    private Animation createLoadingAnimation() {
        // Rings counter-rotate (different speeds looks “computational”)
//        RotateTransition r1 = new RotateTransition(Duration.seconds(3.2), outerRing);
//        r1.setByAngle(360);
//        r1.setInterpolator(Interpolator.LINEAR);
//        r1.setCycleCount(Animation.INDEFINITE);
//
//        RotateTransition r2 = new RotateTransition(Duration.seconds(2.4), middleRing);
//        r2.setByAngle(-360);
//        r2.setInterpolator(Interpolator.LINEAR);
//        r2.setCycleCount(Animation.INDEFINITE);
//
//        RotateTransition r3 = new RotateTransition(Duration.seconds(1.8), innerRing);
//        r3.setByAngle(360);
//        r3.setInterpolator(Interpolator.LINEAR);
//        r3.setCycleCount(Animation.INDEFINITE);

        // Grid/cells “computing” pulse: cycle fills (slight phase offsets)
        Animation c1 = colorPulse(cellOrange, Color.BLACK, GREEN, TEAL, Duration.millis(1200), Duration.millis(0));
        Animation c2 = colorPulse(cellGreen,  GREEN, Color.RED, ORANGE, Duration.millis(1200), Duration.millis(400));
        Animation c3 = colorPulse(cellTeal,   TEAL, ORANGE, Color.BLUE, Duration.millis(1200), Duration.millis(800));

//        Animation r = strock(innerRing,   Duration.millis(900), Duration.millis(400));
        // Optional: tiny “breathing” (subtle)
        ScaleTransition breathe = new ScaleTransition(Duration.seconds(1.6), middleRing);
        breathe.setFromX(1.0); breathe.setFromY(1.0);
        breathe.setToX(1.015); breathe.setToY(1.015);
        breathe.setAutoReverse(true);
        breathe.setCycleCount(Animation.INDEFINITE);
        breathe.setInterpolator(Interpolator.EASE_BOTH);

		return new ParallelTransition(/* r1, r2, r3, r,*/ c1, c2, c3, breathe);
    }

    private Animation createEntryAnimation() {
        setOpacity(0);
        setScaleX(0.98);
        setScaleY(0.98);

        FadeTransition fade = new FadeTransition(Duration.millis(450), this);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition scale = new ScaleTransition(Duration.millis(450), this);
        scale.setFromX(0.98); scale.setFromY(0.98);
        scale.setToX(1.0);   scale.setToY(1.0);
        scale.setInterpolator(Interpolator.EASE_OUT);

        return new ParallelTransition(fade, scale);
    }

    private static Animation colorPulse(Shape s, Color a, Color b, Color c, Duration period, Duration delay) {
        // 3-phase fill cycle: a -> b -> c -> a ...
        Timeline t = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(s.fillProperty(), a),
                        new KeyValue(s.strokeProperty(), a)),
                new KeyFrame(period.divide(3),
                        new KeyValue(s.fillProperty(), b),
                        new KeyValue(s.strokeProperty(), b)),
                new KeyFrame(period.multiply(2).divide(3),
                        new KeyValue(s.fillProperty(), c),
                        new KeyValue(s.strokeProperty(), c)),
                new KeyFrame(period,
                        new KeyValue(s.fillProperty(), a),
                        new KeyValue(s.strokeProperty(), a))
        );
        t.setDelay(delay);
        t.setCycleCount(Animation.INDEFINITE);
        t.setAutoReverse(false);
        return t;
    }
    
    private static Animation strock(SVGPath s, Duration period, Duration delay) {
        // 3-phase fill cycle: a -> b -> c -> a ...
        Timeline t = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(s.strokeLineCapProperty(), StrokeLineCap.ROUND)),
                new KeyFrame(period.divide(3),
                		new KeyValue(s.strokeLineCapProperty(), StrokeLineCap.BUTT)),
                new KeyFrame(period.multiply(2).divide(3),
                		new KeyValue(s.strokeLineCapProperty(), StrokeLineCap.ROUND)),
                new KeyFrame(period,
                		new KeyValue(s.strokeLineCapProperty(), StrokeLineCap.SQUARE))
        );
        t.setDelay(delay);
        t.setCycleCount(Animation.INDEFINITE);
        t.setAutoReverse(false);
        return t;
    }
}
