package com.bulbat0n.figuraunchained.endpoint;

public final class ConsentState {
    public enum Decision {
        UNDECIDED,
        YES,
        NO
    }

    public static final ConsentState EMPTY = new ConsentState(Decision.UNDECIDED, null, null, 0, 0, null);

    public final Decision decision;
    public final String approvedIp;
    public final String approvedSrv;
    public final long lastSeq;
    public final long lastOk;
    public final EndpointResolver.Pending pending;

    public ConsentState(Decision decision, String approvedIp, String approvedSrv, long lastSeq, long lastOk, EndpointResolver.Pending pending) {
        this.decision = decision;
        this.approvedIp = approvedIp;
        this.approvedSrv = approvedSrv;
        this.lastSeq = lastSeq;
        this.lastOk = lastOk;
        this.pending = pending;
    }

    public ConsentState withDecision(Decision newDecision) {
        if (newDecision == decision) {
            return this;
        }
        long seq = Math.max(lastSeq, pending == null ? 0 : pending.seq);
        return new ConsentState(newDecision, null, null, seq, 0, null);
    }

    public ConsentState accepted(String ip, String srv, long seq, long now) {
        return new ConsentState(decision, ip, srv, seq, now, null);
    }

    public ConsentState withPending(EndpointResolver.Pending newPending) {
        return new ConsentState(decision, approvedIp, approvedSrv, lastSeq, lastOk, newPending);
    }

    public ConsentState withoutPending() {
        return new ConsentState(decision, approvedIp, approvedSrv, lastSeq, lastOk, null);
    }
}
