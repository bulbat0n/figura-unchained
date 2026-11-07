package com.bulbat0n.figuraunchained.endpoint;

public final class EndpointDocument {
    public final long seq;
    public final String ip;
    public final String note;
    public final String sig;

    public EndpointDocument(long seq, String ip, String note, String sig) {
        this.seq = seq;
        this.ip = ip;
        this.note = note;
        this.sig = sig;
    }
}
