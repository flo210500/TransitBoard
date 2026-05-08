package de.transitboard.announcement;

import de.transitboard.TransitBoardPlugin;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

public class AnnouncementConfig {

    private boolean enabled;
    private double radius;
    private String namespace;
    private int delayBetweenSounds;
    private float volume;
    private float pitch;
    private boolean onArrival;
    private boolean onDeparture;
    private boolean onDelay;
    private int delayThresholdSeconds;
    private final Map<String, String> sounds = new HashMap<>();
    private String sequenceArrival;
    private String sequenceDeparture;
    private String sequenceDelay;

    public void load(TransitBoardPlugin plugin) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("announcements");
        if (sec == null) { enabled = false; return; }

        enabled               = sec.getBoolean("enabled", false);
        radius                = sec.getDouble("radius", 50);
        namespace             = sec.getString("namespace", "meinserver");
        delayBetweenSounds    = sec.getInt("delay-between-sounds", 8);
        volume                = (float) sec.getDouble("volume", 1.0);
        pitch                 = (float) sec.getDouble("pitch", 1.0);
        onArrival             = sec.getBoolean("on-arrival", true);
        onDeparture           = sec.getBoolean("on-departure", true);
        onDelay               = sec.getBoolean("on-delay", true);
        delayThresholdSeconds = sec.getInt("delay-threshold-seconds", 300);
        sequenceArrival       = sec.getString("sequence-arrival",
            "intro,linie,{prefix},{nummer},faehrt_ein,gleis,{gleis_nummer}");
        sequenceDeparture     = sec.getString("sequence-departure",
            "intro,linie,{prefix},{nummer},faehrt_ab,gleis,{gleis_nummer}");
        sequenceDelay         = sec.getString("sequence-delay",
            "intro,linie,{prefix},{nummer},verspaetung,circa,{delay_minuten},minuten");

        sounds.clear();
        ConfigurationSection ss = sec.getConfigurationSection("sounds");
        if (ss != null) {
            for (String key : ss.getKeys(false)) sounds.put(key, ss.getString(key, ""));
        }
    }

    public boolean isEnabled()             { return enabled; }
    public double getRadius()              { return radius; }
    public String getNamespace()           { return namespace; }
    public int getDelayBetweenSounds()     { return delayBetweenSounds; }
    public float getVolume()               { return volume; }
    public float getPitch()                { return pitch; }
    public boolean isOnArrival()           { return onArrival; }
    public boolean isOnDeparture()         { return onDeparture; }
    public boolean isOnDelay()             { return onDelay; }
    public int getDelayThresholdSeconds()  { return delayThresholdSeconds; }
    public String getSequenceArrival()     { return sequenceArrival; }
    public String getSequenceDeparture()   { return sequenceDeparture; }
    public String getSequenceDelay()       { return sequenceDelay; }
    public String getSound(String key)     { return sounds.getOrDefault(key, ""); }
}
