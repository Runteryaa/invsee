package com.runterya.invsee;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Payload sent from server to client to instruct the client to reload its config.
 */
public record ClientReloadPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClientReloadPayload> ID =
        new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("invsee", "client_reload"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientReloadPayload> CODEC =
        StreamCodec.unit(new ClientReloadPayload());

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
