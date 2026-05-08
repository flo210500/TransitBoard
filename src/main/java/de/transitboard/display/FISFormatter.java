package de.transitboard.display;

import de.transitboard.config.ConfigManager;
import de.transitboard.model.UpcomingArrival;

import java.util.*;

/**
 * Bahnhofs-FIS (4 Schilder): Gleis | Linie | Ziel | Zeit
 * Gleis-FIS    (3 Schilder): Linie | Ziel  | Zeit
 *
 * [TDANZEIGE] ist immer das LINKE Schild.
 * Leere Schilder rechts davon werden automatisch erkannt.
 */
public class FISFormatter {

    private final ConfigManager config;
    private int scrollOffset = 0;

    public FISFormatter(ConfigManager config) {
        this.config = config;
    }

    public void incrementScroll() { scrollOffset++; }

    // ─── Bahnhofs-FIS ─────────────────────────────────────────────────────────
    // Single=Linie+Ziel+Zeit, Double=Linie|Zeit, Triple=Linie|Ziel|Zeit
    // Quad=Gleis|Linie|Ziel|Zeit

    /** Quad loc1: Gleis */
    public List<String> buildStationGleis(List<UpcomingArrival> arrivals, String stationName) {
        List<String> lines = new ArrayList<>();
        lines.add("§f" + center(stationName, 15));
        if (arrivals.isEmpty()) { lines.add(config.getFormatNoService()); pad(lines); return lines; }
        for (UpcomingArrival a : sortArrivals(arrivals)) {
            if (lines.size() >= 4) break;
            lines.add("§f" + center(gleisNumber(a.getGleisDisplayName()), 15));
        }
        pad(lines); return lines;
    }

    /** Quad/Triple loc2 oder Double/Single loc1: Linie */
    public List<String> buildStationLinien(List<UpcomingArrival> arrivals, String stationName) {
        List<String> lines = new ArrayList<>();
        lines.add("§f" + center(stationName, 15));
        if (arrivals.isEmpty()) { lines.add("§f" + config.getFormatNoService()); pad(lines); return lines; }
        for (UpcomingArrival a : sortArrivals(arrivals)) {
            if (lines.size() >= 4) break;
            // Linienfarbe nur für den Prefix, Rest weiß
            lines.add(String.format(config.getFormatLinePrefix(), a.getColoredLineName()));
        }
        pad(lines); return lines;
    }

    /** Quad/Triple loc3 oder Double loc2: Ziel (scrollt) */
    public List<String> buildStationZiel(List<UpcomingArrival> arrivals) {
        List<String> lines = new ArrayList<>();
        lines.add("§f" + center("Ziel", 15));
        if (arrivals.isEmpty()) { lines.add(config.getFormatNoService()); pad(lines); return lines; }
        List<UpcomingArrival> sorted = sortArrivals(arrivals);
        for (int i = 0; i < Math.min(sorted.size(), 3); i++) {
            String dest = sorted.get(i).getDestination();
            lines.add("§f" + scrollText(dest.isEmpty() ? "–" : dest, 15, i));
        }
        pad(lines); return lines;
    }

    /** Quad loc4 oder Triple loc3 oder Double loc2: Zeit */
    public List<String> buildStationZeit(List<UpcomingArrival> arrivals) {
        List<String> lines = new ArrayList<>();
        lines.add("§f" + center("Abfahrt", 15));
        if (arrivals.isEmpty()) { lines.add(config.getFormatNoService()); pad(lines); return lines; }
        for (UpcomingArrival a : sortArrivals(arrivals)) {
            if (lines.size() >= 4) break;
            lines.add("§f" + center(formatTime(a.getEtaSeconds(), a.getDelaySeconds()), 15));
        }
        pad(lines); return lines;
    }

    /** Single: alles auf einem Schild */
    public List<String> buildSingle(List<UpcomingArrival> arrivals, String stationName) {
        List<String> lines = new ArrayList<>();
        lines.add("§f" + center(stationName, 15));
        if (arrivals.isEmpty()) { lines.add(config.getFormatNoService()); pad(lines); return lines; }
        List<UpcomingArrival> sorted = sortArrivals(arrivals);
        for (int i = 0; i < Math.min(sorted.size(), 3); i++) {
            UpcomingArrival a = sorted.get(i);
            String prefix = String.format(config.getFormatLinePrefix(), a.getColoredLineName());
            String time   = formatTime(a.getEtaSeconds());
            String dest   = a.getDestination();
            if (!dest.isEmpty()) {
                String d = scrollText(dest, 15 - prefix.length() - 1, i);
                lines.add(truncate(prefix + " " + d, 15));
            } else {
                lines.add(truncate(prefix + " " + time, 15));
            }
        }
        pad(lines); return lines;
    }

    // ─── Gleis-FIS ────────────────────────────────────────────────────────────
    // Single=Linie+Ziel, Double=Linie|Zeit, Triple=Linie|Ziel|Zeit

