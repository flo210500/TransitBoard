package de.transitboard.config;

import de.transitboard.TransitBoardPlugin;
import de.transitboard.model.GleisConfig;
import de.transitboard.model.LineConfig;
import de.transitboard.model.LineStop;
import de.transitboard.model.StationConfig;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;
import java.util.logging.Logger;

/**
 * Lädt Bahnhöfe und Gleise aus der config.yml.
 * FIS-Schilder werden NICHT mehr hier konfiguriert –
 * sie registrieren sich selbst beim Platzieren ([TDANZEIGE]-Schild).
 */
public class ConfigManager {

    private final TransitBoardPlugin plugin;
    private final Logger log;

    private int sofortThreshold;
    private int historySize;
    private int updateInterval;
    private int holdoverSeconds;

    private String formatSofort;
    private String formatMinutes;
    private String headerAbfahrt;
    private String headerZiel;
    private String headerGleis;
    private String formatSeconds;
    private String formatLinePrefix;
    private String formatNoService;

    private final Map<String, StationConfig> stations = new LinkedHashMap<>();
    private final Map<String, LineConfig> lines = new LinkedHashMap<>();

    public ConfigManager(TransitBoardPlugin plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
    }

    public void load() {
        plugin.reloadConfig();
        stations.clear();

        sofortThreshold = plugin.getConfig().getInt("sofort-threshold-seconds", 10);
        historySize     = plugin.getConfig().getInt("timing-history-size", 5);
        updateInterval  = plugin.getConfig().getInt("update-interval", 20);
        holdoverSeconds = plugin.getConfig().getInt("holdover-seconds", 30);

        formatSofort     = plugin.getConfig().getString("format-sofort",     "Sofort");
        formatMinutes    = plugin.getConfig().getString("format-minutes",     "%d Min");
        headerAbfahrt    = plugin.getConfig().getString("header-abfahrt",    "Abfahrt");
        headerZiel       = plugin.getConfig().getString("header-ziel",       "Ziel");
        headerGleis      = plugin.getConfig().getString("header-gleis",      "Gleis");
        formatSeconds    = plugin.getConfig().getString("format-seconds",     "%d Sek");
        formatLinePrefix = plugin.getConfig().getString("format-line-prefix", "[%s]");
        formatNoService  = plugin.getConfig().getString("format-no-service",  "Kein Betrieb");

        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("stations");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection sc = sec.getConfigurationSection(id);
                if (sc == null) continue;
                try {
                    StationConfig s = parseStation(id, sc);
                    stations.put(id, s);
                    plugin.debugLog("Bahnhof geladen: " + id + " (" + s.getDisplayName()
                           + ") mit " + s.getGleise().size() + " Gleis(en)");
                } catch (Exception e) {
                    log.warning("Fehler beim Laden von Bahnhof '" + id + "': " + e.getMessage());
                }
            }
        }

        loadLines();
        plugin.debugLog("Konfiguration geladen: " + stations.size() + " Bahnhof/Bahnhoefe, " + lines.size() + " Linie(n).");
    }

    private StationConfig parseStation(String id, ConfigurationSection sc) {
        String displayName = sc.getString("display-name", id);

        Map<String, GleisConfig> gleise = new LinkedHashMap<>();
        ConfigurationSection gleiseSec = sc.getConfigurationSection("gleise");
        if (gleiseSec != null) {
            for (String gleisId : gleiseSec.getKeys(false)) {
                ConfigurationSection gc = gleiseSec.getConfigurationSection(gleisId);
                if (gc == null) continue;
                String gleisName = gc.getString("display-name", gleisId);
                // Schilder-Liste leer – wird dynamisch befüllt
                gleise.put(gleisId.toLowerCase(),
                           new GleisConfig(gleisId.toLowerCase(), gleisName));
                plugin.debugLog("  Gleis geladen: " + gleisId + " (" + gleisName + ")");
            }
        }

        StationConfig station = new StationConfig(id, displayName, gleise);

        // Ankündigungs-Position laden
        if (sc.isConfigurationSection("announcement-location")) {
            var al = sc.getConfigurationSection("announcement-location");
            if (al != null) {
                String w = al.getString("world", "world");
                double x = al.getDouble("x", 0);
                double y = al.getDouble("y", 64);
                double z = al.getDouble("z", 0);
                org.bukkit.World world = plugin.getServer().getWorld(w);
                if (world != null) station.setAnnouncementLocation(new org.bukkit.Location(world, x, y, z));
            }
        }
        return station;
    }

    // ─── Getter ───────────────────────────────────────────────────────────────

    public int getSofortThreshold()     { return sofortThreshold; }
    public int getHistorySize()         { return historySize; }
    public int getUpdateInterval()      { return updateInterval; }
    public int getHoldoverSeconds()     { return holdoverSeconds; }
    public String getFormatSofort()     { return formatSofort; }
    public String getFormatMinutes()    { return formatMinutes; }
    public String getHeaderAbfahrt()    { return headerAbfahrt; }
    public String getHeaderZiel()       { return headerZiel; }
    public String getHeaderGleis()      { return headerGleis; }
    public String getFormatSeconds()    { return formatSeconds; }
    public String getFormatLinePrefix() { return formatLinePrefix; }
    public String getFormatNoService()  { return formatNoService; }
    public Map<String, StationConfig> getStations() { return stations; }
    public Map<String, LineConfig> getLines() { return lines; }

    public LineConfig getLine(String lineName) {
        if (lineName == null) return null;
        // Erst exakt suchen, dann case-insensitiv
        LineConfig exact = lines.get(lineName);
        if (exact != null) return exact;
        return lines.get(lineName.toLowerCase());
    }

    private void loadLines() {
        lines.clear();
        var sec = plugin.getConfig().getConfigurationSection("lines");
        if (sec == null) return;
        for (String lineId : sec.getKeys(false)) {
            var lc = sec.getConfigurationSection(lineId);
            if (lc == null) continue;
            String typeStr = lc.getString("type", "ring").toLowerCase();
            LineConfig.Type type = typeStr.equals("shuttle")
                ? LineConfig.Type.SHUTTLE : LineConfig.Type.RING;
            List<LineStop> stops = new ArrayList<>();
            for (var m : lc.getMapList("stops")) {
                @SuppressWarnings("unchecked")
                var map = (java.util.Map<String, Object>) m;
                String stationId = map.getOrDefault("station", "").toString();
                String gleisId   = map.getOrDefault("gleis", "gleis1").toString();
                if (!stationId.isEmpty()) stops.add(new LineStop(stationId, gleisId));
            }
            String color = lc.getString("color", "§f");
            {
                LineConfig lineConfig = new LineConfig(lineId, type, stops, color);
                if (lc.contains("destination")) lineConfig.setDestination(lc.getString("destination", ""));
                if (lc.contains("display-name")) lineConfig.setDisplayName(lc.getString("display-name", ""));
                // Pro-Linie Ansagen laden
                var ann = lc.getConfigurationSection("announcements");
                if (ann != null) {
                    if (ann.contains("enabled"))            lineConfig.setAnnouncementsEnabled(ann.getBoolean("enabled"));
                    if (ann.contains("in-train"))           lineConfig.setInTrainEnabled(ann.getBoolean("in-train"));
                    if (ann.contains("on-arrival"))         lineConfig.setOnArrival(ann.getBoolean("on-arrival"));
                    if (ann.contains("on-departure"))       lineConfig.setOnDeparture(ann.getBoolean("on-departure"));
                    if (ann.contains("on-delay"))           lineConfig.setOnDelay(ann.getBoolean("on-delay"));
                    if (ann.contains("sequence-arrival"))   lineConfig.setSequenceArrival(ann.getString("sequence-arrival"));
                    if (ann.contains("sequence-departure")) lineConfig.setSequenceDeparture(ann.getString("sequence-departure"));
                    if (ann.contains("sequence-delay"))     lineConfig.setSequenceDelay(ann.getString("sequence-delay"));
                }
                lines.put(lineId, lineConfig);
                plugin.debugLog("Linie geladen: " + lineId + " (" + type + ") mit " + stops.size() + " Stops");
            }
        }
    }
}
