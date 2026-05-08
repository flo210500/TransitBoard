package de.transitboard.announcement;

import de.transitboard.TransitBoardPlugin;
import de.transitboard.model.LineConfig;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Spielt Ansagen als Sequenz von Sound-Bausteinen ab.
 *
 * Linienname wird automatisch zerlegt:
 *   U1  → prefix=u, nummer=1
 *   S12 → prefix=s, nummer=12
 *
 * Verspätung wird auf 5-Minuten-Blöcke aufgerundet:
 *   320s → 5 Min
 *   650s → 15 Min (auf nächste 5 aufgerundet)
 */
public class AnnouncementManager {

    private final TransitBoardPlugin plugin;
    private final AnnouncementConfig config;
    private final Logger log;

    public AnnouncementManager(TransitBoardPlugin plugin, AnnouncementConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.log    = plugin.getLogger();
    }

    // ─── Öffentliche Methoden ─────────────────────────────────────────────────

    /** Holt die effektive Sequenz: Linie-spezifisch oder global */
    private String effectiveSequence(LineConfig line, String lineSeq, String globalSeq) {
        return (lineSeq != null) ? lineSeq : globalSeq;
    }

    private boolean isEnabledForLine(LineConfig line) {
        if (line != null && line.getAnnouncementsEnabled() != null)
            return line.getAnnouncementsEnabled();
        return config.isEnabled();
    }

    /** Prüft ob ein bestimmtes Event für diese Linie aktiv ist.
     *  Reihenfolge: Linie-spezifisch → global */
    private boolean isOnArrivalForLine(LineConfig line) {
        if (line != null && line.getOnArrival() != null) return line.getOnArrival();
        return config.isOnArrival();
    }

    private boolean isOnDepartureForLine(LineConfig line) {
        if (line != null && line.getOnDeparture() != null) return line.getOnDeparture();
        return config.isOnDeparture();
    }

    private boolean isOnDelayForLine(LineConfig line) {
        if (line != null && line.getOnDelay() != null) return line.getOnDelay();
        return config.isOnDelay();
    }

    /** Zug fährt ein */
    public void announceArrival(Location location, String lineName,
                                 String gleisDisplayName, long delaySeconds) {
        LineConfig line = plugin.getConfigManager().getLine(lineName);
        if (!isEnabledForLine(line) || !isOnArrivalForLine(line)) return;

        if (isOnDelayForLine(line) && delaySeconds >= config.getDelayThresholdSeconds()) {
            announceDelay(location, lineName, gleisDisplayName, delaySeconds);
            return;
        }

        String seq = effectiveSequence(line,
            line != null ? line.getSequenceArrival() : null,
            config.getSequenceArrival());
        play(location, buildSequence(seq, lineName, gleisDisplayName, delaySeconds));
    }

    /** Zug fährt ab */
    public void announceDeparture(Location location, String lineName,
                                   String gleisDisplayName) {
        LineConfig line = plugin.getConfigManager().getLine(lineName);
        if (!isEnabledForLine(line) || !isOnDepartureForLine(line)) return;
        String seq = effectiveSequence(line,
            line != null ? line.getSequenceDeparture() : null,
            config.getSequenceDeparture());
        play(location, buildSequence(seq, lineName, gleisDisplayName, 0));
    }

    /** Verspätungsansage */
    public void announceDelay(Location location, String lineName,
                               String gleisDisplayName, long delaySeconds) {
        LineConfig line = plugin.getConfigManager().getLine(lineName);
        if (!isEnabledForLine(line) || !isOnDelayForLine(line)) return;
        if (delaySeconds < config.getDelayThresholdSeconds()) return;
        String seq = effectiveSequence(line,
            line != null ? line.getSequenceDelay() : null,
            config.getSequenceDelay());
        play(location, buildSequence(seq, lineName, gleisDisplayName, delaySeconds));
    }

    // ─── Sequenz aufbauen ─────────────────────────────────────────────────────

