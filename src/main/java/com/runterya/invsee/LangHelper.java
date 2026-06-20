package com.runterya.invsee;

import net.minecraft.server.level.ServerPlayer;
import java.lang.reflect.Field;

public class LangHelper {
    public static String getClientLanguage(ServerPlayer player) {
        String lang = "en_us";
        try {
            for (Field field : ServerPlayer.class.getDeclaredFields()) {
                if (field.getType() == String.class) {
                    field.setAccessible(true);
                    String val = (String) field.get(player);
                    if (val != null && val.matches("^[a-z]{2}_[a-z]{2}$")) {
                        lang = val;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lang;
    }
}
