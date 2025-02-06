package net.itzrenzo.telekinesis;

import io.lumine.mythic.bukkit.events.MythicMobDeathEvent;
import io.lumine.mythic.bukkit.MythicBukkit;
import io.lumine.mythic.core.mobs.ActiveMob;
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
import org.bukkit.block.BlockFace;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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
        if (!plugin.isTelekinesisEnabled(player) || !canBreakBlock(player, event.getBlock().getLocation())) {
            return;
        }
        Block block = event.getBlock();
        Material type = block.getType();

        // Handle plants first
        if (type == Material.KELP || type == Material.KELP_PLANT || type == Material.SUGAR_CANE || type == Material.CACTUS || type == Material.BAMBOO) {
            handlePlantBreak(player, block, event);
            return;
        }

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

    private void handlePlantBreak(Player player, Block block, BlockBreakEvent event) {
        Material type = block.getType();
        if (type == Material.KELP || type == Material.KELP_PLANT || type == Material.BAMBOO || type == Material.CACTUS || type == Material.SUGAR_CANE) {
            List<Block> blocksToBreak = new ArrayList<>();
            Block current = block;

            // Only break blocks ABOVE the broken one
            do {
                blocksToBreak.add(current);
                current = current.getRelative(BlockFace.UP);
            } while (isSameVerticalPlant(current, type));

            List<ItemStack> totalDrops = new ArrayList<>();
            for (Block b : blocksToBreak) {
                totalDrops.addAll(b.getDrops(player.getInventory().getItemInMainHand()));
                b.setType((b.getType() == Material.KELP || b.getType() == Material.KELP_PLANT) ? Material.WATER : Material.AIR);
            }

            handleDrops(player, totalDrops, block.getLocation());
            event.setCancelled(true);
        }
    }

    private boolean isSameVerticalPlant(Block block, Material original) {
        Material check = block.getType();
        if (original == Material.KELP || original == Material.KELP_PLANT) {
            return check == Material.KELP || check == Material.KELP_PLANT;
        }
        return check == original;
    }

    private boolean isValidGround(Material plantType, Material groundType) {
        if (plantType == Material.SUGAR_CANE) {
            return groundType == Material.GRASS_BLOCK || groundType == Material.DIRT
                    || groundType == Material.SAND || groundType == Material.RED_SAND;
        } else if (plantType == Material.BAMBOO) {
            return groundType == Material.GRASS_BLOCK || groundType == Material.DIRT
                    || groundType == Material.SAND || groundType == Material.BAMBOO_SAPLING;
        }
        return true;
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
        if (player == null || !plugin.isTelekinesisEnabled(player)) return;

        // Check if MythicMobs is installed before accessing its classes
        boolean isMythicMob = false;
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") != null) {
            isMythicMob = MythicBukkit.inst().getMobManager().isMythicMob(event.getEntity());
        }

        // Skip vanilla drops if PreventOtherDrops is active (handled in MythicMobDeathEvent)
        if (isMythicMob) return;

        // Handle vanilla drops for non-MythicMobs entities
        handleDrops(player, event.getDrops(), event.getEntity().getLocation());
        event.getDrops().clear();

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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMythicMobDeath(MythicMobDeathEvent event) {
        // Skip if MythicMobs isn't installed
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) return;

        Player player = (Player) event.getKiller();
        if (player == null || !plugin.isTelekinesisEnabled(player) || !plugin.isMythicMobsPickupEnabled()) {
            return;
        }

        // Check if PreventOtherDrops is enabled for this mob
        boolean preventOtherDrops = event.getMobType().getConfig().getBoolean("Options.PreventOtherDrops", false);

        // Get MythicMobs drops (custom drops)
        List<ItemStack> mythicDrops = new ArrayList<>(event.getDrops());
        handleDrops(player, mythicDrops, event.getEntity().getLocation());
        event.getDrops().clear();

        // If PreventOtherDrops is enabled, also clear vanilla drops
        if (preventOtherDrops) {
            event.getEntity().getWorld().getLivingEntities().remove(event.getEntity());
            event.getEntity().remove();
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