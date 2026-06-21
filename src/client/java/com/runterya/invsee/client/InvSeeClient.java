package com.runterya.invsee.client;

import com.runterya.invsee.ClientReloadPayload;
import com.runterya.invsee.LangSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public class InvSeeClient implements ClientModInitializer {
    public static ClientConfig config;

    @Override
    public void onInitializeClient() {
        config = ClientConfig.load();

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            sendLangPreference();
        });

        ClientPlayNetworking.registerGlobalReceiver(ClientReloadPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                config = ClientConfig.load();
                sendLangPreference();
                if (context.client().player != null) {
                    context.client().player.displayClientMessage(net.minecraft.network.chat.Component.literal("§aClient configuration reloaded!"), false);
                }
            });
        });
    }

    public static void sendLangPreference() {
        if (ClientPlayNetworking.canSend(LangSyncPayload.ID)) {
            String lang = config.preferredLanguage;
            if ("auto".equals(lang)) {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.getLanguageManager() != null) {
                    lang = mc.getLanguageManager().getSelected();
                }
            }
            ClientPlayNetworking.send(new LangSyncPayload(lang));
        }
    }
}
