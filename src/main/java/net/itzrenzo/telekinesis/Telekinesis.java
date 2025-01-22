package net.itzrenzo.telekinesis;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.*;

public class Telekinesis extends JavaPlugin {
    private FileConfiguration config;
    private FileConfiguration messages;
    private Map<UUID, Boolean> playerStates;
    private Map<UUID, Set<String>> playerBlacklists;
    private Set<String> globalBlacklist;
    private UpdateChecker updateChecker;

    @Override
    public void onEnable() {
        getLogger().info("Telekinesis has been enabled!");
        playerStates = new HashMap<>();
        playerBlacklists = new HashMap<>();
        loadConfigurations();
        getServer().getPluginManager().registerEvents(new TelekinesisListener(this), this);
        getCommand("telekinesis").setExecutor(this);
        getCommand("telekinesis").setTabCompleter(new TelekinesisTabCompleter());


        initUpdateChecker();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(getColoredMessage("player-only-command"));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            player.sendMessage(getColoredMessage("usage"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                if (player.hasPermission("telekinesis.reload")) {
                    loadConfigurations();
                    player.sendMessage(getColoredMessage("reload-success"));
                } else {
                    player.sendMessage(getColoredMessage("no-permission"));
                }
                break;
            case "on":
                if (player.hasPermission("telekinesis.use")) {
                    playerStates.put(player.getUniqueId(), true);
                    player.sendMessage(getColoredMessage("telekinesis-enabled"));
                } else {
                    player.sendMessage(getColoredMessage("no-permission"));
                }
                break;
            case "off":
                if (player.hasPermission("telekinesis.use")) {
                    playerStates.put(player.getUniqueId(), false);
                    player.sendMessage(getColoredMessage("telekinesis-disabled"));
                } else {
                    player.sendMessage(getColoredMessage("no-permission"));
                }
                break;
            case "blacklist":
                if (player.hasPermission("telekinesis.blacklist")) {
                    handleBlacklistCommand(player, args);
                } else {
                    player.sendMessage(getColoredMessage("no-permission"));
                }
                break;
            default:
                player.sendMessage(getColoredMessage("usage"));
        }

        return true;
    }

    private void initUpdateChecker() {
        int resourceId = 118038;
        Duration checkInterval = Duration.ofHours(24);
        updateChecker = new UpdateChecker(this, resourceId, checkInterval);

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, updateChecker::checkForUpdates, 0L, 20L * 60 * 60 * 24); // Run every 24 hours
    }

    private void handleBlacklistCommand(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(getColoredMessage("blacklist-usage"));
            return;
        }

        String subCommand = args[1].toLowerCase();

        if (subCommand.equals("list")) {
            if (player.hasPermission("telekinesis.blacklist.list")) {
                listBlacklistedItems(player);
            } else {
                player.sendMessage(getColoredMessage("no-permission"));
            }
            return;
        }

        String itemsubCommand = args[2].toLowerCase();
        String item = itemsubCommand;
        Set<String> blacklist = playerBlacklists.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>());

        if (subCommand.equals("add")) {
            if (player.hasPermission("telekinesis.blacklist")) {
                blacklist.add(item);
                player.sendMessage(getColoredMessage("blacklist-added").replace("{item}", item));
            } else {
                player.sendMessage(getColoredMessage("no-permission"));
            }
            return;
        }

        if (subCommand.equals("remove")) {
            if (player.hasPermission("telekinesis.blacklist")) {
                blacklist.remove(item);
                player.sendMessage(getColoredMessage("blacklist-removed").replace("{item}", item));
            } else {
                player.sendMessage(getColoredMessage("no-permission"));
            }
            return;
        }
        savePlayerBlacklists();
    }

    private void listBlacklistedItems(Player player) {
        Set<String> blacklist = playerBlacklists.getOrDefault(player.getUniqueId(), new HashSet<>());
        if (blacklist.isEmpty()) {
            player.sendMessage(getColoredMessage("blacklist-empty"));
        } else {
            player.sendMessage(getColoredMessage("blacklist-header"));
            for (String item : blacklist) {
                player.sendMessage(getColoredMessage("blacklist-item").replace("{item}", item));
            }
        }
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

        globalBlacklist = new HashSet<>(config.getStringList("global-blacklist"));

        loadPlayerBlacklists();
    }

    private void loadPlayerBlacklists() {
        File blacklistFile = new File(getDataFolder(), "player_blacklists.yml");
        if (blacklistFile.exists()) {
            YamlConfiguration blacklistConfig = YamlConfiguration.loadConfiguration(blacklistFile);
            for (String uuidString : blacklistConfig.getKeys(false)) {
                UUID uuid = UUID.fromString(uuidString);
                List<String> blacklistedItems = blacklistConfig.getStringList(uuidString);
                playerBlacklists.put(uuid, new HashSet<>(blacklistedItems));
            }
        }
    }

    private void savePlayerBlacklists() {
        File blacklistFile = new File(getDataFolder(), "player_blacklists.yml");
        YamlConfiguration blacklistConfig = new YamlConfiguration();
        for (Map.Entry<UUID, Set<String>> entry : playerBlacklists.entrySet()) {
            blacklistConfig.set(entry.getKey().toString(), new ArrayList<>(entry.getValue()));
        }
        try {
            blacklistConfig.save(blacklistFile);
        } catch (IOException e) {
            getLogger().warning("Failed to save player blacklists: " + e.getMessage());
        }
    }

    public boolean isMythicMobsPickupEnabled() {
        return config.getBoolean("enable-mythicmobs-pickup", true);
    }

    public boolean isTelekinesisEnabled(Player player) {
        return player.getGameMode() != GameMode.CREATIVE &&
                playerStates.getOrDefault(player.getUniqueId(), config.getBoolean("default-enabled"));
    }

    public boolean isItemBlacklisted(Player player, String item) {
        return globalBlacklist.contains(item) ||
                playerBlacklists.getOrDefault(player.getUniqueId(), Collections.emptySet()).contains(item);
    }

    public FileConfiguration getMessages() {
        return messages;
    }

    public String getColoredMessage(String key) {
        return ChatColor.translateAlternateColorCodes('&', messages.getString(key, "Missing message: " + key));
    }

    public boolean isExperiencePickupEnabled() {
        return config.getBoolean("enable-experience-pickup", false);
    }

    public boolean isExperiencePickupMessageEnabled() {
        return config.getBoolean("enable-experience-pickup-message", true);
    }
}