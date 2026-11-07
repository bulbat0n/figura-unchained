package com.bulbat0n.figuraunchained.gui;

import com.bulbat0n.figuraunchained.endpoint.ConsentState;
import com.bulbat0n.figuraunchained.endpoint.UnchainedEndpoint;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.figuramc.figura.backend2.NetworkStuff;
import org.figuramc.figura.config.Configs;
import org.figuramc.figura.gui.FiguraToast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConsentScreen extends Screen {
    private static final String TITLE = "Public Figura Unchained server";
    private static final String[] PARAGRAPHS = {
            "Figura Unchained can connect you to a public server so your avatar is visible to other players. You must answer before the addon makes any network connection.",
            "Step 1 of 2. If you choose Yes, the addon downloads a small signed file from GitHub that tells it where the public server is (GitHub sees your IP address). Nothing is sent to the public server yet.",
            "Step 2 of 2. You will then be shown the server address and asked to approve it. Only after you approve do the server and its hosting provider receive your nickname, UUID, IP address, avatars, login token and password hash.",
            "If you choose No: the addon only connects to a server on your own computer (localhost:55000).",
            "You can change your answer at any time with /figura-unchained consent. To use a different server, enter its address in the Figura Cloud IP field in Figura's settings."
    };
    private static final int PANEL_MAX_WIDTH = 320;
    private static final int PARAGRAPH_GAP = 6;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 10;
    private static final int BODY_COLOR = 0xD8D8D8;
    private static final int NOTICE_COLOR = 0xFFAA00;
    private static final int NOTICE_VALUE_MAX = 48;
    private static final AtomicBoolean HINTED = new AtomicBoolean();

    private final Screen next;
    private final boolean overwrite;
    private final String notice;
    private final List<List<FormattedCharSequence>> body = new ArrayList<>();
    private final List<FormattedCharSequence> noticeLines = new ArrayList<>();
    private int panelX;
    private int titleY;
    private int bodyY;

    public ConsentScreen(Screen next) {
        super(Component.literal(TITLE));
        this.next = next;
        this.overwrite = !UnchainedEndpoint.store().fileExists();
        String current = Configs.SERVER_IP.value;
        this.notice = overwrite && !UnchainedEndpoint.isDefaultValue(current) ? buildNotice(current) : null;
    }

    private static String buildNotice(String current) {
        String shown = current.length() > NOTICE_VALUE_MAX ? current.substring(0, NOTICE_VALUE_MAX - 3) + "..." : current;
        return "Your current Figura Cloud IP (" + shown + ") will be replaced with 'default' once, whichever option you choose. "
                + "'default' means the public server if you choose Yes, or localhost:55000 if you choose No.";
    }

    public static boolean needsWindow() {
        return UnchainedEndpoint.isUndecided();
    }

    public static void hintIfNeeded() {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof TitleScreen || screen instanceof ConsentScreen || screen instanceof ChangeScreen) {
            return;
        }
        if (HINTED.compareAndSet(false, true)) {
            UnchainedToast.send("Choose a server", "/figura-unchained consent", FiguraToast.ToastType.WARNING);
        }
    }

    public static void openLater() {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> {
                if (minecraft.screen instanceof ConsentScreen || minecraft.screen instanceof ChangeScreen) {
                    return;
                }
                if (UnchainedEndpoint.pending() != null) {
                    minecraft.setScreen(new ChangeScreen(minecraft.screen));
                } else {
                    minecraft.setScreen(new ConsentScreen(minecraft.screen));
                }
            });
        }, "figura-unchained-consent");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(width - 40, PANEL_MAX_WIDTH);
        panelX = (width - panelWidth) / 2;
        body.clear();
        noticeLines.clear();
        int bodyHeight = 0;
        for (String paragraph : PARAGRAPHS) {
            List<FormattedCharSequence> lines = font.split(Component.literal(paragraph), panelWidth);
            body.add(lines);
            bodyHeight += lines.size() * font.lineHeight + PARAGRAPH_GAP;
        }
        if (notice != null) {
            noticeLines.addAll(font.split(Component.literal(notice), panelWidth));
            bodyHeight += noticeLines.size() * font.lineHeight + PARAGRAPH_GAP;
        }
        int titleHeight = font.lineHeight + 12;
        int total = titleHeight + bodyHeight + BUTTON_HEIGHT;
        int top = Math.max(8, (height - total) / 2);
        titleY = top;
        bodyY = top + titleHeight;
        int buttonY = Math.min(bodyY + bodyHeight, height - BUTTON_HEIGHT - 8);
        int buttonWidth = (panelWidth - BUTTON_GAP) / 2;
        addRenderableWidget(Button.builder(Component.literal("Yes, connect"), button -> answer(ConsentState.Decision.YES))
                .bounds(panelX, buttonY, buttonWidth, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.literal("No, use localhost"), button -> answer(ConsentState.Decision.NO))
                .bounds(panelX + buttonWidth + BUTTON_GAP, buttonY, buttonWidth, BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xF0101018);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, Component.literal(TITLE).withStyle(ChatFormatting.BOLD), width / 2, titleY, 0xFFFFFF);
        int y = bodyY;
        for (List<FormattedCharSequence> lines : body) {
            for (FormattedCharSequence line : lines) {
                graphics.drawString(font, line, panelX, y, BODY_COLOR);
                y += font.lineHeight;
            }
            y += PARAGRAPH_GAP;
        }
        for (FormattedCharSequence line : noticeLines) {
            graphics.drawString(font, line, panelX, y, NOTICE_COLOR);
            y += font.lineHeight;
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void onClose() {
    }

    private void answer(ConsentState.Decision decision) {
        if (overwrite) {
            UnchainedEndpoint.overwriteServerField();
        }
        UnchainedEndpoint.decide(decision);
        NetworkStuff.reAuth();
        Minecraft.getInstance().setScreen(next);
        if (decision == ConsentState.Decision.YES) {
            ChangeScreen.openWhenReady();
        }
    }
}
