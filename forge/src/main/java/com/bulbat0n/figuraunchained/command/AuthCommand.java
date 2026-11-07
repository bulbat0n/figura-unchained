package com.bulbat0n.figuraunchained.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.bulbat0n.figuraunchained.auth.UnchainedAuth;
import com.bulbat0n.figuraunchained.gui.ConsentScreen;
import com.bulbat0n.figuraunchained.report.ReportTargets;
import net.minecraft.commands.SharedSuggestionProvider;
import org.figuramc.figura.gui.FiguraToast;

public class AuthCommand {
    @SubscribeEvent
    public static void onClientCommandRegister(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("figura-unchained")
            .then(Commands.literal("register")
                .then(Commands.argument("password", StringArgumentType.string())
                    .executes(context -> {
                        String password = StringArgumentType.getString(context, "password");
                        if (password.length() < 6) {
                            FiguraToast.sendToast("Password must be at least 6 characters.", "", FiguraToast.ToastType.ERROR);
                            return 0;
                        }
                        UnchainedAuth.performAuth("register", password);
                        return 1;
                    })
                )
            )
            .then(Commands.literal("login")
                .then(Commands.argument("password", StringArgumentType.string())
                    .executes(context -> {
                        String password = StringArgumentType.getString(context, "password");
                        UnchainedAuth.performAuth("login", password);
                        return 1;
                    })
                )
            )
            .then(Commands.literal("report")
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(ReportTargets.withAvatars(), builder))
                    .then(Commands.argument("reason", StringArgumentType.greedyString())
                        .executes(context -> {
                            UnchainedAuth.performReport(StringArgumentType.getString(context, "player"), StringArgumentType.getString(context, "reason"));
                            return 1;
                        })
                    )
                )
            )
            .then(Commands.literal("consent")
                .executes(context -> {
                    ConsentScreen.openLater();
                    return 1;
                })
            )
        );
    }
}
