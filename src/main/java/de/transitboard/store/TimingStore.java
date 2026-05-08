package de.transitboard.store;

import de.transitboard.model.ActiveRun;
import de.transitboard.model.TrackKey;
import de.transitboard.model.UpcomingArrival;
import de.transitboard.model.GleisConfig;
import de.transitboard.store.StationState;
import de.transitboard.model.StationConfig;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Zentraler Datenspeicher für TrainDisplay.
 *
 * Verwaltet:
 *  1. activeRuns   – Züge die gerade zwischen Timer-Schild und Bahnhof sind
 *  2. timingHistory – gemessene Fahrtzeiten pro TrackKey (gleitender Durchschnitt)
 *
 * Aus diesen Daten werden die UpcomingArrivals berechnet die das FIS anzeigt.
 */
public class TimingStore {

    private final Logger log;
    private final int historySize;

    // trainName → laufende Messung
    private final Map<String, ActiveRun> activeRuns = new ConcurrentHashMap<>();

    // TrackKey → Liste der letzten N gemessenen Fahrtzeiten (ms)
    private final Map<TrackKey, Deque<Long>> timingHistory = new ConcurrentHashMap<>();

    // stationId+gleisId → Zustand (pro Gleis separat)
    private final Map<String, StationState> stationStates = new ConcurrentHashMap<>();

    // trainName → Ankunftszeitpunkt in Millisekunden
    private final Map<String, Long> arrivedAt = new ConcurrentHashMap<>();
    // trainName → der arrived Run (für Linie/Ziel/Gleis Info)
    private final Map<String, ActiveRun> arrivedRuns = new ConcurrentHashMap<>();
    private int holdoverSeconds = 30;

    public void setHoldoverSeconds(int s) { this.holdoverSeconds = s; }

    private TimingStorage timingStorage;

    public TimingStore(Logger log, int historySize) {
        this.log         = log;
        this.historySize = historySize;
    }

    public void setTimingStorage(TimingStorage storage) {
        this.timingStorage = storage;
        storage.load(timingHistory);
    }

    public void saveTiming() {
        if (timingStorage != null) timingStorage.save(timingHistory);
    }

    // ─── Timer-Schild: Zug startet Messung ────────────────────────────────────

    /**
     * Wird aufgerufen wenn ein Zug das [TDTimer]-Schild überfährt.
     * Startet eine neue Messung – oder überschreibt eine laufende
     * (kann passieren wenn ein Zug das Schild zweimal überquert).
     */
    public void startRun(String trainName, TrackKey key) {
        ActiveRun run = new ActiveRun(trainName, key);
        activeRuns.put(trainName, run);
        // Kein Betrieb aufheben wenn erster Zug wieder kommt
        clearNoService(key.getStationId());
        // Nur den eigenen arrivedRun löschen (nicht den anderer Züge auf dem Gleis)
        arrivedRuns.remove(trainName);
        arrivedAt.remove(trainName);
        log.fine("Timer gestartet: " + run);
    }

    // ─── Bahnhof-Einfahrt: Messung abschließen ────────────────────────────────

    /**
     * Wird aufgerufen wenn ein Zug in den Bahnhof einfährt ([TDStation]-Schild).
     *
     * @param trainName  Zugname
     * @param stationId  Bahnhof der erreicht wurde
     * @param gleisId    Gleis das angefahren wird
     * @return true wenn eine Messung abgeschlossen wurde
     */
    public boolean finishRun(String trainName, String stationId, String gleisId) {
        ActiveRun run = activeRuns.remove(trainName);
        if (run == null) return false;

        // Nur wenn Ziel übereinstimmt
        if (!run.getKey().getStationId().equals(stationId.toLowerCase())
         || !run.getKey().getGleisId().equals(gleisId.toLowerCase())) {
            // Falscher Bahnhof/Gleis – Messung verwerfen
            log.fine("Messung verworfen (anderer Bahnhof/Gleis): " + run);
            return false;
        }

        long elapsed = run.elapsedMillis();

        // Plausibilitätscheck: zwischen 2 Sekunden und 60 Minuten
        if (elapsed < 2_000 || elapsed > 3_600_000) {
            log.warning("Implausible Fahrzeit fuer " + run.getKey() + ": " + elapsed + "ms – verworfen.");
            return false;
        }

        // In History speichern
        timingHistory
            .computeIfAbsent(run.getKey(), k -> new ArrayDeque<>())
            .add(elapsed);

        // History auf historySize begrenzen
        Deque<Long> history = timingHistory.get(run.getKey());
        while (history.size() > historySize) {
            history.pollFirst();
        }

        // Zug als angekommen speichern mit eigenem Ankunftszeitpunkt
        arrivedRuns.put(trainName, run);
        arrivedAt.put(trainName, System.currentTimeMillis());

        // Fahrtzeit persistieren
        if (timingStorage != null) timingStorage.save(timingHistory);

        return true;
    }

    // ─── ETA-Berechnung ───────────────────────────────────────────────────────

