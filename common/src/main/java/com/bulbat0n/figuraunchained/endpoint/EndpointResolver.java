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
        public final String sig;

        public Pending(String ip, long seq, String note, String sig) {
            this.ip = ip;
            this.seq = seq;
            this.note = note;
            this.sig = sig;
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

    public Resolution resolve() {
        synchronized (this) {
            lastAttemptAt = clock.getAsLong();
            ConsentState state = store.get();
            if (state.decision != ConsentState.Decision.YES) {
                return publish(local());
            }
            if (state.pending != null) {
                if (pendingValid(state.pending)) {
                    publish(blocked(state.pending));
                } else {
                    listener.onWarning("the stored address change is not valid and was discarded");
                    persist(state.withoutPending());
                }
            }
        }
        EndpointDocument document = null;
        String failure = null;
        try {
            document = verifier.verify(fetcher.fetch());
        } catch (IOException | EndpointException | RuntimeException e) {
            failure = String.valueOf(e.getMessage());
        }
        synchronized (this) {
            ConsentState state = store.get();
            long now = clock.getAsLong();
            if (state.decision != ConsentState.Decision.YES) {
                return publish(local());
            }
            if (failure != null) {
                if (state.pending != null) {
                    listener.onWarning("could not check for newer address changes: " + failure);
                    return publish(blocked(state.pending));
                }
                return fallback(state, now, failure, true);
            }
            return apply(state, now, document);
        }
    }

    public synchronized Resolution setDecision(ConsentState.Decision decision) {
        persist(store.get().withDecision(decision));
        if (decision == ConsentState.Decision.YES) {
            current = null;
            return null;
        }
        return publish(local());
    }

    public synchronized boolean approvePending(Pending expected) {
        ConsentState state = store.get();
        Pending pending = state.pending;
        if (state.decision != ConsentState.Decision.YES || pending == null || expected == null) {
            return false;
        }
        if (pending.seq != expected.seq || !pending.ip.equals(expected.ip) || !pending.sig.equals(expected.sig)) {
            return false;
        }
        if (!pendingValid(pending)) {
            return false;
        }
        persist(state.accepted(pending.ip, pending.seq, clock.getAsLong()));
        publish(new Resolution(pending.ip, Source.FETCHED, null));
        return true;
    }

    public synchronized Resolution declinePending() {
        ConsentState state = store.get();
        if (state.pending == null) {
            return current;
        }
        persist(state.withDecision(ConsentState.Decision.NO));
        return publish(local());
    }

    public boolean retryIfDue() {
        Resolution before;
        synchronized (this) {
            before = current;
            if (before == null || store.get().decision != ConsentState.Decision.YES) {
                return false;
            }
            if (before.source != Source.CACHED && before.source != Source.LOCAL) {
                return false;
            }
            long now = clock.getAsLong();
            if (now - lastAttemptAt < EndpointConstants.RETRY_COOLDOWN_MS) {
                return false;
            }
            lastAttemptAt = now;
        }
        Resolution after = resolve();
        return !after.host.equals(before.host);
    }

    private boolean pendingValid(Pending pending) {
        try {
            verifier.verifyParts(pending.seq, pending.ip, pending.note, pending.sig);
            return true;
        } catch (EndpointException | RuntimeException e) {
            return false;
        }
    }

    private Resolution apply(ConsentState state, long now, EndpointDocument document) {
        if (state.pending != null) {
            if (document.seq <= state.pending.seq) {
                return publish(blocked(state.pending));
            }
            if (document.ip.equals(state.approvedIp)) {
                return accept(state.withoutPending(), now, document.ip, document.seq);
            }
            return propose(state, document);
        }
        if (state.approvedIp == null) {
            if (document.seq < state.lastSeq) {
                return fallback(state, now, "stale endpoint document", false);
            }
            return accept(state, now, document.ip, document.seq);
        }
        if (document.seq > state.lastSeq) {
            if (document.ip.equals(state.approvedIp)) {
                return accept(state, now, document.ip, document.seq);
            }
            return propose(state, document);
        }
        if (document.seq == state.lastSeq && document.ip.equals(state.approvedIp)) {
            return accept(state, now, document.ip, document.seq);
        }
        return fallback(state, now, "stale endpoint document", false);
    }

    private Resolution propose(ConsentState state, EndpointDocument document) {
        Pending pending = new Pending(document.ip, document.seq, document.note, document.sig);
        persist(state.withPending(pending));
        Resolution blocked = publish(blocked(pending));
        listener.onPending(pending);
        return blocked;
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

    private Resolution blocked(Pending pending) {
        return new Resolution(EndpointConstants.LOCAL_HOST, Source.BLOCKED, pending);
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
