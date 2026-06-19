package com.runterya.invsee;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class Lang {
    private static final Map<String, String> currentTranslations = new HashMap<>();
    private static final Gson GSON = new Gson();

    public static void setLanguage(String lang) {
        currentTranslations.clear();
        boolean loaded = false;

        File customLang = new File(FabricLoader.getInstance().getConfigDir().toFile(), "invsee_lang/" + lang + ".json");
        if (customLang.exists()) {
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(customLang), StandardCharsets.UTF_8)) {
                Type type = new TypeToken<Map<String, String>>(){}.getType();
                Map<String, String> map = GSON.fromJson(reader, type);
                if (map != null) {
                    currentTranslations.putAll(map);
                    loaded = true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (!loaded) {
            try (InputStream is = Lang.class.getResourceAsStream("/assets/invsee/lang/" + lang + ".json")) {
                if (is != null) {
                    try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                        Type type = new TypeToken<Map<String, String>>(){}.getType();
                        Map<String, String> map = GSON.fromJson(reader, type);
                        if (map != null) {
                            currentTranslations.putAll(map);
                            loaded = true;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (!loaded && !lang.equals("en_us")) {
            setLanguage("en_us");
        }
    }

    public static String get(String key, Object... args) {
        String text = currentTranslations.getOrDefault(key, key);
        if (args.length > 0) {
            try {
                return String.format(java.util.Locale.US, text, args);
            } catch (Exception e) {
                return text;
            }
        }
        return text;
    }
}
