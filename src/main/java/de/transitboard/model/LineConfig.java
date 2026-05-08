package de.transitboard.model;

import java.util.List;

/**
 * Eine Liniendefinition mit geordneten Stationen.
 *
 * Ring:    Nach dem letzten Stop wieder zum ersten
 * Shuttle: Nach dem letzten Stop rückwärts zum ersten
 */
public class LineConfig {

    public enum Type { RING, SHUTTLE }

    private final String id;
    private final Type type;
    private final List<LineStop> stops;
    private final String color;
    private String destination = "";
    private String displayName = ""; // Anzeigename (optional, sonst ID)
    // Pro-Linie Ansagen-Einstellungen (null = globale Config nutzen)
    private Boolean announcementsEnabled;   // null = global
    private Boolean inTrainEnabled;          // null = global
    private Boolean onArrival;               // null = global
    private Boolean onDeparture;             // null = global
    private Boolean onDelay;                 // null = global
    private String sequenceArrival;          // null = global
    private String sequenceDeparture;        // null = global
    private String sequenceDelay;            // null = global

    public LineConfig(String id, Type type, List<LineStop> stops, String color) {
        this.id    = id;
        this.type  = type;
        this.stops = stops;
        this.color = color != null ? color : "§f";
    }

    // Setter für pro-Linie Ansagen
    public void setAnnouncementsEnabled(Boolean v) { this.announcementsEnabled = v; }
    public void setInTrainEnabled(Boolean v)       { this.inTrainEnabled = v; }
    public void setOnArrival(Boolean v)            { this.onArrival = v; }
    public void setOnDeparture(Boolean v)          { this.onDeparture = v; }
    public void setOnDelay(Boolean v)              { this.onDelay = v; }
    public void setSequenceArrival(String v)       { this.sequenceArrival = v; }
    public void setSequenceDeparture(String v)     { this.sequenceDeparture = v; }
    public void setSequenceDelay(String v)         { this.sequenceDelay = v; }

    // Getter – null bedeutet: globale Config verwenden
    public Boolean getAnnouncementsEnabled() { return announcementsEnabled; }
    public Boolean getInTrainEnabled()       { return inTrainEnabled; }
    public Boolean getOnArrival()            { return onArrival; }
    public Boolean getOnDeparture()          { return onDeparture; }
    public Boolean getOnDelay()              { return onDelay; }
    public String getSequenceArrival()       { return sequenceArrival; }
    public String getSequenceDeparture()     { return sequenceDeparture; }
    public String getSequenceDelay()         { return sequenceDelay; }

    public LineConfig(String id, Type type, List<LineStop> stops) {
        this(id, type, stops, "§f");
    }

    public String getId()          { return id; }
    public Type getType()          { return type; }
    public List<LineStop> getStops() { return stops; }
    public String getColor()         { return color; }

    /** Gibt den Liniennamen mit Farbe zurück, z.B. "§1U1§r" */
    public String getColoredName()   { return color + getDisplayName() + "§r"; }

    /**
     * Gibt den nächsten Stop nach dem gegebenen StationId zurück.
     * Berücksichtigt Ring/Shuttle-Logik.
     *
     * @param currentStationId  Die aktuelle Station
     * @param direction         +1 = vorwärts, -1 = rückwärts (nur Shuttle)
     * @return nächster Stop, oder null wenn Endstation
     */
    public LineStop getNextStop(String currentStationId, int direction) {
        int idx = indexOfStation(currentStationId);
        if (idx < 0) return null;

        int next = idx + direction;

        if (type == Type.RING) {
            next = next % stops.size();
            if (next < 0) next += stops.size();
            return stops.get(next);
        } else { // SHUTTLE
            if (next < 0 || next >= stops.size()) return null;
            return stops.get(next);
        }
    }

    /**
     * Gibt alle Stops nach dem gegebenen zurück (in Fahrtrichtung).
     */
    public List<LineStop> getStopsAfter(String currentStationId, int direction) {
        int idx = indexOfStation(currentStationId);
        if (idx < 0) return List.of();

        java.util.List<LineStop> result = new java.util.ArrayList<>();
        int current = idx;
        int visited = 0;

        while (visited < stops.size() - 1) {
            current = current + direction;
            if (type == Type.RING) {
                current = ((current % stops.size()) + stops.size()) % stops.size();
            } else {
                if (current < 0 || current >= stops.size()) break;
            }
            result.add(stops.get(current));
            visited++;
        }
        // Bei Ring-Linien auch die Ausgangsstation am Ende hinzufügen
        if (type == Type.RING && !result.isEmpty()) {
            result.add(stops.get(idx));
        }
        return result;
    }

    public int indexOfStation(String stationId) {
        for (int i = 0; i < stops.size(); i++) {
            if (stops.get(i).getStationId().equals(stationId.toLowerCase())) return i;
        }
        return -1;
    }

    public boolean containsStation(String stationId) {
        return indexOfStation(stationId) >= 0;
    }

    public String getDestination() { return destination; }
    public void setDestination(String destination) { this.destination = destination; }

    public String getDisplayName() { return displayName.isEmpty() ? id : displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    /**
     * Gibt den Anzeigenamen des Ziels zurück.
     * Wenn konfiguriert: konfigurierten Wert.
     * Sonst bei Shuttle: letzter Stop in Fahrtrichtung.
     * Bei Ring: leer (kein sinnvolles Endziel).
     */
    public String getDestinationName(String currentStationId, int direction,
            java.util.Map<String, de.transitboard.model.StationConfig> stations) {
        // Konfiguriertes Ziel hat Vorrang
        if (!destination.isEmpty()) return destination;
        // Shuttle: letzter Stop in Fahrtrichtung
        if (type == Type.SHUTTLE) {
            java.util.List<LineStop> after = getStopsAfter(currentStationId, direction);
            if (after.isEmpty()) return "";
            String destId = after.get(after.size() - 1).getStationId();
            var station = stations.get(destId);
            return station != null ? station.getDisplayName() : destId;
        }
        // Ring: kein automatisches Ziel
        return "";
    }
}
