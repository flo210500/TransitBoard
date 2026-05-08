package de.transitboard.model;

import java.util.Objects;

/**
 * Eindeutiger Schlüssel für eine gemessene Strecke.
 *
 * Eine Strecke ist definiert durch:
 *   stationId  – Zielbahnhof       (z.B. "hauptbahnhof")
 *   gleisId    – Zielgleis          (z.B. "gleis1")
 *   lineName   – Linie des Zuges    (z.B. "L1")
 *   entryTag   – Einfahrtsweg-Tag   (z.B. "nord", "sued") – optional
 *
 * Das Timer-Schild schreibt diesen Key, der TimingStore speichert
 * die gemessenen Fahrtzeiten darunter.
 */
public class TrackKey {

    private final String stationId;
    private final String gleisId;
    private final String lineName;
    private final String entryTag;   // "" wenn nicht gesetzt

    private final String destination;

    public TrackKey(String stationId, String gleisId, String lineName, String entryTag) {
        this(stationId, gleisId, lineName, entryTag, "");
    }

    public TrackKey(String stationId, String gleisId, String lineName, String entryTag, String destination) {
        this.stationId   = stationId.toLowerCase();
        this.gleisId     = gleisId.toLowerCase();
        this.lineName    = lineName;
        this.entryTag    = entryTag == null ? "" : entryTag.toLowerCase();
        this.destination = destination != null ? destination : "";
    }

    public String getStationId()  { return stationId; }
    public String getGleisId()    { return gleisId; }
    public String getLineName()   { return lineName; }
    public String getEntryTag()   { return entryTag; }
    public String getDestination(){ return destination; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrackKey t)) return false;
        return stationId.equals(t.stationId)
            && gleisId.equals(t.gleisId)
            && lineName.equals(t.lineName)
            && entryTag.equals(t.entryTag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stationId, gleisId, lineName, entryTag);
    }

    @Override
    public String toString() {
        return stationId + "/" + gleisId + "/" + lineName
             + (entryTag.isEmpty() ? "" : "/" + entryTag);
    }
}
