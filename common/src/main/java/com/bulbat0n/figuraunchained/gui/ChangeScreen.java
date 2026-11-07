package com.bulbat0n.figuraunchained.gui;

import com.bulbat0n.figuraunchained.endpoint.EndpointConstants;
import com.bulbat0n.figuraunchained.endpoint.EndpointResolver;
import com.bulbat0n.figuraunchained.endpoint.UnchainedEndpoint;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.figuramc.figura.backend2.NetworkStuff;

import java.util.ArrayList;
import java.util.List;

public class ChangeScreen extends Screen {
    private static final String TITLE_FIRST = "Public server address";
    private static final String TITLE_CHANGED = "Public server address changed";
    private static final int PANEL_MAX_WIDTH = 320;
    private static final int PARAGRAPH_GAP = 6;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 10;
    private static final int BODY_COLOR = 0xD8D8D8;
    private static final int ADDRESS_COLOR = 0xFFFF55;

    private final Screen next;
    private final EndpointResolver.Pending shown;
    private final boolean first;
    private final List<String> texts = new ArrayList<>();
    private final List<Integer> colors = new ArrayList<>();
    private final List<List<FormattedCharSequence>> body = new ArrayList<>();
    private int panelX;
    private int titleY;
    private int bodyY;

    public ChangeScreen(Screen next) {
        super(Component.literal(UnchainedEndpoint.approvedIp() == null ? TITLE_FIRST : TITLE_CHANGED));
        this.next = next;
        EndpointResolver.Pending pending = UnchainedEndpoint.pending();
        this.shown = pending;
        String previous = UnchainedEndpoint.approvedIp();
        this.first = previous == null;
        if (first) {
            add("The public Figura Unchained server is at the address below.", BODY_COLOR);
            add("Address: " + (pending == null ? "" : pending.ip), ADDRESS_COLOR);
        } else {
            add("The public Figura Unchained server has moved to a new address.", BODY_COLOR);
            add("Previous address: " + previous, BODY_COLOR);
            add("New address: " + (pending == null ? "" : pending.ip), ADDRESS_COLOR);
        }
        if (pending != null && !pending.note.isEmpty()) {
            add("Message from the maintainer: " + pending.note.replace("\u00a7", "").trim(), BODY_COLOR);
        }
        add((first ? "Step 2 of 2. " : "") + "Until you answer, the addon makes no connection to this address. If you choose Yes, your nickname, UUID, IP address, avatars, login token and password hash will be sent to it and to its hosting provider.", BODY_COLOR);
        add("If you choose No, the addon will only use " + EndpointConstants.LOCAL_HOST + ". You can change your answer at any time with /figura-unchained consent.", BODY_COLOR);
    }

    public static void openWhenReady() {
        Thread thread = new Thread(() -> {
            UnchainedEndpoint.awaitReady();
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> {
                if (minecraft.screen instanceof ConsentScreen || minecraft.screen instanceof ChangeScreen) {
                    return;
                }
                if (UnchainedEndpoint.pending() != null) {
                    minecraft.setScreen(new ChangeScreen(minecraft.screen));
                }
            });
        }, "figura-unchained-change");
        thread.setDaemon(true);
        thread.start();
    }

    private void add(String text, int color) {
        texts.add(text);
        colors.add(color);
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(width - 40, PANEL_MAX_WIDTH);
        panelX = (width - panelWidth) / 2;
        body.clear();
        int bodyHeight = 0;
        for (String text : texts) {
            List<FormattedCharSequence> lines = font.split(Component.literal(text), panelWidth);
            body.add(lines);
            bodyHeight += lines.size() * font.lineHeight + PARAGRAPH_GAP;
        }
        int titleHeight = font.lineHeight + 12;
        int total = titleHeight + bodyHeight + BUTTON_HEIGHT;
        int top = Math.max(8, (height - total) / 2);
        titleY = top;
        bodyY = top + titleHeight;
        int buttonY = Math.min(bodyY + bodyHeight, height - BUTTON_HEIGHT - 8);
        int buttonWidth = (panelWidth - BUTTON_GAP) / 2;
        addRenderableWidget(Button.builder(Component.literal(first ? "Yes, connect" : "Yes, switch"), button -> answer(true))
                .bounds(panelX, buttonY, buttonWidth, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.literal("No, use localhost"), button -> answer(false))
                .bounds(panelX + buttonWidth + BUTTON_GAP, buttonY, buttonWidth, BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xF0101018);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title.copy().withStyle(ChatFormatting.BOLD), width / 2, titleY, 0xFFFFFF);
        int y = bodyY;
        for (int i = 0; i < body.size(); i++) {
            for (FormattedCharSequence line : body.get(i)) {
                graphics.drawString(font, line, panelX, y, colors.get(i));
                y += font.lineHeight;
            }
            y += PARAGRAPH_GAP;
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void onClose() {
    }

    private void answer(boolean approve) {
        EndpointResolver resolver = UnchainedEndpoint.resolver();
        if (approve) {
            if (!resolver.approvePending(shown) && UnchainedEndpoint.pending() != null) {
                Minecraft.getInstance().setScreen(new ChangeScreen(next));
                return;
            }
        } else {
            resolver.declinePending();
        }
        NetworkStuff.reAuth();
        UnchainedEndpoint.refreshResources();
        Minecraft.getInstance().setScreen(next);
    }
}
