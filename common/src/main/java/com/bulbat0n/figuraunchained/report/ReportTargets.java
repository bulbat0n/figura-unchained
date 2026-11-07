package com.bulbat0n.figuraunchained.report;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.figuramc.figura.avatar.Avatar;
import org.figuramc.figura.avatar.AvatarManager;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

public final class ReportTargets {
    public static final int REASON_MAX = 200;

    private ReportTargets() {
    }

    public static List<String> withAvatars() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null || minecraft.player == null) {
            return List.of();
        }
        UUID self = minecraft.player.getUUID();
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Avatar avatar : AvatarManager.getLoadedAvatars()) {
            if (avatar.owner.equals(self)) {
                continue;
            }
            PlayerInfo info = connection.getPlayerInfo(avatar.owner);
            if (info != null) {
                names.add(info.getProfile().getName());
            }
        }
        return new ArrayList<>(names);
    }

    public static String cleanReason(String reason) {
        if (reason == null) {
            return null;
        }
        String trimmed = reason.trim();
        if (trimmed.isEmpty() || trimmed.codePointCount(0, trimmed.length()) > REASON_MAX) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isISOControl(trimmed.charAt(i))) {
                return null;
            }
        }
        return trimmed;
    }
}
