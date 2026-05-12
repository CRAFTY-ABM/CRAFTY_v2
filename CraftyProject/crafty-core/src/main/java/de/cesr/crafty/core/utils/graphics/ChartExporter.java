package de.cesr.crafty.core.utils.graphics;

import de.cesr.crafty.core.cli.ConfigLoader;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.DecimalFormat;
import java.util.*;
import java.util.List;

/**
 * Utility for exporting simple time-series line charts as PNG using only the JDK (Java2D).
 *
 * This class is intended as a lightweight replacement for third-party charting libraries in
 * environments where additional dependencies are undesirable. It renders one or more series as
 * polylines with small point markers, draws axes with "nice" tick steps, and places a legend to
 * the right of the plot area.
 *
 * Legend behavior
 * To support a large number of series (e.g., 30+), the legend is rendered in multiple columns.
 * The right margin is computed dynamically based on the number of series and the longest legend
 * label, while enforcing a minimum plot width to prevent the plot area from collapsing.
 *
 * Headless execution
 * The renderer is safe to run in headless mode (recommended:
 * {@code -Djava.awt.headless=true}). Output is written via {@link javax.imageio.ImageIO}.
 *
 * Usage<
 * {@code
 * Map<String, ArrayList<Double>> series = new HashMap<>();
 * series.put("A", new ArrayList<>(List.of(1.0, 2.0, 3.0)));
 * series.put("B", new ArrayList<>(List.of(2.0, 1.5, 2.5)));
 *
 * ChartExporter.createAndSaveChartAsPNG(series, 2020, "Example", "/tmp/example");
 * }
 *
 * Note: The output file name will have the suffix {@code ".PNG"} appended by this
 * utility.
 */
public final class ChartExporter {

    private ChartExporter() {
    }

    public static void createAndSaveChartAsPNG(Map<String, ArrayList<Double>> hashData,
                                               int startYear,
                                               String chartTitle,
                                               String pngPath) {
        createAndSaveChartAsPNG(hashData, null, startYear, chartTitle, pngPath);
    }

