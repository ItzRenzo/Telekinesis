package net.itzrenzo.telekinesis;

import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.logging.Level;

public class UpdateChecker {
    private final Telekinesis plugin;
    private final int resourceId;
    private final Duration checkInterval;
    private Instant lastCheckTime;

    public UpdateChecker(Telekinesis plugin, int resourceId, Duration checkInterval) {
        this.plugin = plugin;
        this.resourceId = resourceId;
        this.checkInterval = checkInterval;
        this.lastCheckTime = Instant.MIN;
    }

    public void checkForUpdates() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Instant currentTime = Instant.now();
            if (Duration.between(lastCheckTime, currentTime).compareTo(checkInterval) < 0) {
                return; // Skip update check if the interval hasn't elapsed yet
            }

            try {
                URL url = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + resourceId);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                String latestVersion = new BufferedReader(new InputStreamReader(connection.getInputStream())).readLine();

                String currentVersion = plugin.getDescription().getVersion();
                if (latestVersion != null && !latestVersion.equalsIgnoreCase(currentVersion)) {
                    plugin.getLogger().info("A new update is available! Version " + latestVersion + " can be downloaded from the SpigotMC website.");
                    // Add code here to notify server administrators or broadcast a message to players
                }

                lastCheckTime = currentTime; // Update the last check time
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to check for updates: " + e.getMessage(), e);
            }
        });
    }
}