package de.transitboard.store;

import de.transitboard.model.UpcomingArrival;

import java.util.List;

/**
 * Zustandsspeicher für einen Bahnhof.
 *
 * Zustände:
 *   NORMAL      – Züge kommen, ETAs werden angezeigt
 *   HOLDOVER    – Zug ist gerade angekommen, letzte Anzeige bleibt X Sekunden
 *   EMPTY       – keine Züge bekannt, Schilder leer
 *   NO_SERVICE  – manuell gesetzt per /td nobetrieb, bleibt bis erster Zug kommt
 */
public class StationState {

    public enum Status { NORMAL, HOLDOVER, EMPTY, NO_SERVICE }

    private Status status = Status.EMPTY;
    private List<UpcomingArrival> lastArrivals = List.of();
    private long holdoverUntilMillis = 0;

    public Status getStatus() { return status; }

    public List<UpcomingArrival> getLastArrivals() { return lastArrivals; }

    public void setNoService() {
        status = Status.NO_SERVICE;
        lastArrivals = List.of();
    }

    public void clearNoService() {
        if (status == Status.NO_SERVICE) {
            status = Status.EMPTY;
        }
    }

    public void forceEmpty() {
        status = Status.EMPTY;
        lastArrivals = List.of();
        holdoverUntilMillis = 0;
    }

    /**
     * Wird jede Sekunde vom SignUpdater aufgerufen.
     * Gibt zurück was aktuell angezeigt werden soll (null = leere Schilder).
     */
    public List<UpcomingArrival> update(List<UpcomingArrival> freshArrivals, int holdoverSeconds) {
        if (status == Status.NO_SERVICE) {
            return null; // "Kein Betrieb" Text
        }

        if (!freshArrivals.isEmpty()) {
            // Normalbetrieb
            status = Status.NORMAL;
            lastArrivals = freshArrivals;
            holdoverUntilMillis = System.currentTimeMillis() + (holdoverSeconds * 1000L);
            return freshArrivals;
        }

        // Keine Züge mehr
        if (status == Status.NORMAL || status == Status.HOLDOVER) {
            long now = System.currentTimeMillis();
            if (now < holdoverUntilMillis) {
                // Nachhaltezeit läuft noch
                status = Status.HOLDOVER;
                return lastArrivals;
            }
        }

        // Leer
        status = Status.EMPTY;
        lastArrivals = List.of();
        return List.of(); // leere Schilder
    }
}
