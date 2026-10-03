package com.bulbat0n.figuraunchained.auth;

import net.minecraft.client.MinecraftClient;
import org.figuramc.figura.gui.FiguraToast;
import com.bulbat0n.figuraunchained.mixin.HttpAPIAccessor;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.nio.file.Files;

public class UnchainedAuth {
    public static String JWT = "";
    private static File TOKEN_FILE = null;

    public static void init() {
        try {
            String uuid = MinecraftClient.getInstance().getSession().getUuidOrNull().toString();
            File dir = new File(MinecraftClient.getInstance().runDirectory, "config/figura-unchained");
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
    
    public static int checkVersion() {
        try {
            String baseUrl = HttpAPIAccessor.invokeGetUri("").toString().replace("/api", "");
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/version"))
                    .GET()
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                String body = response.body().replaceAll("\\s+", "");
                if (body.contains("\"unchained_api\":1")) {
                    return 2;
                }
                return 1;
            }
            return 0;
        } catch (Exception e) {}
        return 0;
    }
    public static void performAuth(String action, String password) {
        new Thread(() -> {
            int vStatus = checkVersion();
            if (vStatus == 0) {
                FiguraToast.sendToast("Connection Error", "Backend is offline or unreachable.", FiguraToast.ToastType.ERROR);
                return;
            } else if (vStatus == 1) {
                FiguraToast.sendToast("Version Mismatch", "Incompatible Unchained Backend.", FiguraToast.ToastType.ERROR);
                return;
            }
            try {
                String uuid = MinecraftClient.getInstance().getSession().getUuidOrNull().toString();
                String username = MinecraftClient.getInstance().getSession().getUsername();
                
                String hash = getHash(password, username);
                String json = "{\"uuid\":\"" + uuid + "\", \"hash\":\"" + hash + "\"}";
                
                String baseUrl = HttpAPIAccessor.invokeGetUri("").toString().replace("/api", "");
                if (baseUrl.endsWith("/")) {
                    baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
                }
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + "/api/auth/" + action))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
                String body = response.body();
                
                if (response.statusCode() == 200) {
                    String token = body.split("\"token\"\\s*:\\s*\"")[1].split("\"")[0];
                    JWT = token;
                    
                    File dir = new File(MinecraftClient.getInstance().runDirectory, "config/figura-unchained");
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    TOKEN_FILE = new File(dir, uuid + ".txt");
                    Files.writeString(TOKEN_FILE.toPath(), token);
                    
                    String toastTitle = "Auth";
                    String toastDesc = "Success";
                    if (body.contains("\"title\"")) {
                        try { toastTitle = body.split("\"title\"\\s*:\\s*\"")[1].split("\"")[0]; } catch (Exception ignore) {}
                    }
                    if (body.contains("\"message\"")) {
                        try { toastDesc = body.split("\"message\"\\s*:\\s*\"")[1].split("\"")[0]; } catch (Exception ignore) {}
                    }
                    
                    FiguraToast.sendToast(toastTitle, toastDesc, action.equals("register") ? FiguraToast.ToastType.WARNING : FiguraToast.ToastType.DEFAULT);
                    
                    org.figuramc.figura.backend2.NetworkStuff.reAuth();
                } else {
                    String errorMsg = body;
                    if (body.contains("\"error\"")) {
                        try {
                            errorMsg = body.split("\"error\"\\s*:\\s*\"")[1].split("\"")[0];
                        } catch (Exception ignore) {}
                    }
                    FiguraToast.sendToast("Auth Failed", response.statusCode() + ": " + errorMsg, FiguraToast.ToastType.ERROR);
                }
            } catch (Exception e) {
                String errMsg = e.getMessage();
                if (errMsg == null) {
                    errMsg = "Backend is offline or unreachable.";
                }
                FiguraToast.sendToast("Auth Error", errMsg, FiguraToast.ToastType.ERROR);
            }
        }).start();
    }
}
