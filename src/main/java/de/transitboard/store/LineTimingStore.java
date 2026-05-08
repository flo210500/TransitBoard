package de.transitboard.store;

import de.transitboard.model.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Herzstück des neuen linienbasierten Systems.
 *
 * Verwaltet:
 * 1. Aktive Zugpositionen (welcher Zug ist wo auf welcher Linie)
 * 2. Gemessene Teilzeiten zwischen Stationen
 * 3. ETA-Berechnung für alle Stationen einer Linie
 */
public class LineTimingStore {

    private final Logger log;
    private final int historySize;
    private de.transitboard.TransitBoardPlugin plugin;

    // trainName → aktuelle Position
    private final Map<String, TrainPosition> positions = new ConcurrentHashMap<>();

    // "lineName/fromStation/toStation" → Liste gemessener Zeiten (ms)
    private final Map<String, Deque<Long>> segmentTimes = new ConcurrentHashMap<>();

    private LineTimingStorage storage;

    public LineTimingStore(Logger log, int historySize) {
        this.log         = log;
        this.historySize = historySize;
    }

    public void setPlugin(de.transitboard.TransitBoardPlugin plugin) {
        this.plugin = plugin;
    }

    private void debugLog(String msg) {
        if (plugin != null && plugin.isDebugMode()) log.info("[DEBUG] " + msg);
    }

    public void setStorage(LineTimingStorage s) {
        this.storage = s;
        s.load(segmentTimes);
    }

    public void save() {
        if (storage != null) storage.save(segmentTimes);
    }

    // ─── TDTimer: Zug startet an einer Station ────────────────────────────────

    /**
     * Zug fährt über [TDTimer] – startet an startStationId auf Linie lineName.
     */
    public void startAt(String trainName, String lineName, String startStationId, int direction) {
        TrainPosition pos = new TrainPosition(trainName, lineName, startStationId, direction);
        positions.put(trainName, pos);
        debugLog("Zug '" + trainName + "' gestartet: Linie=" + lineName
               + " Station=" + startStationId + " dir=" + direction);
    }

    // ─── TDStop: Zug erreicht eine Station ───────────────────────────────────

    /**
     * Zug fährt über [TDStop] – erreicht stationId.
     * Misst die Teilzeit von der letzten Station.
     */
    public void arriveAt(String trainName, String stationId, LineConfig line) {
        TrainPosition pos = positions.get(trainName);
        if (pos == null) {
            log.fine("arriveAt: kein aktiver Run für '" + trainName + "' – ignoriert");
            return;
        }
        if (!pos.getLineName().equals(line.getId())) {
                return;
        }

        String fromStation = pos.getLastStationId();
        long elapsed       = pos.elapsedSinceLastStation();

        // Keine Messung wenn Start und Ziel die gleiche Station sind
        if (fromStation.equals(stationId)) {
            pos.advance(stationId);
            return;
        }

        // Plausibilitätscheck
        if (elapsed < 500 || elapsed > 3_600_000) {
            log.warning("Implausible Teilzeit " + fromStation
                + "→" + stationId + ": " + elapsed + "ms – verworfen");
        } else {
            // Teilzeit speichern
            String segKey = segmentKey(line.getId(), fromStation, stationId);
            segmentTimes.computeIfAbsent(segKey, k -> new ArrayDeque<>()).add(elapsed);
            Deque<Long> hist = segmentTimes.get(segKey);
            while (hist.size() > historySize) hist.pollFirst();
            debugLog("Teilzeit " + fromStation + "→" + stationId
                + ": " + (elapsed/1000) + "s (\u2300 " + (avgSegment(segKey)/1000) + "s)");
            if (storage != null) storage.save(segmentTimes);
        }

        // Position aktualisieren mit Segmentverspätung
        long avg = avgSegment(segmentKey(line.getId(), fromStation, stationId));
        long segmentDelay = (avg > 0) ? (elapsed - avg) : 0;
        pos.advance(stationId, segmentDelay);

        // Shuttle-Richtung umkehren wenn Endstation
        if (line.getType() == LineConfig.Type.SHUTTLE) {
            int idx = line.indexOfStation(stationId);
            if (idx == 0) pos.setDirection(1);
            else if (idx == line.getStops().size() - 1) pos.setDirection(-1);
        }
    }

    // ─── ETA-Berechnung ───────────────────────────────────────────────────────

    /**
     * Berechnet ETAs für alle Stationen nach dem aktuellen Standort des Zuges.
     * Gibt auch die aktuelle Verspätung zurück (elapsed > avg für erstes Segment).
     *
     * @return Map: stationId → ETA in Sekunden (-1 = unbekannt)
     */
    public Map<String, Long> getETAs(String trainName, LineConfig line) {
        TrainPosition pos = positions.get(trainName);
        if (pos == null) return Map.of();

        Map<String, Long> result = new LinkedHashMap<>();
        String current = pos.getLastStationId();
        long elapsed = pos.elapsedSinceLastStation();

        List<LineStop> upcoming = line.getStopsAfter(current, pos.getDirection());
        String from = current;
        long totalAvg = 0; // Summe aller Segment-Durchschnitte bis zu diesem Punkt

        for (LineStop stop : upcoming) {
            String segKey = segmentKey(line.getId(), from, stop.getStationId());
            long avg = avgSegment(segKey);

            if (avg <= 0) {
                result.put(stop.getStationId(), -1L);
            } else {
                totalAvg += avg;
                long etaMs = totalAvg - elapsed;
                result.put(stop.getStationId(), Math.max(0, etaMs / 1000L));
            }

            from = stop.getStationId();
        }

        return result;
    }

    /**
     * Berechnet die aktuelle Verspätung eines Zuges in Sekunden.
     * Positiv = Verspätung, 0 = pünktlich.
     */
    public long getDelay(String trainName, LineConfig line) {
        TrainPosition pos = positions.get(trainName);
        if (pos == null) return 0;

        // Akkumulierte Verspätung + aktuelle Segmentverspätung
        String current = pos.getLastStationId();
        List<LineStop> upcoming = line.getStopsAfter(current, pos.getDirection());
        if (upcoming.isEmpty()) return pos.getAccumulatedDelayMs() / 1000L;

        String firstSegKey = segmentKey(line.getId(), current, upcoming.get(0).getStationId());
        long avg = avgSegment(firstSegKey);
        long currentSegmentDelay = (avg > 0) ? Math.max(0, pos.elapsedSinceLastStation() - avg) : 0;

        return Math.max(0, (pos.getAccumulatedDelayMs() + currentSegmentDelay) / 1000L);
    }

    /**
     * Gibt alle aktiven Züge auf einer Linie zurück.
     */
    public List<TrainPosition> getTrainsOnLine(String lineName) {
        List<TrainPosition> result = new ArrayList<>();
        for (TrainPosition pos : positions.values()) {
            if (pos.getLineName().equals(lineName)) result.add(pos);
        }
        return result;
    }

    /**
     * Entfernt einen Zug aus dem System (TDExit).
     */
    public void removeTrain(String trainName) {
        positions.remove(trainName);
    }

    // ─── Hilfsmethoden ────────────────────────────────────────────────────────

    private String segmentKey(String line, String from, String to) {
        return line + "/" + from + "/" + to;
    }

    private long avgSegment(String key) {
        Deque<Long> hist = segmentTimes.get(key);
        if (hist == null || hist.isEmpty()) return 0L;
        return hist.stream().mapToLong(Long::longValue).sum() / hist.size();
    }

    public Map<String, Deque<Long>> getSegmentTimes() { return segmentTimes; }
    public Map<String, TrainPosition> getPositions()   { return positions; }
}
