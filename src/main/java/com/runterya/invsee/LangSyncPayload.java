package com.runterya.invsee;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Payload for syncing client's preferred/actual language to the server.
 */
public record LangSyncPayload(String lang) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<LangSyncPayload> ID =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("invsee", "lang_sync"));

    public static final StreamCodec<FriendlyByteBuf, LangSyncPayload> CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            LangSyncPayload::lang,
            LangSyncPayload::new
        );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
