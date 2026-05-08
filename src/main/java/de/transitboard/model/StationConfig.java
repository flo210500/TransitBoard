package de.transitboard.model;

import org.bukkit.Location;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ein Bahnhof mit mehreren Gleisen und FIS-Schildern.
 *
 * Bahnhofs-FIS → Liste von FISSign (single oder double)
 * Gleis-FIS    → GleisConfig.getSigns()
 */
public class StationConfig {

    private final String id;
    private final String displayName;
    private final Map<String, GleisConfig> gleise;

    private final List<FISSign> stationSigns = new ArrayList<>();
    private org.bukkit.Location announcementLocation;

    public StationConfig(String id, String displayName, Map<String, GleisConfig> gleise) {
        this.id          = id;
        this.displayName = displayName;
        this.gleise      = gleise;
    }

    public String getId()                        { return id; }
    public String getDisplayName()               { return displayName; }
    public Map<String, GleisConfig> getGleise()  { return gleise; }
    public List<FISSign> getStationSigns()              { return stationSigns; }
    public org.bukkit.Location getAnnouncementLocation() { return announcementLocation; }
    public void setAnnouncementLocation(org.bukkit.Location loc) { this.announcementLocation = loc; }

    public void addStationSign(FISSign sign) {
        stationSigns.add(sign);
    }

    /** Kompatibilität mit SignStorage – Single-Schild */
    public void addStationSign(Location loc) {
        stationSigns.add(new FISSign(loc, null, null, null, FISSign.Mode.SINGLE, displayName));
    }

    public void addGleisSign(String gleisId, FISSign sign) {
        GleisConfig gleis = gleise.get(gleisId.toLowerCase());
        if (gleis != null) gleis.addSign(sign);
    }

    public GleisConfig getGleis(String gleisId) {
        return gleise.get(gleisId.toLowerCase());
    }

    @Override
    public String toString() {
        return "StationConfig{id='" + id + "', gleise=" + gleise.keySet() + "}";
    }
}
