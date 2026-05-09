package de.transitboard.model;

/**
 * Eine vorhergesagte Ankunft am FIS.
 * Ziel wird aus dem Display-Namen des Zuges gelesen:
 *   "[U1] Hauptbahnhof" → lineName="U1", destination="Hauptbahnhof"
 */
public class UpcomingArrival implements Comparable<UpcomingArrival> {

    private final String trainName;
    private final String lineName;
    private final String coloredLineName;
    private final String destination;
    private final String stationId;      // Station-ID
    private final String gleisId;
    private final String gleisDisplayName;
    private final long etaSeconds;
    private final long delaySeconds;

    public UpcomingArrival(String trainName, String lineName, String destination,
                           String gleisId, String gleisDisplayName, long etaSeconds) {
        this(trainName, lineName, lineName, destination, "", gleisId, gleisDisplayName, etaSeconds, 0L);
    }

    public UpcomingArrival(String trainName, String lineName, String coloredLineName,
                           String destination, String gleisId, String gleisDisplayName,
                           long etaSeconds, long delaySeconds) {
        this(trainName, lineName, coloredLineName, destination, "", gleisId, gleisDisplayName, etaSeconds, delaySeconds);
    }

    public UpcomingArrival(String trainName, String lineName, String coloredLineName,
                           String destination, String stationId, String gleisId, String gleisDisplayName,
                           long etaSeconds, long delaySeconds) {
        this.trainName        = trainName;
        this.lineName         = lineName;
        this.coloredLineName  = coloredLineName != null ? coloredLineName : lineName;
        this.destination      = destination != null ? destination : "";
        this.stationId        = stationId != null ? stationId : "";
        this.gleisId          = gleisId;
        this.gleisDisplayName = gleisDisplayName;
        this.etaSeconds       = etaSeconds;
        this.delaySeconds     = Math.max(0, delaySeconds);
    }



    public String getTrainName()        { return trainName; }
    public String getLineName()         { return lineName; }
    public String getColoredLineName()   { return coloredLineName; }
    public String getDestination()      { return destination; }
    public String getStationId()        { return stationId; }
    public String getGleisId()          { return gleisId; }
    public String getGleisDisplayName() { return gleisDisplayName; }
    public long   getEtaSeconds()       { return etaSeconds; }
    public long   getDelaySeconds()     { return delaySeconds; }
    public boolean isDelayed()          { return delaySeconds > 30; } // >30s gilt als Verspätung

    @Override
    public int compareTo(UpcomingArrival o) {
        return Long.compare(this.etaSeconds, o.etaSeconds);
    }

    @Override
    public String toString() {
        return "UpcomingArrival{line='" + lineName + "', dest='" + destination
             + "', gleis='" + gleisId + "', eta=" + etaSeconds + "s}";
    }
}
