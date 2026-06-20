package com.runterya.invsee.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/invsee/client.json");

    public String preferredLanguage = "auto";

    public static ClientConfig load() {
        if (FILE.exists()) {
            try (FileReader reader = new FileReader(FILE)) {
                return GSON.fromJson(reader, ClientConfig.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        ClientConfig config = new ClientConfig();
        config.save();
        return config;
    }

    public void save() {
        FILE.getParentFile().mkdirs();
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(this, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
