package de.transitboard;

import de.transitboard.announcement.AnnouncementConfig;
import de.transitboard.announcement.InTrainAnnouncer;
import de.transitboard.announcement.AnnouncementManager;
import de.transitboard.command.LineCommand;
import de.transitboard.command.StationCommand;
import de.transitboard.command.TabCompleter;
import de.transitboard.config.ConfigManager;
import de.transitboard.display.MapDisplay;
import de.transitboard.display.SignUpdater;
import de.transitboard.listener.FISSignListener;
import de.transitboard.listener.SignBreakListener;
import de.transitboard.store.SignStorage;
import de.transitboard.listener.TransitBoardSignAction;
import de.transitboard.model.ActiveRun;
import de.transitboard.model.TrackKey;
import de.transitboard.store.LineTimingStorage;
import de.transitboard.store.LineTimingStore;
import de.transitboard.store.TimingStore;
import de.transitboard.store.TimingStorage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Field;
import java.util.Deque;
import java.util.List;
import java.util.Map;

public class TransitBoardPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private TimingStore timingStore;
    private LineTimingStore lineTimingStore;
    private AnnouncementConfig announcementConfig;
    private AnnouncementManager announcementManager;
    private InTrainAnnouncer inTrainAnnouncer;
    private SignStorage signStorage;
    private BukkitTask updateTask;

    @Override
    public void onLoad() {
        TransitBoardSignAction.registerAll(this);
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();

        try {
            Class.forName("com.bergerkiller.bukkit.tc.signactions.SignAction");
        } catch (ClassNotFoundException e) {
            getLogger().severe("TrainCarts-Klassen nicht gefunden! Ist TrainCarts installiert?");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        configManager = new ConfigManager(this);
        configManager.load();

        timingStore = new TimingStore(getLogger(), configManager.getHistorySize());
        timingStore.setHoldoverSeconds(configManager.getHoldoverSeconds());
        timingStore.setTimingStorage(new TimingStorage(this));

        lineTimingStore = new LineTimingStore(getLogger(), configManager.getHistorySize());
        lineTimingStore.setPlugin(this);
        lineTimingStore.setStorage(new LineTimingStorage(this));

        announcementConfig = new AnnouncementConfig();
        announcementConfig.load(this);
        announcementManager = new AnnouncementManager(this, announcementConfig);

        inTrainAnnouncer = new InTrainAnnouncer(this);
        inTrainAnnouncer.runTaskTimer(this, 20L, 20L);

        signStorage = new SignStorage(this);
        signStorage.load();

        // Bukkit-Listener für FIS-Schilder (unabhängig von TC)
        getServer().getPluginManager().registerEvents(new FISSignListener(this), this);
        getServer().getPluginManager().registerEvents(new SignBreakListener(this), this);
        getServer().getPluginManager().registerEvents(new de.transitboard.listener.FISSignEditListener(this), this);

        startUpdateTask();
        registerCommands();

        getLogger().info("TransitBoard v" + getDescription().getVersion() + " gestartet.");
    }

    @Override
    public void onDisable() {
        if (signStorage != null) signStorage.save();
        if (timingStore != null) timingStore.saveTiming();
        if (lineTimingStore != null) lineTimingStore.save();
        if (lineTimingStore != null) lineTimingStore.save();
        if (updateTask != null) updateTask.cancel();
        if (inTrainAnnouncer != null) inTrainAnnouncer.cancel();
        TransitBoardSignAction.unregisterAll();
        getLogger().info("TrainDisplay deaktiviert.");
    }

    // ─── Commands programmatisch registrieren ─────────────────────────────────
    // Paper-Plugin.yml unterstützt keine commands-Sektion mit onCommand-Routing.
    // Wir hängen uns direkt in die SimpleCommandMap ein.

    private void registerCommands() {
        SimpleCommandMap map = findCommandMap();
        if (map == null) {
            getLogger().severe("CommandMap nicht gefunden - /td nicht verfuegbar!");
            return;
        }
        TabCompleter tc = new TabCompleter(this);
        for (String name : new String[]{"traindisplay", "td"}) {
            String finalName = name;
            map.register("traindisplay", new org.bukkit.command.Command(name) {
                @Override
                public boolean execute(CommandSender sender, String label, String[] args) {
                    return handleCommand(sender, args);
                }
                @Override
                public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
                    return tc.onTabComplete(sender, this, alias, args);
                }
                @Override public String getDescription() { return "TrainDisplay Verwaltung"; }
                @Override public String getUsage() { return "/" + finalName + " <reload|status|list>"; }
            });
        }
        getLogger().info("Commands /traindisplay und /td registriert.");
    }

    private SimpleCommandMap findCommandMap() {
        // Probiere alle bekannten Feldnamen (CraftServer, Paper, Purpur unterscheiden sich)
        for (String fieldName : new String[]{"commandMap", "f", "k"}) {
            try {
                Field f = getServer().getClass().getDeclaredField(fieldName);
                f.setAccessible(true);
                Object val = f.get(getServer());
                if (val instanceof SimpleCommandMap scm) return scm;
            } catch (Exception ignored) {}
        }
        // Fallback: alle Felder durchsuchen
        for (Field f : getServer().getClass().getDeclaredFields()) {
            try {
                f.setAccessible(true);
                Object val = f.get(getServer());
                if (val instanceof SimpleCommandMap scm) {
                    getLogger().info("CommandMap gefunden via Feld: " + f.getName());
                    return scm;
                }
            } catch (Exception ignored) {}
        }
        // Superklassen durchsuchen
        Class<?> cls = getServer().getClass().getSuperclass();
        while (cls != null) {
            for (Field f : cls.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(getServer());
                    if (val instanceof SimpleCommandMap scm) {
                        getLogger().info("CommandMap in Superklasse gefunden: "
                            + cls.getSimpleName() + "#" + f.getName());
                        return scm;
                    }
                } catch (Exception ignored) {}
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        return handleCommand(sender, args);
    }

    private boolean handleCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("traindisplay.admin")) {
            sender.sendMessage("§cKeine Berechtigung.");
            return true;
        }
        if (configManager == null || timingStore == null) {
            sender.sendMessage("§cTrainDisplay ist nicht vollständig gestartet.");
            return true;
        }
        if (args.length == 0) { sendHelp(sender); return true; }

        switch (args[0].toLowerCase()) {

            case "debug" -> {
                debugMode = !debugMode;
                sender.sendMessage(debugMode
                    ? "§aDebug-Modus aktiviert – erweiterte Logs werden ausgegeben."
                    : "§7Debug-Modus deaktiviert.");
            }
            case "reload" -> {
                if (updateTask != null) updateTask.cancel();
        if (inTrainAnnouncer != null) inTrainAnnouncer.cancel();
                configManager.load();
                announcementConfig.load(this);
                announcementManager = new AnnouncementManager(this, announcementConfig);
                if (inTrainAnnouncer != null) inTrainAnnouncer.loadConfig();

        inTrainAnnouncer = new InTrainAnnouncer(this);
        inTrainAnnouncer.runTaskTimer(this, 20L, 20L);
                timingStore = new TimingStore(getLogger(), configManager.getHistorySize());
        timingStore.setHoldoverSeconds(configManager.getHoldoverSeconds());
        timingStore.setTimingStorage(new TimingStorage(this));

        lineTimingStore = new LineTimingStore(getLogger(), configManager.getHistorySize());
        lineTimingStore.setPlugin(this);
        lineTimingStore.setStorage(new LineTimingStorage(this));

        announcementConfig = new AnnouncementConfig();
        announcementConfig.load(this);
        announcementManager = new AnnouncementManager(this, announcementConfig);

        inTrainAnnouncer = new InTrainAnnouncer(this);
        inTrainAnnouncer.runTaskTimer(this, 20L, 20L);
                startUpdateTask();
                signStorage = new SignStorage(this);
                signStorage.load();
                sender.sendMessage("§aTransitBoard neu geladen. "
                    + configManager.getStations().size() + " Bahnhof/Bahnhoefe.");
            }

            case "status" -> {
                sender.sendMessage("§b══ TransitBoard Status ══");
                sender.sendMessage("§7Version: §f" + getDescription().getVersion());
                sender.sendMessage("§7Aktive Messungen: §f" + timingStore.getActiveRunCount());
                sender.sendMessage("§7Bahnhoefe: §f" + configManager.getStations().size());
                sender.sendMessage("");
                sender.sendMessage("§7Laufende Zuege:");
                for (ActiveRun run : timingStore.getActiveRuns()) {
                    sender.sendMessage(String.format("  §f%s §7→ %s (seit %ds)",
                        run.getTrainName(),
                        run.getKey(),
                        run.elapsedMillis() / 1000));
                }
                sender.sendMessage("");
                sender.sendMessage("§7Gespeicherte Fahrtzeiten:");
                for (Map.Entry<TrackKey, Deque<Long>> e : timingStore.getTimingHistory().entrySet()) {
                    long avg = e.getValue().stream().mapToLong(Long::longValue).sum()
                               / e.getValue().size();
                    sender.sendMessage(String.format("  §f%s §7⌀ %ds (%d Messungen)",
                        e.getKey(), avg / 1000, e.getValue().size()));
                }
            }

            case "line"    -> new LineCommand(this).handle(sender, args);
            case "station" -> new StationCommand(this).handle(sender, args);

            case "mapdisplay" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cVerwendung: /td mapdisplay create <station> [gleis]");
                    sender.sendMessage("§cVerwendung: /td mapdisplay getmap <id>");
                    return true;
                }
                switch (args[1].toLowerCase()) {
                    case "create" -> {
                        String stationId = args[2].toLowerCase();
                        String gleisId   = args.length >= 4 ? args[3].toLowerCase() : null;
                        var station = configManager.getStations().get(stationId);
                        if (station == null) {
                            sender.sendMessage("§cBahnhof '" + stationId + "' nicht gefunden!");
                            return true;
                        }
                        MapDisplay md = new MapDisplay(this, station, gleisId);
                        List<Integer> ids = md.create();
                        String regKey = gleisId != null ? stationId + "/" + gleisId : stationId;
                        mapDisplayRegistry.put(regKey, md);
                        if (sender instanceof org.bukkit.entity.Player player) {
                            for (MapDisplay.MapEntry entry : md.getMapEntries()) {
                                org.bukkit.inventory.ItemStack mapItem =
                                    new org.bukkit.inventory.ItemStack(org.bukkit.Material.FILLED_MAP);
                                var meta = (org.bukkit.inventory.meta.MapMeta) mapItem.getItemMeta();
                                meta.setMapView(entry.view());
                                meta.setDisplayName(" ");
                                mapItem.setItemMeta(meta);
                                player.getInventory().addItem(mapItem);
                            }
                            sender.sendMessage("§a4 FIS-Maps ins Inventar gelegt! IDs: §f" + ids);
                            sender.sendMessage("§7Platziere sie nebeneinander in Item-Frames.");
                        } else {
                            sender.sendMessage("§a4 Maps erstellt! IDs: §f" + ids);
                        }
                    }
                    case "getmap" -> {
                        if (!(sender instanceof org.bukkit.entity.Player player)) {
                            sender.sendMessage("§cNur für Spieler.");
                            return true;
                        }
                        String regKey = args[2].toLowerCase();
                        if (args.length >= 4) regKey += "/" + args[3].toLowerCase();
                        MapDisplay md = mapDisplayRegistry.get(regKey);
                        if (md == null) {
                            sender.sendMessage("§cKein MapDisplay für '" + regKey + "' gefunden.");
                            sender.sendMessage("§7Verfügbar: §f" + mapDisplayRegistry.keySet());
                            return true;
                        }
                        for (MapDisplay.MapEntry entry : md.getMapEntries()) {
                            org.bukkit.inventory.ItemStack mapItem =
                                new org.bukkit.inventory.ItemStack(org.bukkit.Material.FILLED_MAP);
                            var meta = (org.bukkit.inventory.meta.MapMeta) mapItem.getItemMeta();
                            meta.setMapView(entry.view());
                            meta.setDisplayName(" ");
                            mapItem.setItemMeta(meta);
                            player.getInventory().addItem(mapItem);
                        }
                        sender.sendMessage("§a4 FIS-Maps für §f'" + regKey + "' §ains Inventar gelegt.");
                    }
                    default -> {
                        sender.sendMessage("§cVerwendung: /td mapdisplay create <station> [gleis]");
                        sender.sendMessage("§cVerwendung: /td mapdisplay getmap <station> [gleis]");
                    }
                }
            }
            case "nobetrieb" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cVerwendung: /td nobetrieb <bahnhofId>");
                    sender.sendMessage("§cOder: /td nobetrieb <bahnhofId> off");
                    break;
                }
                String sid = args[1].toLowerCase();
                boolean off = args.length >= 3 && args[2].equalsIgnoreCase("off");
                if (!configManager.getStations().containsKey(sid)) {
                    sender.sendMessage("§cBahnhof '" + sid + "' nicht gefunden!");
                    break;
                }
                if (off) {
                    timingStore.clearNoService(sid);
                    sender.sendMessage("§aKein-Betrieb für '" + sid + "' aufgehoben.");
                } else {
                    timingStore.setNoService(sid);
                    sender.sendMessage("§eKein-Betrieb für '" + sid + "' aktiviert.");
                    sender.sendMessage("§7Wird automatisch aufgehoben wenn der erste Zug wieder kommt.");
                }
            }

            case "list" -> {
                sender.sendMessage("§b══ Bahnhoefe ══");
                if (configManager.getStations().isEmpty()) {
                    sender.sendMessage("§7Keine Bahnhoefe konfiguriert.");
                } else {
                    configManager.getStations().forEach((id, s) -> {
                        sender.sendMessage("§f" + id + " §7(" + s.getDisplayName() + ")");
                        s.getGleise().forEach((gid, g) ->
                            sender.sendMessage("  §7Gleis §f" + gid
                                + " §7(" + g.getDisplayName() + ")"
                                + " – " + g.getSigns().size() + " Schild(er)"));
                        sender.sendMessage("  §7Bahnhofs-FIS: §f"
                            + s.getStationSigns().size() + " Schild(er)");
                    });
                }
            }

            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage("§b══ TrainDisplay Hilfe ══");
        s.sendMessage("§f/td reload              §7– Konfiguration neu laden");
        s.sendMessage("§f/td status              §7– Aktive Messungen und Fahrtzeiten");
        s.sendMessage("§f/td list                §7– Alle Bahnhoefe und Gleise");
        s.sendMessage("§f/td nobetrieb <id>      §7– Kein-Betrieb aktivieren");
        s.sendMessage("§f/td line <sub>          §7– Linien verwalten (/td line für Details)");
        s.sendMessage("§f/td station <sub>       §7– Bahnhöfe verwalten (/td station für Details)");
        s.sendMessage("§f/td nobetrieb <id> off  §7– Kein-Betrieb aufheben");
    }

    private void startUpdateTask() {
        if (updateTask != null) updateTask.cancel();
        if (inTrainAnnouncer != null) inTrainAnnouncer.cancel();
        SignUpdater updater = new SignUpdater(this, timingStore);
        int interval = configManager.getUpdateInterval();
        updateTask = updater.runTaskTimer(this, interval, interval);
    }

    public ConfigManager getConfigManager() { return configManager; }
    public TimingStore getTimingStore()     { return timingStore; }
    public LineTimingStore getLineTimingStore()       { return lineTimingStore; }
    public AnnouncementManager getAnnouncementManager() { return announcementManager; }
    public InTrainAnnouncer getInTrainAnnouncer()       { return inTrainAnnouncer; }

    private boolean debugMode = false;
    public boolean isDebugMode() { return debugMode; }
    public void setDebugMode(boolean debug) { this.debugMode = debug; }
    public void debugLog(String msg) { if (debugMode) getLogger().info("[DEBUG] " + msg); }

    // stationId(+gleisId) → MapDisplay
    private final java.util.Map<String, MapDisplay> mapDisplayRegistry = new java.util.LinkedHashMap<>();
    public java.util.Map<String, MapDisplay> getMapDisplayRegistry() { return mapDisplayRegistry; }

    public SignStorage getSignStorage() { return signStorage; }
    /** Lädt Config und Schilder neu – nach jedem Command aufrufen. */
    public void reloadAll() {
        configManager.load();
        if (signStorage != null) {
            signStorage = new SignStorage(this);
            signStorage.load();
        }
    }
}
