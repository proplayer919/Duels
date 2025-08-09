package com.meteordevelopments.duels.gui.replays;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.config.Lang;
import com.meteordevelopments.duels.gui.BaseButton;
import com.meteordevelopments.duels.replay.MatchReplayData;
import com.meteordevelopments.duels.replay.ReplayManager;
import com.meteordevelopments.duels.util.Log;
import com.meteordevelopments.duels.util.inventory.ItemBuilder;
import com.meteordevelopments.duels.util.gui.SinglePageGui;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.*;

public class ReplayListGui extends SinglePageGui<DuelsPlugin> {

    private static final int REPLAYS_PER_PAGE = 28; // 4 rows of 7 items
    private final ReplayManager replayManager;
    private final Lang lang;
    
    private int page = 0;
    private List<MatchReplayData> replays;

    public ReplayListGui(DuelsPlugin plugin, Player player) {
        super(plugin, plugin.getLang().getMessage("GUI.replay.title", "page", 1), 6);
        this.replayManager = plugin.getReplayManager();
        this.lang = plugin.getLang();
        this.replays = replayManager.getPlayerReplays(player.getUniqueId());
        
        // Sort replays by start time (newest first)
        this.replays.sort((a, b) -> Long.compare(b.getStartTime(), a.getStartTime()));
        
        update();
    }

    private void update() {
        // Clear current inventory
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, null);
        }
        
        if (!replayManager.isEnabled()) {
            // Show disabled message
            set(22, new BaseButton(plugin, ItemBuilder.of(Material.BARRIER)
                    .name(lang.getMessage("GUI.replay.disabled.name"))
                    .lore(lang.getMessage("GUI.replay.disabled.lore"))
                    .build()) {
                @Override
                public void onClick(Player player) {
                    // Do nothing
                }
            });
            return;
        }
        
        if (replays.isEmpty()) {
            // Show no replays message
            set(22, new BaseButton(plugin, ItemBuilder.of(Material.GRAY_STAINED_GLASS_PANE)
                    .name(lang.getMessage("GUI.replay.no-replays.name"))
                    .lore(lang.getMessage("GUI.replay.no-replays.lore"))
                    .build()) {
                @Override
                public void onClick(Player player) {
                    // Do nothing
                }
            });
        } else {
            // Display replays for current page
            int startIndex = page * REPLAYS_PER_PAGE;
            int endIndex = Math.min(startIndex + REPLAYS_PER_PAGE, replays.size());
            
            for (int i = startIndex; i < endIndex; i++) {
                MatchReplayData replay = replays.get(i);
                int slot = 10 + (i - startIndex) % 7 + ((i - startIndex) / 7) * 9;
                
                set(slot, new ReplayButton(replay));
            }
            
            // Add pagination buttons
            addPaginationButtons();
        }
        
        // Close button
        set(49, new BaseButton(plugin, ItemBuilder.of(Material.RED_STAINED_GLASS_PANE)
                .name(lang.getMessage("GUI.replay.close.name"))
                .build()) {
            @Override
            public void onClick(Player player) {
                player.closeInventory();
            }
        });
    }

    private ItemStack createReplayItem(MatchReplayData replay, Player player) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm");
        Date startDate = new Date(replay.getStartTime());
        
        // Get opponent names
        List<String> opponentNames = new ArrayList<>();
        for (UUID uuid : replay.getPlayerUUIDs()) {
            // If player is null (called from constructor), show all participant names
            // Otherwise, filter out the viewing player
            if (player == null || !uuid.equals(player.getUniqueId())) {
                Player opponent = Bukkit.getPlayer(uuid);
                if (opponent != null) {
                    opponentNames.add(opponent.getName());
                } else {
                    // Try to get name from offline player
                    String name = Bukkit.getOfflinePlayer(uuid).getName();
                    opponentNames.add(name != null ? name : "Unknown");
                }
            }
        }
        
        String opponents = opponentNames.isEmpty() ? "Unknown" : String.join(", ", opponentNames);
        
        return ItemBuilder.of(Material.ENDER_EYE)
                .name(lang.getMessage("GUI.replay.item.name", 
                        "opponents", opponents,
                        "kit", replay.getKitName()))
                .lore(lang.getMessage("GUI.replay.item.lore",
                        "arena", replay.getArenaName(),
                        "duration", replay.getFormattedDuration(),
                        "date", dateFormat.format(startDate),
                        "status", replay.isComplete() ? "Completed" : "In Progress"))
                .build();
    }

    private void addPaginationButtons() {
        int totalPages = (int) Math.ceil((double) replays.size() / REPLAYS_PER_PAGE);
        
        if (page > 0) {
            // Previous page button
            set(45, new BaseButton(plugin, ItemBuilder.of(Material.ARROW)
                    .name(lang.getMessage("GUI.replay.previous.name"))
                    .lore(lang.getMessage("GUI.replay.previous.lore", "page", page))
                    .build()) {
                @Override
                public void onClick(Player player) {
                    page--;
                    update(player);
                }
            });
        }
        
        if (page < totalPages - 1) {
            // Next page button
            set(53, new BaseButton(plugin, ItemBuilder.of(Material.ARROW)
                    .name(lang.getMessage("GUI.replay.next.name"))
                    .lore(lang.getMessage("GUI.replay.next.lore", "page", page + 2))
                    .build()) {
                @Override
                public void onClick(Player player) {
                    page++;
                    update(player);
                }
            });
        }
        
        // Page info
        set(49, new BaseButton(plugin, ItemBuilder.of(Material.PAPER)
                .name(lang.getMessage("GUI.replay.page-info.name", "current", page + 1, "total", totalPages))
                .lore(lang.getMessage("GUI.replay.page-info.lore", "replays", replays.size()))
                .build()) {
            @Override
            public void onClick(Player player) {
                // Do nothing - just info
            }
        });
    }

    private class ReplayButton extends BaseButton {
        private final MatchReplayData replay;

        public ReplayButton(MatchReplayData replay) {
            super(ReplayListGui.this.plugin, createReplayItem(replay, null));
            this.replay = replay;
        }

        @Override
        public void onClick(Player player) {
            if (!replay.isComplete()) {
                player.sendMessage(lang.getMessage("GUI.replay.replay-in-progress"));
                return;
            }
            
            // Try to start replay playback
            if (replayManager.getAdvancedReplayHook().isApiAvailable()) {
                try {
                    // Close the GUI first
                    player.closeInventory();
                    
                    // Start replay playback - this would need to be implemented based on AdvancedReplay API
                    // For now, we'll send a command or use reflection to start the replay
                    Bukkit.dispatchCommand(player, "replay play " + replay.getReplayId());
                    player.sendMessage(lang.getMessage("GUI.replay.starting-replay"));
                    
                } catch (Exception e) {
                    player.sendMessage(lang.getMessage("GUI.replay.error-starting"));
                    Log.error("Failed to start replay: " + e.getMessage());
                }
            } else {
                player.sendMessage(lang.getMessage("GUI.replay.unavailable"));
            }
        }
        
        @Override
        public void update(Player player) {
            setDisplayed(createReplayItem(replay, player));
        }
    }
}
