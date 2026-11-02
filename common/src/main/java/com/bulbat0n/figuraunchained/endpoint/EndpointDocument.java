package com.bulbat0n.figuraunchained.endpoint;

public final class EndpointDocument {
    public final long seq;
    public final String ip;
    public final String note;

    public EndpointDocument(long seq, String ip, String note) {
        this.seq = seq;
        this.ip = ip;
        this.note = note;
    }
}
