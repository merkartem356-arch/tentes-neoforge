package com.tentnets;

import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Грузится только на клиенте. */
public final class ClientHooks {
    private ClientHooks() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        ThermalClient.tick();
    }
}