    /**
     * Wandelt eine Sequenz-Config-Zeile in eine Liste von Sound-Keys um.
     * Variablen werden ersetzt durch ihre Bausteine.
     *
     * {prefix}        → "u" oder "s"
     * {nummer}        → "1", "2", etc. (Zahl wird in Ziffern aufgeteilt wenn >9)
     * {gleis_nummer}  → Gleisname wird in Ziffern aufgeteilt
     * {delay_minuten} → Verspätung in 5-Min-Blöcken
     */
    private List<String> buildSequence(String template, String lineName,
                                        String gleisDisplayName, long delaySeconds) {
        List<String> result = new ArrayList<>();
        String[] parts = template.split(",");

        // Linie zerlegen
        String prefix = extractPrefix(lineName);
        List<String> nummerKeys = extractNumberKeys(extractNumber(lineName));

        // Gleis zerlegen
        String gleisNum = gleisDisplayName.replaceAll("[^0-9]", "");
        List<String> gleisKeys = extractNumberKeys(gleisNum.isEmpty() ? "1" : gleisNum);

        // Verspätung auf 5-Min-Blöcke aufrunden
        long delayMin = roundToFiveMinutes(delaySeconds / 60);
        List<String> delayKeys = extractNumberKeys(String.valueOf(delayMin));

        for (String part : parts) {
            part = part.trim();
            switch (part) {
                case "{prefix}"          -> result.add(prefix);
                case "{number}"          -> result.addAll(nummerKeys);
                case "{platform_number}" -> result.addAll(gleisKeys);
                case "{delay_minutes}"   -> result.addAll(delayKeys);
                default               -> result.add(part);
            }
        }
        return result;
    }

    // ─── Sound abspielen ──────────────────────────────────────────────────────

    /**
     * Spielt eine Sequenz von Sound-Keys mit konfigurierbarem Delay ab.
     * Alle Spieler im Radius hören die Ansage.
     */
    private void play(Location location, List<String> soundKeys) {
        List<Player> listeners = getListeners(location);
        if (listeners.isEmpty()) return;

        new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                if (index >= soundKeys.size()) { cancel(); return; }

                String key = soundKeys.get(index++);
                String soundPath = resolveSound(key);
                if (soundPath.isEmpty()) return; // Baustein nicht konfiguriert

                String fullSound = config.getNamespace() + ":" + soundPath;

                for (Player p : listeners) {
                    if (p.isOnline() && p.getLocation().distance(location) <= config.getRadius()) {
                        try {
                            p.playSound(p.getLocation(), fullSound,
                                config.getVolume(), config.getPitch());
                        } catch (Exception e) {
                            log.fine("Sound konnte nicht abgespielt werden: " + fullSound);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, config.getDelayBetweenSounds());
    }

    // ─── Hilfsmethoden ────────────────────────────────────────────────────────

    private String resolveSound(String key) {
        // Direkter Key (z.B. "intro", "u", "s")
        String sound = config.getSound(key);
        if (!sound.isEmpty()) return sound;

        // Zahl → Zahlen-Ordner
        if (key.matches("\\d+")) {
            String zahlenBase = config.getSound("numbers");
            if (!zahlenBase.isEmpty()) return zahlenBase + key;
        }
        return "";
    }

    private String extractPrefix(String lineName) {
        if (lineName.toUpperCase().startsWith("U")) return "u";
        if (lineName.toUpperCase().startsWith("S")) return "s";
        return lineName.substring(0, 1).toLowerCase();
    }

    private String extractNumber(String lineName) {
        return lineName.replaceAll("[^0-9]", "");
    }

    /**
     * Zerlegt eine Zahl in Sound-Keys.
     * Einstellige Zahlen: direkt ("1" → ["1"])
     * Mehrstellige Zahlen: als einzelne Zahl wenn ≤20, sonst Ziffern
     *   "12" → ["12"] (wenn sound "12" existiert) sonst ["1","2"]
     */
    private List<String> extractNumberKeys(String number) {
        List<String> result = new ArrayList<>();
        if (number.isEmpty()) return result;

        // Erst versuchen ob die ganze Zahl als Sound existiert
        if (!resolveSound(number).isEmpty()) {
            result.add(number);
            return result;
        }

        // Sonst Ziffern einzeln
        for (char c : number.toCharArray()) {
            result.add(String.valueOf(c));
        }
        return result;
    }

    /** Rundet Minuten auf nächsten 5-Minuten-Block auf */
    private long roundToFiveMinutes(long minutes) {
        if (minutes <= 0) return 5;
        return (long) (Math.ceil(minutes / 5.0) * 5);
    }

    private List<Player> getListeners(Location location) {
        List<Player> result = new ArrayList<>();
        if (location.getWorld() == null) return result;
        for (Player p : location.getWorld().getPlayers()) {
            if (p.getLocation().distance(location) <= config.getRadius()) {
                result.add(p);
            }
        }
        return result;
    }
}
