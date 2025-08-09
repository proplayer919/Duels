package com.meteordevelopments.duels.hook.hooks;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.util.Log;
import com.meteordevelopments.duels.util.hook.PluginHook;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.UUID;

@Getter
public class AdvancedReplayHook extends PluginHook<DuelsPlugin> {

    public static final String NAME = "AdvancedReplay";
    
    private Object replayAPI;
    private Method startRecordingMethod;
    private Method stopRecordingMethod;
    private Method getReplayMethod;
    private Method createReplayMethod;
    private Method getReplayIdMethod;
    private Class<?> replayClass;
    private boolean apiAvailable = false;

    public AdvancedReplayHook(final DuelsPlugin plugin) {
        super(plugin, NAME);
        
        try {
            // Try to get the AdvancedReplay API class
            Class<?> apiClass = Class.forName("me.jumper251.replay.ReplayAPI");
            
            // Get the singleton instance
            Method getInstanceMethod = apiClass.getMethod("getInstance");
            replayAPI = getInstanceMethod.invoke(null);
            
            // Get the Replay class
            replayClass = Class.forName("me.jumper251.replay.replaysystem.Replay");
            
            // Get necessary methods
            startRecordingMethod = apiClass.getMethod("recordReplay", String.class, Location.class, Integer.class);
            stopRecordingMethod = apiClass.getMethod("stopRecording", String.class, Boolean.class);
            getReplayMethod = apiClass.getMethod("getReplay", String.class);
            getReplayIdMethod = replayClass.getMethod("getId");
            
            apiAvailable = true;
            Log.info("Successfully hooked into AdvancedReplay API");
            
        } catch (Exception e) {
            Log.warn("Failed to hook into AdvancedReplay API: " + e.getMessage());
            apiAvailable = false;
        }
    }

    /**
     * Check if the AdvancedReplay plugin is enabled and available
     */
    public boolean isEnabled() {
        Plugin plugin = getPlugin();
        return plugin != null && plugin.isEnabled();
    }

    /**
     * Starts recording a replay for a match
     * @param replayId Unique identifier for the replay
     * @param location Location to center the recording around
     * @param duration Maximum duration in seconds (0 for unlimited)
     * @return true if recording started successfully
     */
    public boolean startRecording(String replayId, Location location, int duration) {
        if (!isEnabled() || !apiAvailable) {
            return false;
        }
        
        try {
            Object replay = startRecordingMethod.invoke(replayAPI, replayId, location, duration);
            return replay != null;
        } catch (Exception e) {
            Log.warn("Failed to start replay recording: " + e.getMessage());
            return false;
        }
    }

    /**
     * Stops recording a replay
     * @param replayId The replay identifier
     * @param save Whether to save the replay
     * @return true if stopped successfully
     */
    public boolean stopRecording(String replayId, boolean save) {
        if (!isEnabled() || !apiAvailable) {
            return false;
        }
        
        try {
            stopRecordingMethod.invoke(replayAPI, replayId, save);
            return true;
        } catch (Exception e) {
            Log.warn("Failed to stop replay recording: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets a replay by its ID
     * @param replayId The replay identifier
     * @return The replay object or null if not found
     */
    public Object getReplay(String replayId) {
        if (!isEnabled() || !apiAvailable) {
            return null;
        }
        
        try {
            return getReplayMethod.invoke(replayAPI, replayId);
        } catch (Exception e) {
            Log.warn("Failed to get replay: " + e.getMessage());
            return null;
        }
    }

    /**
     * Gets the ID of a replay object
     * @param replay The replay object
     * @return The replay ID or null if failed
     */
    public String getReplayId(Object replay) {
        if (!isEnabled() || !apiAvailable || replay == null) {
            return null;
        }
        
        try {
            Object result = getReplayIdMethod.invoke(replay);
            return result != null ? result.toString() : null;
        } catch (Exception e) {
            Log.warn("Failed to get replay ID: " + e.getMessage());
            return null;
        }
    }

    /**
     * Generates a unique replay ID for a match
     * @param matchId The match identifier
     * @param playerUUIDs The UUIDs of players in the match
     * @return A unique replay ID
     */
    public String generateReplayId(String matchId, Collection<UUID> playerUUIDs) {
        StringBuilder builder = new StringBuilder("duel_");
        builder.append(matchId);
        builder.append("_");
        builder.append(System.currentTimeMillis());
        return builder.toString();
    }

    /**
     * Check if the API is available and ready to use
     * @return true if API is available
     */
    public boolean isApiAvailable() {
        return isEnabled() && apiAvailable;
    }
}
