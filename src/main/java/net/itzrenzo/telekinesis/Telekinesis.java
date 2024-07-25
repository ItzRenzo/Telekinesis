package net.itzrenzo.telekinesis;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Telekinesis extends JavaPlugin {
    private FileConfiguration config;
    private FileConfiguration messages;
    private Map<UUID, Boolean> playerStates;

    @Override
    public void onEnable() {
        getLogger().info("Telekinesis has been enabled!");
        loadConfigurations();
        playerStates = new HashMap<>();
        getServer().getPluginManager().registerEvents(new TelekinesisListener(this), this);
        getCommand("telekinesis").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String messageplayer = ChatColor.translateAlternateColorCodes('&', messages.getString("player-only-command"));
        String messageusage = ChatColor.translateAlternateColorCodes('&', messages.getString("usage"));
        String messagereload = ChatColor.translateAlternateColorCodes('&', messages.getString("reload-success"));
        String messagenoperm = ChatColor.translateAlternateColorCodes('&', messages.getString("no-permission"));

        if (!(sender instanceof Player)) {
            sender.sendMessage(messageplayer);
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage(messageusage);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload") && player.hasPermission("telekinesis.reload")) {
            loadConfigurations();
            player.sendMessage(messagereload);
            return true;
        }

        if (!player.hasPermission("telekinesis.use")) {
            player.sendMessage(messagenoperm);
            return true;
        }

        String messageon = ChatColor.translateAlternateColorCodes('&', messages.getString("telekinesis-enabled"));
        String messageoff = ChatColor.translateAlternateColorCodes('&', messages.getString("telekinesis-disabled"));

        if (args[0].equalsIgnoreCase("on")) {
            playerStates.put(player.getUniqueId(), true);
            player.sendMessage(messageon);
        } else if (args[0].equalsIgnoreCase("off")) {
            playerStates.put(player.getUniqueId(), false);
            player.sendMessage(messageoff);
        } else {

            player.sendMessage(messageusage);
        }

        return true;
    }

    public void loadConfigurations() {
        saveDefaultConfig();
        reloadConfig();
        config = getConfig();

        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public boolean isTelekinesisEnabled(Player player) {
        return playerStates.getOrDefault(player.getUniqueId(), config.getBoolean("default-enabled"));
    }

    public FileConfiguration getMessages() {
        return messages;
    }

}
