package com.bulbat0n.figuraunchained;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import com.bulbat0n.figuraunchained.command.AuthCommand;

@Mod("figura_unchained")
public class FiguraUnchainedNeoForge {
    public FiguraUnchainedNeoForge() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            FiguraUnchainedClient.init();
            NeoForge.EVENT_BUS.register(AuthCommand.class);
        }
    }
}
