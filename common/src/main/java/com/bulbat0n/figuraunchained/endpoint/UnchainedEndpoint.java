package com.bulbat0n.figuraunchained.endpoint;

import com.bulbat0n.figuraunchained.auth.UnchainedAuth;
import net.minecraft.client.Minecraft;
import org.figuramc.figura.FiguraMod;
import org.figuramc.figura.config.ConfigManager;
import org.figuramc.figura.config.Configs;
import org.figuramc.figura.gui.FiguraToast;

import java.io.File;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UnchainedEndpoint {
    private static final CompletableFuture<Void> DONE = CompletableFuture.completedFuture(null);
    private static final AtomicBoolean CACHE_TOAST = new AtomicBoolean();
    private static final AtomicBoolean LOCAL_TOAST = new AtomicBoolean();

    private static final EndpointResolver.Listener LISTENER = new EndpointResolver.Listener() {
        @Override
        public void onCacheFallback(String reason) {
            FiguraMod.LOGGER.warn("Unchained: using the last known server address (" + reason + ")");
            if (CACHE_TOAST.compareAndSet(false, true)) {
                FiguraToast.sendToast("Public server", "Using the last known address.", FiguraToast.ToastType.WARNING);
            }
        }

        @Override
        public void onLocalFallback(String reason) {
            FiguraMod.LOGGER.warn("Unchained: using " + EndpointConstants.LOCAL_HOST + " (" + reason + ")");
            if (LOCAL_TOAST.compareAndSet(false, true)) {
                FiguraToast.sendToast("Public server unavailable", "Using " + EndpointConstants.LOCAL_HOST + ".", FiguraToast.ToastType.WARNING);
            }
        }

        @Override
        public void onPending(EndpointResolver.Pending pending) {
            FiguraMod.LOGGER.warn("Unchained: the public server address changed to " + pending.ip + " (seq " + pending.seq + "), waiting for approval");
        }

        @Override
        public void onWarning(String message) {
            FiguraMod.LOGGER.warn("Unchained: " + message);
        }
    };

    private static ConsentStore store;
    private static EndpointResolver resolver;
    private static CompletableFuture<Void> ready;

    private UnchainedEndpoint() {
    }

    public static synchronized ConsentStore store() {
        if (store == null) {
            store = new ConsentStore(new File(Minecraft.getInstance().gameDirectory, "config/figura-unchained/consent.json").toPath());
        }
        return store;
    }

    public static synchronized EndpointResolver resolver() {
        if (resolver == null) {
            List<PublicKey> keys;
            try {
                keys = EndpointVerifier.decodeKeys(EndpointConstants.KEY_MAIN, EndpointConstants.KEY_BACKUP);
            } catch (GeneralSecurityException | IllegalArgumentException e) {
                FiguraMod.LOGGER.error("Unchained: cannot load the endpoint public keys: " + e.getMessage());
                keys = List.of();
            }
            resolver = new EndpointResolver(
                    store(),
                    new HttpEndpointFetcher(URI.create(EndpointConstants.URL), "FiguraUnchained/" + UnchainedAuth.getAddonVersion(), true),
                    new EndpointVerifier(keys),
                    System::currentTimeMillis,
                    LISTENER);
        }
        return resolver;
    }

    public static boolean isDefaultValue(String value) {
        return value != null && value.trim().equalsIgnoreCase(EndpointConstants.DEFAULT_VALUE);
    }

    public static void ensureStarted() {
        startIfNeeded();
    }

    public static EndpointResolver.Pending pending() {
        if (!isDefaultValue(Configs.SERVER_IP.value) || store().get().decision != ConsentState.Decision.YES) {
            return null;
        }
        EndpointResolver.Resolution resolution = resolver().current();
        return resolution == null ? null : resolution.pending;
    }

    public static String approvedIp() {
        return store().get().approvedIp;
    }

    public static boolean isUndecided() {
        return store().get().decision == ConsentState.Decision.UNDECIDED;
    }

    public static String hostFor(String configValue) {
        try {
            ConsentState.Decision decision = store().get().decision;
            if (decision == ConsentState.Decision.UNDECIDED) {
                return EndpointConstants.LOCAL_HOST;
            }
            if (!isDefaultValue(configValue)) {
                return null;
            }
            if (decision != ConsentState.Decision.YES) {
                return EndpointConstants.LOCAL_HOST;
            }
            EndpointResolver.Resolution resolution = resolver().current();
            return resolution == null ? EndpointConstants.LOCAL_HOST : resolution.host;
        } catch (RuntimeException e) {
            FiguraMod.LOGGER.error("Unchained: cannot resolve the server address: " + e);
            return EndpointConstants.LOCAL_HOST;
        }
    }

    public static void awaitReady() {
        try {
            startIfNeeded().get(EndpointConstants.AWAIT_READY_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
        }
    }

    public static boolean isBlocked() {
        if (!isDefaultValue(Configs.SERVER_IP.value)) {
            return false;
        }
        EndpointResolver.Resolution resolution = resolver().current();
        return resolution != null && resolution.source == EndpointResolver.Source.BLOCKED;
    }

    public static boolean retryAfterFailure() {
        if (!isDefaultValue(Configs.SERVER_IP.value)) {
            return false;
        }
        return resolver().retryIfDue();
    }

    public static void overwriteServerField() {
        if (EndpointConstants.DEFAULT_VALUE.equals(Configs.SERVER_IP.value)) {
            return;
        }
        Configs.SERVER_IP.setTempValue(EndpointConstants.DEFAULT_VALUE);
        Configs.SERVER_IP.setValue(EndpointConstants.DEFAULT_VALUE);
        ConfigManager.saveConfig();
    }

    public static void decide(ConsentState.Decision decision) {
        resolver().setDecision(decision);
        synchronized (UnchainedEndpoint.class) {
            ready = null;
        }
        startIfNeeded();
    }

    private static synchronized CompletableFuture<Void> startIfNeeded() {
        if (!isDefaultValue(Configs.SERVER_IP.value) || store().get().decision != ConsentState.Decision.YES) {
            return DONE;
        }
        if (ready != null) {
            return ready;
        }
        CompletableFuture<Void> future = new CompletableFuture<>();
        ready = future;
        Thread thread = new Thread(() -> {
            try {
                resolver().resolve();
            } catch (Throwable t) {
                FiguraMod.LOGGER.error("Unchained: the endpoint resolver failed: " + t);
            } finally {
                future.complete(null);
            }
        }, "figura-unchained-endpoint");
        thread.setDaemon(true);
        thread.start();
        return future;
    }
}
