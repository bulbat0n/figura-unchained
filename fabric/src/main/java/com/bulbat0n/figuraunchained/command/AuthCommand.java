package com.bulbat0n.figuraunchained.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import com.bulbat0n.figuraunchained.auth.UnchainedAuth;
import com.bulbat0n.figuraunchained.gui.ConsentScreen;
import com.bulbat0n.figuraunchained.report.ReportTargets;
import net.minecraft.commands.SharedSuggestionProvider;
import org.figuramc.figura.gui.FiguraToast;

public class AuthCommand implements ClientCommandRegistrationCallback {

    @Override
    public void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess) {
        dispatcher.register(ClientCommandManager.literal("figura-unchained")
            .then(ClientCommandManager.literal("register")
                .then(ClientCommandManager.argument("password", StringArgumentType.string())
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
            .then(ClientCommandManager.literal("login")
                .then(ClientCommandManager.argument("password", StringArgumentType.string())
                    .executes(context -> {
                        String password = StringArgumentType.getString(context, "password");
                        UnchainedAuth.performAuth("login", password);
                        return 1;
                    })
                )
            )
            .then(ClientCommandManager.literal("report")
                .then(ClientCommandManager.argument("player", StringArgumentType.word())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(ReportTargets.withAvatars(), builder))
                    .then(ClientCommandManager.argument("reason", StringArgumentType.greedyString())
                        .executes(context -> {
                            UnchainedAuth.performReport(StringArgumentType.getString(context, "player"), StringArgumentType.getString(context, "reason"));
                            return 1;
                        })
                    )
                )
            )
            .then(ClientCommandManager.literal("consent")
                .executes(context -> {
                    ConsentScreen.openLater();
                    return 1;
                })
            )
        );
    }
}
