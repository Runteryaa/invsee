package com.runterya.invsee;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class Config {
    private static final File LEGACY_CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "invsee.json");
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "invsee/config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    public static Config INSTANCE;

    public String language = "en_us";

    public static class ButtonConfig {
        public String type; // "status", "location", "ender_chest", "xp", "custom", "empty"
        public String item = null; // Used for "custom"
        public String name = null; // Used for "custom"
        public java.util.List<String> lore = null; // Used for "custom"
        public String command = null;

        public ButtonConfig() {}

        public ButtonConfig(String type, String command) {
            this.type = type;
            this.command = command;
        }

        public ButtonConfig(String type, String item, String name, java.util.List<String> lore, String command) {
            this.type = type;
            this.item = item;
            this.name = name;
            this.lore = lore;
            this.command = command;
        }
    }

    public java.util.List<ButtonConfig> top_row_buttons = java.util.Arrays.asList(
        new ButtonConfig("custom", "minecraft:golden_apple", " ", java.util.Arrays.asList(
            "§c{lang:health}: {health}/{maxhealth}",
            "§6{lang:food}: {food}/20",
            "{effects}",
            "{lastseen}"
        ), "#dummy"),
        new ButtonConfig("custom", "minecraft:paper", "§b{lang:location}: {x} {y} {z}", java.util.Arrays.asList(
            "§7{lang:dimension}: {dimension}"
        ), "#tp"),
        new ButtonConfig("custom", "minecraft:ender_chest", "§6{lang:open_ender_chest}", new java.util.ArrayList<>(), "#enderchest"),
        new ButtonConfig("custom", "minecraft:experience_bottle", "§e{lang:xp_level}: {xplevel}", new java.util.ArrayList<>(), "#xp")
    );

    public static Config load() {
        if (LEGACY_CONFIG_FILE.exists() && !CONFIG_FILE.exists()) {
            if (!CONFIG_FILE.getParentFile().exists()) {
                CONFIG_FILE.getParentFile().mkdirs();
            }
            LEGACY_CONFIG_FILE.renameTo(CONFIG_FILE);
        }

        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, Config.class);
                // Ensure buttons list is initialized if missing in config
                if (INSTANCE.top_row_buttons == null) {
                    INSTANCE.top_row_buttons = new Config().top_row_buttons;
                }
                return INSTANCE;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Config config = new Config();
        save(config);
        INSTANCE = config;
        return config;
    }

    public static void save(Config config) {
        if (!CONFIG_FILE.getParentFile().exists()) {
            CONFIG_FILE.getParentFile().mkdirs();
        }
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(config, writer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
