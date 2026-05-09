package de.transitboard.command;

import de.transitboard.TransitBoardPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class TabCompleter implements org.bukkit.command.TabCompleter {

    private final TransitBoardPlugin plugin;

    public TabCompleter(TransitBoardPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                       String alias, String[] args) {
        List<String> result = new ArrayList<>();

        if (args.length == 1) {
            // /td <sub>
            result.addAll(List.of("reload", "status", "list", "nobetrieb", "line", "station", "debug", "mapdisplay"));

        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "nobetrieb" ->
                    result.addAll(plugin.getConfigManager().getStations().keySet());
                case "station" ->
                    result.addAll(List.of("create", "delete", "addgleis", "removegleis",
                                          "rename", "renamegleis", "setlocation", "list", "info"));
                case "line" ->
                    result.addAll(List.of("create", "delete", "addstop", "removestop",
                                          "movestop", "rename", "setpos", "setcolor",
                                          "setdestination", "setdisplayname", "list", "info"));
            }

        } else if (args.length == 2 && args[0].equalsIgnoreCase("mapdisplay")) {
            result.addAll(List.of("create", "getmap"));

        } else if (args.length == 3 && args[0].equalsIgnoreCase("mapdisplay")) {
            if (args[1].equalsIgnoreCase("create"))
                result.addAll(plugin.getConfigManager().getStations().keySet());
            else if (args[1].equalsIgnoreCase("getmap"))
                result.addAll(plugin.getMapDisplayRegistry().keySet());

        } else if (args.length == 4 && args[0].equalsIgnoreCase("mapdisplay")
                && args[1].equalsIgnoreCase("create")) {
            var station = plugin.getConfigManager().getStations().get(args[2].toLowerCase());
            if (station != null) result.addAll(station.getGleise().keySet());

        } else if (args.length == 3 && args[0].equalsIgnoreCase("nobetrieb")) {
            result.addAll(List.of("off"));

        } else if (args.length == 3 && args[0].equalsIgnoreCase("station")) {
            switch (args[1].toLowerCase()) {
                case "delete", "info", "addgleis", "removegleis", "rename", "renamegleis", "setlocation" ->
                    result.addAll(plugin.getConfigManager().getStations().keySet());
            }

        } else if (args.length == 3 && args[0].equalsIgnoreCase("line")) {
            switch (args[1].toLowerCase()) {
                case "create" ->
                    // Neuer Linienname – kein Vorschlag sinnvoll
                    result.add("<linienname>");
                case "delete", "info", "addstop", "removestop", "movestop",
                     "rename", "setpos", "setcolor", "setdestination", "setdisplayname" ->
                    result.addAll(plugin.getConfigManager().getLines().keySet());
            }

        } else if (args.length == 4 && args[0].equalsIgnoreCase("station")) {
            switch (args[1].toLowerCase()) {
                case "addgleis" -> result.add("<gleisid>");
                case "removegleis", "renamegleis" -> {
                    var st = plugin.getConfigManager().getStations().get(args[2].toLowerCase());
                    if (st != null) result.addAll(st.getGleise().keySet());
                }
            }

        } else if (args.length == 4 && args[0].equalsIgnoreCase("line")
                && args[1].equalsIgnoreCase("setcolor")) {
            result.addAll(List.of("&0","&1","&2","&3","&4","&5","&6","&7",
                                  "&8","&9","&a","&b","&c","&d","&e","&f"));

        } else if (args.length == 4 && args[0].equalsIgnoreCase("line")
                && args[1].equalsIgnoreCase("setdestination")) {
            result.add("<ziel>");

        } else if (args.length == 4 && args[0].equalsIgnoreCase("line")) {
            switch (args[1].toLowerCase()) {
                case "create" ->
                    // Typ: ring oder shuttle
                    result.addAll(List.of("ring", "shuttle"));
                case "addstop", "removestop", "movestop", "setpos" ->
                    // Stationen die in dieser Linie sind (removestop/movestop)
                    // oder alle Stationen (addstop)
                    result.addAll(getStationsForLine(args[2], args[1]));
            }

        } else if (args.length == 5 && args[0].equalsIgnoreCase("station")) {
            if (args[1].equalsIgnoreCase("addgleis")) result.add("<anzeigename>");

        } else if (args.length == 5 && args[0].equalsIgnoreCase("line")
                && args[1].equalsIgnoreCase("create")) {
            result.addAll(List.of("&0","&1","&2","&3","&4","&5","&6","&7",
                                  "&8","&9","&a","&b","&c","&d","&e","&f"));

        } else if (args.length == 5 && args[0].equalsIgnoreCase("line")) {
            switch (args[1].toLowerCase()) {
                case "addstop" ->
                    // Gleise des gewählten Bahnhofs
                    result.addAll(getGleiseForStation(args[3]));
                case "movestop" ->
                    result.addAll(List.of("up", "down"));
                case "setpos" -> {
                    var l = plugin.getConfigManager().getLine(args[2].toLowerCase());
                    if (l != null) for (int i = 1; i <= l.getStops().size(); i++) result.add(String.valueOf(i));
                }
            }

        }

        // Filter nach bereits getipptem Text
        String typed = args[args.length - 1].toLowerCase();
        return result.stream()
            .filter(s -> s.toLowerCase().startsWith(typed))
            .sorted()
            .collect(Collectors.toList());
    }

    private List<String> getStationsForLine(String lineId, String subCommand) {
        var line = plugin.getConfigManager().getLine(lineId.toLowerCase());
        if (line == null) return new ArrayList<>(plugin.getConfigManager().getStations().keySet());

        if (subCommand.equalsIgnoreCase("addstop")) {
            // Alle Bahnhöfe die noch nicht in der Linie sind
            var inLine = line.getStops().stream()
                .map(s -> s.getStationId()).collect(Collectors.toSet());
            return plugin.getConfigManager().getStations().keySet().stream()
                .filter(s -> !inLine.contains(s))
                .collect(Collectors.toList());
        } else {
            // Nur Bahnhöfe die in der Linie sind
            return line.getStops().stream()
                .map(s -> s.getStationId())
                .collect(Collectors.toList());
        }
    }

    private List<String> getGleiseForStation(String stationId) {
        var station = plugin.getConfigManager().getStations().get(stationId.toLowerCase());
        if (station == null) return List.of("gleis1");
        return new ArrayList<>(station.getGleise().keySet());
    }
}
