package com.bulbat0n.figuraunchained.endpoint;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class EndpointVerifier {
    private static final String PREFIX = "figura-unchained-endpoint-v1";
    private static final Pattern HOST = Pattern.compile("(?=.{4,253}\\z)([a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}(:[0-9]{1,5})?");
    private static final String[] BAD_SUFFIXES = {".local", ".internal", ".localhost", ".lan"};
    private static final Set<String> ALLOWED_FIELDS = Set.of("v", "seq", "ip", "note", "sig");
    private static final int SIGNATURE_BASE64_LENGTH = 88;

    private final List<PublicKey> keys;

    public EndpointVerifier(List<PublicKey> keys) {
        this.keys = List.copyOf(keys);
    }

    public static List<PublicKey> decodeKeys(String... encoded) throws GeneralSecurityException {
        KeyFactory factory = KeyFactory.getInstance("Ed25519");
        List<PublicKey> result = new ArrayList<>();
        for (String key : encoded) {
            result.add(factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key))));
        }
        return result;
    }

    public EndpointDocument verify(byte[] body) throws EndpointException {
        if (body.length == 0 || body.length > EndpointConstants.MAX_BODY_BYTES) {
            throw new EndpointException("unexpected document size");
        }
        Map<String, Object> fields = FlatJson.parse(FlatJson.decodeUtf8(body));
        for (String name : fields.keySet()) {
            if (!ALLOWED_FIELDS.contains(name)) {
                throw new EndpointException("unexpected field in the document");
            }
        }
        if (requireInteger(fields, "v") != 1) {
            throw new EndpointException("unsupported document version");
        }
        long seq = requireInteger(fields, "seq");
        String ip = requireString(fields, "ip");
        String note = fields.containsKey("note") ? requireString(fields, "note") : "";
        String sig = requireString(fields, "sig");
        return verifyParts(seq, ip, note, sig);
    }

    public EndpointDocument verifyParts(long seq, String ip, String note, String sig) throws EndpointException {
        if (seq < EndpointConstants.MIN_SEQ || seq > EndpointConstants.MAX_SEQ) {
            throw new EndpointException("seq is out of range");
        }
        if (!isValidHost(ip)) {
            throw new EndpointException("the host is not acceptable");
        }
        if (!isValidNote(note)) {
            throw new EndpointException("the note is not acceptable");
        }
        byte[] signature = decodeSignature(sig);
        if (!signatureMatches(seq, ip, note, signature)) {
            throw new EndpointException("the signature does not match");
        }
        return new EndpointDocument(seq, ip, note, sig);
    }

    public static boolean isValidHost(String ip) {
        if (ip == null || !HOST.matcher(ip).matches()) {
            return false;
        }
        int colon = ip.indexOf(':');
        String host = colon < 0 ? ip : ip.substring(0, colon);
        if (colon >= 0) {
            int port = Integer.parseInt(ip.substring(colon + 1));
            if (port < 1 || port > 65535) {
                return false;
            }
        }
        for (String suffix : BAD_SUFFIXES) {
            if (host.endsWith(suffix)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidNote(String note) {
        if (note == null || note.codePointCount(0, note.length()) > EndpointConstants.NOTE_MAX_CHARS) {
            return false;
        }
        int index = 0;
        while (index < note.length()) {
            int codePoint = note.codePointAt(index);
            index += Character.charCount(codePoint);
            if (codePoint == ' ') {
                continue;
            }
            switch (Character.getType(codePoint)) {
                case Character.CONTROL:
                case Character.FORMAT:
                case Character.SURROGATE:
                case Character.PRIVATE_USE:
                case Character.UNASSIGNED:
                case Character.LINE_SEPARATOR:
                case Character.PARAGRAPH_SEPARATOR:
                case Character.SPACE_SEPARATOR:
                    return false;
                default:
                    break;
            }
        }
        return true;
    }

    private boolean signatureMatches(long seq, String ip, String note, byte[] signature) throws EndpointException {
        byte[] message = (PREFIX + "\n" + seq + "\n" + ip + "\n" + note).getBytes(StandardCharsets.UTF_8);
        for (PublicKey key : keys) {
            try {
                Signature verifier = Signature.getInstance("Ed25519");
                verifier.initVerify(key);
                verifier.update(message);
                if (verifier.verify(signature)) {
                    return true;
                }
            } catch (GeneralSecurityException e) {
                throw new EndpointException("Ed25519 verification is not available");
            }
        }
        return false;
    }

    private static byte[] decodeSignature(String encoded) throws EndpointException {
        if (encoded.length() != SIGNATURE_BASE64_LENGTH || !encoded.endsWith("==")) {
            throw new EndpointException("the signature has an unexpected length");
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new EndpointException("the signature is not valid base64");
        }
        if (decoded.length != 64) {
            throw new EndpointException("the signature has an unexpected length");
        }
        return decoded;
    }

    private static long requireInteger(Map<String, Object> fields, String name) throws EndpointException {
        Object value = fields.get(name);
        if (!(value instanceof Long)) {
            throw new EndpointException("field '" + name + "' must be an integer");
        }
        return (Long) value;
    }

    private static String requireString(Map<String, Object> fields, String name) throws EndpointException {
        Object value = fields.get(name);
        if (!(value instanceof String)) {
            throw new EndpointException("field '" + name + "' must be a string");
        }
        return (String) value;
    }
}
