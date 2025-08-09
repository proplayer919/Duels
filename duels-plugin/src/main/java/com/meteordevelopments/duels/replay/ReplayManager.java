package com.meteordevelopments.duels.replay;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.api.event.match.MatchEndEvent;
import com.meteordevelopments.duels.api.event.match.MatchStartEvent;
import com.meteordevelopments.duels.api.match.Match;
import com.meteordevelopments.duels.config.Config;
import com.meteordevelopments.duels.hook.hooks.AdvancedReplayHook;
import com.meteordevelopments.duels.util.Loadable;
import com.meteordevelopments.duels.util.Log;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class ReplayManager implements Loadable {

    private final DuelsPlugin plugin;
    private final Config config;
    private AdvancedReplayHook advancedReplayHook;
    
    // Maps to store replay data
    private final Map<String, String> activeReplays = new ConcurrentHashMap<>(); // matchId -> replayId
    private final Map<UUID, List<MatchReplayData>> playerReplays = new ConcurrentHashMap<>(); // playerUUID -> replays
    private final Map<String, MatchReplayData> replayData = new ConcurrentHashMap<>(); // replayId -> replay data
    
    // Configuration
    private boolean enabled = false;
    private int maxReplaysPerPlayer = 10;
    private int replayDuration = 600; // 10 minutes default
    
    public ReplayManager(final DuelsPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfiguration();
    }

    @Override
    public void handleLoad() {
        // Get AdvancedReplay hook first
        advancedReplayHook = plugin.getHookManager().getHook(AdvancedReplayHook.class);
        
        // Load configuration from the config file directly
        try {
            org.bukkit.configuration.file.FileConfiguration config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                new java.io.File(plugin.getDataFolder(), "config.yml"));
            
            enabled = config.getBoolean("replay.enabled", false);
            maxReplaysPerPlayer = config.getInt("replay.max-replays-per-player", 10);
            replayDuration = config.getInt("replay.duration", 600);
        } catch (Exception e) {
            // Fallback to defaults if config reading fails
            enabled = false;
            maxReplaysPerPlayer = 10;
            replayDuration = 600;
            Log.warn("Failed to load replay configuration, using defaults: " + e.getMessage());
        }
        
        if (!enabled) {
            Log.info("Replay system is disabled in configuration");
            return;
        }
        
        if (advancedReplayHook == null || !advancedReplayHook.isApiAvailable()) {
            Log.warn("AdvancedReplay not found or API unavailable. Replay features will be disabled.");
            enabled = false;
            return;
        }
        
        // Register event listener
        Bukkit.getPluginManager().registerEvents(new ReplayListener(), plugin);
        
        Log.info("Replay system enabled with AdvancedReplay integration");
    }

    @Override
    public void handleUnload() {
        // Stop all active recordings
        for (String replayId : activeReplays.values()) {
            if (advancedReplayHook != null && advancedReplayHook.isApiAvailable()) {
                advancedReplayHook.stopRecording(replayId, true);
            }
        }
        
        activeReplays.clear();
        playerReplays.clear();
        replayData.clear();
    }

    /**
     * Starts recording a match replay
     */
    public boolean startMatchRecording(Match match, Location centerLocation) {
        if (!isEnabled()) {
            return false;
        }
        
        try {
            String matchId = generateMatchId(match);
            Collection<UUID> playerUUIDs = new ArrayList<>();
            
            for (Player player : match.getPlayers()) {
                playerUUIDs.add(player.getUniqueId());
            }
            
            String replayId = advancedReplayHook.generateReplayId(matchId, playerUUIDs);
            
            if (advancedReplayHook.startRecording(replayId, centerLocation, replayDuration)) {
                activeReplays.put(matchId, replayId);
                
                // Create replay data
                MatchReplayData data = new MatchReplayData(
                    replayId,
                    matchId,
                    new ArrayList<>(playerUUIDs),
                    System.currentTimeMillis(),
                    match.getKit() != null ? match.getKit().getName() : "None",
                    match.getArena() != null ? match.getArena().getName() : "Unknown"
                );
                
                replayData.put(replayId, data);
                
                Log.info("Started replay recording for match " + matchId + " with replay ID: " + replayId);
                return true;
            }
        } catch (Exception e) {
            Log.error("Failed to start match recording: " + e.getMessage());
        }
        
        return false;
    }

    /**
     * Stops recording a match replay
     */
    public void stopMatchRecording(Match match, boolean save) {
        if (!isEnabled()) {
            return;
        }
        
        try {
            String matchId = generateMatchId(match);
            String replayId = activeReplays.remove(matchId);
            
            if (replayId != null && advancedReplayHook.stopRecording(replayId, save)) {
                if (save) {
                    // Add to player replay lists
                    MatchReplayData data = replayData.get(replayId);
                    if (data != null) {
                        data.setEndTime(System.currentTimeMillis());
                        
                        for (UUID playerId : data.getPlayerUUIDs()) {
                            List<MatchReplayData> playerReplayList = playerReplays.computeIfAbsent(playerId, k -> new ArrayList<>());
                            playerReplayList.add(data);
                            
                            // Limit replays per player
                            if (playerReplayList.size() > maxReplaysPerPlayer) {
                                playerReplayList.remove(0); // Remove oldest replay
                                // Could implement deletion of old replay files here if needed
                            }
                        }
                    }
                    
                    Log.info("Stopped and saved replay recording for match " + matchId);
                } else {
                    // Remove data if not saving
                    replayData.remove(replayId);
                    Log.info("Stopped replay recording for match " + matchId + " without saving");
                }
            }
        } catch (Exception e) {
            Log.error("Failed to stop match recording: " + e.getMessage());
        }
    }

    /**
     * Gets replays for a specific player
     */
    public List<MatchReplayData> getPlayerReplays(UUID playerId) {
        return playerReplays.getOrDefault(playerId, new ArrayList<>());
    }

    /**
     * Gets a specific replay by ID
     */
    public MatchReplayData getReplayData(String replayId) {
        return replayData.get(replayId);
    }

    /**
     * Check if replay system is enabled and working
     */
    public boolean isEnabled() {
        return enabled && advancedReplayHook != null && advancedReplayHook.isApiAvailable();
    }

    /**
     * Generates a unique match ID
     */
    private String generateMatchId(Match match) {
        // Generate a unique ID based on timestamp and match hash
        return "match_" + System.currentTimeMillis() + "_" + Math.abs(match.hashCode());
    }

    /**
     * Event listener for match events
     */
    private class ReplayListener implements Listener {

        @EventHandler(priority = EventPriority.MONITOR)
        public void onMatchStart(MatchStartEvent event) {
            if (!isEnabled()) {
                return;
            }
            
            Match match = event.getMatch();
            
            // Get center location from arena
            Location centerLocation = null;
            if (match.getArena() != null) {
                // Try to get center location from arena positions
                Location pos1 = match.getArena().getPosition(1);
                Location pos2 = match.getArena().getPosition(2);
                
                if (pos1 != null && pos2 != null) {
                    centerLocation = pos1.clone().add(pos2).multiply(0.5);
                } else if (pos1 != null) {
                    centerLocation = pos1.clone();
                } else if (pos2 != null) {
                    centerLocation = pos2.clone();
                }
            }
            
            // Fallback to first player location
            if (centerLocation == null && event.getPlayers().length > 0) {
                centerLocation = event.getPlayers()[0].getLocation();
            }
            
            if (centerLocation != null) {
                startMatchRecording(match, centerLocation);
            } else {
                Log.warn("Could not determine center location for match replay recording");
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onMatchEnd(MatchEndEvent event) {
            if (!isEnabled()) {
                return;
            }
            
            Match match = event.getMatch();
            
            // Always save the replay unless it was a plugin disable
            boolean save = event.getReason() != MatchEndEvent.Reason.PLUGIN_DISABLE;
            
            stopMatchRecording(match, save);
        }
    }
}
