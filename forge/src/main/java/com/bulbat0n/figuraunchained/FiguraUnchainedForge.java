package com.bulbat0n.figuraunchained;

import com.bulbat0n.figuraunchained.command.AuthCommand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

@Mod("figura_unchained")
public class FiguraUnchainedForge {
    public FiguraUnchainedForge() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            FiguraUnchainedClient.init();
            MinecraftForge.EVENT_BUS.register(AuthCommand.class);
        }
    }
}
