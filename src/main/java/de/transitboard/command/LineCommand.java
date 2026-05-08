package de.transitboard.command;

import de.transitboard.TransitBoardPlugin;
import de.transitboard.model.LineConfig;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * /td line create <id> <ring|shuttle>
 * /td line delete <id>
 * /td line addstop <id> <station> <gleis>
 * /td line removestop <id> <station>
 * /td line movestop <id> <station> <up|down>
 * /td line list
 * /td line info <id>
 */
public class LineCommand {

    private final TransitBoardPlugin plugin;

    public LineCommand(TransitBoardPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length < 2) { sendHelp(sender); return true; }

        switch (args[1].toLowerCase()) {
            case "create"     -> cmdCreate(sender, args);
            case "delete"     -> cmdDelete(sender, args);
            case "addstop"    -> cmdAddStop(sender, args);
            case "removestop" -> cmdRemoveStop(sender, args);
            case "movestop"   -> cmdMoveStop(sender, args);
            case "rename"     -> cmdRename(sender, args);
            case "setcolor"       -> cmdSetColor(sender, args);
            case "setpos"         -> cmdSetPos(sender, args);
            case "setdestination" -> cmdSetDestination(sender, args);
            case "setdisplayname" -> cmdSetDisplayName(sender, args);
            case "list"           -> cmdList(sender);
            case "info"       -> cmdInfo(sender, args);
            default           -> sendHelp(sender);
        }
        return true;
    }

    private void cmdCreate(CommandSender s, String[] args) {
        if (args.length < 4) {
            s.sendMessage("§cVerwendung: /td line create <id> <ring|shuttle> [farbe]");
            s.sendMessage("§7Beispiel: /td line create U1 ring §1"); return;
        }
        String id    = args[2].toLowerCase();
        String type  = args[3].toLowerCase();
        String color = args.length >= 5 ? args[4].replace("&", "§") : "§f";
        if (!type.equals("ring") && !type.equals("shuttle")) {
            s.sendMessage("§cTyp muss 'ring' oder 'shuttle' sein."); return;
        }
        if (plugin.getConfigManager().getLines().containsKey(id)) {
            s.sendMessage("§cLinie '" + id + "' existiert bereits!"); return;
        }
        plugin.getConfig().set("lines." + id + ".type", type);
        plugin.getConfig().set("lines." + id + ".color", color);
        plugin.getConfig().set("lines." + id + ".stops", new ArrayList<>());
        plugin.saveConfig();
        plugin.reloadAll();
        s.sendMessage("§aLinie " + color + id + "§a (" + type + ") erstellt.");
        s.sendMessage("§7Stops hinzufügen: /td line addstop " + id + " <station> <gleis>");
    }

    private void cmdDelete(CommandSender s, String[] args) {
        if (args.length < 3) { s.sendMessage("§cVerwendung: /td line delete <id>"); return; }
        String id = args[2].toLowerCase();
        if (!plugin.getConfigManager().getLines().containsKey(id)) {
            s.sendMessage("§cLinie '" + id + "' nicht gefunden!"); return;
        }
        plugin.getConfig().set("lines." + id, null);
        plugin.saveConfig();
        plugin.reloadAll();
        s.sendMessage("§aLinie §f'" + id + "' §agelöscht.");
    }

    private void cmdAddStop(CommandSender s, String[] args) {
        if (args.length < 5) {
            s.sendMessage("§cVerwendung: /td line addstop <linie> <station> <gleis>"); return;
        }
        String lineId    = args[2].toLowerCase();
        String stationId = args[3].toLowerCase();
        String gleisId   = args[4].toLowerCase();

        if (plugin.getConfigManager().getLine(lineId) == null) {
            s.sendMessage("§cLinie '" + lineId + "' nicht gefunden!"); return;
        }
        if (!plugin.getConfigManager().getStations().containsKey(stationId)) {
            s.sendMessage("§cBahnhof '" + stationId + "' nicht in config.yml!");
            s.sendMessage("§7Verfügbar: " + String.join(", ",
                plugin.getConfigManager().getStations().keySet())); return;
        }

        @SuppressWarnings("unchecked")
        var stopList = (java.util.List<java.util.Map<String, Object>>) (java.util.List<?>) plugin.getConfig().getMapList("lines." + lineId + ".stops");
        var newStop  = new LinkedHashMap<String, Object>();
        newStop.put("station", stationId);
        newStop.put("gleis", gleisId);
        stopList.add(newStop);
        plugin.getConfig().set("lines." + lineId + ".stops", stopList);
        plugin.saveConfig();
        plugin.reloadAll();

        int pos = plugin.getConfigManager().getLine(lineId).getStops().size();
        s.sendMessage("§aStop §f" + pos + "§a hinzugefügt: §f" + stationId + " / " + gleisId);
    }

    private void cmdRemoveStop(CommandSender s, String[] args) {
        if (args.length < 4) {
            s.sendMessage("§cVerwendung: /td line removestop <linie> <station>"); return;
        }
        String lineId    = args[2].toLowerCase();
        String stationId = args[3].toLowerCase();

        if (plugin.getConfigManager().getLine(lineId) == null) {
            s.sendMessage("§cLinie '" + lineId + "' nicht gefunden!"); return;
        }

        @SuppressWarnings("unchecked")
        var stopList = (java.util.List<java.util.Map<String, Object>>) (java.util.List<?>) plugin.getConfig().getMapList("lines." + lineId + ".stops");
        boolean removed = stopList.removeIf(m ->
            stationId.equalsIgnoreCase(m.getOrDefault("station","").toString()));

        if (!removed) { s.sendMessage("§cStation '" + stationId + "' nicht in Linie gefunden!"); return; }

        plugin.getConfig().set("lines." + lineId + ".stops", stopList);
        plugin.saveConfig();
        plugin.reloadAll();
        s.sendMessage("§aStation §f'" + stationId + "' §aaus Linie §f'" + lineId + "' §aentfernt.");
    }

    private void cmdMoveStop(CommandSender s, String[] args) {
        if (args.length < 5) {
            s.sendMessage("§cVerwendung: /td line movestop <linie> <station> <up|down>"); return;
        }
        String lineId    = args[2].toLowerCase();
        String stationId = args[3].toLowerCase();
        String dir       = args[4].toLowerCase();

        if (!dir.equals("up") && !dir.equals("down")) {
            s.sendMessage("§cRichtung muss 'up' oder 'down' sein."); return;
        }
        if (plugin.getConfigManager().getLine(lineId) == null) {
            s.sendMessage("§cLinie '" + lineId + "' nicht gefunden!"); return;
        }

        @SuppressWarnings("unchecked")
        var stopList = (java.util.List<java.util.Map<String, Object>>) (java.util.List<?>) plugin.getConfig().getMapList("lines." + lineId + ".stops");
        int idx = -1;
        for (int i = 0; i < stopList.size(); i++) {
            if (stationId.equalsIgnoreCase(
                    stopList.get(i).getOrDefault("station","").toString())) {
                idx = i; break;
            }
        }
        if (idx < 0) { s.sendMessage("§cStation nicht gefunden!"); return; }

        int target = dir.equals("up") ? idx - 1 : idx + 1;
        if (target < 0 || target >= stopList.size()) {
            s.sendMessage("§cKann nicht weiter " + (dir.equals("up") ? "hoch" : "runter") + " verschoben werden."); return;
        }

        var tmp = stopList.get(idx);
        stopList.set(idx, stopList.get(target));
        stopList.set(target, tmp);
        plugin.getConfig().set("lines." + lineId + ".stops", stopList);
        plugin.saveConfig();
        plugin.reloadAll();
        s.sendMessage("§aStop §f'" + stationId + "' §averschoben.");
    }

    private void cmdList(CommandSender s) {
        var lines = plugin.getConfigManager().getLines();
        if (lines.isEmpty()) {
            s.sendMessage("§7Keine Linien. Erstellen: /td line create <id> <ring|shuttle>"); return;
        }
        s.sendMessage("§b══ Linien ══");
        lines.forEach((id, l) -> s.sendMessage(String.format(
            "§f%s §7(%s) – §f%d Stops", id, l.getType().name().toLowerCase(), l.getStops().size())));
    }

    private void cmdInfo(CommandSender s, String[] args) {
        if (args.length < 3) { s.sendMessage("§cVerwendung: /td line info <id>"); return; }
        String lineId = args[2].toLowerCase();
        LineConfig line = plugin.getConfigManager().getLine(lineId);
        if (line == null) { s.sendMessage("§cLinie '" + lineId + "' nicht gefunden!"); return; }

        s.sendMessage("§b══ Linie " + lineId + " ══");
        s.sendMessage("§7Typ: §f" + line.getType().name().toLowerCase());
        s.sendMessage("§7Stops:");
        for (int i = 0; i < line.getStops().size(); i++) {
            var stop = line.getStops().get(i);
            s.sendMessage(String.format("  §f%d. §7%s §8/ §7%s",
                i + 1, stop.getStationId(), stop.getGleisId()));
        }
    }

    private void cmdSetColor(CommandSender s, String[] args) {
        if (args.length < 4) {
            s.sendMessage("§cVerwendung: /td line setcolor <id> <farbe>");
            s.sendMessage("§7Beispiel: /td line setcolor U1 §1"); return;
        }
        String id    = args[2].toLowerCase();
        String color = args[3].replace("&", "§");
        if (plugin.getConfigManager().getLine(id) == null) {
            s.sendMessage("§cLinie '" + id + "' nicht gefunden!"); return;
        }
        plugin.getConfig().set("lines." + id + ".color", color);
        plugin.saveConfig();
        plugin.reloadAll();
        s.sendMessage("§aFarbe für Linie " + color + id + "§a gesetzt.");
    }

    private void cmdRename(CommandSender s, String[] args) {
        if (args.length < 4) {
            s.sendMessage("§cVerwendung: /td line rename <id> <neuer-id>"); return;
        }
        String oldId = args[2].toLowerCase();
        String newId = args[3].toLowerCase();

        if (!plugin.getConfigManager().getLines().containsKey(oldId)) {
            s.sendMessage("§cLinie '" + oldId + "' nicht gefunden!"); return;
        }
        if (plugin.getConfigManager().getLines().containsKey(newId)) {
            s.sendMessage("§cLinie '" + newId + "' existiert bereits!"); return;
        }

        // Alten Eintrag kopieren und unter neuem Namen speichern
        var oldSection = plugin.getConfig().getConfigurationSection("lines." + oldId);
        if (oldSection == null) { s.sendMessage("§cFehler beim Lesen der Konfiguration."); return; }

        // Alle Werte kopieren
        plugin.getConfig().set("lines." + newId + ".type",
            plugin.getConfig().getString("lines." + oldId + ".type"));
        plugin.getConfig().set("lines." + newId + ".color",
            plugin.getConfig().getString("lines." + oldId + ".color", "§f"));
        plugin.getConfig().set("lines." + newId + ".stops",
            new java.util.ArrayList<>(plugin.getConfig().getMapList("lines." + oldId + ".stops")));
        // Alten Eintrag löschen
        plugin.getConfig().set("lines." + oldId, null);
        plugin.saveConfig();
        plugin.reloadAll();
        s.sendMessage("§aLinie §f'" + oldId + "' §aumbenannt zu §f'" + newId + "'.");
    }

    private void cmdSetPos(CommandSender s, String[] args) {
        if (args.length < 5) {
            s.sendMessage("§cVerwendung: /td line setpos <linie> <station> <position>");
            s.sendMessage("§7Beispiel: /td line setpos U1 hbf 1");
            return;
        }
        String lineId    = args[2].toLowerCase();
        String stationId = args[3].toLowerCase();
        int targetPos;
        try {
            targetPos = Integer.parseInt(args[4]) - 1; // 1-basiert → 0-basiert
        } catch (NumberFormatException e) {
            s.sendMessage("§cPosition muss eine Zahl sein."); return;
        }

        var line = plugin.getConfigManager().getLine(lineId);
        if (line == null) { s.sendMessage("§cLinie '" + lineId + "' nicht gefunden!"); return; }

        @SuppressWarnings("unchecked")
        var stopList = (java.util.List<java.util.Map<String, Object>>) (java.util.List<?>) plugin.getConfig().getMapList("lines." + lineId + ".stops");
        int currentIdx = -1;
        for (int i = 0; i < stopList.size(); i++) {
            if (stationId.equalsIgnoreCase(
                    stopList.get(i).getOrDefault("station","").toString())) {
                currentIdx = i; break;
            }
        }
        if (currentIdx < 0) { s.sendMessage("§cStation '" + stationId + "' nicht in Linie!"); return; }
        if (targetPos < 0 || targetPos >= stopList.size()) {
            s.sendMessage("§cPosition muss zwischen 1 und " + stopList.size() + " liegen."); return;
        }
        if (targetPos == currentIdx) {
            s.sendMessage("§7Station ist bereits an Position " + (targetPos + 1) + "."); return;
        }

        // Herausnehmen und an neuer Stelle einfügen
        var stop = stopList.remove(currentIdx);
        stopList.add(targetPos, stop);
        plugin.getConfig().set("lines." + lineId + ".stops", stopList);
        plugin.saveConfig();
        plugin.reloadAll();
        s.sendMessage("§aStation §f'" + stationId + "' §aan Position §f" + (targetPos + 1) + " §averschoben.");
    }

    private void cmdSetDisplayName(CommandSender s, String[] args) {
        if (args.length < 4) {
            s.sendMessage("§cVerwendung: /td line setdisplayname <id> <name>");
            return;
        }
        String lineId = args[2].toLowerCase();
        if (plugin.getConfigManager().getLine(lineId) == null) {
            s.sendMessage("§cLinie '" + lineId + "' nicht gefunden!");
            return;
        }
        String name = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
        plugin.getConfig().set("lines." + lineId + ".display-name", name);
        plugin.saveConfig();
        plugin.reloadAll();
        s.sendMessage("§aAnzeigename der Linie §f'" + lineId + "' §aauf §f'" + name + "' §agesetzt.");
    }

    private void cmdSetDestination(CommandSender s, String[] args) {
        if (args.length < 4) {
            s.sendMessage("§cVerwendung: /td line setdestination <id> <ziel>");
            return;
        }
        String lineId = args[2].toLowerCase();
        if (plugin.getConfigManager().getLine(lineId) == null) {
            s.sendMessage("§cLinie '" + lineId + "' nicht gefunden!");
            return;
        }
        String destination = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
        plugin.getConfig().set("lines." + lineId + ".destination", destination);
        plugin.saveConfig();
        plugin.reloadAll();
        s.sendMessage("§aZiel der Linie §f'" + lineId + "' §aauf §f'" + destination + "' §agesetzt.");
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage("§b══ /td line ══");
        s.sendMessage("§f/td line create <id> <ring|shuttle>        §7– Erstellen");
        s.sendMessage("§f/td line delete <id>                       §7– Löschen");
        s.sendMessage("§f/td line addstop <id> <station> <gleis>    §7– Stop hinzufügen");
        s.sendMessage("§f/td line removestop <id> <station>         §7– Stop entfernen");
        s.sendMessage("§f/td line movestop <id> <station> <up|down> §7– Stop verschieben");
        s.sendMessage("§f/td line list                              §7– Alle Linien");
        s.sendMessage("§f/td line info <id>                         §7– Details");
        s.sendMessage("§f/td line rename <id> <neue-id>            §7– Umbenennen");
        s.sendMessage("§f/td line setcolor <id> <farbe>           §7– Farbe setzen (z.B. §1)");
        s.sendMessage("§f/td line setpos <id> <station> <pos>      §7– Stop an Position setzen");
        s.sendMessage("§f/td line setdestination <id> <ziel>       §7– Zielanzeige setzen");
        s.sendMessage("§f/td line setdisplayname <id> <name>       §7– Anzeigenamen setzen");
    }
}
