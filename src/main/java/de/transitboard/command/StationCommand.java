package de.transitboard.command;

import de.transitboard.TransitBoardPlugin;
import de.transitboard.model.StationConfig;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * /td station create <id> <anzeigename>
 * /td station delete <id>
 * /td station addgleis <id> <gleisid> <anzeigename>
 * /td station removegleis <id> <gleisid>
 * /td station list
 * /td station info <id>
 */
public class StationCommand {

    private final TransitBoardPlugin plugin;

    public StationCommand(TransitBoardPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length < 2) { sendHelp(sender); return true; }

        switch (args[1].toLowerCase()) {
            case "create"      -> cmdCreate(sender, args);
            case "delete"      -> cmdDelete(sender, args);
            case "addgleis"    -> cmdAddGleis(sender, args);
            case "removegleis" -> cmdRemoveGleis(sender, args);
            case "rename"      -> cmdRename(sender, args);
            case "renamegleis" -> cmdRenameGleis(sender, args);
            case "setlocation" -> cmdSetLocation(sender, args);
            case "list"        -> cmdList(sender);
            case "info"        -> cmdInfo(sender, args);
            default            -> sendHelp(sender);
        }
        return true;
    }

    private void cmdCreate(CommandSender s, String[] args) {
        if (args.length < 4) {
            s.sendMessage("§cVerwendung: /td station create <id> <anzeigename>");
            s.sendMessage("§7Beispiel: /td station create hbf Hauptbahnhof");
            return;
        }
        String id   = args[2].toLowerCase();
        // Alles ab args[3] als Anzeigename zusammensetzen
        String name = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));

        if (plugin.getConfigManager().getStations().containsKey(id)) {
            s.sendMessage("§cBahnhof '" + id + "' existiert bereits!"); return;
        }

        plugin.getConfig().set("stations." + id + ".display-name", name);
        plugin.getConfig().set("stations." + id + ".gleise", new LinkedHashMap<>());
        plugin.saveConfig();
        plugin.reloadAll();

        s.sendMessage("§aBahnhof §f'" + id + "' §a(" + name + ") erstellt.");
        s.sendMessage("§7Gleise hinzufügen: /td station addgleis " + id + " <gleisid> <anzeigename>");
    }

    private void cmdDelete(CommandSender s, String[] args) {
        if (args.length < 3) { s.sendMessage("§cVerwendung: /td station delete <id>"); return; }
        String id = args[2].toLowerCase();

        if (!plugin.getConfigManager().getStations().containsKey(id)) {
            s.sendMessage("§cBahnhof '" + id + "' nicht gefunden!"); return;
        }

        // Warnung wenn Bahnhof in einer Linie verwendet wird
        plugin.getConfigManager().getLines().forEach((lineId, line) -> {
            if (line.containsStation(id)) {
                s.sendMessage("§e⚠ Bahnhof ist noch in Linie §f'" + lineId + "' §everwendet!");
            }
        });

        plugin.getConfig().set("stations." + id, null);
        plugin.saveConfig();
        plugin.reloadAll();
        s.sendMessage("§aBahnhof §f'" + id + "' §agelöscht.");
    }

    private void cmdAddGleis(CommandSender s, String[] args) {
        if (args.length < 5) {
            s.sendMessage("§cVerwendung: /td station addgleis <bahnhof> <gleisid> <anzeigename>");
            s.sendMessage("§7Beispiel: /td station addgleis hbf gleis1 Gleis 1");
            return;
        }
        String stationId = args[2].toLowerCase();
        String gleisId   = args[3].toLowerCase();
        String gleisName = String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length));

        if (!plugin.getConfigManager().getStations().containsKey(stationId)) {
            s.sendMessage("§cBahnhof '" + stationId + "' nicht gefunden!"); return;
        }

        StationConfig station = plugin.getConfigManager().getStations().get(stationId);
        if (station.getGleis(gleisId) != null) {
            s.sendMessage("§cGleis '" + gleisId + "' existiert bereits in '" + stationId + "'!"); return;
        }

        plugin.getConfig().set("stations." + stationId + ".gleise." + gleisId + ".display-name", gleisName);
        plugin.saveConfig();
        plugin.reloadAll();
        s.sendMessage("§aGleis §f'" + gleisId + "' §a(" + gleisName + ") zu §f'" + stationId + "' §ahinzugefügt.");
    }

    private void cmdRemoveGleis(CommandSender s, String[] args) {
        if (args.length < 4) {
            s.sendMessage("§cVerwendung: /td station removegleis <bahnhof> <gleisid>"); return;
        }
        String stationId = args[2].toLowerCase();
        String gleisId   = args[3].toLowerCase();

        if (!plugin.getConfigManager().getStations().containsKey(stationId)) {
            s.sendMessage("§cBahnhof '" + stationId + "' nicht gefunden!"); return;
        }
        StationConfig station = plugin.getConfigManager().getStations().get(stationId);
        if (station.getGleis(gleisId) == null) {
            s.sendMessage("§cGleis '" + gleisId + "' nicht in '" + stationId + "' gefunden!"); return;
        }

        plugin.getConfig().set("stations." + stationId + ".gleise." + gleisId, null);
        plugin.saveConfig();
        plugin.reloadAll();
        s.sendMessage("§aGleis §f'" + gleisId + "' §aaus §f'" + stationId + "' §aentfernt.");
    }

    private void cmdList(CommandSender s) {
        var stations = plugin.getConfigManager().getStations();
        if (stations.isEmpty()) {
            s.sendMessage("§7Keine Bahnhöfe. Erstellen: /td station create <id> <name>"); return;
        }
        s.sendMessage("§b══ Bahnhöfe ══");
        stations.forEach((id, st) -> s.sendMessage(String.format(
            "§f%s §7(%s) – §f%d Gleis(e)",
            id, st.getDisplayName(), st.getGleise().size())));
    }

    private void cmdInfo(CommandSender s, String[] args) {
        if (args.length < 3) { s.sendMessage("§cVerwendung: /td station info <id>"); return; }
        String id = args[2].toLowerCase();
        StationConfig station = plugin.getConfigManager().getStations().get(id);
        if (station == null) { s.sendMessage("§cBahnhof '" + id + "' nicht gefunden!"); return; }

        s.sendMessage("§b══ Bahnhof " + id + " ══");
        s.sendMessage("§7Anzeigename: §f" + station.getDisplayName());
        s.sendMessage("§7Gleise:");
        station.getGleise().forEach((gid, g) ->
            s.sendMessage("  §f" + gid + " §7– " + g.getDisplayName()));
        s.sendMessage("§7FIS-Schilder: §f" + station.getStationSigns().size());

        // In welchen Linien ist dieser Bahnhof?
        var lineIds = new ArrayList<String>();
        plugin.getConfigManager().getLines().forEach((lineId, line) -> {
            if (line.containsStation(id)) lineIds.add(lineId);
        });
        if (!lineIds.isEmpty()) {
            s.sendMessage("§7Linien: §f" + String.join(", ", lineIds));
        }
    }

    private void cmdSetLocation(CommandSender s, String[] args) {
        if (args.length < 3) {
            s.sendMessage("§cVerwendung: /td station setlocation <id>");
            s.sendMessage("§7Steh am gewünschten Ankündigungs-Punkt."); return;
        }
        if (!(s instanceof org.bukkit.entity.Player p)) {
            s.sendMessage("§cNur für Spieler."); return;
        }
        String id = args[2].toLowerCase();
        if (!plugin.getConfigManager().getStations().containsKey(id)) {
            s.sendMessage("§cBahnhof '" + id + "' nicht gefunden!"); return;
        }

        org.bukkit.Location loc = p.getLocation();

        // In config.yml speichern
        plugin.getConfig().set("stations." + id + ".announcement-location.world",
            loc.getWorld().getName());
        plugin.getConfig().set("stations." + id + ".announcement-location.x", loc.getX());
        plugin.getConfig().set("stations." + id + ".announcement-location.y", loc.getY());
        plugin.getConfig().set("stations." + id + ".announcement-location.z", loc.getZ());
        plugin.saveConfig();
        plugin.reloadAll();

        s.sendMessage("§aAnsage-Position für §f'" + id + "' §aauf deine Position gesetzt.");
        s.sendMessage(String.format("§7(%.1f, %.1f, %.1f)", loc.getX(), loc.getY(), loc.getZ()));
    }

    private void cmdRename(CommandSender s, String[] args) {
        if (args.length < 4) {
            s.sendMessage("§cVerwendung: /td station rename <id> <neuer-name>"); return;
        }
        String id      = args[2].toLowerCase();
        String newName = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));

        if (!plugin.getConfigManager().getStations().containsKey(id)) {
            s.sendMessage("§cBahnhof '" + id + "' nicht gefunden!"); return;
        }
        plugin.getConfig().set("stations." + id + ".display-name", newName);
        plugin.saveConfig();
        plugin.reloadAll();
        s.sendMessage("§aBahnhof §f'" + id + "' §aumbenannt zu §f'" + newName + "'.");
    }

    private void cmdRenameGleis(CommandSender s, String[] args) {
        if (args.length < 5) {
            s.sendMessage("§cVerwendung: /td station renamegleis <bahnhof> <gleis> <neuer-name>"); return;
        }
        String stationId = args[2].toLowerCase();
        String gleisId   = args[3].toLowerCase();
        String newName   = String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length));

        if (!plugin.getConfigManager().getStations().containsKey(stationId)) {
            s.sendMessage("§cBahnhof '" + stationId + "' nicht gefunden!"); return;
        }
        if (plugin.getConfigManager().getStations().get(stationId).getGleis(gleisId) == null) {
            s.sendMessage("§cGleis '" + gleisId + "' nicht gefunden!"); return;
        }
        plugin.getConfig().set("stations." + stationId + ".gleise." + gleisId + ".display-name", newName);
        plugin.saveConfig();
        plugin.reloadAll();
        s.sendMessage("§aGleis §f'" + gleisId + "' §aumbenannt zu §f'" + newName + "'.");
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage("§b══ /td station ══");
        s.sendMessage("§f/td station create <id> <name>              §7– Erstellen");
        s.sendMessage("§f/td station delete <id>                     §7– Löschen");
        s.sendMessage("§f/td station addgleis <id> <gleis> <name>    §7– Gleis hinzufügen");
        s.sendMessage("§f/td station removegleis <id> <gleis>        §7– Gleis entfernen");
        s.sendMessage("§f/td station list                            §7– Alle Bahnhöfe");
        s.sendMessage("§f/td station info <id>                       §7– Details");
        s.sendMessage("§f/td station rename <id> <name>             §7– Umbenennen");
        s.sendMessage("§f/td station setlocation <id>              §7– Ansage-Position setzen");
        s.sendMessage("§f/td station renamegleis <id> <gleis> <n>   §7– Gleis umbenennen");
    }
}
