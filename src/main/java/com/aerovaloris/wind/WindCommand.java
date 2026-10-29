package com.aerovaloris.wind;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /wind                      – pokaż aktualny wiatr
 * /wind set <kurs> [siła]    – ustaw i zablokuj wiatr (kurs: 0=N, 90=E, 180=S, 270=W – w KTÓRĄ stronę wieje)
 * /wind random               – odblokuj i wylosuj nowy wiatr
 */
public final class WindCommand {
    private WindCommand() {}

    public static void register(final RegisterCommandsEvent event) {
        final CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("wind")
                .executes(WindCommand::show)
                .then(Commands.literal("set").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("bearing", DoubleArgumentType.doubleArg(0, 360))
                                .executes(c -> set(c, DoubleArgumentType.getDouble(c, "bearing"), -1))
                                .then(Commands.argument("strength", DoubleArgumentType.doubleArg(0, 1))
                                        .executes(c -> set(c, DoubleArgumentType.getDouble(c, "bearing"),
                                                DoubleArgumentType.getDouble(c, "strength"))))))
                .then(Commands.literal("random").requires(s -> s.hasPermission(2))
                        .executes(WindCommand::random)));
    }

    private static int show(final CommandContext<CommandSourceStack> c) {
        final ServerLevel level = c.getSource().getLevel();
        final WindData data = WindData.get(level);
        final int pct = (int) Math.round(data.effectiveStrength(level) * 100);
        c.getSource().sendSuccess(() -> Component.translatable("aeronauticsvaloris.command.wind.show",
                WindMath.bearingName(data.bearing()).copy().withStyle(ChatFormatting.AQUA),
                String.format("%.0f", data.bearing()),
                pct,
                data.locked()
                        ? Component.translatable("aeronauticsvaloris.command.wind.locked")
                        : Component.translatable("aeronauticsvaloris.command.wind.next", data.ticksUntilChange() / 20)
        ), false);
        return 1;
    }

    private static int set(final CommandContext<CommandSourceStack> c, final double bearing, final double strength) {
        final ServerLevel level = c.getSource().getLevel();
        final WindData data = WindData.get(level);
        final double s = strength < 0 ? data.baseStrength() : strength;
        data.set(bearing, s, true, true);
        WindManager.sync(level, true);
        return show(c);
    }

    private static int random(final CommandContext<CommandSourceStack> c) {
        final ServerLevel level = c.getSource().getLevel();
        final WindData data = WindData.get(level);
        data.unlock(level.getRandom());
        WindManager.sync(level, false);
        return show(c);
    }
}
