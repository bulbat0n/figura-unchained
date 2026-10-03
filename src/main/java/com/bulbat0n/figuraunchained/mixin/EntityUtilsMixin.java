package com.bulbat0n.figuraunchained.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.figuramc.figura.utils.EntityUtils;
import java.util.UUID;

@Mixin(EntityUtils.class)
public class EntityUtilsMixin {
    
    @Inject(method = "checkInvalidPlayer", at = @At("HEAD"), cancellable = true)
    private static void bypassEntityUUIDCheck(UUID id, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
