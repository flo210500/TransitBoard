package de.transitboard.store;

import de.transitboard.TransitBoardPlugin;
import de.transitboard.model.TrackKey;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * Speichert und lädt die gemessenen Fahrtzeiten (timingHistory) in timing.yml.
 *
 * Format:
 *   timings:
 *     "hbf/gleis1/L1":
 *       - 45000
 *       - 47000
 *       - 44500
 */
public class TimingStorage {

    private final TransitBoardPlugin plugin;
    private final Logger log;
    private final File file;

    public TimingStorage(TransitBoardPlugin plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
        this.file   = new File(plugin.getDataFolder(), "timing.yml");
    }

    public void loadSegments(Map<String, Deque<Long>> segmentTimes) {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var sec = yaml.getConfigurationSection("segments");
        if (sec == null) return;
        int count = 0;
        for (String key : sec.getKeys(false)) {
            List<Long> values = new ArrayList<>();
            for (Object v : yaml.getList("segments." + key, List.of())) {
                try { values.add(Long.parseLong(v.toString())); }
                catch (NumberFormatException ignored) {}
            }
            if (!values.isEmpty()) {
                segmentTimes.put(key, new ArrayDeque<>(values));
                count++;
            }
        }
        log.info(count + " Segment-Fahrtzeiten aus timing.yml geladen.");
    }

    public void saveSegments(Map<String, Deque<Long>> segmentTimes) {
        // Laden, Segments updaten, speichern (Legacy-Daten behalten)
        YamlConfiguration yaml = file.exists()
            ? YamlConfiguration.loadConfiguration(file)
            : new YamlConfiguration();
        for (var e : segmentTimes.entrySet()) {
            yaml.set("segments." + e.getKey().replace("/", "_"), new ArrayList<>(e.getValue()));
        }
        try { yaml.save(file); }
        catch (IOException e) { log.warning("Konnte segments nicht speichern: " + e.getMessage()); }
    }

    public void load(Map<TrackKey, Deque<Long>> timingHistory) {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        var section = yaml.getConfigurationSection("timings");
        if (section == null) return;

        int count = 0;
        for (String key : section.getKeys(false)) {
            TrackKey tk = parseKey(key);
            if (tk == null) continue;

            List<Long> values = new ArrayList<>();
            for (Object v : yaml.getList("timings." + key, List.of())) {
                try { values.add(Long.parseLong(v.toString())); }
                catch (NumberFormatException ignored) {}
            }

            if (!values.isEmpty()) {
                timingHistory.put(tk, new ArrayDeque<>(values));
                count++;
            }
        }
        log.info(count + " Fahrtzeit-Einträge aus timing.yml geladen.");
    }

    public void save(Map<TrackKey, Deque<Long>> timingHistory) {
        YamlConfiguration yaml = new YamlConfiguration();

        for (Map.Entry<TrackKey, Deque<Long>> e : timingHistory.entrySet()) {
            String key = formatKey(e.getKey());
            yaml.set("timings." + key, new ArrayList<>(e.getValue()));
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            log.warning("Konnte timing.yml nicht speichern: " + e.getMessage());
        }
    }

    // ─── Hilfsmethoden ────────────────────────────────────────────────────────

    /** TrackKey → "stationId/gleisId/lineName/entryTag" */
    private String formatKey(TrackKey key) {
        String base = key.getStationId() + "/" + key.getGleisId() + "/" + key.getLineName();
        return key.getEntryTag().isEmpty() ? base : base + "/" + key.getEntryTag();
    }

    /** "stationId/gleisId/lineName[/entryTag]" → TrackKey */
    private TrackKey parseKey(String key) {
        String[] parts = key.split("/");
        if (parts.length < 3) return null;
        String stationId = parts[0];
        String gleisId   = parts[1];
        String lineName  = parts[2];
        String entryTag  = parts.length > 3 ? parts[3] : "";
        return new TrackKey(stationId, gleisId, lineName, entryTag);
    }
}
