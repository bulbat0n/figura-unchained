package com.bulbat0n.figuraunchained.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.figuramc.figura.config.Configs;

@Mixin(Configs.class)
public abstract class ConfigsMixin {

    @ModifyConstant(method = "<clinit>", constant = @Constant(stringValue = "figura.moonlight-devs.org"))
    private static String unchainedChangeDefaultIP(String original) {
        return "localhost:52493";
    }
}
