package com.meteordevelopments.duels.replay;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
public class MatchReplayData {
    private final String replayId;
    private final String matchId;
    private final List<UUID> playerUUIDs;
    private final long startTime;
    private final String kitName;
    private final String arenaName;
    private long endTime;
    
    public MatchReplayData(String replayId, String matchId, List<UUID> playerUUIDs, 
                          long startTime, String kitName, String arenaName) {
        this.replayId = replayId;
        this.matchId = matchId;
        this.playerUUIDs = playerUUIDs;
        this.startTime = startTime;
        this.kitName = kitName;
        this.arenaName = arenaName;
        this.endTime = 0;
    }
    
    /**
     * Gets the duration of the match in milliseconds
     */
    public long getDuration() {
        return endTime > 0 ? endTime - startTime : System.currentTimeMillis() - startTime;
    }
    
    /**
     * Gets the duration of the match in seconds
     */
    public long getDurationSeconds() {
        return getDuration() / 1000;
    }
    
    /**
     * Checks if the replay is complete (has ended)
     */
    public boolean isComplete() {
        return endTime > 0;
    }
    
    /**
     * Gets formatted duration as MM:SS
     */
    public String getFormattedDuration() {
        long seconds = getDurationSeconds();
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
