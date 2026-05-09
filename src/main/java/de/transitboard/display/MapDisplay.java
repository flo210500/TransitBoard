package de.transitboard.display;

import de.transitboard.TransitBoardPlugin;
import de.transitboard.model.StationConfig;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.map.MapView;

import java.util.ArrayList;
import java.util.List;

/**
 * Verwaltet ein 1×4 Map-Display für eine Station/Gleis.
 * Registriert 4 MapViews und gibt die Map-IDs zurück.
 */
public class MapDisplay {

    public record MapEntry(int id, MapView view) {}

    private final TransitBoardPlugin plugin;
    private final StationConfig station;
    private final String gleisId;
    private final List<MapView> maps = new ArrayList<>();
    private final List<Integer> mapIds = new ArrayList<>();
    private final List<MapEntry> mapEntries = new ArrayList<>();

    // Geteiltes Render-State für alle 4 Maps dieser Instanz
    private java.awt.image.BufferedImage sharedImage = null;
    private long lastRenderMs = 0;

    public java.awt.image.BufferedImage getSharedImage() { return sharedImage; }
    public void setSharedImage(java.awt.image.BufferedImage img) { this.sharedImage = img; }
    public long getLastRenderMs() { return lastRenderMs; }
    public void setLastRenderMs(long ms) { this.lastRenderMs = ms; }

    public MapDisplay(TransitBoardPlugin plugin, StationConfig station, String gleisId) {
        this.plugin   = plugin;
        this.station  = station;
        this.gleisId  = gleisId;
    }

    /** Erstellt 4 Maps und registriert die Renderer. Gibt Map-IDs zurück. */
    public List<Integer> create() {
        World world = Bukkit.getWorlds().get(0);
        maps.clear();
        mapIds.clear();

        for (int i = 0; i < 4; i++) {
            MapView view = Bukkit.createMap(world);
            view.setScale(MapView.Scale.FARTHEST);
            view.setTrackingPosition(false);
            view.setUnlimitedTracking(false);

            // Standard-Renderer entfernen
            view.getRenderers().forEach(view::removeRenderer);

            // Unseren Renderer hinzufügen
            view.addRenderer(new MapDisplayRenderer(plugin, station, gleisId, i, this));

            maps.add(view);
            mapIds.add(view.getId());
            mapEntries.add(new MapEntry(view.getId(), view));
        }

        plugin.debugLog("MapDisplay erstellt für " + station.getId()
            + (gleisId != null ? "/" + gleisId : "")
            + " – Map-IDs: " + mapIds);

        // Jede Sekunde das Bild neu rendern und an alle Spieler schicken
        MapDisplayRenderer renderer = new MapDisplayRenderer(plugin, station, gleisId, 0, this);
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            setSharedImage(renderer.renderFullPublic());
            setLastRenderMs(System.currentTimeMillis());
            for (org.bukkit.entity.Player player : plugin.getServer().getOnlinePlayers()) {
                for (MapView view : maps) {
                    player.sendMap(view);
                }
            }
        }, 20L, 20L);

        return mapIds;
    }

    public List<Integer> getMapIds()       { return mapIds; }
    public List<MapView> getMaps()         { return maps; }
    public List<MapEntry> getMapEntries()  { return mapEntries; }
}
