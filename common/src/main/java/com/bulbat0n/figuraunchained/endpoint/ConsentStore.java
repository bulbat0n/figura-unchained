package com.bulbat0n.figuraunchained.endpoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ConsentStore {
    private static final int MAX_FILE_BYTES = 4096;

    private static final EndpointResolver.Pending INVALID_PENDING = new EndpointResolver.Pending("", 0, "", "", "");

    private final Path path;
    private ConsentState cached;

    public ConsentStore(Path path) {
        this.path = path;
    }

    public synchronized boolean fileExists() {
        return Files.exists(path);
    }

    public synchronized ConsentState get() {
        if (cached == null) {
            cached = read();
        }
        return cached;
    }

    public synchronized void set(ConsentState state) throws IOException {
        cached = state;
        byte[] bytes = render(state).getBytes(StandardCharsets.UTF_8);
        Path parent = path.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, path.getFileName() + ".", ".tmp");
        try {
            Files.write(temp, bytes);
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private ConsentState read() {
        if (!Files.isRegularFile(path)) {
            return ConsentState.EMPTY;
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length > MAX_FILE_BYTES) {
                return ConsentState.EMPTY;
            }
            Map<String, Object> fields = FlatJson.parse(FlatJson.decodeUtf8(bytes));
            ConsentState.Decision decision = ConsentState.Decision.UNDECIDED;
            if (fields.containsKey("decision")) {
                Object value = fields.get("decision");
                if ("yes".equals(value)) {
                    decision = ConsentState.Decision.YES;
                } else if ("no".equals(value)) {
                    decision = ConsentState.Decision.NO;
                } else {
                    return ConsentState.EMPTY;
                }
            }
            String approvedIp = null;
            if (fields.containsKey("approved_ip")) {
                Object value = fields.get("approved_ip");
                if (!(value instanceof String) || !EndpointVerifier.isValidHost((String) value)) {
                    return ConsentState.EMPTY;
                }
                approvedIp = (String) value;
            }
            String approvedSrv = null;
            if (fields.containsKey("approved_srv")) {
                Object value = fields.get("approved_srv");
                if (!(value instanceof String) || !EndpointVerifier.isValidSrv((String) value)) {
                    return ConsentState.EMPTY;
                }
                approvedSrv = (String) value;
            }
            long lastSeq = readNonNegative(fields, "last_seq");
            long lastOk = readNonNegative(fields, "last_ok");
            if (lastSeq < 0 || lastOk < 0) {
                return ConsentState.EMPTY;
            }
            EndpointResolver.Pending pending = readPending(fields);
            if (pending == INVALID_PENDING) {
                return ConsentState.EMPTY;
            }
            if (decision != ConsentState.Decision.YES) {
                pending = null;
            }
            return new ConsentState(decision, approvedIp, approvedSrv, lastSeq, lastOk, pending);
        } catch (IOException | EndpointException e) {
            return ConsentState.EMPTY;
        }
    }

    private static EndpointResolver.Pending readPending(Map<String, Object> fields) {
        boolean any = fields.containsKey("pending_ip") || fields.containsKey("pending_seq") || fields.containsKey("pending_sig") || fields.containsKey("pending_note") || fields.containsKey("pending_srv");
        if (!any) {
            return null;
        }
        Object ip = fields.get("pending_ip");
        Object srv = fields.get("pending_srv");
        Object seq = fields.get("pending_seq");
        Object sig = fields.get("pending_sig");
        Object note = fields.containsKey("pending_note") ? fields.get("pending_note") : "";
        if (!(ip instanceof String) || !EndpointVerifier.isValidHost((String) ip)) {
            return INVALID_PENDING;
        }
        if (!(seq instanceof Long) || (Long) seq < EndpointConstants.MIN_SEQ || (Long) seq > EndpointConstants.MAX_SEQ) {
            return INVALID_PENDING;
        }
        if (!(srv instanceof String) || !EndpointVerifier.isValidSrv((String) srv)) {
            return INVALID_PENDING;
        }
        if (!(sig instanceof String) || !(note instanceof String) || !EndpointVerifier.isValidNote((String) note)) {
            return INVALID_PENDING;
        }
        return new EndpointResolver.Pending((String) ip, (Long) seq, (String) note, (String) srv, (String) sig);
    }

    private static long readNonNegative(Map<String, Object> fields, String name) {
        if (!fields.containsKey(name)) {
            return 0;
        }
        Object value = fields.get(name);
        if (!(value instanceof Long) || (Long) value < 0) {
            return -1;
        }
        return (Long) value;
    }

    private static String render(ConsentState state) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("v", 1L);
        if (state.decision != ConsentState.Decision.UNDECIDED) {
            values.put("decision", state.decision == ConsentState.Decision.YES ? "yes" : "no");
        }
        if (state.approvedIp != null) {
            values.put("approved_ip", state.approvedIp);
        }
        if (state.approvedSrv != null) {
            values.put("approved_srv", state.approvedSrv);
        }
        values.put("last_seq", state.lastSeq);
        values.put("last_ok", state.lastOk);
        if (state.pending != null) {
            values.put("pending_ip", state.pending.ip);
            values.put("pending_seq", state.pending.seq);
            values.put("pending_note", state.pending.note);
            values.put("pending_srv", state.pending.srv);
            values.put("pending_sig", state.pending.sig);
        }
        return FlatJson.write(values) + "\n";
    }
}
