package de.transitboard.model;

/**
 * Repräsentiert einen Zug der gerade zwischen Timer-Schild und Bahnhof unterwegs ist.
 *
 * Wird beim Überfahren des [TDTimer]-Schilds erstellt und beim
 * Erreichen des Bahnhofs ([TDStation]-Einfahrt) abgeschlossen.
 */
public class ActiveRun {

    private final String trainName;     // TrainCarts-interner Zugname
    private final TrackKey key;         // Welche Strecke wird gemessen
    private final long startMillis;     // System.currentTimeMillis() beim Timer-Schild

    public ActiveRun(String trainName, TrackKey key) {
        this.trainName   = trainName;
        this.key         = key;
        this.startMillis = System.currentTimeMillis();
    }

    public String getTrainName()  { return trainName; }
    public TrackKey getKey()      { return key; }
    public long getStartMillis()  { return startMillis; }

    /**
     * Wie viele Millisekunden sind seit dem Timer-Schild vergangen?
     */
    public long elapsedMillis() {
        return System.currentTimeMillis() - startMillis;
    }

    @Override
    public String toString() {
        return "ActiveRun{train='" + trainName + "', key=" + key
             + ", elapsed=" + elapsedMillis() + "ms}";
    }
}