    /**
     * Berechnet alle vorhergesagten Ankünfte für einen Bahnhof.
     *
     * Für jeden aktiven Lauf dessen Zielbahnhof dieser Bahnhof ist:
     *   ETA = Durchschnitt(Fahrtzeiten) - bisher_gefahrene_Zeit
     *
     * @param station  Der Zielbahnhof
     * @return Sortierte Liste aller Ankünfte (kürzeste zuerst)
     */
    public List<UpcomingArrival> getArrivalsForStation(StationConfig station) {
        List<UpcomingArrival> result = new ArrayList<>();

        for (ActiveRun run : activeRuns.values()) {
            if (!run.getKey().getStationId().equals(station.getId())) continue;

            GleisConfig gleis = station.getGleis(run.getKey().getGleisId());
            if (gleis == null) continue;

            long avgMillis = getAverageMillis(run.getKey());
            if (avgMillis <= 0) {
                // Noch keine Messung vorhanden → ETA unbekannt, trotzdem anzeigen
                // mit "?" damit der Fahrdienstleiter weiß dass es läuft
                result.add(new UpcomingArrival(
                    run.getTrainName(),
                    run.getKey().getLineName(),
                    run.getKey().getDestination(),
                    gleis.getId(),
                    gleis.getDisplayName(),
                    -1
                ));
                continue;
            }

            long etaMillis  = avgMillis - run.elapsedMillis();
            long etaSeconds = Math.max(1, etaMillis / 1000L); // min 1s bis TDStation

            result.add(new UpcomingArrival(
                run.getTrainName(),
                run.getKey().getLineName(),
                run.getKey().getDestination(),
                gleis.getId(),
                gleis.getDisplayName(),
                etaSeconds
            ));
        }

        // Angekommene Züge mit ETA=0 anzeigen.
        // Ein arrivedRun bleibt bis der gleiche Zug wieder das TDTimer-Schild
        // überfährt (wird in startRun gelöscht) ODER bis der Fallback-Holdover
        // abläuft (für den Fall dass ein Zug nie wieder kommt).
        long now = System.currentTimeMillis();
        arrivedAt.entrySet().removeIf(e -> now - e.getValue() > holdoverSeconds * 1000L);
        arrivedRuns.keySet().retainAll(arrivedAt.keySet());
        for (Map.Entry<String, ActiveRun> entry : arrivedRuns.entrySet()) {
            ActiveRun run = entry.getValue();
            String trainName2 = entry.getKey();
            if (!run.getKey().getStationId().equals(station.getId())) continue;
            GleisConfig gleis = station.getGleis(run.getKey().getGleisId());
            if (gleis == null) continue;
            boolean alreadyActive = result.stream()
                .anyMatch(a -> a.getTrainName().equals(run.getTrainName()));
            if (!alreadyActive) {
                result.add(new UpcomingArrival(
                    run.getTrainName(), run.getKey().getLineName(),
                    run.getKey().getDestination(), gleis.getId(),
                    gleis.getDisplayName(), 0L));
            }
        }

        Collections.sort(result);
        return result;
    }

    /**
     * Berechnet alle Ankünfte für ein einzelnes Gleis.
     */
    public List<UpcomingArrival> getArrivalsForGleis(StationConfig station, String gleisId) {
        List<UpcomingArrival> all = getArrivalsForStation(station);
        List<UpcomingArrival> filtered = new ArrayList<>();
        for (UpcomingArrival a : all) {
            if (a.getGleisId().equals(gleisId.toLowerCase())) {
                filtered.add(a);
            }
        }
        return filtered;
    }

    // ─── Hilfsmethoden ────────────────────────────────────────────────────────

    private long getAverageMillis(TrackKey key) {
        Deque<Long> history = timingHistory.get(key);
        if (history == null || history.isEmpty()) return 0L;
        long sum = 0;
        for (long v : history) sum += v;
        return sum / history.size();
    }

    /** Key für stateStates: stationId oder stationId/gleisId */
    private String stateKey(String stationId, String gleisId) {
        return gleisId == null || gleisId.isEmpty()
            ? stationId.toLowerCase()
            : stationId.toLowerCase() + "/" + gleisId.toLowerCase();
    }

    public void clearStationState(String stationId) {
        stationStates.forEach((k, v) -> {
            if (k.startsWith(stationId.toLowerCase())) v.forceEmpty();
        });
    }

    public StationState getOrCreateState(String stationId) {
        return stationStates.computeIfAbsent(stationId.toLowerCase(), k -> new StationState());
    }

    public StationState getOrCreateGleisState(String stationId, String gleisId) {
        return stationStates.computeIfAbsent(stateKey(stationId, gleisId), k -> new StationState());
    }

    public void setNoService(String stationId) {
        // Alle Gleise dieses Bahnhofs auf NoService setzen
        stationStates.forEach((k, v) -> { if (k.startsWith(stationId.toLowerCase())) v.setNoService(); });
        getOrCreateState(stationId).setNoService();
    }

    public void clearNoService(String stationId) {
        stationStates.forEach((k, v) -> { if (k.startsWith(stationId.toLowerCase())) v.clearNoService(); });
        getOrCreateState(stationId).clearNoService();
    }

    /** Entfernt einen angekommenen Zug sofort von der Anzeige (TDExit-Schild). */
    public void clearArrived(String trainName) {
        arrivedRuns.remove(trainName);
        arrivedAt.remove(trainName);
    }

    public int getActiveRunCount()    { return activeRuns.size(); }
    public Map<TrackKey, Deque<Long>> getTimingHistory() { return timingHistory; }

    /**
     * Für /td status: gibt alle aktiven Läufe aus.
     */
    public Collection<ActiveRun> getActiveRuns() { return activeRuns.values(); }
}
