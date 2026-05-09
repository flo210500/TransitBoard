package de.transitboard.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aktuelle bekannte Position eines Zuges auf einer Linie.
 *
 * Speichert:
 * - Welche Linie
 * - Zuletzt passierte Station + Zeitstempel
 * - Fahrtrichtung (für Shuttle)
 * - Gemessene Teilzeiten dieser Fahrt
 */
public class TrainPosition {

    private final String trainName;
    private final String lineName;
    private String lastStationId;
    private long lastStationMillis;
    private int direction;
    private long accumulatedDelayMs = 0; // Aufaddierte Verspätung über alle Segmente

    // stationId → Zeitstempel (ms) wann diese Station passiert wurde
    private final Map<String, Long> stationTimestamps = new LinkedHashMap<>();

    public TrainPosition(String trainName, String lineName,
                         String startStationId, int direction) {
        this.trainName      = trainName;
        this.lineName       = lineName;
        this.lastStationId  = startStationId;
        this.lastStationMillis = System.currentTimeMillis();
        this.direction      = direction;
        stationTimestamps.put(startStationId, lastStationMillis);
    }

    /** Zug hat eine neue Station passiert. Verspätung aufaddieren. */
    public void advance(String stationId, long segmentDelayMs) {
        long now = System.currentTimeMillis();
        accumulatedDelayMs = Math.max(0, accumulatedDelayMs + segmentDelayMs);
        lastStationId     = stationId;
        lastStationMillis = now;
        stationTimestamps.put(stationId, now);
    }

    /** Zug hat eine neue Station passiert (ohne Verspätungsinfo). */
    public void advance(String stationId) {
        advance(stationId, 0);
    }

    /** Wie viele ms sind seit der letzten Station vergangen? */
    public long elapsedSinceLastStation() {
        return System.currentTimeMillis() - lastStationMillis;
    }

    public long getAccumulatedDelayMs() { return accumulatedDelayMs; }
    public void setAccumulatedDelayMs(long ms) { this.accumulatedDelayMs = ms; }

    public String getTrainName()     { return trainName; }
    public String getLineName()      { return lineName; }
    public String getLastStationId() { return lastStationId; }
    public int getDirection()        { return direction; }
    public void setDirection(int d)  { this.direction = d; }

    @Override
    public String toString() {
        return "TrainPosition{train='" + trainName + "', line='" + lineName
             + "', lastStation='" + lastStationId + "', dir=" + direction + "}";
    }
}
