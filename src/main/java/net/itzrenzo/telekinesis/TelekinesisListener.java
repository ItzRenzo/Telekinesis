package net.itzrenzo.telekinesis;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;

import java.util.Collection;

public class TelekinesisListener implements Listener {
    private final Telekinesis plugin;

    public TelekinesisListener(Telekinesis plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (plugin.isTelekinesisEnabled(player)) {
            // Check if the block is a container
            if (event.getBlock().getState() instanceof Container) {
                Container container = (Container) event.getBlock().getState();
                Inventory inventory = container.getInventory();

                // Check if the container is not empty
                if (!inventory.isEmpty()) {
                    event.setCancelled(true);
                    sendContainerNotEmptyWarning(player);
                    return;
                }
            }

            // Handle regular block drops
            Collection<ItemStack> drops = event.getBlock().getDrops(player.getInventory().getItemInMainHand());
            handleDrops(player, drops, event.getBlock().getLocation());
            event.setDropItems(false);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player player = event.getEntity().getKiller();
        if (player != null && plugin.isTelekinesisEnabled(player)) {
            handleDrops(player, event.getDrops(), event.getEntity().getLocation());
            event.getDrops().clear();
        }
    }

    private void handleDrops(Player player, Collection<ItemStack> drops, Location location) {
        for (ItemStack item : drops) {
            if (item != null && item.getAmount() > 0) {
                if (player.getInventory().firstEmpty() == -1) {
                    sendInventoryFullWarning(player);
                    if (plugin.getConfig().getBoolean("drop-items", true)) {
                        location.getWorld().dropItemNaturally(location, item);
                    }
                } else {
                    player.getInventory().addItem(item);
                }
            }
        }
    }

    private void sendInventoryFullWarning(Player player) {
        String message = ChatColor.translateAlternateColorCodes('&', plugin.getMessages().getString("inventory-full"));
        sendWarning(player, message);
    }

    private void sendContainerNotEmptyWarning(Player player) {
        String message = ChatColor.translateAlternateColorCodes('&', plugin.getMessages().getString("container-not-empty"));
        sendWarning(player, message);
    }

    private void sendWarning(Player player, String message) {
        String method = plugin.getConfig().getString("warning-method", "CHAT").toUpperCase();

        switch (method) {
            case "ACTION_BAR":
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(message));
                break;
            case "TITLE":
                player.sendTitle("", message, 10, 70, 20);
                break;
            default:
                player.sendMessage(message);
        }
    }
}