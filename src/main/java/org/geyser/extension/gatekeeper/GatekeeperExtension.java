package org.geyser.extension.gatekeeper;

import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.event.connection.BedrockLoginEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPreReloadEvent;
import org.geysermc.geyser.api.extension.Extension;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.connection.Connection;

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

    @Override
    public void onEnable() {
        configPath = this.dataFolder().resolve("config.yml");
        whitelistPath = Paths.get("whitelist.json");
        loadConfig();
        loadWhitelist();
        logger().info("GatekeeperExtension enabled!");
    }

    @Subscribe
    public void onPostInitialize(GeyserPostInitializeEvent event) {
        // Optionally, watch the whitelist file for changes here with a thread or a file watcher.
    }

    @Subscribe
    public void onReload(GeyserPreReloadEvent event) {
        loadConfig();
        loadWhitelist();
        logger().info("GatekeeperExtension config and whitelist reloaded!");
    }

    @Subscribe
    public void onBedrockLogin(BedrockLoginEvent event) {
        Connection conn = event.connection();
        String username = conn.javaUsername();
        String deviceOS = event.deviceOs().name();
        logger().info("Player " + username + " is joining from device OS: " + deviceOS);

        if (vanillaWhitelist.contains(username)) {
            logger().info("Player " + username + " is whitelisted, skipping OS check.");
            return;
        }

        if (disallowedOS.contains(deviceOS)) {
            // Use /kick command (requires server to be running and command to be available)
            GeyserApi.api().server().dispatchCommand("kick " + username + " " + kickMessage);
            logger().info("Kicked player " + username + " for using device OS: " + deviceOS);
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
                // Each entry is an object with a 'name' field
                String json = new String(Files.readAllBytes(whitelistPath));
                // Simple and hacky parse to extract all "name" fields
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
