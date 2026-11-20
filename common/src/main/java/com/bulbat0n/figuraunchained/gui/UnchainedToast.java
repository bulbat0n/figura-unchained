package com.bulbat0n.figuraunchained.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.figuramc.figura.gui.FiguraToast;

import java.util.concurrent.ConcurrentLinkedQueue;

public final class UnchainedToast {
    private record Entry(Object title, Object message, FiguraToast.ToastType type) {
    }

    private static final ConcurrentLinkedQueue<Entry> QUEUE = new ConcurrentLinkedQueue<>();
    private static final int MAX_QUEUED = 16;
    private static final int MAX_SERVER_TEXT = 100;

    private UnchainedToast() {
    }

    public static void send(Object title, Object message, FiguraToast.ToastType type) {
        if (QUEUE.size() < MAX_QUEUED) {
            QUEUE.add(new Entry(title, message, type));
        }
    }

    public static void sendServer(String title, String message, FiguraToast.ToastType type) {
        send(Component.literal(cleanServerText(title)), Component.literal(cleanServerText(message)), type);
    }

    public static String cleanServerText(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length() && out.length() < MAX_SERVER_TEXT; i++) {
            char c = text.charAt(i);
            if (c == '\u00A7') {
                i++;
            } else if (Character.isISOControl(c)) {
                out.append(' ');
            } else {
                out.append(c);
            }
        }
        return out.toString().trim();
    }

    public static void flush() {
        if (QUEUE.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getOverlay() != null || minecraft.font == null) {
            return;
        }
        Entry entry;
        while ((entry = QUEUE.poll()) != null) {
            FiguraToast.sendToast(entry.title(), entry.message(), entry.type());
        }
    }
}
