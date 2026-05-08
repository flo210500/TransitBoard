package de.transitboard.listener;

import de.transitboard.TransitBoardPlugin;
import de.transitboard.model.FISSign;
import de.transitboard.model.GleisConfig;
import de.transitboard.model.StationConfig;
import org.bukkit.ChatColor;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class FISSignEditListener implements Listener {

    private final TransitBoardPlugin plugin;

    public FISSignEditListener(TransitBoardPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSignInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getClickedBlock() == null) return;
        if (!(event.getClickedBlock().getState() instanceof Sign sign)) return;

        Player player = event.getPlayer();
        if (!player.hasPermission("transitboard.admin")) return;
        if (!player.isSneaking()) return;

        // Prüfen ob dieses Schild ein registriertes FIS-Schild ist
        var loc = event.getClickedBlock().getLocation();

        for (var station : plugin.getConfigManager().getStations().values()) {
            // Bahnhofs-FIS
            for (FISSign fisSign : station.getStationSigns()) {
                if (isSignLoc(fisSign, loc)) {
                    event.setCancelled(true);
                    openSignEditor(player, sign, station, null, fisSign);
                    return;
                }
            }
            // Gleis-FIS
            for (var gleis : station.getGleise().values()) {
                for (FISSign fisSign : gleis.getSigns()) {
                    if (isSignLoc(fisSign, loc)) {
                        event.setCancelled(true);
                        openSignEditor(player, sign, station, gleis, fisSign);
                        return;
                    }
                }
            }
        }
    }

    private boolean isSignLoc(FISSign fisSign, org.bukkit.Location loc) {
        return (fisSign.getLoc1() != null && fisSign.getLoc1().getBlockX() == loc.getBlockX()
                && fisSign.getLoc1().getBlockY() == loc.getBlockY()
                && fisSign.getLoc1().getBlockZ() == loc.getBlockZ())
            || (fisSign.getLoc2() != null && fisSign.getLoc2().getBlockX() == loc.getBlockX()
                && fisSign.getLoc2().getBlockY() == loc.getBlockY()
                && fisSign.getLoc2().getBlockZ() == loc.getBlockZ())
            || (fisSign.getLoc3() != null && fisSign.getLoc3().getBlockX() == loc.getBlockX()
                && fisSign.getLoc3().getBlockY() == loc.getBlockY()
                && fisSign.getLoc3().getBlockZ() == loc.getBlockZ());
    }

    private void openSignEditor(Player player, Sign sign, StationConfig station,
                                 GleisConfig gleis, FISSign fisSign) {
        // Info anzeigen
        player.sendMessage(ChatColor.DARK_AQUA + "══ FIS-Schild Info ══");
        player.sendMessage(ChatColor.GRAY + "Bahnhof: " + ChatColor.WHITE + station.getId()
            + ChatColor.GRAY + " (" + station.getDisplayName() + ")");
        if (gleis != null) {
            player.sendMessage(ChatColor.GRAY + "Gleis: " + ChatColor.WHITE + gleis.getId()
                + ChatColor.GRAY + " (" + gleis.getDisplayName() + ")");
        } else {
            player.sendMessage(ChatColor.GRAY + "Typ: " + ChatColor.WHITE + "Bahnhofs-FIS");
        }
        player.sendMessage(ChatColor.GRAY + "Modus: " + ChatColor.WHITE + fisSign.getMode().name().toLowerCase());
        player.sendMessage(ChatColor.GRAY + "Position: " + ChatColor.WHITE
            + fisSign.getLoc1().getBlockX() + ", "
            + fisSign.getLoc1().getBlockY() + ", "
            + fisSign.getLoc1().getBlockZ());

        // Schild-Editor öffnen mit vorausgefüllten Werten
        try {
            var front = sign.getSide(Side.FRONT);
            front.setLine(0, "[tdanzeige]");
            front.setLine(1, station.getId());
            front.setLine(2, gleis != null ? gleis.getId() : "");
            front.setLine(3, "");
            sign.update(true, false);
            player.openSign(sign, Side.FRONT);
        } catch (Exception e) {
            plugin.getLogger().warning("Konnte Sign-Editor nicht öffnen: " + e.getMessage());
        }
    }
}
