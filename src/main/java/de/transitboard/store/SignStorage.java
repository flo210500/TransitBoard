package de.transitboard.store;

import de.transitboard.TransitBoardPlugin;
import de.transitboard.model.FISSign;
import de.transitboard.model.StationConfig;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class SignStorage {

    private final TransitBoardPlugin plugin;
    private final Logger log;
    private final File file;

    public SignStorage(TransitBoardPlugin plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
        this.file   = new File(plugin.getDataFolder(), "signs.yml");
    }

    public void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        int count = 0;

        // Bahnhofs-FIS
        var stationSec = yaml.getConfigurationSection("station-signs");
        if (stationSec != null) {
            for (String stationId : stationSec.getKeys(false)) {
                StationConfig station = plugin.getConfigManager().getStations().get(stationId);
                if (station == null) continue;
                for (Map<?, ?> raw : stationSec.getMapList(stationId)) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) raw;
                    Location loc1 = parseLocation(m, "loc1");
                    if (loc1 == null) loc1 = parseLocation(m, "left"); // legacy
                    if (loc1 == null) loc1 = parseLocation(m, null);   // legacy single
                    if (loc1 == null) continue;

                    String modeStr = m.getOrDefault("mode", "single").toString();
                    FISSign.Mode mode = switch (modeStr) {
                        case "quad"   -> FISSign.Mode.QUAD;
                        case "triple" -> FISSign.Mode.TRIPLE;
                        case "double" -> FISSign.Mode.DOUBLE;
                        default       -> FISSign.Mode.SINGLE;
                    };
                    Location loc2 = parseLocation(m, "loc2");
                    Location loc3 = parseLocation(m, "loc3");
                    Location loc4 = parseLocation(m, "loc4");
                    station.addStationSign(new FISSign(loc1, loc2, loc3, loc4, mode, station.getDisplayName()));
                    count++;
                }
            }
        }

        // Gleis-FIS
        var gleisSec = yaml.getConfigurationSection("gleis-signs");
        if (gleisSec != null) {
            for (String stationId : gleisSec.getKeys(false)) {
                StationConfig station = plugin.getConfigManager().getStations().get(stationId);
                if (station == null) continue;
                var gs = gleisSec.getConfigurationSection(stationId);
                if (gs == null) continue;
                for (String gleisId : gs.getKeys(false)) {
                    for (Map<?, ?> raw : gs.getMapList(gleisId)) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> m = (Map<String, Object>) raw;
                        Location loc1 = parseLocation(m, "loc1");
                        if (loc1 == null) loc1 = parseLocation(m, null);
                        if (loc1 == null) continue;
                        String modeStr = m.getOrDefault("mode", "single").toString();
                        FISSign.Mode gMode = switch (modeStr) {
                            case "triple" -> FISSign.Mode.TRIPLE;
                            case "double" -> FISSign.Mode.DOUBLE;
                            default       -> FISSign.Mode.SINGLE;
                        };
                        Location loc2 = parseLocation(m, "loc2");
                        Location loc3 = parseLocation(m, "loc3");
                        String gleisName = station.getGleis(gleisId) != null
                            ? station.getGleis(gleisId).getDisplayName() : gleisId;
                        station.addGleisSign(gleisId,
                            new FISSign(loc1, loc2, loc3, null, gMode, gleisName));
                        count++;
                    }
                }
            }
        }

        plugin.debugLog(count + " FIS-Schild(er) aus signs.yml geladen.");

        // Alle Schilder auf Grundzustand zurücksetzen
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (var station : plugin.getConfigManager().getStations().values()) {
                for (var sign : station.getStationSigns()) {
                    resetSign(sign, station.getDisplayName());
                }
                for (var gleis : station.getGleise().values()) {
                    for (var sign : gleis.getSigns()) {
                        resetSign(sign, gleis.getDisplayName());
                    }
                }
            }
        }, 20L); // 1 Sekunde nach Start damit die Welt geladen ist
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();

        for (var entry : plugin.getConfigManager().getStations().entrySet()) {
            String stationId = entry.getKey();
            StationConfig station = entry.getValue();

            if (!station.getStationSigns().isEmpty()) {
                List<Map<String, Object>> list = new ArrayList<>();
                for (FISSign fs : station.getStationSigns()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("mode", fs.getMode().name().toLowerCase());
                    putLocation(m, "loc1", fs.getLoc1());
                    if (fs.getLoc2() != null) putLocation(m, "loc2", fs.getLoc2());
                    if (fs.getLoc3() != null) putLocation(m, "loc3", fs.getLoc3());
                    if (fs.getLoc4() != null) putLocation(m, "loc4", fs.getLoc4());
                    list.add(m);
                }
                yaml.set("station-signs." + stationId, list);
            }

            for (var ge : station.getGleise().entrySet()) {
                var gleisSigns = ge.getValue().getSigns();
                if (!gleisSigns.isEmpty()) {
                    List<Map<String, Object>> list = new ArrayList<>();
                    for (FISSign fs : gleisSigns) {
                        Map<String, Object> m = new LinkedHashMap<>();
                        m.put("mode", fs.getMode().name().toLowerCase());
                        putLocation(m, "loc1", fs.getLoc1());
                        if (fs.getLoc2() != null) putLocation(m, "loc2", fs.getLoc2());
                        if (fs.getLoc3() != null) putLocation(m, "loc3", fs.getLoc3());
                        list.add(m);
                    }
                    yaml.set("gleis-signs." + stationId + "." + ge.getKey(), list);
                }
            }
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            log.warning("Konnte signs.yml nicht speichern: " + e.getMessage());
        }
    }

    private void resetSign(FISSign sign, String headerName) {
        org.bukkit.ChatColor aqua = org.bukkit.ChatColor.DARK_AQUA;
        String h = aqua + center(headerName, 13);
        writeSignLine(sign.getLoc1(), h, "", "", "");
        if (sign.getLoc2() != null) {
            String h2 = sign.isTriple() || sign.isQuad()
                ? aqua + center(plugin.getConfigManager().getHeaderZiel(), 13) : aqua + center(plugin.getConfigManager().getHeaderAbfahrt(), 13);
            writeSignLine(sign.getLoc2(), h2, "", "", "");
        }
        if (sign.getLoc3() != null) writeSignLine(sign.getLoc3(), aqua + center(plugin.getConfigManager().getHeaderAbfahrt(), 13), "", "", "");
        if (sign.getLoc4() != null) writeSignLine(sign.getLoc4(), aqua + center(plugin.getConfigManager().getHeaderAbfahrt(), 13), "", "", "");
    }

    private void writeSignLine(org.bukkit.Location loc, String l0, String l1, String l2, String l3) {
        if (loc == null) return;
        var block = loc.getBlock();
        if (!(block.getState() instanceof org.bukkit.block.Sign sign)) return;
        try {
            var front = sign.getSide(org.bukkit.block.sign.Side.FRONT);
            front.setLine(0, l0); front.setLine(1, l1);
            front.setLine(2, l2); front.setLine(3, l3);
            sign.update(true, false);
        } catch (Exception ignored) {}
    }

    private String center(String text, int width) {
        if (text == null) return "";
        if (text.length() >= width) return text.substring(0, width);
        int pad = width - text.length();
        return " ".repeat(pad / 2) + text + " ".repeat(pad - pad / 2);
    }

    private Location parseLocation(Map<String, Object> m, String prefix) {
        try {
            String p = prefix != null ? prefix + "-" : "";
            String worldName = m.getOrDefault(p + "world", "world").toString();
            int x = Integer.parseInt(m.getOrDefault(p + "x", "0").toString());
            int y = Integer.parseInt(m.getOrDefault(p + "y", "64").toString());
            int z = Integer.parseInt(m.getOrDefault(p + "z", "0").toString());
            World world = plugin.getServer().getWorld(worldName);
            if (world == null) return null;
            return new Location(world, x, y, z);
        } catch (Exception e) {
            return null;
        }
    }

    private void putLocation(Map<String, Object> m, String prefix, Location loc) {
        String p = prefix != null ? prefix + "-" : "";
        m.put(p + "world", loc.getWorld().getName());
        m.put(p + "x", loc.getBlockX());
        m.put(p + "y", loc.getBlockY());
        m.put(p + "z", loc.getBlockZ());
    }
}
