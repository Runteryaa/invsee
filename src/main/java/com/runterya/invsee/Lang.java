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
    private static final Map<String, Map<String, String>> translationsCache = new HashMap<>();
    private static final Gson GSON = new Gson();
    
    // Server's default language
    private static String defaultLanguage = "en_us";

    public static void setLanguage(String lang) {
        defaultLanguage = lang;
        loadLanguage(lang);
    }

    private static void loadLanguage(String lang) {
        if (translationsCache.containsKey(lang)) return;
        Map<String, String> langMap = new HashMap<>();

        File legacyLangDir = new File(FabricLoader.getInstance().getConfigDir().toFile(), "invsee_lang");
        File newLangDir = new File(FabricLoader.getInstance().getConfigDir().toFile(), "invsee/lang");
        
        if (legacyLangDir.exists() && legacyLangDir.isDirectory() && !newLangDir.exists()) {
            if (!newLangDir.getParentFile().exists()) {
                newLangDir.getParentFile().mkdirs();
            }
            legacyLangDir.renameTo(newLangDir);
        }

        if (!newLangDir.exists()) {
            newLangDir.mkdirs();
        }

        String[] defaultLangs = {"en_us", "tr_tr"};
        for (String dl : defaultLangs) {
            File defaultFile = new File(newLangDir, dl + ".json");
            if (!defaultFile.exists()) {
                try (InputStream is = Lang.class.getResourceAsStream("/assets/invsee/lang/" + dl + ".json")) {
                    if (is != null) {
                        java.nio.file.Files.copy(is, defaultFile.toPath());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        boolean loaded = false;
        File customLang = new File(newLangDir, lang + ".json");
        if (customLang.exists()) {
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(customLang), StandardCharsets.UTF_8)) {
                Type type = new TypeToken<Map<String, String>>(){}.getType();
                Map<String, String> map = GSON.fromJson(reader, type);
                if (map != null) {
                    langMap.putAll(map);
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
                            langMap.putAll(map);
                            loaded = true;
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (loaded) {
            translationsCache.put(lang, langMap);
        }
    }

    public static String getFor(String lang, String key, Object... args) {
        loadLanguage(lang);
        Map<String, String> map = translationsCache.get(lang);
        
        String text = null;
        if (map != null && map.containsKey(key)) {
            text = map.get(key);
        } else {
            // Fallback to default server language
            loadLanguage(defaultLanguage);
            Map<String, String> defaultMap = translationsCache.get(defaultLanguage);
            if (defaultMap != null && defaultMap.containsKey(key)) {
                text = defaultMap.get(key);
            } else {
                // Fallback to en_us if all else fails
                if (!defaultLanguage.equals("en_us") && !lang.equals("en_us")) {
                    loadLanguage("en_us");
                    Map<String, String> enMap = translationsCache.get("en_us");
                    if (enMap != null && enMap.containsKey(key)) {
                        text = enMap.get(key);
                    }
                }
            }
        }

        if (text == null) return key;

        if (args.length > 0) {
            try {
                return String.format(java.util.Locale.US, text, args);
            } catch (Exception e) {
                return text;
            }
        }
        return text;
    }

    public static String get(String key, Object... args) {
        return getFor(defaultLanguage, key, args);
    }
}
