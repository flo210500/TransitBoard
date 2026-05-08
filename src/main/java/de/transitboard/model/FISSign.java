package de.transitboard.model;

import org.bukkit.Location;

/**
 * Ein registriertes FIS.
 *
 * Bahnhofs-FIS Modi:
 *   SINGLE: loc1=alles
 *   DOUBLE: loc1=Linie, loc2=Zeit
 *   TRIPLE: loc1=Linie, loc2=Ziel, loc3=Zeit
 *   QUAD:   loc1=Gleis, loc2=Linie, loc3=Ziel, loc4=Zeit
 *
 * Gleis-FIS Modi:
 *   SINGLE: loc1=alles
 *   DOUBLE: loc1=Linie, loc2=Zeit
 *   TRIPLE: loc1=Linie, loc2=Ziel, loc3=Zeit
 */
public class FISSign {

    public enum Mode { SINGLE, DOUBLE, TRIPLE, QUAD }

    private final Location loc1;
    private final Location loc2;
    private final Location loc3;
    private final Location loc4;
    private final Mode mode;
    private final String stationDisplayName;

    public FISSign(Location loc1, Location loc2, Location loc3, Location loc4,
                   Mode mode, String stationDisplayName) {
        this.loc1               = loc1;
        this.loc2               = loc2;
        this.loc3               = loc3;
        this.loc4               = loc4;
        this.mode               = mode;
        this.stationDisplayName = stationDisplayName;
    }

    public Location getLoc1()             { return loc1; }
    public Location getLoc2()             { return loc2; }
    public Location getLoc3()             { return loc3; }
    public Location getLoc4()             { return loc4; }
    public Mode getMode()                 { return mode; }
    public String getStationDisplayName() { return stationDisplayName; }
    public boolean isDouble()             { return mode == Mode.DOUBLE || mode == Mode.TRIPLE || mode == Mode.QUAD; }
    public boolean isTriple()             { return mode == Mode.TRIPLE || mode == Mode.QUAD; }
    public boolean isQuad()               { return mode == Mode.QUAD; }
}
