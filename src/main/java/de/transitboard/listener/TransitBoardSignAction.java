package de.transitboard.listener;

import com.bergerkiller.bukkit.tc.events.SignActionEvent;
import com.bergerkiller.bukkit.tc.events.SignChangeActionEvent;
import com.bergerkiller.bukkit.tc.signactions.SignAction;
import com.bergerkiller.bukkit.tc.signactions.SignActionType;
import com.bergerkiller.bukkit.tc.utils.SignBuildOptions;
import de.transitboard.TransitBoardPlugin;
import de.transitboard.model.LineConfig;
import de.transitboard.store.LineTimingStore;
import de.transitboard.store.TimingStore;
import java.util.Set;

/**
 * TC-SignActions für Strecken-Schilder.
 *
 * Schilder werden direkt als eigene TC-Typen registriert – kein [+train]-Wrapper nötig.
 * TC verschiebt die Zeilen: getLine(1) = unser Header, getLine(2) = Linie, getLine(3) = Station
 *
 *   [TDTimer]    ← Zeile 1
 *   u1           ← Zeile 2 (Linie)
 *   hbf          ← Zeile 3 (Station)
 *
 *   [TDStop]     ← Zeile 1
 *   u1           ← Zeile 2 (Linie)
 *   hbf          ← Zeile 3 (Station)
 *
 *   [TDExit]     ← Zeile 1
 */
public class TransitBoardSignAction extends SignAction {

    private static final String HEADER_TIMER   = "tdtimer";
    private static final String HEADER_STOP    = "tdstop";
    private static final String HEADER_EXIT    = "tdexit";
    private static final String HEADER_STATION = "tdstation"; // Legacy

    private static TransitBoardSignAction instanceTimer;
    private static TransitBoardSignAction instanceStop;
    private static TransitBoardSignAction instanceExit;
    private static TransitBoardSignAction instanceStation;

    private final TransitBoardPlugin plugin;
    private final String header;

    private TransitBoardSignAction(TransitBoardPlugin plugin, String header) {
        this.plugin = plugin;
        this.header = header;
    }

    public static void registerAll(TransitBoardPlugin plugin) {
        instanceTimer   = new TransitBoardSignAction(plugin, HEADER_TIMER);
        instanceStop    = new TransitBoardSignAction(plugin, HEADER_STOP);
        instanceExit    = new TransitBoardSignAction(plugin, HEADER_EXIT);
        instanceStation = new TransitBoardSignAction(plugin, HEADER_STATION);
        SignAction.register(instanceTimer);
        SignAction.register(instanceStop);
        SignAction.register(instanceExit);
        SignAction.register(instanceStation);
        plugin.getLogger().info("SignActions registriert: [tdtimer] [tdstop] [tdexit]");
    }

    public static void unregisterAll() {
        try { if (instanceTimer   != null) SignAction.unregister(instanceTimer);   } catch (Exception ignored) {}
        try { if (instanceStop    != null) SignAction.unregister(instanceStop);    } catch (Exception ignored) {}
        try { if (instanceExit    != null) SignAction.unregister(instanceExit);    } catch (Exception ignored) {}
        try { if (instanceStation != null) SignAction.unregister(instanceStation); } catch (Exception ignored) {}
        instanceTimer = instanceStop = instanceExit = instanceStation = null;
    }

    @Override
    public boolean match(SignActionEvent info) {
        return info.isType(header);
    }

    @Override
    public boolean build(SignChangeActionEvent event) {
        return SignBuildOptions.create()
                .setName(header)
                .setDescription("TransitBoard " + header)
                .handle(event.getPlayer());
    }

    // Deduplication: pro Tick wird jede trainName+header-Kombination nur einmal verarbeitet
    private final Set<String> processedThisTick = new java.util.concurrent.ConcurrentSkipListSet<>();

