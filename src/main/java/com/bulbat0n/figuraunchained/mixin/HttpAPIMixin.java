package com.bulbat0n.figuraunchained.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.figuramc.figura.backend2.HttpAPI;

@Mixin(HttpAPI.class)
public abstract class HttpAPIMixin {
    @Shadow protected static String getBackendAddressWithPort() { return null; }

    @Inject(method = "getBackendAddress", at = @At("HEAD"), cancellable = true)
    private static void unchainedGetBackendAddress(CallbackInfoReturnable<String> cir) {
        String address = getBackendAddressWithPort();
        
        if (address.startsWith("http://") || address.startsWith("https://")) {
            cir.setReturnValue(address + "/api");
        } 
        else if (address.startsWith("localhost") || address.matches("^[0-9]{1,3}(\\.[0-9]{1,3}){3}(:[0-9]{1,5})?$")) {
            cir.setReturnValue("http://" + address + "/api");
        } 
        else {
            cir.setReturnValue("https://" + address + "/api");
        }
    }
}
