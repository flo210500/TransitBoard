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

    /** Erstellt 4 neue Maps und registriert die Renderer. Gibt Map-IDs zurück. */
    public List<Integer> create() {
        World world = Bukkit.getWorlds().get(0);
        maps.clear();
        mapIds.clear();
        mapEntries.clear();

        for (int i = 0; i < 4; i++) {
            MapView view = Bukkit.createMap(world);
            view.setScale(MapView.Scale.FARTHEST);
            view.setTrackingPosition(false);
            view.setUnlimitedTracking(false);
            view.getRenderers().forEach(view::removeRenderer);
            view.addRenderer(new MapDisplayRenderer(plugin, station, gleisId, i, this));
            maps.add(view);
            mapIds.add(view.getId());
            mapEntries.add(new MapEntry(view.getId(), view));
        }

        plugin.debugLog("MapDisplay erstellt für " + station.getId()
            + (gleisId != null ? "/" + gleisId : "") + " – Map-IDs: " + mapIds);

        startUpdateTask();
        return mapIds;
    }

    /** Stellt bestehende Maps nach Neustart wieder her (Renderer neu registrieren). */
    public boolean restore(List<Integer> ids) {
        maps.clear();
        mapIds.clear();
        mapEntries.clear();

        for (int i = 0; i < ids.size(); i++) {
            MapView view = Bukkit.getMap(ids.get(i));
            if (view == null) {
                plugin.getLogger().warning("MapDisplay: Map-ID " + ids.get(i) + " nicht gefunden!");
                return false;
            }
            view.getRenderers().forEach(view::removeRenderer);
            view.addRenderer(new MapDisplayRenderer(plugin, station, gleisId, i, this));
            maps.add(view);
            mapIds.add(view.getId());
            mapEntries.add(new MapEntry(view.getId(), view));
        }

        plugin.debugLog("MapDisplay wiederhergestellt für " + station.getId()
            + (gleisId != null ? "/" + gleisId : "") + " – Map-IDs: " + mapIds);

        startUpdateTask();
        return true;
    }

    private void startUpdateTask() {
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
    }

    public List<Integer> getMapIds()       { return mapIds; }
    public List<MapView> getMaps()         { return maps; }
    public List<MapEntry> getMapEntries()  { return mapEntries; }
}
