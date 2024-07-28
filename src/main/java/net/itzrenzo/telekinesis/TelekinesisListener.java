package net.itzrenzo.telekinesis;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

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

            // Handle experience drops
            if (plugin.isExperiencePickupEnabled()) {
                int expToDrop = event.getExpToDrop();
                if (expToDrop > 0) {
                    player.giveExp(expToDrop);
                    if (plugin.isExperiencePickupMessageEnabled()) {
                        sendExperiencePickupMessage(player, expToDrop);
                    }
                    event.setExpToDrop(0);
                }
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player player = event.getEntity().getKiller();
        if (player != null && plugin.isTelekinesisEnabled(player)) {
            handleDrops(player, event.getDrops(), event.getEntity().getLocation());
            event.getDrops().clear();

            // Handle experience drops
            if (plugin.isExperiencePickupEnabled()) {
                int expToDrop = event.getDroppedExp();
                if (expToDrop > 0) {
                    player.giveExp(expToDrop);
                    if (plugin.isExperiencePickupMessageEnabled()) {
                        sendExperiencePickupMessage(player, expToDrop);
                    }
                    event.setDroppedExp(0);
                }
            }
        }
    }

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (plugin.isTelekinesisEnabled(player)) {
                ItemStack item = event.getItem().getItemStack();
                if (!plugin.isItemBlacklisted(player, item.getType().name().toLowerCase())) {
                    event.setCancelled(true);
                    if (player.getInventory().firstEmpty() != -1) {
                        player.getInventory().addItem(item);
                        event.getItem().remove();
                    } else {
                        sendInventoryFullWarning(player);
                    }
                }
            }
        }
    }

    private void handleDrops(Player player, Collection<ItemStack> drops, Location location) {
        for (ItemStack item : drops) {
            if (item != null && item.getAmount() > 0) {
                if (!plugin.isItemBlacklisted(player, item.getType().name().toLowerCase())) {
                    if (player.getInventory().firstEmpty() == -1) {
                        sendInventoryFullWarning(player);
                        if (plugin.getConfig().getBoolean("drop-items", true)) {
                            location.getWorld().dropItemNaturally(location, item);
                        }
                    } else {
                        player.getInventory().addItem(item);
                    }
                } else {
                    location.getWorld().dropItemNaturally(location, item);
                }
            }
        }
    }

    private void sendInventoryFullWarning(Player player) {
        String message = plugin.getColoredMessage("inventory-full");
        sendWarning(player, message);
    }

    private void sendContainerNotEmptyWarning(Player player) {
        String message = plugin.getColoredMessage("container-not-empty");
        sendWarning(player, message);
    }

    private void sendExperiencePickupMessage(Player player, int amount) {
        String message = plugin.getColoredMessage("experience-pickup")
                .replace("{amount}", String.valueOf(amount));
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