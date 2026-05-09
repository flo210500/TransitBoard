package de.transitboard.display;

import de.transitboard.TransitBoardPlugin;
import de.transitboard.model.StationConfig;
import de.transitboard.model.UpcomingArrival;
import de.transitboard.store.StationState;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapPalette;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.entity.Player;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Rendert ein DB-artiges FIS-Display auf Minecraft-Maps.
 *
 * Layout (4 Maps à 128×128 = 512×128 gesamt):
 * ┌─────────────────────────────┬──────────────┬──────────────┬──────────────┐
 * │  Nächster Zug (2 Maps)      │  2. Zug      │  3. Zug      │  4. Zug      │
 * │  Zeit groß + Linie + Ziel   │  kompakt     │  kompakt     │  kompakt     │
 * │  Zwischenstopps             │              │              │              │
 * └─────────────────────────────┴──────────────┴──────────────┴──────────────┘
 */
public class MapDisplayRenderer extends MapRenderer {

    private static final Color BG          = new Color(0x00, 0x21, 0x87);
    private static final Color BG_DARK     = new Color(0x00, 0x15, 0x5A);
    private static final Color TEXT_WHITE  = new Color(0xFF, 0xFF, 0xFF);
    private static final Color TEXT_GRAY   = new Color(0xCC, 0xCC, 0xCC);
    private static final Color DELAY_RED   = new Color(0xFF, 0x33, 0x33);
    private static final Color ACCENT_LINE = new Color(0xFF, 0xCC, 0x00);
    private static final Color DIVIDER     = new Color(0x33, 0x55, 0xAA);
    private static final Color BG_COMPACT  = new Color(0x00, 0x18, 0x6E);

    private final TransitBoardPlugin plugin;
    private final StationConfig station;
    private final String gleisId;
    private final int mapIndex;

    // Geteiltes Image – wird von MapDisplay verwaltet
    private final MapDisplay owner;

    public MapDisplayRenderer(TransitBoardPlugin plugin, StationConfig station,
                               String gleisId, int mapIndex, MapDisplay owner) {
        super(false);
        this.plugin   = plugin;
        this.station  = station;
        this.gleisId  = gleisId;
        this.mapIndex = mapIndex;
        this.owner    = owner;
    }

