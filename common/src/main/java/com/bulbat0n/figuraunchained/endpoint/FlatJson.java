package com.bulbat0n.figuraunchained.endpoint;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FlatJson {
    private final String text;
    private int pos;

    private FlatJson(String text) {
        this.text = text;
    }

    public static String decodeUtf8(byte[] bytes) throws EndpointException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new EndpointException("the document is not valid UTF-8");
        }
    }

    public static Map<String, Object> parse(String text) throws EndpointException {
        FlatJson parser = new FlatJson(text);
        Map<String, Object> result = parser.readObject();
        parser.skipWhitespace();
        if (parser.pos != text.length()) {
            throw new EndpointException("trailing data after the JSON object");
        }
        return result;
    }

    public static String write(Map<String, Object> values) {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            writeString(out, entry.getKey());
            out.append(':');
            Object value = entry.getValue();
            if (value instanceof Long || value instanceof Integer) {
                out.append(value.toString());
            } else {
                writeString(out, String.valueOf(value));
            }
        }
        return out.append('}').toString();
    }

    private static void writeString(StringBuilder out, String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"') {
                out.append("\\\"");
            } else if (c == '\\') {
                out.append("\\\\");
            } else if (c < 0x20 || c > 0x7e) {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        out.append('"');
    }

    private Map<String, Object> readObject() throws EndpointException {
        skipWhitespace();
        expect('{');
        Map<String, Object> result = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return result;
        }
        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            Object value = readValue();
            if (result.put(key, value) != null) {
                throw new EndpointException("duplicate key");
            }
            skipWhitespace();
            char c = next();
            if (c == '}') {
                return result;
            }
            if (c != ',') {
                throw new EndpointException("expected ',' or '}'");
            }
        }
    }

    private Object readValue() throws EndpointException {
        char c = peek();
        if (c == '"') {
            return readString();
        }
        if (c == '-' || isDigit(c)) {
            return readInteger();
        }
        throw new EndpointException("only strings and integers are allowed");
    }

    private Long readInteger() throws EndpointException {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        char first = peek();
        if (first == '0') {
            pos++;
        } else if (first >= '1' && first <= '9') {
            while (pos < text.length() && isDigit(text.charAt(pos))) {
                pos++;
            }
        } else {
            throw new EndpointException("malformed number");
        }
        if (pos < text.length()) {
            char after = text.charAt(pos);
            if (isDigit(after) || after == '.' || after == 'e' || after == 'E') {
                throw new EndpointException("only integers are allowed");
            }
        }
        String digits = text.substring(start, pos);
        if (digits.length() > 18) {
            throw new EndpointException("integer out of range");
        }
        return Long.parseLong(digits);
    }

    private String readString() throws EndpointException {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return out.toString();
            }
            if (c < 0x20) {
                throw new EndpointException("control character in a string");
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char escaped = next();
            switch (escaped) {
                case '"':
                    out.append('"');
                    break;
                case '\\':
                    out.append('\\');
                    break;
                case '/':
                    out.append('/');
                    break;
                case 'b':
                    out.append('\b');
                    break;
                case 'f':
                    out.append('\f');
                    break;
                case 'n':
                    out.append('\n');
                    break;
                case 'r':
                    out.append('\r');
                    break;
                case 't':
                    out.append('\t');
                    break;
                case 'u':
                    int code = 0;
                    for (int i = 0; i < 4; i++) {
                        int digit = hexValue(next());
                        if (digit < 0) {
                            throw new EndpointException("malformed unicode escape");
                        }
                        code = (code << 4) | digit;
                    }
                    out.append((char) code);
                    break;
                default:
                    throw new EndpointException("unknown escape sequence");
            }
        }
    }

    private static int hexValue(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'F') {
            return c - 'A' + 10;
        }
        return -1;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private void skipWhitespace() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return;
            }
            pos++;
        }
    }

    private char peek() throws EndpointException {
        if (pos >= text.length()) {
            throw new EndpointException("unexpected end of the document");
        }
        return text.charAt(pos);
    }

    private char next() throws EndpointException {
        char c = peek();
        pos++;
        return c;
    }

    private void expect(char expected) throws EndpointException {
        if (next() != expected) {
            throw new EndpointException("unexpected character, expected '" + expected + "'");
        }
    }
}
