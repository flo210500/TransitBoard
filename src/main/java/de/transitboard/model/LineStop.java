package de.transitboard.model;

/**
 * Ein Halt auf einer Linie.
 */
public class LineStop {

    private final String stationId;
    private final String gleisId;

    public LineStop(String stationId, String gleisId) {
        this.stationId = stationId.toLowerCase();
        this.gleisId   = gleisId.toLowerCase();
    }

    public String getStationId() { return stationId; }
    public String getGleisId()   { return gleisId; }

    @Override
    public String toString() {
        return stationId + "/" + gleisId;
    }
}
