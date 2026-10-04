package com.bulbat0n.figuraunchained.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import com.bulbat0n.figuraunchained.auth.UnchainedAuth;
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
        );
    }
}
