package com.bulbat0n.figuraunchained.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.figuramc.figura.backend2.HttpAPI;
import org.figuramc.figura.config.Configs;
import com.bulbat0n.figuraunchained.auth.UnchainedAuth;
import com.bulbat0n.figuraunchained.endpoint.UnchainedEndpoint;

import java.net.http.HttpRequest;

@Mixin(HttpAPI.class)
public abstract class HttpAPIMixin {
    @Shadow(remap = false) protected static String getBackendAddressWithPort() { return null; }

    @Inject(method = "header", at = @At("RETURN"), remap = false)
    private void unchainedClientHeaders(String url, CallbackInfoReturnable<HttpRequest.Builder> cir) {
        UnchainedAuth.applyClientHeaders(cir.getReturnValue()::header);
    }

    @Inject(method = "getBackendAddressWithPort", at = @At("HEAD"), cancellable = true, remap = false)
    private static void unchainedResolveDefault(CallbackInfoReturnable<String> cir) {
        String host = UnchainedEndpoint.hostFor(Configs.SERVER_IP.value);
        if (host != null) {
            cir.setReturnValue(host);
        }
    }

    @Inject(method = "getBackendAddress", at = @At("HEAD"), cancellable = true, remap = false)
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
