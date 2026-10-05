package com.bulbat0n.figuraunchained.fabric;

import com.bulbat0n.figuraunchained.FiguraUnchainedClient;
import com.bulbat0n.figuraunchained.command.AuthCommand;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

public class FiguraUnchainedFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FiguraUnchainedClient.init();
        ClientCommandRegistrationCallback.EVENT.register(new AuthCommand());
    }
}
