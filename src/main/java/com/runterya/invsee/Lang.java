package com.runterya.invsee;

import java.util.HashMap;
import java.util.Map;

public class Lang {
    private static final Map<String, Map<String, String>> translations = new HashMap<>();
    private static String currentLang = "en";

    static {
        Map<String, String> en = new HashMap<>();
        en.put("health", "Health");
        en.put("food", "Food");
        en.put("effects", "Effects");
        en.put("effects_none", "None");
        en.put("last_seen", "Last Seen");
        en.put("days_ago", "%d days ago");
        en.put("hours_ago", "%d hours ago");
        en.put("minutes_ago", "%d minutes ago");
        en.put("just_now", "just now");
        en.put("click_teleport", "Click here to teleport!");
        en.put("ender_chest", "%s's Ender Chest");
        en.put("offline_inv", "%s's Offline Inv");
        en.put("no_player_data", "No player data found!");
        en.put("no_other_players", "No other players found!");
        en.put("player_list", "Player List");
        en.put("cannot_self", "You cannot invsee yourself!");
        en.put("stolen_xp", "§aStolen %d XP!");
        en.put("player_inventory", "%s's Inventory");
        en.put("player_not_found", "Player not found or has no data!");
        en.put("location", "Location: %s");
        en.put("dimension", "Dimension: %s");
        en.put("xp_level", "XP: %d lvl");
        en.put("open_ender_chest", "Open Ender Chest");
        en.put("failed_load_data", "Failed to load player data!");
        en.put("prev_page", "Previous Page");
        en.put("next_page", "Next Page");
        en.put("online_players", "Online Players");
        en.put("offline_players", "Offline Players");
        en.put("page_info", "Page %d / %d");
        en.put("click_view_offline", "Click to view Offline Players");
        en.put("click_view_online", "Click to view Online Players");
        translations.put("en", en);

        Map<String, String> tr = new HashMap<>();
        tr.put("health", "Can");
        tr.put("food", "Açlık");
        tr.put("effects", "Efektler");
        tr.put("effects_none", "Yok");
        tr.put("last_seen", "Son Görülme");
        tr.put("days_ago", "%d gün önce");
        tr.put("hours_ago", "%d saat önce");
        tr.put("minutes_ago", "%d dakika önce");
        tr.put("just_now", "az önce");
        tr.put("click_teleport", "Işınlanmak için tıklayın!");
        tr.put("ender_chest", "%s'in Ender Sandığı");
        tr.put("offline_inv", "%s'in Çevrimdışı Env.");
        tr.put("no_player_data", "Oyuncu verisi bulunamadı!");
        tr.put("no_other_players", "Başka oyuncu bulunamadı!");
        tr.put("player_list", "Oyuncu Listesi");
        tr.put("cannot_self", "Kendi envanterine bakamazsın!");
        tr.put("stolen_xp", "§a%d XP çalındı!");
        tr.put("player_inventory", "%s'in Envanteri");
        tr.put("player_not_found", "Oyuncu bulunamadı veya verisi yok!");
        tr.put("location", "Konum: %s");
        tr.put("dimension", "Boyut: %s");
        tr.put("xp_level", "XP: %d lvl");
        tr.put("open_ender_chest", "Ender Sandığını Aç");
        tr.put("failed_load_data", "Oyuncu verisi yüklenemedi!");
        tr.put("prev_page", "Önceki Sayfa");
        tr.put("next_page", "Sonraki Sayfa");
        tr.put("online_players", "Çevrimiçi Oyuncular");
        tr.put("offline_players", "Çevrimdışı Oyuncular");
        tr.put("page_info", "Sayfa %d / %d");
        tr.put("click_view_offline", "Çevrimdışı Oyuncuları Görmek İçin Tıklayın");
        tr.put("click_view_online", "Çevrimiçi Oyuncuları Görmek İçin Tıklayın");
        translations.put("tr", tr);
    }

    public static void setLanguage(String lang) {
        if (translations.containsKey(lang)) {
            currentLang = lang;
        } else {
            currentLang = "en";
        }
    }

    public static String get(String key, Object... args) {
        String text = translations.getOrDefault(currentLang, translations.get("en")).getOrDefault(key, key);
        if (args.length > 0) {
            return String.format(java.util.Locale.US, text, args);
        }
        return text;
    }
}