    public static void createAndSaveChartAsPNG(Map<String, ArrayList<Double>> hashData,
                                               Map<String, String> hashColors,
                                               int startYear,
                                               String chartTitle,
                                               String pngPath) {

        if (hashData == null || hashData.isEmpty()) {
            return;
        }

        // Keep same default size as your XChart version
        int width = 800;
        int height = 600;

        Layout layout = Layout.compute(hashData, hashColors, startYear, chartTitle, width, height);

        try {
            if (ConfigLoader.config != null && ConfigLoader.config.generate_charts_plots_PNG) {
                File out = new File(pngPath + ".PNG");
                ensureParentDir(out);
                BufferedImage img = renderToImage(layout);
                ImageIO.write(img, "png", out);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --------------------------- Rendering (PNG) ---------------------------

    private static BufferedImage renderToImage(Layout l) {
        BufferedImage img = new BufferedImage(l.width, l.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();

        // Headless-safe rendering hints
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Background
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, l.width, l.height);

        // Title
        g.setColor(Color.DARK_GRAY);
        g.setFont(l.titleFont);
        drawCenteredString(g, l.title, new Rectangle(0, 10, l.width, l.topMargin - 10));

        // Plot area background
        g.setColor(new Color(250, 250, 250));
        g.fillRect(l.plotX, l.plotY, l.plotW, l.plotH);

        // Grid + axes
        drawAxesAndGridPng(g, l);

        // Series
        for (String name : l.seriesOrder) {
            List<Point> pts = l.seriesPoints.get(name);
            if (pts == null || pts.size() < 1) continue;

            Color c = l.seriesColor.getOrDefault(name, Color.GRAY);
            g.setColor(c);
            g.setStroke(new BasicStroke(2.0f));

            // polyline
            for (int i = 1; i < pts.size(); i++) {
                Point p0 = pts.get(i - 1);
                Point p1 = pts.get(i);
                g.drawLine(p0.x, p0.y, p1.x, p1.y);
            }

            // markers (size ~4 like your XChart config)
            int r = 2;
            for (Point p : pts) {
                Shape s = new Ellipse2D.Double(p.x - r, p.y - r, r * 2.0, r * 2.0);
                g.fill(s);
            }
        }

        // Legend (outside east) — now wraps into columns instead of cutting off
        drawLegendPng(g, l);

        g.dispose();
        return img;
    }

    private static void drawAxesAndGridPng(Graphics2D g, Layout l) {
        // Gridlines
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(230, 230, 230));

        for (double yTick : l.yTicks) {
            int y = l.toScreenY(yTick);
            g.drawLine(l.plotX, y, l.plotX + l.plotW, y);
        }

        for (int yearTick : l.xTicks) {
            int x = l.toScreenX(yearTick);
            g.drawLine(x, l.plotY, x, l.plotY + l.plotH);
        }

        // Axes
        g.setColor(Color.DARK_GRAY);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(l.plotX, l.plotY, l.plotX, l.plotY + l.plotH);                 // Y axis
        g.drawLine(l.plotX, l.plotY + l.plotH, l.plotX + l.plotW, l.plotY + l.plotH); // X axis

        // Tick labels
        g.setFont(l.axisFont);
        g.setColor(Color.DARK_GRAY);

        DecimalFormat yFmt = new DecimalFormat("0.##");

        // Y labels
        for (double yTick : l.yTicks) {
            int y = l.toScreenY(yTick);
            String s = yFmt.format(yTick);
            int sw = g.getFontMetrics().stringWidth(s);
            g.drawString(s, l.plotX - 10 - sw, y + 5);
        }

        // X labels (years)
        for (int yearTick : l.xTicks) {
            int x = l.toScreenX(yearTick);
            String s = Integer.toString(yearTick);
            int sw = g.getFontMetrics().stringWidth(s);
            g.drawString(s, x - sw / 2, l.plotY + l.plotH + 25);
        }
    }

    private static void drawLegendPng(Graphics2D g, Layout l) {
        int startX = l.plotX + l.plotW + 15;
        int startY = l.plotY;

        int lineLen = 18;
        int rowH = 18;
        int colGap = 20;

        g.setFont(l.legendFont);
        FontMetrics fm = g.getFontMetrics();

        int rowsPerCol = Math.max(1, l.plotH / rowH);

        int maxLabelW = 0;
        for (String name : l.seriesOrder) {
            maxLabelW = Math.max(maxLabelW, fm.stringWidth(name));
        }

        int colW = lineLen + 8 + maxLabelW + colGap;

        for (int i = 0; i < l.seriesOrder.size(); i++) {
            String name = l.seriesOrder.get(i);

            int col = i / rowsPerCol;
            int row = i % rowsPerCol;

            int x = startX + col * colW;
            int y = startY + row * rowH;

            // safety: don't draw outside image
            if (x > l.width - 5) break;

            Color c = l.seriesColor.getOrDefault(name, Color.GRAY);

            g.setColor(c);
            g.setStroke(new BasicStroke(2f));
            g.drawLine(x, y + 6, x + lineLen, y + 6);
            g.fill(new Ellipse2D.Double(x + lineLen / 2.0 - 2, y + 6 - 2, 4, 4));

            g.setColor(Color.DARK_GRAY);
            g.drawString(name, x + lineLen + 8, y + 10);
        }
    }

    private static void drawCenteredString(Graphics2D g, String text, Rectangle rect) {
        FontMetrics fm = g.getFontMetrics();
        int x = rect.x + (rect.width - fm.stringWidth(text)) / 2;
        int y = rect.y + ((rect.height - fm.getHeight()) / 2) + fm.getAscent();
        g.drawString(text, x, y);
    }

    // --------------------------- Layout / Scaling ---------------------------

    private static final class Layout {
        final int width, height;
        final String title;

        // margins
        final int leftMargin = 70;
        final int topMargin = 60;
        final int bottomMargin = 60;

        // dynamic right margin (based on legend size)
        final int rightMargin;

        final int plotX, plotY, plotW, plotH;

        final double yMin = 0.0;
        final double yMax;

        final int xMinYear;
        final int xMaxYear;

        final List<Integer> xTicks;
        final List<Double> yTicks;

        final List<String> seriesOrder;
        final Map<String, List<Point>> seriesPoints;
        final Map<String, Color> seriesColor;

        final Font titleFont = new Font("SansSerif", Font.BOLD, 16);
        final Font axisFont = new Font("SansSerif", Font.PLAIN, 11);
        final Font legendFont = new Font("SansSerif", Font.PLAIN, 11);

        private Layout(int width,
                       int height,
                       String title,
                       int startYear,
                       int maxLen,
                       double yMax,
                       int xMinYear,
                       int xMaxYear,
                       List<Integer> xTicks,
                       List<Double> yTicks,
                       List<String> seriesOrder,
                       Map<String, List<Point>> seriesPoints,
                       Map<String, Color> seriesColor,
                       int rightMargin) {

            this.width = width;
            this.height = height;
            this.title = title;

            this.rightMargin = rightMargin;

            this.plotX = leftMargin;
            this.plotY = topMargin;
            this.plotW = width - leftMargin - this.rightMargin;
            this.plotH = height - topMargin - bottomMargin;

            this.yMax = (yMax <= yMin) ? (yMin + 1.0) : yMax;

            this.xMinYear = xMinYear;
            this.xMaxYear = xMaxYear;

            this.xTicks = xTicks;
            this.yTicks = yTicks;

            this.seriesOrder = seriesOrder;
            this.seriesPoints = seriesPoints;
            this.seriesColor = seriesColor;
        }

        int toScreenX(int year) {
            if (xMaxYear == xMinYear) return plotX + plotW / 2;
            double t = (year - xMinYear) / (double) (xMaxYear - xMinYear);
            return (int) Math.round(plotX + t * plotW);
        }

        int toScreenY(double y) {
            double yy = Math.max(yMin, Math.min(yMax, y));
            double t = (yy - yMin) / (yMax - yMin);
            return (int) Math.round(plotY + plotH - t * plotH);
        }

        static Layout compute(Map<String, ArrayList<Double>> data,
                              Map<String, String> colors,
                              int startYear,
                              String title,
                              int width,
                              int height) {

            // stable order for repeatability
            List<String> names = new ArrayList<>(data.keySet());
            Collections.sort(names);

            int maxLen = 0;
            double yMax = 0.0;

            for (String n : names) {
                List<Double> ys = data.get(n);
                if (ys == null) continue;
                maxLen = Math.max(maxLen, ys.size());
                for (Double v : ys) {
                    if (v == null) continue;
                    yMax = Math.max(yMax, v);
                }
            }

            int xMinYear = startYear;
            int xMaxYear = startYear + Math.max(0, maxLen - 1);

            List<Integer> xTicks = computeYearTicks(xMinYear, xMaxYear, 6);
            List<Double> yTicks = computeYTicks(0.0, (yMax <= 0.0 ? 1.0 : yMax), 6);

            // ---- Dynamic legend sizing -> dynamic right margin ----
            final int defaultRightMargin = 200;
            final int minPlotW = 250;

            final int topMargin = 60;
            final int bottomMargin = 60;
            final int plotH = height - topMargin - bottomMargin;

            final int rowH = 18;
            final int rowsPerCol = Math.max(1, plotH / rowH);

            final int seriesCount = names.size();
            final int colsNeeded = (int) Math.ceil(seriesCount / (double) rowsPerCol);

            final Font legendFont = new Font("SansSerif", Font.PLAIN, 11);
            final int maxLabelW = maxLegendLabelWidth(names, legendFont);

            final int lineLen = 18;
            final int colGap = 20;
            final int colW = lineLen + 8 + maxLabelW + colGap;

            int neededRightMargin = 15 + colsNeeded * colW; // padding + columns

            // cap right margin so plot doesn't collapse
            int maxRightMargin = width - 70 - minPlotW; // leftMargin=70
            if (maxRightMargin < defaultRightMargin) maxRightMargin = defaultRightMargin;

            int rightMargin = Math.min(Math.max(defaultRightMargin, neededRightMargin), maxRightMargin);
            // ------------------------------------------------------

            Map<String, Color> seriesColor = new HashMap<>();
            Map<String, List<Point>> seriesPoints = new HashMap<>();

            Layout l = new Layout(width, height, title, startYear, maxLen, yMax, xMinYear, xMaxYear,
                    xTicks, yTicks, names, seriesPoints, seriesColor, rightMargin);

            // colors
            Color[] palette = defaultPalette();
            int pi = 0;

            for (String n : names) {
                Color c = null;
                if (colors != null && colors.containsKey(n)) {
                    c = safeDecodeColor(colors.get(n));
                }
                if (c == null) {
                    c = palette[pi % palette.length];
                    pi++;
                }
                seriesColor.put(n, c);

                ArrayList<Double> ys = data.get(n);
                if (ys == null || ys.isEmpty()) continue;

                List<Point> pts = new ArrayList<>(ys.size());
                for (int i = 0; i < ys.size(); i++) {
                    Double v = ys.get(i);
                    if (v == null) continue;
                    int year = startYear + i;
                    pts.add(new Point(l.toScreenX(year), l.toScreenY(v)));
                }
                seriesPoints.put(n, pts);
            }

            return l;
        }

        private static int maxLegendLabelWidth(List<String> names, Font legendFont) {
            BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = img.createGraphics();
            try {
                g.setFont(legendFont);
                FontMetrics fm = g.getFontMetrics();
                int max = 0;
                for (String n : names) {
                    max = Math.max(max, fm.stringWidth(n));
                }
                return max;
            } finally {
                g.dispose();
            }
        }

        private static List<Integer> computeYearTicks(int minYear, int maxYear, int approxCount) {
            if (maxYear <= minYear) return List.of(minYear);
            int span = maxYear - minYear;
            int step = Math.max(1, span / Math.max(1, (approxCount - 1)));
            // round step to something nicer
            int nice = niceStep(step);
            List<Integer> ticks = new ArrayList<>();
            int y = minYear;
            while (y <= maxYear) {
                ticks.add(y);
                y += nice;
            }
            if (ticks.get(ticks.size() - 1) != maxYear) ticks.add(maxYear);
            return ticks;
        }

        private static List<Double> computeYTicks(double min, double max, int approxCount) {
            if (max <= min) return List.of(min);
            double span = max - min;
            double raw = span / Math.max(1, (approxCount - 1));
            double nice = niceStepDouble(raw);
            double start = Math.floor(min / nice) * nice;
            List<Double> ticks = new ArrayList<>();
            for (double v = start; v <= max + 0.5 * nice; v += nice) {
                if (v >= min - 1e-9) ticks.add(v);
            }
            if (ticks.isEmpty()) ticks.add(min);
            return ticks;
        }

        private static int niceStep(int step) {
            if (step <= 1) return 1;
            int[] nice = {1, 2, 5, 10, 20, 25, 50, 100};
            for (int n : nice) if (step <= n) return n;
            // scale up
            int pow = 1;
            while (pow * 100 < step) pow *= 10;
            return pow * 100;
        }

        private static double niceStepDouble(double step) {
            if (step <= 0) return 1.0;
            double exp = Math.pow(10, Math.floor(Math.log10(step)));
            double f = step / exp;
            double nf = (f <= 1) ? 1 : (f <= 2) ? 2 : (f <= 5) ? 5 : 10;
            return nf * exp;
        }

        private static Color[] defaultPalette() {
            // simple, readable defaults (no dependency)
            return new Color[]{
                    new Color(0x1f77b4),
                    new Color(0xff7f0e),
                    new Color(0x2ca02c),
                    new Color(0xd62728),
                    new Color(0x9467bd),
                    new Color(0x8c564b),
                    new Color(0xe377c2),
                    new Color(0x7f7f7f),
                    new Color(0xbcbd22),
                    new Color(0x17becf)
            };
        }
    }

    private static Color safeDecodeColor(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;

        // accept "#RRGGBB", "0xRRGGBB", "RRGGBB"
        if (t.startsWith("0x") || t.startsWith("0X")) t = t.substring(2);
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() == 6) {
            try {
                int rgb = Integer.parseInt(t, 16);
                return new Color(rgb);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static void ensureParentDir(File f) {
        File p = f.getParentFile();
        if (p != null && !p.exists()) {
            // noinspection ResultOfMethodCallIgnored
            p.mkdirs();
        }
    }
}
