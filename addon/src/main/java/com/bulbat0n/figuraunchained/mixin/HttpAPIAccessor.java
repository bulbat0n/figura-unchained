package com.bulbat0n.figuraunchained.mixin;

import org.figuramc.figura.backend2.HttpAPI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import java.net.URI;

@Mixin(HttpAPI.class)
public interface HttpAPIAccessor {
    @Invoker("getUri")
    static URI invokeGetUri(String path) {
        throw new UnsupportedOperationException();
    }
}
