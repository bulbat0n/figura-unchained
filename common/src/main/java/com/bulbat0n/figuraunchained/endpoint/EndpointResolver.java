package com.bulbat0n.figuraunchained.endpoint;

import java.io.IOException;
import java.util.function.LongSupplier;

public final class EndpointResolver {
    public enum Source {
        FETCHED,
        CACHED,
        LOCAL,
        BLOCKED
    }

    public static final class Pending {
        public final String ip;
        public final long seq;
        public final String note;

        public Pending(String ip, long seq, String note) {
            this.ip = ip;
            this.seq = seq;
            this.note = note;
        }
    }

    public static final class Resolution {
        public final String host;
        public final Source source;
        public final Pending pending;

        public Resolution(String host, Source source, Pending pending) {
            this.host = host;
            this.source = source;
            this.pending = pending;
        }
    }

    public interface Listener {
        void onCacheFallback(String reason);

        void onLocalFallback(String reason);

        void onPending(Pending pending);

        void onWarning(String message);
    }

    private final ConsentStore store;
    private final EndpointFetcher fetcher;
    private final EndpointVerifier verifier;
    private final LongSupplier clock;
    private final Listener listener;
    private volatile Resolution current;
    private long lastAttemptAt;

    public EndpointResolver(ConsentStore store, EndpointFetcher fetcher, EndpointVerifier verifier, LongSupplier clock, Listener listener) {
        this.store = store;
        this.fetcher = fetcher;
        this.verifier = verifier;
        this.clock = clock;
        this.listener = listener;
    }

    public Resolution current() {
        return current;
    }

    public synchronized Resolution resolve() {
        long now = clock.getAsLong();
        lastAttemptAt = now;
        ConsentState state = store.get();
        if (state.decision != ConsentState.Decision.YES) {
            return publish(local());
        }
        EndpointDocument document;
        try {
            document = verifier.verify(fetcher.fetch());
        } catch (IOException | EndpointException | RuntimeException e) {
            return fallback(state, now, e.getMessage(), true);
        }
        return apply(state, now, document);
    }

    public synchronized Resolution setDecision(ConsentState.Decision decision) {
        persist(store.get().withDecision(decision));
        if (decision == ConsentState.Decision.YES) {
            current = null;
            return null;
        }
        return publish(local());
    }

    public synchronized Resolution approvePending() {
        Resolution resolution = current;
        ConsentState state = store.get();
        if (resolution == null || resolution.pending == null || state.decision != ConsentState.Decision.YES) {
            return resolution;
        }
        Pending pending = resolution.pending;
        persist(state.accepted(pending.ip, pending.seq, clock.getAsLong()));
        return publish(new Resolution(pending.ip, Source.FETCHED, null));
    }

    public synchronized Resolution declinePending() {
        Resolution resolution = current;
        if (resolution == null || resolution.pending == null) {
            return resolution;
        }
        persist(store.get().withDecision(ConsentState.Decision.NO));
        return publish(local());
    }

    public synchronized boolean retryIfDue() {
        Resolution before = current;
        if (before == null || store.get().decision != ConsentState.Decision.YES) {
            return false;
        }
        if (before.source != Source.CACHED && before.source != Source.LOCAL) {
            return false;
        }
        if (clock.getAsLong() - lastAttemptAt < EndpointConstants.RETRY_COOLDOWN_MS) {
            return false;
        }
        Resolution after = resolve();
        return !after.host.equals(before.host);
    }

    private Resolution apply(ConsentState state, long now, EndpointDocument document) {
        if (state.approvedIp == null) {
            if (document.seq <= state.lastSeq) {
                return fallback(state, now, "stale endpoint document", false);
            }
            return accept(state, now, document.ip, document.seq);
        }
        if (document.seq > state.lastSeq) {
            if (document.ip.equals(state.approvedIp)) {
                return accept(state, now, document.ip, document.seq);
            }
            Pending pending = new Pending(document.ip, document.seq, document.note);
            Resolution blocked = publish(new Resolution(EndpointConstants.LOCAL_HOST, Source.BLOCKED, pending));
            listener.onPending(pending);
            return blocked;
        }
        if (document.seq == state.lastSeq && document.ip.equals(state.approvedIp)) {
            return accept(state, now, document.ip, document.seq);
        }
        return fallback(state, now, "stale endpoint document", false);
    }

    private Resolution accept(ConsentState state, long now, String ip, long seq) {
        persist(state.accepted(ip, seq, now));
        return publish(new Resolution(ip, Source.FETCHED, null));
    }

    private Resolution fallback(ConsentState state, long now, String reason, boolean notify) {
        if (state.approvedIp != null && now - state.lastOk <= EndpointConstants.CACHE_MAX_AGE_MS) {
            if (notify) {
                listener.onCacheFallback(reason);
            }
            return publish(new Resolution(state.approvedIp, Source.CACHED, null));
        }
        listener.onLocalFallback(reason);
        return publish(local());
    }

    private Resolution local() {
        return new Resolution(EndpointConstants.LOCAL_HOST, Source.LOCAL, null);
    }

    private Resolution publish(Resolution resolution) {
        current = resolution;
        return resolution;
    }

    private void persist(ConsentState state) {
        try {
            store.set(state);
        } catch (IOException e) {
            listener.onWarning("could not save the consent state: " + e.getMessage());
        }
    }
}
