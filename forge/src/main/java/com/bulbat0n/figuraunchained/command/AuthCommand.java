package com.bulbat0n.figuraunchained.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import com.bulbat0n.figuraunchained.auth.UnchainedAuth;
import com.bulbat0n.figuraunchained.gui.ConsentScreen;
import com.bulbat0n.figuraunchained.report.ReportTargets;
import com.bulbat0n.figuraunchained.util.CommandUsage;
import org.figuramc.figura.gui.FiguraToast;

public class AuthCommand {
    private static int usage(CommandSourceStack source, String text) {
        source.sendFailure(Component.literal(text));
        return 0;
    }

    @SubscribeEvent
    public static void onClientCommandRegister(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("figura-unchained")
            .executes(context -> usage(context.getSource(), CommandUsage.ROOT))
            .then(Commands.literal("register")
                .executes(context -> usage(context.getSource(), CommandUsage.REGISTER))
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
                .executes(context -> usage(context.getSource(), CommandUsage.LOGIN))
                .then(Commands.argument("password", StringArgumentType.string())
                    .executes(context -> {
                        String password = StringArgumentType.getString(context, "password");
                        UnchainedAuth.performAuth("login", password);
                        return 1;
                    })
                )
            )
            .then(Commands.literal("report")
                .executes(context -> usage(context.getSource(), CommandUsage.REPORT))
                .then(Commands.argument("player", StringArgumentType.word())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(ReportTargets.withAvatars(), builder))
                    .executes(context -> usage(context.getSource(), CommandUsage.REPORT))
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
            .then(Commands.argument("args", StringArgumentType.greedyString())
                .executes(context -> usage(context.getSource(), CommandUsage.ROOT))
            )
        );
    }
}
