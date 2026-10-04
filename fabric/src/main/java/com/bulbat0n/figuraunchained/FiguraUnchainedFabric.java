package com.bulbat0n.figuraunchained;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import com.bulbat0n.figuraunchained.command.AuthCommand;

public class FiguraUnchainedFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        FiguraUnchainedClient.init();
        ClientCommandRegistrationCallback.EVENT.register(new AuthCommand());
    }
}
