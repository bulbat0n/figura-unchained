package com.bulbat0n.figuraunchained.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.figuramc.figura.backend2.NetworkStuff;
import org.figuramc.figura.FiguraMod;
import net.minecraft.client.MinecraftClient;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketAdapter;
import com.neovisionaries.ws.client.WebSocketException;
import org.figuramc.figura.backend2.websocket.S2CMessageHandler;

import java.nio.ByteBuffer;
import java.util.UUID;

@Mixin(NetworkStuff.class)
public abstract class NetworkStuffMixin {

    @Shadow private static int authCheck;
    @Shadow protected static void authSuccess(String token) {}
    @Shadow private static void fetchMOTD() {}
    @Shadow protected static WebSocket ws;
    @Shadow public static int backendStatus;

    @Inject(method = "checkUUID", at = @At("HEAD"), cancellable = true)
    private static void unchainedCheckUUID(UUID id, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false); 
    }

    @Inject(method = "auth", at = @At("HEAD"), cancellable = true)
    private static void unchainedAuth(CallbackInfo ci) {
        authCheck = 6000;
        com.bulbat0n.figuraunchained.auth.UnchainedAuth.init();
        String tokenPayload = MinecraftClient.getInstance().getSession().getUuidOrNull().toString();
        if (com.bulbat0n.figuraunchained.auth.UnchainedAuth.JWT != null && !com.bulbat0n.figuraunchained.auth.UnchainedAuth.JWT.isEmpty()) {
            tokenPayload += ":" + com.bulbat0n.figuraunchained.auth.UnchainedAuth.JWT;
        }
        authSuccess(tokenPayload);
        fetchMOTD();
        ci.cancel(); 
    }

    @Inject(method = "reAuth", at = @At("HEAD"), cancellable = true)
    private static void unchainedReAuth(CallbackInfo ci) {
        authCheck = 6000;
        com.bulbat0n.figuraunchained.auth.UnchainedAuth.init();
        String tokenPayload = MinecraftClient.getInstance().getSession().getUuidOrNull().toString();
        if (com.bulbat0n.figuraunchained.auth.UnchainedAuth.JWT != null && !com.bulbat0n.figuraunchained.auth.UnchainedAuth.JWT.isEmpty()) {
            tokenPayload += ":" + com.bulbat0n.figuraunchained.auth.UnchainedAuth.JWT;
        }
        authSuccess(tokenPayload);
        fetchMOTD();
        ci.cancel();
    }

    @Inject(method = "connectWS", at = @At("HEAD"), cancellable = true)
    private static void unchainedConnectWS(String tokenPayload, CallbackInfo ci) {
        if (ws != null) ws.disconnect();
        try {
            String wsUrl = HttpAPIAccessor.invokeGetUri("/ws").toString()
                    .replace("http://", "ws://")
                    .replace("https://", "wss://");
                    
            FiguraMod.LOGGER.info("Connecting WebSocket (Unchained) to: " + wsUrl);
            
            ws = new WebSocketFactory()
                    .setConnectionTimeout(5000)
                    .createSocket(wsUrl);
            
            String uuid = tokenPayload;
            if (tokenPayload.contains(":")) {
                String[] parts = tokenPayload.split(":", 2);
                uuid = parts[0];
                ws.addHeader("Authorization", "Bearer " + parts[1]);
            }
                    
            ws.addHeader("token", uuid);
                    
            ws.addListener(new WebSocketAdapter() {
                @Override
                public void onBinaryMessage(WebSocket websocket, byte[] binary) throws Exception {
                    S2CMessageHandler.handle(ByteBuffer.wrap(binary));
                }
                
                @Override
                public void onError(WebSocket websocket, WebSocketException cause) throws Exception {
                    FiguraMod.LOGGER.error("WS Error: " + cause.getMessage());
                }
            });
            
            ws.connectAsynchronously();
            backendStatus = 3; 
        } catch (Exception e) {
            FiguraMod.LOGGER.error("WebSocket connection error: " + e.getMessage());
            backendStatus = 1;
        }
        ci.cancel(); 
    }
}
