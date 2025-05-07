package org.geyser.extension.gatekeeper;

import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.event.bedrock.SessionLoginEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPreReloadEvent;
import org.geysermc.geyser.api.extension.Extension;

import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

import java.nio.file.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

import org.yaml.snakeyaml.Yaml;

public class GatekeeperExtension implements Extension {
    private Set<String> disallowedOS = new HashSet<>(Arrays.asList("UWP", "UNKNOWN"));
    private String kickMessage = "You are not allowed to join from this device!";
    private Set<String> vanillaWhitelist = new HashSet<>();
    private Path configPath;
    private Path whitelistPath;

    public void onEnable() {
        configPath = this.dataFolder().resolve("config.yml");
        whitelistPath = Paths.get("whitelist.json");
        loadConfig();
        loadWhitelist();
        logger().info("GatekeeperExtension enabled!");
    }

    @Subscribe
    public void onPostInitialize(GeyserPostInitializeEvent event) {}

    @Subscribe
    public void onReload(GeyserPreReloadEvent event) {
        loadConfig();
        loadWhitelist();
        logger().info("GatekeeperExtension config and whitelist reloaded!");
    }

    @Subscribe
    public void onBedrockSessionLogin(SessionLoginEvent event) {
        String username = event.connection().javaUsername();
        String xuid = event.connection().xuid();

        FloodgatePlayer player = FloodgateApi.getInstance().getPlayerByXuid(xuid);

        String deviceOs = "UNKNOWN";
        if (player != null) {
            deviceOs = player.getDeviceOs().toString();
        } else {
            logger().info("Floodgate player not found for " + username + ". Device OS set to UNKNOWN.");
        }

        logger().info("Player " + username + " is joining from device OS: " + deviceOs);

        if (vanillaWhitelist.contains(username)) {
            logger().info("Player " + username + " is whitelisted, skipping OS check.");
            return;
        }

        if (disallowedOS.contains(deviceOs.toUpperCase())) {
            event.setCancelled(true);
            event.setKickReason(kickMessage);
            logger().info("Kicked player " + username + " for using device OS: " + deviceOs);
        }
    }

    private void loadConfig() {
        if (!Files.exists(configPath)) {
            // Write default config
            try {
                Files.createDirectories(configPath.getParent());
                Files.write(configPath, Arrays.asList(
                        "disallowed_os:",
                        "  - UWP",
                        "  - UNKNOWN",
                        "kick_message: \"You are not allowed to join from this device!\""
                ), StandardCharsets.UTF_8);
            } catch (IOException e) {
                logger().error("Failed to create default config.yml", e);
            }
        }
        // Load config
        try (InputStream in = Files.newInputStream(configPath)) {
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(in);
            List<String> osList = (List<String>) config.get("disallowed_os");
            if (osList != null) {
                disallowedOS = new HashSet<>(osList);
            }
            String msg = (String) config.get("kick_message");
            if (msg != null) {
                kickMessage = msg;
            }
        } catch (IOException e) {
            logger().error("Failed to load config.yml", e);
        }
    }

    private void loadWhitelist() {
        vanillaWhitelist.clear();
        if (Files.exists(whitelistPath)) {
            try (Reader reader = Files.newBufferedReader(whitelistPath, StandardCharsets.UTF_8)) {
                String json = new String(Files.readAllBytes(whitelistPath));
                int idx = 0;
                while ((idx = json.indexOf("\"name\"", idx)) != -1) {
                    int start = json.indexOf(":", idx) + 1;
                    int quote1 = json.indexOf("\"", start) + 1;
                    int quote2 = json.indexOf("\"", quote1);
                    if (quote1 > 0 && quote2 > quote1) {
                        String name = json.substring(quote1, quote2);
                        vanillaWhitelist.add(name);
                    }
                    idx = quote2;
                }
            } catch (IOException e) {
                logger().error("Failed to read whitelist.json", e);
            }
        }
    }
}
