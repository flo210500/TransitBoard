package de.transitboard.listener;

import de.transitboard.TransitBoardPlugin;
import de.transitboard.model.FISSign;
import de.transitboard.model.GleisConfig;
import de.transitboard.model.StationConfig;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Entfernt FIS-Schilder aus dem Speicher wenn sie abgebaut werden.
 */
public class SignBreakListener implements Listener {

    private final TransitBoardPlugin plugin;

    public SignBreakListener(TransitBoardPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        boolean removed = false;

        for (StationConfig station : plugin.getConfigManager().getStations().values()) {

            // Bahnhofs-FIS prüfen
            var iter = station.getStationSigns().iterator();
            while (iter.hasNext()) {
                FISSign sign = iter.next();
                if (locEquals(sign.getLoc1(), loc) || locEquals(sign.getLoc2(), loc)
                 || locEquals(sign.getLoc3(), loc) || locEquals(sign.getLoc4(), loc)) {
                    iter.remove();
                    removed = true;
                    plugin.getLogger().info("FIS-Schild entfernt: "
                        + formatLoc(loc) + " (Bahnhof: " + station.getId() + ")");
                }
            }

            // Gleis-FIS prüfen
            for (GleisConfig gleis : station.getGleise().values()) {
                var gleisIter = gleis.getSigns().iterator();
                while (gleisIter.hasNext()) {
                    FISSign gs = gleisIter.next();
                    if (locEquals(gs.getLoc1(), loc) || locEquals(gs.getLoc2(), loc)
                     || locEquals(gs.getLoc3(), loc)) {
                        gleisIter.remove();
                        removed = true;
                        plugin.getLogger().info("Gleis-FIS-Schild entfernt: "
                            + formatLoc(loc) + " (Gleis: " + gleis.getId() + ")");
                    }
                }
            }
        }

        if (removed && plugin.getSignStorage() != null) {
            plugin.getSignStorage().save();
        }
    }

    private boolean locEquals(Location a, Location b) {
        if (a == null || b == null) return false;
        return a.getBlockX() == b.getBlockX()
            && a.getBlockY() == b.getBlockY()
            && a.getBlockZ() == b.getBlockZ()
            && a.getWorld().equals(b.getWorld());
    }

    private String formatLoc(Location loc) {
        return loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }
}
