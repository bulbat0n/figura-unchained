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
        Path temp = parent.resolve(path.getFileName() + ".tmp");
        Files.write(temp, bytes);
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
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
            long lastSeq = readNonNegative(fields, "last_seq");
            long lastOk = readNonNegative(fields, "last_ok");
            if (lastSeq < 0 || lastOk < 0) {
                return ConsentState.EMPTY;
            }
            return new ConsentState(decision, approvedIp, lastSeq, lastOk);
        } catch (IOException | EndpointException e) {
            return ConsentState.EMPTY;
        }
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
        values.put("last_seq", state.lastSeq);
        values.put("last_ok", state.lastOk);
        return FlatJson.write(values) + "\n";
    }
}
