package com.bulbat0n.figuraunchained.mixin;

import com.bulbat0n.figuraunchained.endpoint.UnchainedEndpoint;
import com.bulbat0n.figuraunchained.gui.ChangeScreen;
import com.bulbat0n.figuraunchained.gui.ConsentScreen;
import com.bulbat0n.figuraunchained.gui.UnchainedToast;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.figuramc.figura.FiguraMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FiguraMod.class)
public class FiguraTickMixin {
    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private static void unchainedConsentWindow(CallbackInfo ci) {
        UnchainedToast.flush();
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof TitleScreen)) {
            return;
        }
        if (ConsentScreen.needsWindow()) {
            minecraft.setScreen(new ConsentScreen(minecraft.screen));
            return;
        }
        UnchainedEndpoint.ensureStarted();
        if (UnchainedEndpoint.pending() != null && UnchainedEndpoint.isSettled()) {
            minecraft.setScreen(new ChangeScreen(minecraft.screen));
        }
    }
}
