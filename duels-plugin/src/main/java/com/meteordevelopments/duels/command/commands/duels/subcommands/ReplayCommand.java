package com.meteordevelopments.duels.command.commands.duels.subcommands;

import com.meteordevelopments.duels.DuelsPlugin;
import com.meteordevelopments.duels.Permissions;
import com.meteordevelopments.duels.command.BaseCommand;
import com.meteordevelopments.duels.gui.replays.ReplayListGui;
import com.meteordevelopments.duels.replay.ReplayManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class ReplayCommand extends BaseCommand {

    private final ReplayManager replayManager;

    public ReplayCommand(final DuelsPlugin plugin) {
        super(plugin, "replay", Permissions.REPLAY, null, 1, true, "replays");
        this.replayManager = plugin.getReplayManager();
    }

    @Override
    protected void execute(final CommandSender sender, final String label, final String[] args) {
        final Player player = (Player) sender;
        
        if (!replayManager.isEnabled()) {
            lang.sendMessage(player, "COMMAND.duels.replay.disabled");
            return;
        }
        
        // Open the replay list GUI
        new ReplayListGui(plugin, player).open(player);
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        return Collections.emptyList();
    }
}
