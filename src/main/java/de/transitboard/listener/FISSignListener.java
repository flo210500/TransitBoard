package de.transitboard.listener;

import de.transitboard.TransitBoardPlugin;
import de.transitboard.model.FISSign;
import de.transitboard.model.StationConfig;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;

import java.util.ArrayList;
import java.util.List;

public class FISSignListener implements Listener {

    private final TransitBoardPlugin plugin;

    public FISSignListener(TransitBoardPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSignChange(SignChangeEvent event) {
        String line0 = event.getLine(0);
        if (line0 == null) return;
        if (!line0.trim().toLowerCase().replace("[","").replace("]","").equals("tdanzeige")) return;

        if (!event.getPlayer().hasPermission("transitboard.admin")) {
            event.getPlayer().sendMessage(ChatColor.RED + "Keine Berechtigung.");
            event.setCancelled(true);
            return;
        }
        if (plugin.getConfigManager() == null) {
            event.getPlayer().sendMessage(ChatColor.RED + "Plugin nicht bereit.");
            return;
        }

        String stationId = event.getLine(1) == null ? "" : event.getLine(1).trim().toLowerCase();
        String gleisId   = event.getLine(2) == null ? "" : event.getLine(2).trim().toLowerCase();

        if (stationId.isEmpty()) {
            event.getPlayer().sendMessage(ChatColor.RED + "Zeile 2 = Bahnhof-ID fehlt!");
            event.setCancelled(true);
            return;
        }

        StationConfig station = plugin.getConfigManager().getStations().get(stationId);
        if (station == null) {
            event.getPlayer().sendMessage(ChatColor.RED
                + "Bahnhof '" + stationId + "' nicht gefunden! Verfuegbar: "
                + String.join(", ", plugin.getConfigManager().getStations().keySet()));
            event.setCancelled(true);
            return;
        }

        Location loc1 = event.getBlock().getLocation();
        BlockFace facing = getSignFacing(event.getBlock());
        Location[] neighbours = findEmptySignsLeft(loc1, facing);
        Location loc2 = neighbours[0];
        Location loc3 = neighbours[1];

        Location loc4 = null;
        if (loc3 != null && gleisId.isEmpty()) {
            BlockFace[] order = getSearchOrder(getSignFacing(event.getBlock()));
            for (BlockFace face : order) {
                Location adj4 = loc3.getBlock().getRelative(face).getLocation();
                if (!adj4.equals(loc2) && !adj4.equals(loc1) && isEmptySign(adj4)) {
                    loc4 = adj4;
                    break;
                }
            }
        }

        FISSign.Mode mode;
        if (loc4 != null)      mode = FISSign.Mode.QUAD;
        else if (loc3 != null) mode = FISSign.Mode.TRIPLE;
        else if (loc2 != null) mode = FISSign.Mode.DOUBLE;
        else                   mode = FISSign.Mode.SINGLE;

        plugin.debugLog("FIS-Erkennung: mode=" + mode
            + " loc1=" + formatLoc(loc1)
            + " loc2=" + (loc2 != null ? formatLoc(loc2) : "null")
            + " loc3=" + (loc3 != null ? formatLoc(loc3) : "null")
            + " loc4=" + (loc4 != null ? formatLoc(loc4) : "null"));

        final Location finalLoc4 = loc4;

        if (gleisId.isEmpty()) {
            station.addStationSign(new FISSign(loc1, loc2, loc3, finalLoc4, mode, station.getDisplayName()));
            labelStationSigns(event, station.getDisplayName(), mode, loc2, loc3, finalLoc4);
            event.getPlayer().sendMessage(ChatColor.GREEN
                + "Bahnhofs-FIS (" + mode.name().toLowerCase()
                + ") fuer '" + station.getDisplayName() + "' registriert!");

        } else {
            // Gleis auch mit "gleis"-Prefix versuchen
            if (station.getGleis(gleisId) == null && !gleisId.isEmpty()) {
                if (station.getGleis("gleis" + gleisId) != null) gleisId = "gleis" + gleisId;
                else if (gleisId.startsWith("gleis") && station.getGleis(gleisId.substring(5)) != null) gleisId = gleisId.substring(5);
            }
            if (station.getGleis(gleisId) == null) {
                // Fallback: "1" → "gleis1" versuchen
                if (station.getGleis("gleis" + gleisId) != null) {
                    gleisId = "gleis" + gleisId;
                } else {
                    event.getPlayer().sendMessage(ChatColor.RED
                        + "Gleis '" + gleisId + "' nicht gefunden! Verfuegbar: "
                        + String.join(", ", station.getGleise().keySet()));
                    event.setCancelled(true);
                    return;
                }
            }
            String gleisName = station.getGleis(gleisId).getDisplayName();

            Location gleis2 = neighbours[0];
            Location gleis3 = gleis2 != null ? neighbours[1] : null;

            FISSign.Mode gleisMode;
            if (gleis3 != null)      gleisMode = FISSign.Mode.TRIPLE;
            else if (gleis2 != null) gleisMode = FISSign.Mode.DOUBLE;
            else                     gleisMode = FISSign.Mode.SINGLE;

            station.addGleisSign(gleisId,
                new FISSign(loc1, gleis2, gleis3, null, gleisMode, gleisName));

            event.setLine(0, ChatColor.DARK_AQUA + center(gleisName, 13));
            event.setLine(1, ""); event.setLine(2, ""); event.setLine(3, "");
            if (gleisMode == FISSign.Mode.TRIPLE) {
                initSign(gleis2, ChatColor.DARK_AQUA + center("Ziel", 13), "", "", "");
                initSign(gleis3, ChatColor.DARK_AQUA + center("Abfahrt", 13), "", "", "");
            } else if (gleisMode == FISSign.Mode.DOUBLE) {
                initSign(gleis2, ChatColor.DARK_AQUA + center("Abfahrt", 13), "", "", "");
            }
            event.getPlayer().sendMessage(ChatColor.GREEN
                + "Gleis-FIS (" + gleisMode.name().toLowerCase()
                + ") fuer '" + station.getDisplayName() + " / " + gleisName + "' registriert!");
        }

        plugin.debugLog("FIS registriert: station='"
            + stationId + "' gleis='" + gleisId + "' modus=" + mode);

        if (plugin.getSignStorage() != null) plugin.getSignStorage().save();
    }

    private void labelStationSigns(SignChangeEvent event, String stationName,
                                   FISSign.Mode mode, Location loc2, Location loc3, Location loc4) {
        switch (mode) {
            case SINGLE -> {
                event.setLine(0, ChatColor.DARK_AQUA + center(stationName, 13));
                event.setLine(1, ""); event.setLine(2, "");
                event.setLine(3, ChatColor.GRAY + "Warte...");
            }
            case DOUBLE -> {
                event.setLine(0, ChatColor.DARK_AQUA + center(stationName, 13));
                event.setLine(1, ""); event.setLine(2, ""); event.setLine(3, "");
                initSign(loc2, ChatColor.DARK_AQUA + center("Abfahrt", 13), "", "", "");
            }
            case TRIPLE -> {
                event.setLine(0, ChatColor.DARK_AQUA + center(stationName, 13));
                event.setLine(1, ""); event.setLine(2, ""); event.setLine(3, "");
                initSign(loc2, ChatColor.DARK_AQUA + center("Ziel", 13), "", "", "");
                initSign(loc3, ChatColor.DARK_AQUA + center("Abfahrt", 13), "", "", "");
            }
            case QUAD -> {
                event.setLine(0, ChatColor.DARK_AQUA + center("Gleis", 13));
                event.setLine(1, ""); event.setLine(2, ""); event.setLine(3, "");
                initSign(loc2, ChatColor.DARK_AQUA + center(stationName, 13), "", "", "");
                initSign(loc3, ChatColor.DARK_AQUA + center("Ziel", 13), "", "", "");
                initSign(loc4, ChatColor.DARK_AQUA + center("Abfahrt", 13), "", "", "");
            }
        }
    }

    private Location[] findEmptySignsLeft(Location loc, BlockFace facing) {
        BlockFace[] searchOrder = getSearchOrder(facing);
        List<Location> found = new ArrayList<>();

        for (BlockFace face : searchOrder) {
            if (found.size() >= 2) break;
            Location adj = loc.getBlock().getRelative(face).getLocation();
            if (isEmptySign(adj)) {
                found.add(adj);
                if (found.size() == 1) {
                    Location adj2 = adj.getBlock().getRelative(face).getLocation();
                    if (isEmptySign(adj2)) found.add(adj2);
                }
                break;
            }
        }

        Location loc2 = found.size() > 0 ? found.get(0) : null;
        Location loc3 = found.size() > 1 ? found.get(1) : null;
        return new Location[]{loc2, loc3};
    }

    private BlockFace[] getSearchOrder(BlockFace facing) {
        BlockFace right = turnRight(facing);
        BlockFace left  = turnLeft(facing);
        return new BlockFace[]{right, left, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
    }

    private boolean isEmptySign(Location loc) {
        Block block = loc.getBlock();
        if (!(block.getState() instanceof Sign sign)) return false;
        try {
            var front = sign.getSide(Side.FRONT);
            for (int i = 0; i < 4; i++) {
                String line = front.getLine(i);
                if (line != null && !org.bukkit.ChatColor.stripColor(line).isBlank()) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private BlockFace getSignFacing(Block block) {
        if (block.getBlockData() instanceof org.bukkit.block.data.type.WallSign ws) {
            return ws.getFacing();
        }
        return BlockFace.SOUTH;
    }

    private BlockFace turnLeft(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.WEST;
            case WEST  -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.EAST;
            case EAST  -> BlockFace.NORTH;
            default    -> BlockFace.WEST;
        };
    }

    private BlockFace turnRight(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST  -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST  -> BlockFace.NORTH;
            default    -> BlockFace.EAST;
        };
    }

    private void initSign(Location loc, String l0, String l1, String l2, String l3) {
        if (loc == null) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!(loc.getBlock().getState() instanceof Sign sign)) return;
            try {
                var front = sign.getSide(Side.FRONT);
                front.setLine(0, l0); front.setLine(1, l1);
                front.setLine(2, l2); front.setLine(3, l3);
                sign.update(true, false);
            } catch (Exception ignored) {}
        }, 2L);
    }

    private String center(String text, int width) {
        if (text.length() >= width) return text.substring(0, width);
        int pad = width - text.length();
        return " ".repeat(pad / 2) + text + " ".repeat(pad - pad / 2);
    }

    private String formatLoc(Location loc) {
        return loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }
}
