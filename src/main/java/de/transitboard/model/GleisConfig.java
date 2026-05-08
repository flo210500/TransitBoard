package de.transitboard.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Ein Gleis mit FIS-Schildern.
 * Gleis-FIS nutzt FISSign genau wie Bahnhofs-FIS,
 * aber ohne Gleisnummer-Schild (loc1=Linie, loc2=Ziel, loc3=Zeit).
 */
public class GleisConfig {

    private final String id;
    private final String displayName;
    private final List<FISSign> signs = new ArrayList<>();

    public GleisConfig(String id, String displayName) {
        this.id          = id;
        this.displayName = displayName;
    }

    public String getId()          { return id; }
    public String getDisplayName() { return displayName; }
    public List<FISSign> getSigns() { return signs; }

    public void addSign(FISSign sign) {
        signs.add(sign);
    }
}
