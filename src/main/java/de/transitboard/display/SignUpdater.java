package de.transitboard.display;

import de.transitboard.TransitBoardPlugin;
import de.transitboard.model.FISSign;
import de.transitboard.model.GleisConfig;
import de.transitboard.model.LineConfig;
import de.transitboard.model.StationConfig;
import de.transitboard.model.TrainPosition;
import de.transitboard.model.UpcomingArrival;
import de.transitboard.store.StationState;
import de.transitboard.store.TimingStore;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class SignUpdater extends BukkitRunnable {

    private final TransitBoardPlugin plugin;
    private final TimingStore store;
    private final FISFormatter formatter;
    private final Logger log;

    private final de.transitboard.store.LineTimingStore lineStore;

    public SignUpdater(TransitBoardPlugin plugin, TimingStore store) {
        this.plugin    = plugin;
        this.store     = store;
        this.lineStore = plugin.getLineTimingStore();
        this.formatter = new FISFormatter(plugin.getConfigManager());
        this.log       = plugin.getLogger();
    }

    @Override
    public void run() {
        try {
            formatter.incrementScroll();
            for (StationConfig station : plugin.getConfigManager().getStations().values()) {
                try {
                    updateStation(station);
                } catch (Exception e) {
                    log.warning("Fehler beim Update von Station '" + station.getId() + "': " + e);
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            log.warning("Kritischer Fehler im SignUpdater: " + e);
            e.printStackTrace();
        }
    }

    private void updateStation(StationConfig station) {
        // Ankünfte aus Linien-System holen
        List<UpcomingArrival> lineArrivals = getLineArrivals(station);

        // Frische Ankünfte holen (inkl. arrivedRuns mit ETA=0)
        List<UpcomingArrival> legacyArrivals = store.getArrivalsForStation(station);

        // Zusammenführen: Linien-Ankünfte haben Vorrang
        List<UpcomingArrival> fresh = mergeArrivals(lineArrivals, legacyArrivals);

        // Zustand für diesen Bahnhof holen und aktualisieren
        StationState state = store.getOrCreateState(station.getId());
        int holdover = plugin.getConfigManager().getHoldoverSeconds();
        List<UpcomingArrival> arrivals = state.update(fresh, holdover);

        // arrivals == null → Kein Betrieb
        // arrivals.isEmpty() → leer (keine Anzeige)
        boolean noService = (arrivals == null);
        boolean empty     = !noService && arrivals.isEmpty();
        String name = station.getDisplayName();

        // ── Bahnhofs-FIS ──────────────────────────────────────────────────────
        for (FISSign fisSign : station.getStationSigns()) {
            if (noService) {
                writeNoService(fisSign, name);
            } else if (empty) {
                writeClear(fisSign, name);
            } else if (fisSign.isQuad()) {
                // loc1=Gleis, loc2=Linie, loc3=Ziel, loc4=Zeit
                writeSign(fisSign.getLoc1(), formatter.buildStationGleis(arrivals, name));
                writeSign(fisSign.getLoc2(), formatter.buildStationLinien(arrivals, name));
                writeSign(fisSign.getLoc3(), formatter.buildStationZiel(arrivals));
                writeSign(fisSign.getLoc4(), formatter.buildStationZeit(arrivals));
            } else if (fisSign.isTriple()) {
                // loc1=Linie, loc2=Ziel, loc3=Zeit
                writeSign(fisSign.getLoc1(), formatter.buildStationLinien(arrivals, name));
                writeSign(fisSign.getLoc2(), formatter.buildStationZiel(arrivals));
                writeSign(fisSign.getLoc3(), formatter.buildStationZeit(arrivals));
            } else if (fisSign.isDouble()) {
                // loc1=Linie, loc2=Zeit
                writeSign(fisSign.getLoc1(), formatter.buildStationLinien(arrivals, name));
                writeSign(fisSign.getLoc2(), formatter.buildStationZeit(arrivals));
            } else {
                writeSign(fisSign.getLoc1(), formatter.buildSingle(arrivals, name));
            }
        }

        // ── Gleis-FIS ─────────────────────────────────────────────────────────
        for (GleisConfig gleis : station.getGleise().values()) {
            if (gleis.getSigns().isEmpty()) continue;

            // Eigener State pro Gleis
            StationState gleisState = store.getOrCreateGleisState(station.getId(), gleis.getId());
            List<UpcomingArrival> freshGleis = getLineArrivals(station, gleis.getId());
            List<UpcomingArrival> ga = gleisState.update(
                noService ? Collections.emptyList() : freshGleis,
                plugin.getConfigManager().getHoldoverSeconds());
            if (noService) ga = Collections.emptyList();
            boolean gleisEmpty = ga != null && ga.isEmpty();
            boolean gleisNoSvc = ga == null;

            for (FISSign gs : gleis.getSigns()) {
                if (gleisNoSvc) {
                    writeLines(gs.getLoc1(), List.of(center(gleis.getDisplayName(),15),
                        plugin.getConfigManager().getFormatNoService(), "", ""));
                    writeClearLoc(gs.getLoc2()); writeClearLoc(gs.getLoc3());
                } else if (gleisEmpty) {
                    String gName = ChatColor.DARK_AQUA + center(gleis.getDisplayName(), 13);
                    if (gs.isTriple()) {
                        writeLines(gs.getLoc1(), List.of(gName, "", "", ""));
                        writeLines(gs.getLoc2(), List.of(ChatColor.DARK_AQUA + center(plugin.getConfigManager().getHeaderZiel(), 13), "", "", ""));
                        writeLines(gs.getLoc3(), List.of(ChatColor.DARK_AQUA + center(plugin.getConfigManager().getHeaderAbfahrt(), 13), "", "", ""));
                    } else if (gs.isDouble()) {
                        writeLines(gs.getLoc1(), List.of(gName, "", "", ""));
                        writeLines(gs.getLoc2(), List.of(ChatColor.DARK_AQUA + center(plugin.getConfigManager().getHeaderAbfahrt(), 13), "", "", ""));
                    } else {
                        writeLines(gs.getLoc1(), List.of(gName, "", "", ""));
                    }
                } else if (gs.isTriple()) {
                    writeSign(gs.getLoc1(), formatter.buildGleisLinien(ga, gleis.getDisplayName()));
                    writeSign(gs.getLoc2(), formatter.buildGleisZiel(ga));
                    writeSign(gs.getLoc3(), formatter.buildGleisZeit(ga));
                } else if (gs.isDouble()) {
                    writeSign(gs.getLoc1(), formatter.buildGleisLinien(ga, gleis.getDisplayName()));
                    writeSign(gs.getLoc2(), formatter.buildGleisZeit(ga));
                } else {
                    writeSign(gs.getLoc1(), formatter.buildGleisSingle(ga, gleis.getDisplayName()));
                }
            }
        }
    }

    // ─── Kein Betrieb / Leer ──────────────────────────────────────────────────

    private void writeNoService(FISSign sign, String stationName) {
        String noSvc = plugin.getConfigManager().getFormatNoService();
        writeLines(sign.getLoc1(), List.of(center(stationName, 15), noSvc, "", ""));
        writeClearLoc(sign.getLoc2());
        writeClearLoc(sign.getLoc3());
        writeClearLoc(sign.getLoc4());
    }

    private void writeClear(FISSign sign, String stationName) {
        String sName = ChatColor.DARK_AQUA + center(stationName, 13);
        String sZiel = ChatColor.DARK_AQUA + center(plugin.getConfigManager().getHeaderZiel(), 13);
        String sAbfahrt = ChatColor.DARK_AQUA + center(plugin.getConfigManager().getHeaderAbfahrt(), 13);
        String sGleis = ChatColor.DARK_AQUA + center(plugin.getConfigManager().getHeaderGleis(), 13);
        List<String> empty = List.of("", "", "", "");
        if (sign.isQuad()) {
            writeLines(sign.getLoc1(), List.of(sGleis, "", "", ""));
            writeLines(sign.getLoc2(), List.of(sName, "", "", ""));
            writeLines(sign.getLoc3(), List.of(sZiel, "", "", ""));
            writeLines(sign.getLoc4(), List.of(sAbfahrt, "", "", ""));
        } else if (sign.isTriple()) {
            writeLines(sign.getLoc1(), List.of(sName, "", "", ""));
            writeLines(sign.getLoc2(), List.of(sZiel, "", "", ""));
            writeLines(sign.getLoc3(), List.of(sAbfahrt, "", "", ""));
        } else if (sign.isDouble()) {
            writeLines(sign.getLoc1(), List.of(sName, "", "", ""));
            writeLines(sign.getLoc2(), List.of(sAbfahrt, "", "", ""));
        } else {
            writeLines(sign.getLoc1(), List.of(sName, "", "", ""));
        }
    }

    // ─── Schreiben ────────────────────────────────────────────────────────────

    private void writeSign(Location loc, List<String> lines) {
        writeLines(loc, lines);
    }

    private void writeLines(Location loc, List<String> lines) {
        if (loc == null) return;
        Block block = loc.getBlock();
        if (!(block.getState() instanceof Sign sign)) return;
        try {
            var front = sign.getSide(Side.FRONT);
            // Schildfarbe auf Weiß setzen damit Farben korrekt dargestellt werden
            front.setColor(org.bukkit.DyeColor.WHITE);
            for (int i = 0; i < 4; i++) {
                front.setLine(i, i < lines.size() ? lines.get(i) : "");
            }
            sign.update(true, false);
        } catch (Exception e) {
            log.fine("Schild-Update fehlgeschlagen: " + e.getMessage());
        }
    }

    /**
     * Berechnet Ankünfte aus dem Linien-System für eine Station.
     */
    private List<UpcomingArrival> getLineArrivals(StationConfig station) {
        return getLineArrivals(station, null);
    }

    private List<UpcomingArrival> getLineArrivals(StationConfig station, String gleisId) {
        List<UpcomingArrival> result = new ArrayList<>();
        if (lineStore == null) return result;


        for (var lineEntry : plugin.getConfigManager().getLines().entrySet()) {
            var lineConfig = lineEntry.getValue();
            if (!lineConfig.containsStation(station.getId())) continue;

            // Alle Züge auf dieser Linie
            for (var pos : lineStore.getTrainsOnLine(lineConfig.getId())) {
                var etas = lineStore.getETAs(pos.getTrainName(), lineConfig);
                Long eta = etas.get(station.getId());
                if (eta == null || eta < 0) continue;

                TrainPosition trainPos = lineStore.getPositions().get(pos.getTrainName());
                if (trainPos == null) continue;

                // Gleis aus Liniendefinition
                int stopIdx = lineConfig.indexOfStation(station.getId());
                if (stopIdx < 0) continue;
                var stop = lineConfig.getStops().get(stopIdx);
                var gleis = station.getGleis(stop.getGleisId());
                if (gleis == null) continue;

                long delay = lineStore.getDelay(pos.getTrainName(), lineConfig);

                // Ziel aus Liniendefinition ableiten (trainPos bereits oben definiert)
                int dir = trainPos.getDirection();
                String destination = lineConfig.getDestinationName(
                    pos.getLastStationId(), dir,
                    plugin.getConfigManager().getStations());

                String coloredName = lineConfig.getColoredName();
                result.add(new UpcomingArrival(
                    pos.getTrainName(),
                    pos.getLineName(),
                    coloredName,
                    destination,
                    station.getId(),
                    gleis.getId(),
                    gleis.getDisplayName(),
                    eta,
                    delay
                ));
            }
        }
        Collections.sort(result);
        // Gleis-Filter wenn angegeben
        if (gleisId != null) {
            result.removeIf(a -> !a.getGleisId().equals(gleisId));
        }
        return result;
    }

    /**
     * Führt Linien- und Legacy-Ankünfte zusammen.
     * Linien-Ankünfte haben Vorrang – Legacy nur wenn kein Linien-Eintrag.
     */
    private List<UpcomingArrival> mergeArrivals(List<UpcomingArrival> line,
                                                 List<UpcomingArrival> legacy) {
        if (line.isEmpty()) return legacy;
        if (legacy.isEmpty()) return line;

        Set<String> lineTrains = new java.util.HashSet<>();
        for (var a : line) lineTrains.add(a.getTrainName());

        List<UpcomingArrival> merged = new ArrayList<>(line);
        for (var a : legacy) {
            if (!lineTrains.contains(a.getTrainName())) merged.add(a);
        }
        Collections.sort(merged);
        return merged;
    }

    private void writeClearLoc(Location loc) {
        writeLines(loc, List.of("", "", "", ""));
    }

    private String center(String text, int width) {
        if (text.length() >= width) return text.substring(0, width);
        int pad = width - text.length();
        return " ".repeat(pad / 2) + text + " ".repeat(pad - pad / 2);
    }
}