    /** Gleis-FIS Linie (loc1) */
    public List<String> buildGleisLinien(List<UpcomingArrival> arrivals, String gleisName) {
        List<String> lines = new ArrayList<>();
        lines.add(center(gleisName, 15));
        if (arrivals.isEmpty()) { lines.add(config.getFormatNoService()); pad(lines); return lines; }
        for (UpcomingArrival a : sortArrivals(arrivals)) {
            if (lines.size() >= 4) break;
            lines.add(String.format(config.getFormatLinePrefix(), a.getColoredLineName()));
        }
        pad(lines); return lines;
    }

    /** Gleis-FIS Ziel (loc2 bei Triple) */
    public List<String> buildGleisZiel(List<UpcomingArrival> arrivals) {
        List<String> lines = new ArrayList<>();
        lines.add("§f" + center("Ziel", 15));
        if (arrivals.isEmpty()) { lines.add(config.getFormatNoService()); pad(lines); return lines; }
        List<UpcomingArrival> sorted = sortArrivals(arrivals);
        for (int i = 0; i < Math.min(sorted.size(), 3); i++) {
            String dest = sorted.get(i).getDestination();
            lines.add("§f" + scrollText(dest.isEmpty() ? "–" : dest, 15, i));
        }
        pad(lines); return lines;
    }

    /** Gleis-FIS Zeit (loc2 bei Double, loc3 bei Triple) */
    public List<String> buildGleisZeit(List<UpcomingArrival> arrivals) {
        return buildStationZeit(arrivals);
    }

    /** Gleis-FIS Single */
    public List<String> buildGleisSingle(List<UpcomingArrival> arrivals, String gleisName) {
        List<String> lines = new ArrayList<>();
        lines.add(center(gleisName, 15));
        if (arrivals.isEmpty()) { lines.add(config.getFormatNoService()); pad(lines); return lines; }
        List<UpcomingArrival> sorted = sortArrivals(arrivals);
        for (int i = 0; i < Math.min(sorted.size(), 3); i++) {
            UpcomingArrival a = sorted.get(i);
            String prefix = String.format(config.getFormatLinePrefix(), a.getColoredLineName());
            String dest   = a.getDestination();
            if (!dest.isEmpty()) {
                lines.add(truncate(prefix + " " + scrollText(dest, 15 - prefix.length() - 1, i), 15));
            } else {
                lines.add(truncate(prefix + " " + formatTime(a.getEtaSeconds(), a.getDelaySeconds()), 15));
            }
        }
        pad(lines); return lines;
    }

    // ─── Sortierung ───────────────────────────────────────────────────────────

    public List<UpcomingArrival> sortArrivals(List<UpcomingArrival> arrivals) {
        Map<String, UpcomingArrival> nextPerLine = new LinkedHashMap<>();
        List<UpcomingArrival> rest = new ArrayList<>();
        for (UpcomingArrival a : arrivals) {
            if (!nextPerLine.containsKey(a.getLineName())) nextPerLine.put(a.getLineName(), a);
            else rest.add(a);
        }
        List<UpcomingArrival> ordered = new ArrayList<>(nextPerLine.values());
        Collections.sort(ordered);
        Collections.sort(rest);
        ordered.addAll(rest);
        return ordered;
    }

    // ─── Hilfsmethoden ────────────────────────────────────────────────────────

    private String gleisNumber(String name) {
        String digits = name.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? truncate(name.trim(), 15) : digits;
    }

    private String scrollText(String text, int maxLen, int lineIndex) {
        if (maxLen <= 0) return "";
        if (text.length() <= maxLen) return text;
        String padded = text + "   ";
        int offset = (scrollOffset + lineIndex * 3) % padded.length();
        return (padded + padded).substring(offset, offset + maxLen);
    }

    private String center(String text, int width) {
        if (text.length() >= width) return text.substring(0, width);
        int pad = width - text.length();
        return " ".repeat(pad / 2) + text + " ".repeat(pad - pad / 2);
    }

    private String truncate(String text, int maxLen) {
        if (maxLen <= 0) return "";
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }

    private void pad(List<String> lines) {
        while (lines.size() < 4) lines.add("");
    }

    private String formatTime(long seconds) {
        return formatTime(seconds, 0);
    }

    private String formatTime(long seconds, long delaySeconds) {
        if (seconds < 0) return "?";
        boolean hasDelay = delaySeconds > 30;
        boolean isSofort = seconds <= config.getSofortThreshold();

        if (isSofort && hasDelay) {
            long delayMin = (long) Math.ceil(delaySeconds / 60.0);
            return "+" + delayMin + "m";
        } else if (isSofort) {
            return config.getFormatSofort();
        } else {
            long minutes = (long) Math.ceil(seconds / 60.0);
            String time = String.format(config.getFormatMinutes(), minutes);
            if (hasDelay) {
                long delayMin = (long) Math.ceil(delaySeconds / 60.0);
                time += " +" + delayMin + "m";
            }
            return time;
        }
    }
}
