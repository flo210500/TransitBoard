package de.transitboard.store;

import de.transitboard.TransitBoardPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * Speichert und lädt die Segment-Fahrtzeiten des Linien-Systems in line-timing.yml.
 *
 * Format:
 *   segments:
 *     "U1/hbf/bhf_a":
 *       - 30000
 *       - 31500
 *       - 29800
 */
public class LineTimingStorage {

    private final TransitBoardPlugin plugin;
    private final Logger log;
    private final File file;

    public LineTimingStorage(TransitBoardPlugin plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
        this.file   = new File(plugin.getDataFolder(), "line-timing.yml");
    }

    public void load(Map<String, Deque<Long>> segmentTimes) {
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
        log.info(count + " Segment-Fahrtzeiten aus line-timing.yml geladen.");
    }

    public void save(Map<String, Deque<Long>> segmentTimes) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (var e : segmentTimes.entrySet()) {
            yaml.set("segments." + e.getKey(), new ArrayList<>(e.getValue()));
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            log.warning("Konnte line-timing.yml nicht speichern: " + e.getMessage());
        }
    }
}
