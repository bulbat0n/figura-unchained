package com.bulbat0n.figuraunchained.auth;

import net.minecraft.client.Minecraft;
import org.figuramc.figura.gui.FiguraToast;
import com.bulbat0n.figuraunchained.endpoint.UnchainedEndpoint;
import com.bulbat0n.figuraunchained.gui.ConsentScreen;
import com.bulbat0n.figuraunchained.gui.UnchainedToast;
import com.bulbat0n.figuraunchained.mixin.HttpAPIAccessor;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Properties;
import java.util.UUID;
import java.util.function.BiConsumer;

public class UnchainedAuth {
    public static String JWT = "";
    private static File TOKEN_FILE = null;

    private static String addonVersion = null;

    public static String getUsername() {
        return Minecraft.getInstance().getUser().getName();
    }

    public static String getCanonicalUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public static String getLocalUuid() {
        return getCanonicalUuid(getUsername());
    }

    public static String getSessionUuid() {
        UUID id = Minecraft.getInstance().getUser().getProfileId();
        return id == null ? null : id.toString();
    }

    public static String getWorldUuid() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : mc.player.getUUID().toString();
    }

    public static void awaitPlayer() {
        Minecraft mc = Minecraft.getInstance();
        for (int i = 0; i < 60 && mc.level != null && mc.player == null; i++) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public static String getAddonVersion() {
        if (addonVersion == null) {
            String v = "unknown";
            try (InputStream in = UnchainedAuth.class.getResourceAsStream("/figura_unchained_build.properties")) {
                if (in != null) {
                    Properties p = new Properties();
                    p.load(in);
                    String read = p.getProperty("version", "").trim();
                    if (!read.isEmpty() && !read.startsWith("$")) v = read;
                }
            } catch (Exception ignored) {}
            addonVersion = v;
        }
        return addonVersion;
    }

    private static String encodeHeader(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static void applyClientHeaders(BiConsumer<String, String> setter) {
        try {
            setter.accept("client-username", encodeHeader(getUsername()));
            String session = getSessionUuid();
            if (session != null) setter.accept("client-uuid", session);
            String world = getWorldUuid();
            if (world != null && !world.equalsIgnoreCase(getLocalUuid()) && !world.equalsIgnoreCase(session)) setter.accept("client-world-uuid", world);
            setter.accept("addon-version", getAddonVersion());
        } catch (Exception ignored) {}
    }

    private static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c);
            }
        }
        return sb.toString();
    }

    public static void init() {
        try {
            String uuid = getLocalUuid();
            File dir = new File(Minecraft.getInstance().gameDirectory, "config/figura-unchained");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            TOKEN_FILE = new File(dir, uuid + ".txt");
            if (TOKEN_FILE.exists()) {
                JWT = Files.readString(TOKEN_FILE.toPath()).trim();
            } else {
                JWT = "";
            }
        } catch (Exception e) {}
    }

    private static String getHash(String password, String username) {
        try {
            byte[] salt = username.getBytes(StandardCharsets.UTF_8);
            int iterations = 100000; 
            int keyLength = 256;

            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, keyLength);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = skf.generateSecret(spec).getEncoded();

            StringBuilder hexString = new StringBuilder(2 * hash.length);
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }
    
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private static String getBaseUrl() {
        String url = HttpAPIAccessor.invokeGetUri("").toString();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/api")) {
            url = url.substring(0, url.length() - 4);
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private static JsonObject parseObject(String text) {
        try {
            JsonElement element = JsonParser.parseString(text);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String stringField(JsonObject json, String key) {
        if (json == null || !json.has(key) || !json.get(key).isJsonPrimitive()) {
            return null;
        }
        return json.get(key).getAsString();
    }

    private static void writeToken(File file, String token) throws IOException {
        Path path = file.toPath();
        if (!Files.exists(path)) {
            Files.createFile(path);
        }
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
        }
        Files.writeString(path, token);
    }

    public static int checkVersion() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/api/version"))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject json = parseObject(response.body());
                if (json != null && json.has("unchained_api") && json.get("unchained_api").isJsonPrimitive()) {
                    try {
                        return json.get("unchained_api").getAsInt() == 1 ? 2 : 1;
                    } catch (NumberFormatException ignored) {
                    }
                }
                return 1;
            }
            return 0;
        } catch (Exception e) {}
        return 0;
    }

    public static int checkVersionResolving() {
        UnchainedEndpoint.awaitReady();
        if (UnchainedEndpoint.isUndecided()) {
            return 4;
        }
        if (UnchainedEndpoint.isBlocked()) {
            return 3;
        }
        int status = checkVersion();
        if (status == 0) {
            boolean changed = UnchainedEndpoint.retryAfterFailure();
            if (UnchainedEndpoint.isBlocked()) {
                return 3;
            }
            if (changed) {
                status = checkVersion();
            }
        }
        return status;
    }

    public static void performAuth(String action, String password) {
        Thread worker = new Thread(() -> {
            int vStatus = checkVersionResolving();
            if (vStatus == 0) {
                UnchainedToast.send("Connection Error", "Backend is offline or unreachable.", FiguraToast.ToastType.ERROR);
                return;
            } else if (vStatus == 1) {
                UnchainedToast.send("Version Mismatch", "Incompatible Unchained Backend.", FiguraToast.ToastType.ERROR);
                return;
            } else if (vStatus == 3) {
                UnchainedToast.send("Connection blocked", "Approve the new address first.", FiguraToast.ToastType.ERROR);
                return;
            } else if (vStatus == 4) {
                UnchainedToast.send("Answer required", "Choose Yes or No.", FiguraToast.ToastType.ERROR);
                ConsentScreen.openLater();
                return;
            }
            try {
                String uuid = getLocalUuid();
                String username = Minecraft.getInstance().getUser().getName();
                
                String hash = getHash(password, username);
                String json = "{\"uuid\":\"" + uuid + "\", \"username\":\"" + jsonEscape(username) + "\", \"hash\":\"" + hash + "\"}";
                
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(getBaseUrl() + "/api/auth/" + action))
                        .timeout(REQUEST_TIMEOUT)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json));
                applyClientHeaders(requestBuilder::header);
                HttpRequest request = requestBuilder.build();

                HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                String body = response.body();
                
                if (response.statusCode() == 200) {
                    JsonObject reply = parseObject(body);
                    String token = stringField(reply, "token");
                    if (token == null || token.isEmpty()) {
                        UnchainedToast.send("Auth Failed", "Malformed server response.", FiguraToast.ToastType.ERROR);
                        return;
                    }
                    JWT = token;
                    
                    File dir = new File(Minecraft.getInstance().gameDirectory, "config/figura-unchained");
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    TOKEN_FILE = new File(dir, uuid + ".txt");
                    writeToken(TOKEN_FILE, token);
                    
                    String toastTitle = stringField(reply, "title");
                    String toastDesc = stringField(reply, "message");
                    if (toastTitle == null) toastTitle = "Auth";
                    if (toastDesc == null) toastDesc = "Success";
                    
                    UnchainedToast.send(toastTitle, toastDesc, action.equals("register") ? FiguraToast.ToastType.WARNING : FiguraToast.ToastType.DEFAULT);
                    
                    org.figuramc.figura.backend2.NetworkStuff.reAuth();
                } else {
                    String errorMsg = stringField(parseObject(body), "error");
                    if (errorMsg == null) {
                        errorMsg = body.length() > 100 ? body.substring(0, 100) : body;
                    }
                    UnchainedToast.send("Auth Failed", response.statusCode() + ": " + errorMsg, FiguraToast.ToastType.ERROR);
                }
            } catch (Exception e) {
                String errMsg = e.getMessage();
                if (errMsg == null) {
                    errMsg = "Backend is offline or unreachable.";
                }
                UnchainedToast.send("Auth Error", errMsg, FiguraToast.ToastType.ERROR);
            }
        }, "figura-unchained-auth");
        worker.setDaemon(true);
        worker.start();
    }
}
