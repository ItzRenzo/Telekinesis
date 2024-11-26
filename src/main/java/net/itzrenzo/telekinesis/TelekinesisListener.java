package net.itzrenzo.telekinesis;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class TelekinesisListener implements Listener {
    private final Telekinesis plugin;
    private final List<Material> shulkerBoxMaterials;

    public TelekinesisListener(Telekinesis plugin) {
        this.plugin = plugin;
        this.shulkerBoxMaterials = Arrays.asList(
                Material.SHULKER_BOX,
                Material.WHITE_SHULKER_BOX,
                Material.ORANGE_SHULKER_BOX,
                Material.MAGENTA_SHULKER_BOX,
                Material.LIGHT_BLUE_SHULKER_BOX,
                Material.YELLOW_SHULKER_BOX,
                Material.LIME_SHULKER_BOX,
                Material.PINK_SHULKER_BOX,
                Material.GRAY_SHULKER_BOX,
                Material.LIGHT_GRAY_SHULKER_BOX,
                Material.CYAN_SHULKER_BOX,
                Material.PURPLE_SHULKER_BOX,
                Material.BLUE_SHULKER_BOX,
                Material.BROWN_SHULKER_BOX,
                Material.GREEN_SHULKER_BOX,
                Material.RED_SHULKER_BOX,
                Material.BLACK_SHULKER_BOX
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (plugin.isTelekinesisEnabled(player) && canBreakBlock(player, event.getBlock().getLocation())) {
            Block block = event.getBlock();

            // Check if the block is a container, but not a Shulker Box
            if (block.getState() instanceof Container && !isShulkerBox(block)) {
                Container container = (Container) block.getState();
                Inventory inventory = container.getInventory();

                // Check if the container is not empty
                if (!inventory.isEmpty()) {
                    event.setCancelled(true);
                    sendContainerNotEmptyWarning(player);
                    return;
                }
            }

            // Special handling for beds
            if (block.getBlockData() instanceof Bed) {
                event.setCancelled(true);
                handleBedBreak(player, block);
            } else {
                // Handle regular block drops (including Shulker Boxes)
                Collection<ItemStack> drops = block.getDrops(player.getInventory().getItemInMainHand());
                handleDrops(player, drops, block.getLocation());
                event.setDropItems(false);
            }

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

    private boolean canBreakBlock(Player player, Location location) {
        if (isWorldGuardAvailable()) {
            try {
                RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
                RegionQuery query = container.createQuery();
                com.sk89q.worldedit.util.Location loc = BukkitAdapter.adapt(location);
                return query.testBuild(loc, WorldGuardPlugin.inst().wrapPlayer(player), Flags.BLOCK_BREAK);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to check WorldGuard permissions: " + e.getMessage());
            }
        }
        return true;
    }


    private void handleBedBreak(Player player, Block block) {
        Bed bed = (Bed) block.getBlockData();
        Block bedHead;
        Block bedFoot;

        if (bed.getPart() == Bed.Part.HEAD) {
            bedHead = block;
            bedFoot = block.getRelative(bed.getFacing().getOppositeFace());
        } else {
            bedFoot = block;
            bedHead = block.getRelative(bed.getFacing());
        }

        // Create the bed item using the head part's material
        Material bedMaterial = bedHead.getType();
        ItemStack bedItem = new ItemStack(bedMaterial);

        // Add the bed directly to the player's inventory or drop it if inventory is full
        if (player.getInventory().addItem(bedItem).isEmpty()) {
            // Item was added successfully
        } else {
            // Inventory is full, drop the item
            player.getWorld().dropItemNaturally(player.getLocation(), bedItem);
            sendInventoryFullWarning(player);
        }

        // Remove both parts of the bed
        bedHead.setType(Material.AIR);
        bedFoot.setType(Material.AIR);
    }

    private boolean isWorldGuardAvailable() {
        return Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
    }


    private boolean isShulkerBox(Block block) {
        return shulkerBoxMaterials.contains(block.getType());
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