    @Override
    public void render(MapView view, MapCanvas canvas, Player player) {
        // Map 0 rendert das Gesamtbild, andere nehmen ihren Ausschnitt
        if (mapIndex == 0) {
            long now = System.currentTimeMillis();
            if (now - owner.getLastRenderMs() > 1000) {
                owner.setSharedImage(renderFull());
                owner.setLastRenderMs(now);
            }
        }

        BufferedImage img = owner.getSharedImage();
        if (img == null) return;

        int offsetX = mapIndex * 128;
        for (int x = 0; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                int rgb = img.getRGB(offsetX + x, y);
                canvas.setPixel(x, y, MapPalette.matchColor(
                    new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF)));
            }
        }
    }

    public BufferedImage renderFullPublic() { return renderFull(); }

    private BufferedImage renderFull() {
        BufferedImage img = new BufferedImage(512, 128, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Hintergrund
        g.setColor(BG);
        g.fillRect(0, 0, 512, 128);

        // Ankünfte holen
        List<UpcomingArrival> arrivals = getArrivals();

        if (arrivals.isEmpty()) {
            renderEmpty(g);
        } else {
            // Erste 2 Maps: nächster Zug groß
            if (arrivals.size() > 0) renderMain(g, arrivals.get(0));
            // Maps 2-3: kompakt
            if (arrivals.size() > 1) renderCompact(g, arrivals.get(1), 256);
            if (arrivals.size() > 2) renderCompact(g, arrivals.get(2), 384);
            if (arrivals.size() > 3) renderCompact(g, arrivals.get(3), 384 + 64);
        }

        // Trennlinien zwischen Maps
        g.setColor(DIVIDER);
        g.fillRect(255, 4, 1, 120);
        g.fillRect(383, 4, 1, 120);

        g.dispose();
        return img;
    }

    /** Rendert den nächsten Zug auf Maps 0+1 (0–255px) */
    private void renderMain(Graphics2D g, UpcomingArrival a) {
        int x = 6, y = 0;

        // Zeitanzeige groß
        Font timeFont = new Font("Dialog", Font.BOLD, 38);
        g.setFont(timeFont);
        g.setColor(TEXT_WHITE);
        String timeStr = formatTime(a.getEtaSeconds());
        g.drawString(timeStr, x, 42);

        // Verspätung in Rot daneben
        if (a.getDelaySeconds() > 30) {
            FontMetrics fm = g.getFontMetrics();
            int tw = fm.stringWidth(timeStr);
            Font delayFont = new Font("Dialog", Font.BOLD, 18);
            g.setFont(delayFont);
            g.setColor(DELAY_RED);
            g.drawString("+" + (int)Math.ceil(a.getDelaySeconds()/60.0) + "m",
                x + tw + 5, 32);
            g.setColor(TEXT_WHITE);
        }

        // Linienbox (DB-Stil: farbiger Hintergrund)
        drawLineBox(g, 220, 8, a);

        // Zielname groß
        Font destFont = new Font("Dialog", Font.BOLD, 22);
        g.setFont(destFont);
        g.setColor(TEXT_WHITE);
        String dest = a.getDestination().isEmpty() ? a.getLineName() : a.getDestination();
        g.drawString(truncate(dest, g, 220), x, 68);

        // Trennlinie
        g.setColor(DIVIDER);
        g.fillRect(x, 74, 244, 1);

        // Zwischenstopps (klein)
        Font stopFont = new Font("Dialog", Font.PLAIN, 10);
        g.setFont(stopFont);
        g.setColor(TEXT_GRAY);
        // Placeholder – echte Zwischenstopps wären aus der Liniendefinition
        String stops = buildIntermediateStops(a);
        if (!stops.isEmpty()) {
            drawWrappedText(g, stops, x, 86, 244, 10);
        }

        // Gleis unten (ohne Prefix)
        Font gleisFont = new Font("Dialog", Font.BOLD, 11);
        g.setFont(gleisFont);
        g.setColor(TEXT_GRAY);
        g.drawString(a.getGleisDisplayName(), x, 122);

        // Gleis-Indikator Linie
        g.setColor(parseColor(a.getColoredLineName()));
        g.fillRect(x, 112, 50, 3);
    }

    /** Rendert einen kompakten Zug (128px breit) */
    private void renderCompact(Graphics2D g, UpcomingArrival a, int startX) {
        // Gleicher Hintergrund wie Map 0+1
        g.setColor(BG);
        g.fillRect(startX + 1, 0, 127, 128);

        int x = startX + 6;

        // Zeit mittel
        Font timeFont = new Font("Dialog", Font.BOLD, 22);
        g.setFont(timeFont);
        g.setColor(TEXT_WHITE);
        g.drawString(formatTime(a.getEtaSeconds()), x, 30);

        // Verspätung
        if (a.getDelaySeconds() > 30) {
            Font df = new Font("Dialog", Font.BOLD, 12);
            g.setFont(df);
            g.setColor(DELAY_RED);
            g.drawString("+" + (int)Math.ceil(a.getDelaySeconds()/60.0) + "m", x, 44);
            g.setColor(TEXT_WHITE);
        }

        // Linienbox klein
        drawLineBoxSmall(g, startX + 90, 6, a);

        // Ziel
        Font destFont = new Font("Dialog", Font.BOLD, 14);
        g.setFont(destFont);
        g.setColor(TEXT_WHITE);
        String dest = a.getDestination().isEmpty() ? a.getLineName() : a.getDestination();
        g.drawString(truncate(dest, g, 116), x, 60);

        // Trennlinie
        g.setColor(DIVIDER);
        g.fillRect(x, 65, 116, 1);

        // Zwischenstopps statt Gleis
        String stops = buildIntermediateStops(a);
        if (!stops.isEmpty()) {
            Font stopFont = new Font("Dialog", Font.PLAIN, 9);
            g.setFont(stopFont);
            g.setColor(TEXT_GRAY);
            drawWrappedText(g, stops, x, 77, 116, 9);
        }

        // Farbstreifen oben
        g.setColor(parseColor(a.getColoredLineName()));
        g.fillRect(startX + 1, 0, 126, 3);
    }

    private void renderEmpty(Graphics2D g) {
        g.setColor(TEXT_GRAY);
        Font f = new Font("Dialog", Font.PLAIN, 14);
        g.setFont(f);
        g.drawString(station.getDisplayName(), 6, 30);
        g.setFont(new Font("Dialog", Font.PLAIN, 11));
        g.drawString("Keine Abfahrten", 6, 50);
    }

    private void drawLineBox(Graphics2D g, int x, int y, UpcomingArrival a) {
        Color lineColor = parseColor(a.getColoredLineName());
        String lineName = stripColor(a.getColoredLineName());
        Font f = new Font("Dialog", Font.BOLD, 13);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(lineName) + 10;
        int h = 20;
        g.setColor(lineColor);
        g.fillRoundRect(x, y, w, h, 4, 4);
        g.setColor(Color.WHITE);
        g.drawString(lineName, x + 5, y + 14);
    }

    private void drawLineBoxSmall(Graphics2D g, int x, int y, UpcomingArrival a) {
        Color lineColor = parseColor(a.getColoredLineName());
        String lineName = stripColor(a.getColoredLineName());
        Font f = new Font("Dialog", Font.BOLD, 10);
        g.setFont(f);
        FontMetrics fm = g.getFontMetrics();
        int w = fm.stringWidth(lineName) + 8;
        int h = 15;
        g.setColor(lineColor);
        g.fillRoundRect(x, y, w, h, 3, 3);
        g.setColor(Color.WHITE);
        g.drawString(lineName, x + 4, y + 11);
    }

    private String buildIntermediateStops(UpcomingArrival a) {
        var lineConfig = plugin.getConfigManager().getLine(a.getLineName());
        if (lineConfig == null) return "";
        var stations = plugin.getConfigManager().getStations();

        // Alle Stops nach der FIS-Station aus der Liniendefinition (statisch)
        var upcoming = lineConfig.getStopsAfter(a.getStationId(), 1);
        StringBuilder sb = new StringBuilder();
        for (var stop : upcoming) {
            // Letzte Station (Ausgangsstation bei Ring) nicht anzeigen
            if (stop.getStationId().equals(a.getStationId())) break;
            var s = stations.get(stop.getStationId());
            if (s != null) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(s.getDisplayName());
            }
        }
        return sb.toString();
    }

    private void drawWrappedText(Graphics2D g, String text, int x, int y, int maxW, int lineH) {
        FontMetrics fm = g.getFontMetrics();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        int cy = y;
        for (String word : words) {
            String test = line.length() > 0 ? line + " " + word : word;
            if (fm.stringWidth(test) > maxW) {
                g.drawString(line.toString(), x, cy);
                cy += lineH + 2;
                line = new StringBuilder(word);
                if (cy > 108) break;
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0 && cy <= 108) g.drawString(line.toString(), x, cy);
    }

    private String truncate(String text, Graphics2D g, int maxW) {
        FontMetrics fm = g.getFontMetrics();
        if (fm.stringWidth(text) <= maxW) return text;
        while (text.length() > 1 && fm.stringWidth(text + "…") > maxW)
            text = text.substring(0, text.length() - 1);
        return text + "…";
    }

    private String formatTime(long seconds) {
        if (seconds <= plugin.getConfigManager().getSofortThreshold())
            return plugin.getConfigManager().getFormatSofort();
        long min = (long) Math.ceil(seconds / 60.0);
        return min + " Min";
    }

    private Color parseColor(String coloredName) {
        if (coloredName == null || coloredName.isEmpty()) return ACCENT_LINE;
        // §1=dunkelblau, §2=dunkelgrün, §3=cyan, §4=dunkelrot, §5=lila,
        // §6=gold, §9=blau, §a=grün, §b=hellblau, §c=rot, §d=pink, §e=gelb
        if (coloredName.startsWith("§")) {
            return switch (coloredName.charAt(1)) {
                case '1' -> new Color(0x00, 0x00, 0xAA);
                case '2' -> new Color(0x00, 0xAA, 0x00);
                case '3' -> new Color(0x00, 0xAA, 0xAA);
                case '4' -> new Color(0xAA, 0x00, 0x00);
                case '5' -> new Color(0xAA, 0x00, 0xAA);
                case '6' -> new Color(0xFF, 0xAA, 0x00);
                case '9' -> new Color(0x55, 0x55, 0xFF);
                case 'a' -> new Color(0x55, 0xFF, 0x55);
                case 'b' -> new Color(0x55, 0xFF, 0xFF);
                case 'c' -> new Color(0xFF, 0x55, 0x55);
                case 'd' -> new Color(0xFF, 0x55, 0xFF);
                case 'e' -> new Color(0xFF, 0xFF, 0x55);
                default  -> ACCENT_LINE;
            };
        }
        return ACCENT_LINE;
    }

    private String stripColor(String s) {
        if (s == null) return "";
        return s.replaceAll("§[0-9a-fk-or]", "").replace("§r", "").trim();
    }

    private List<UpcomingArrival> getArrivals() {
        var store = plugin.getTimingStore();
        if (gleisId != null && !gleisId.isEmpty()) {
            return store.getArrivalsForGleis(station, gleisId);
        }
        StationState state = store.getOrCreateState(station.getId());
        return state.getLastArrivals();
    }
}
