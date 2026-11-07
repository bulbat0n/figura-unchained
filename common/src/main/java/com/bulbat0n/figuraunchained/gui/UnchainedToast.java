package com.bulbat0n.figuraunchained.gui;

import net.minecraft.client.Minecraft;
import org.figuramc.figura.gui.FiguraToast;

import java.util.concurrent.ConcurrentLinkedQueue;

public final class UnchainedToast {
    private record Entry(Object title, Object message, FiguraToast.ToastType type) {
    }

    private static final ConcurrentLinkedQueue<Entry> QUEUE = new ConcurrentLinkedQueue<>();
    private static final int MAX_QUEUED = 16;

    private UnchainedToast() {
    }

    public static void send(Object title, Object message, FiguraToast.ToastType type) {
        if (QUEUE.size() < MAX_QUEUED) {
            QUEUE.add(new Entry(title, message, type));
        }
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
