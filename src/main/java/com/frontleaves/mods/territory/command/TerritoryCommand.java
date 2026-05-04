package com.frontleaves.mods.territory.command;

import com.frontleaves.mods.territory.Territory;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public class TerritoryCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("territory")
                        .then(Commands.literal("wand")
                                .executes(context -> giveWand(context.getSource())))
                        .then(Commands.literal("admin")
                                .requires(source -> source.hasPermission(2))
                                .executes(context -> giveAdminWand(context.getSource())))
                        .then(Commands.literal("info")
                                .executes(context -> {
                                    context.getSource().sendSuccess(
                                            () -> Component.translatable("territory.cmd.not_implemented").withStyle(ChatFormatting.YELLOW), false);
                                    return 0;
                                }))
                        .then(Commands.literal("list")
                                .executes(context -> {
                                    context.getSource().sendSuccess(
                                            () -> Component.translatable("territory.cmd.not_implemented").withStyle(ChatFormatting.YELLOW), false);
                                    return 0;
                                }))
        );
    }

    private static int giveWand(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            ItemStack wand = new ItemStack(Territory.TERRITORY_WAND.get());
            if (!player.getInventory().add(wand)) {
                player.drop(wand, false);
            }
            source.sendSuccess(
                    () -> Component.translatable("territory.cmd.wand_given").withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        source.sendFailure(Component.translatable("territory.cmd.players_only").withStyle(ChatFormatting.RED));
        return 0;
    }

    private static int giveAdminWand(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            ItemStack wand = new ItemStack(Territory.ADMIN_TERRITORY_WAND.get());
            if (!player.getInventory().add(wand)) {
                player.drop(wand, false);
            }
            source.sendSuccess(
                    () -> Component.translatable("territory.cmd.admin_wand_given").withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        source.sendFailure(Component.translatable("territory.cmd.players_only").withStyle(ChatFormatting.RED));
        return 0;
    }
}