    @Override
    public void execute(SignActionEvent event) {
        if (!event.isAction(SignActionType.MEMBER_ENTER, SignActionType.GROUP_ENTER)) return;

        var member = event.hasMember() ? event.getMember()
                   : (event.hasGroup() ? event.getGroup().head() : null);
        if (member == null) return;

        String trainName = member.getGroup().getProperties().getTrainName();
        String dedupeKey = trainName + "/" + header;

        // Wenn schon in diesem Tick verarbeitet → ignorieren
        if (!processedThisTick.add(dedupeKey)) return;

        // Am Ende des Ticks Set leeren
        plugin.getServer().getScheduler().runTask(plugin, () ->
            processedThisTick.remove(dedupeKey));

        LineTimingStore lineStore = plugin.getLineTimingStore();
        TimingStore legacyStore  = plugin.getTimingStore();
        if (lineStore == null) return;


        // TC verschiebt Zeilen: getLine(1)=Header, getLine(2)=Linie, getLine(3)=Station
        String line2 = event.getLine(2) != null ? event.getLine(2).trim() : "";
        String line3 = event.getLine(3) != null ? event.getLine(3).trim() : "";

        switch (header) {
            case HEADER_TIMER -> {
                String lineName  = line2;
                String stationId = line3.toLowerCase();

                if (lineName.isEmpty() || stationId.isEmpty()) {
                    plugin.getLogger().warning("TDTimer: Linie oder Station fehlt!");
                    return;
                }
                LineConfig lineConfig = plugin.getConfigManager().getLine(lineName);
                if (lineConfig == null) {
                    plugin.getLogger().warning("TDTimer: Linie '" + lineName + "' nicht gefunden!");
                    return;
                }

                int direction = 1;
                if (lineConfig.getType() == LineConfig.Type.SHUTTLE) {
                    int idx = lineConfig.indexOfStation(stationId);
                    if (idx == lineConfig.getStops().size() - 1) direction = -1;
                }

                lineStore.startAt(trainName, lineName, stationId, direction);
                if (legacyStore != null) legacyStore.clearArrived(trainName);
            }

            case HEADER_STOP -> {
                String lineName  = line2;
                String stationId = line3.toLowerCase();

                if (lineName.isEmpty() || stationId.isEmpty()) {
                    plugin.getLogger().warning("TDStop: Linie oder Station fehlt!");
                    return;
                }
                LineConfig lineConfig = plugin.getConfigManager().getLine(lineName);
                if (lineConfig == null) return;

                long delaySeconds = lineStore.getDelay(trainName, lineConfig);
                lineStore.arriveAt(trainName, stationId, lineConfig);

                var ita = plugin.getInTrainAnnouncer();
                if (ita != null) ita.onStationPassed(trainName, stationId);

                var am = plugin.getAnnouncementManager();
                if (am != null && event.getSign() != null) {
                    var station = plugin.getConfigManager().getStations().get(stationId);
                    int stopIdx = lineConfig.indexOfStation(stationId);
                    String gleisName = "1";
                    if (station != null && stopIdx >= 0) {
                        var stop = lineConfig.getStops().get(stopIdx);
                        var gleis = station.getGleis(stop.getGleisId());
                        if (gleis != null) gleisName = gleis.getDisplayName();
                    }
                    final String fg = gleisName;
                    final long fd = delaySeconds;
                    final String ln = lineName;
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        org.bukkit.Location loc = event.getSign().getLocation();
                        var st = plugin.getConfigManager().getStations().get(stationId);
                        if (st != null && st.getAnnouncementLocation() != null)
                            loc = st.getAnnouncementLocation();
                        am.announceArrival(loc, ln, fg, fd);
                    });
                }
            }

            case HEADER_EXIT -> {
                String lineName = line2;
                var am = plugin.getAnnouncementManager();
                if (am != null && event.getSign() != null && !lineName.isEmpty()) {
                    var pos = lineStore.getPositions().get(trainName);
                    if (pos != null) {
                        LineConfig lc = plugin.getConfigManager().getLine(pos.getLineName());
                        if (lc != null) {
                            var station = plugin.getConfigManager().getStations()
                                .get(pos.getLastStationId());
                            int idx = lc.indexOfStation(pos.getLastStationId());
                            String gleisName = "1";
                            if (station != null && idx >= 0) {
                                var stop = lc.getStops().get(idx);
                                var gleis = station.getGleis(stop.getGleisId());
                                if (gleis != null) gleisName = gleis.getDisplayName();
                            }
                            final String fg = gleisName;
                            final String ln = pos.getLineName();
                            plugin.getServer().getScheduler().runTask(plugin, () ->
                                am.announceDeparture(event.getSign().getLocation(), ln, fg));
                        }
                    }
                }
                lineStore.removeTrain(trainName);
                if (legacyStore != null) legacyStore.clearArrived(trainName);

                // FIS-Schilder aller Stationen dieser Linie sofort leeren
                if (!lineName.isEmpty()) {
                    var lc = plugin.getConfigManager().getLine(lineName);
                    if (lc != null) {
                        for (var stop : lc.getStops()) {
                            plugin.getTimingStore().clearStationState(stop.getStationId());
                        }
                    }
                }
            }

            case HEADER_STATION -> {
                // Legacy TDStation
                String stationId = line2.toLowerCase();
                String gleisId   = line3.toLowerCase();
                if (stationId.isEmpty() || gleisId.isEmpty()) return;
                if (legacyStore != null) legacyStore.finishRun(trainName, stationId, gleisId);
            }
        }
    }

    @Override
    public boolean canSupportRC() { return false; }
}
