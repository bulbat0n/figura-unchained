package com.bulbat0n.figuraunchained.endpoint;

public final class EndpointConstants {
    public static final String URL = "https://raw.githubusercontent.com/bulbat0n/figura-unchained-endpoint/refs/heads/main/endpoint.json";
    public static final String KEY_MAIN = "MCowBQYDK2VwAyEASpKUh+WnfJ0VlZE8jLDhx35zMl7MaN1eAWHwkBTz1lA=";
    public static final String KEY_BACKUP = "MCowBQYDK2VwAyEAiZw+V3otChsw7vIr2JPiU0oydiW7x0RLCOiW730b5J4=";

    public static final String DEFAULT_VALUE = "default";
    public static final String LOCAL_HOST = "localhost:55000";

    public static final long MIN_SEQ = 1;
    public static final long MAX_SEQ = 2147483647L;
    public static final int MAX_BODY_BYTES = 4096;
    public static final int NOTE_MAX_CHARS = 200;

    public static final long CONNECT_TIMEOUT_MS = 3000;
    public static final long REQUEST_TIMEOUT_MS = 4000;
    public static final long FETCH_DEADLINE_MS = 6000;
    public static final long AWAIT_READY_MS = 7000;
    public static final long CACHE_MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000;
    public static final long RETRY_COOLDOWN_MS = 10L * 60 * 1000;

    private EndpointConstants() {
    }
}
