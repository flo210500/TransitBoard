package de.transitboard.announcement;

import com.bergerkiller.bukkit.tc.controller.MinecartGroup;
import com.bergerkiller.bukkit.tc.controller.MinecartMember;
import de.transitboard.TransitBoardPlugin;
import de.transitboard.model.LineConfig;
import de.transitboard.model.LineStop;
import de.transitboard.model.StationConfig;
import de.transitboard.model.TrainPosition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Spielt Innenraum-Ansagen für Fahrgäste im Zug ab.
 *
 * Prüft jede Sekunde ob ein Zug die Schwelle zur nächsten Station
 * unterschritten hat und spielt dann eine Ansage für alle Spieler im Zug.
 *
 * Verhindert Doppel-Ansagen durch ein "bereits angesagt"-Set.
 * Wird zurückgesetzt wenn der Zug eine neue Station passiert.
 */
public class InTrainAnnouncer extends BukkitRunnable {

    private final TransitBoardPlugin plugin;

    // "trainName/stationId" → bereits angesagt
    private final Set<String> announced = new HashSet<>();

    // Config-Werte
    private boolean enabled;
    private int thresholdSeconds;
    private String stationsSoundPath;
    private String sequence;
    private boolean useTitle;
    private String titleText;
    private String subtitleText;
    private float volume;
    private float pitch;
    private String namespace;

    public InTrainAnnouncer(TransitBoardPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        ConfigurationSection sec = plugin.getConfig()
            .getConfigurationSection("announcements.in-train");
        if (sec == null) { enabled = false; return; }

        enabled            = sec.getBoolean("enabled", true);
        thresholdSeconds   = sec.getInt("threshold-seconds", 60);
        stationsSoundPath  = sec.getString("stations-sound-path", "traindisplay/stations/");
        sequence           = sec.getString("sequence", "next_station,{station_sound}");
        useTitle           = false;
        titleText          = sec.getString("chat-prefix", "§8[§bZug§8] §7Nächste Station: §f");
        subtitleText       = sec.getString("subtitle", "{station_name}");
        volume             = (float) sec.getDouble("volume", 0.8);
        pitch              = (float) sec.getDouble("pitch", 1.0);
        namespace          = plugin.getConfig().getString("announcements.namespace", "meinserver");
    }

    @Override
    public void run() {
        if (!enabled) return;

        var lineStore = plugin.getLineTimingStore();
        if (lineStore == null) return;

        for (Map.Entry<String, TrainPosition> entry : lineStore.getPositions().entrySet()) {
            String trainName = entry.getKey();
            TrainPosition pos = entry.getValue();

            LineConfig line = plugin.getConfigManager().getLine(pos.getLineName());
            if (line == null) continue;

            // Nächste Station bestimmen
            List<LineStop> upcoming = line.getStopsAfter(pos.getLastStationId(), pos.getDirection());
            if (upcoming.isEmpty()) continue;

            LineStop nextStop = upcoming.get(0);
            String announcedKey = trainName + "/" + nextStop.getStationId();

            // Schon angesagt?
            if (announced.contains(announcedKey)) continue;

            // Pro-Linie prüfen ob Innenraum-Ansagen aktiv
            var lineConfig = plugin.getConfigManager().getLine(pos.getLineName());
            if (lineConfig != null && lineConfig.getInTrainEnabled() != null
                    && !lineConfig.getInTrainEnabled()) continue;

            // ETA zur nächsten Station
            Map<String, Long> etas = lineStore.getETAs(trainName, line);
            Long eta = etas.get(nextStop.getStationId());
            if (eta == null || eta < 0) continue;

            // Schwelle unterschritten?
            if (eta > thresholdSeconds) continue;

            // Ansage machen
            StationConfig station = plugin.getConfigManager()
                .getStations().get(nextStop.getStationId());
            String stationDisplayName = station != null
                ? station.getDisplayName() : nextStop.getStationId();

            announceInTrain(trainName, pos.getLineName(), nextStop.getStationId(), stationDisplayName);
            announced.add(announcedKey);
        }

        // Alte Einträge bereinigen – wenn Zug eine Station passiert hat
        // wird der Key nicht mehr relevant
        announced.removeIf(key -> {
            String[] parts = key.split("/", 2);
            if (parts.length < 2) return true;
            TrainPosition pos = plugin.getLineTimingStore().getPositions().get(parts[0]);
            if (pos == null) return true; // Zug nicht mehr aktiv
            // Wenn Zug jetzt schon weiter ist, Key entfernen
            return pos.getLastStationId().equals(parts[1]);
        });
    }

    /**
     * Spielt die Ansage für alle Spieler im Zug ab.
     */
    private void announceInTrain(String trainName, String lineName,
                                  String stationId, String stationDisplayName) {
        // Zug-Gruppe finden
        MinecartGroup group = findGroup(trainName);
        if (group == null) return;

        // Alle Spieler im Zug sammeln
        List<Player> passengers = new java.util.ArrayList<>();
        for (MinecartMember<?> member : group) {
            for (Entity e : member.getEntity().getPassengers()) {
                if (e instanceof Player p) passengers.add(p);
            }
        }
        if (passengers.isEmpty()) return;

        // Chat-Nachricht mit Linienname
        var lineConfig = plugin.getConfigManager().getLine(lineName);
        String lineColor = lineConfig != null ? lineConfig.getColor() : "§f";
        String chatMsg = "§8[" + lineColor + lineName + "§8] §7Nächste Station: §f" + stationDisplayName;
        for (Player p : passengers) {
            p.sendMessage(chatMsg);
        }

        // Sound abspielen
        if (!sequence.isEmpty()) {
            String stationSound = namespace + ":" + stationsSoundPath + stationId;
            String nextStationSound = namespace + ":traindisplay/next_station";

            for (Player p : passengers) {
                int delay = 0;
                for (String part : sequence.split(",")) {
                    part = part.trim();
                    final String soundKey;
                    final int finalDelay = delay;

                    if (part.equals("{station_sound}")) {
                        soundKey = stationSound;
                    } else if (part.equals("next_station")) {
                        soundKey = nextStationSound;
                    } else {
                        delay += 8;
                        continue;
                    }

                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                        if (p.isOnline()) {
                            try {
                                p.playSound(p.getLocation(), soundKey, volume, pitch);
                            } catch (Exception ignored) {}
                        }
                    }, finalDelay);
                    delay += 8;
                }
            }
        }

        plugin.getLogger().info("Innenraum-Ansage: " + trainName
            + " → " + stationDisplayName);
    }

    private MinecartGroup findGroup(String trainName) {
        for (MinecartGroup group : MinecartGroup.getGroups()) {
            if (group == null || group.isEmpty()) continue;
            if (group.getProperties().getTrainName().equals(trainName)) return group;
        }
        return null;
    }

    /** Wird aufgerufen wenn ein Zug eine Station passiert – Reset für diese Station */
    public void onStationPassed(String trainName, String stationId) {
        announced.remove(trainName + "/" + stationId);
    }
}
