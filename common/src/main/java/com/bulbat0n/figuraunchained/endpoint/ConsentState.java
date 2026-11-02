package com.bulbat0n.figuraunchained.endpoint;

public final class ConsentState {
    public enum Decision {
        UNDECIDED,
        YES,
        NO
    }

    public static final ConsentState EMPTY = new ConsentState(Decision.UNDECIDED, null, 0, 0);

    public final Decision decision;
    public final String approvedIp;
    public final long lastSeq;
    public final long lastOk;

    public ConsentState(Decision decision, String approvedIp, long lastSeq, long lastOk) {
        this.decision = decision;
        this.approvedIp = approvedIp;
        this.lastSeq = lastSeq;
        this.lastOk = lastOk;
    }

    public ConsentState withDecision(Decision newDecision) {
        return new ConsentState(newDecision, null, 0, 0);
    }

    public ConsentState accepted(String ip, long seq, long now) {
        return new ConsentState(decision, ip, seq, now);
    }
}
