package com.frontleaves.mods.territory.network;

import com.frontleaves.mods.territory.Territory;
import com.frontleaves.mods.territory.client.ClientSelectionState;
import com.frontleaves.mods.territory.geometry.AABB;
import com.frontleaves.mods.territory.storage.ServerSelectionCache;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * 领地模组网络包注册与处理器
 *
 * <p>通过 {@code modEventBus.addListener(TerritoryPayloads::register)} 注册，
 * 在 {@link RegisterPayloadHandlersEvent} 中完成 C→S / S→C 双向 payload 注册。</p>
 */
public class TerritoryPayloads {

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");

        registrar.playToServer(
            SelectionUpdatePayload.TYPE,
            SelectionUpdatePayload.STREAM_CODEC,
            TerritoryPayloads::handleSelectionUpdate
        );

        registrar.playToClient(
            SelectionResponsePayload.TYPE,
            SelectionResponsePayload.STREAM_CODEC,
            TerritoryPayloads::handleSelectionResponse
        );
    }

    private static void handleSelectionUpdate(SelectionUpdatePayload payload, IPayloadContext context) {
        var player = context.player();
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        if (payload.admin() && !serverPlayer.hasPermissions(2)) {
            PacketDistributor.sendToPlayer(serverPlayer,
                new SelectionResponsePayload(false, "territory.msg.admin_only", List.of()));
            return;
        }

        BlockPos pos1 = payload.pos1();
        BlockPos pos2 = payload.pos2();

        AABB box = AABB.from(pos1, pos2);
        long volume = box.volume();

        if (!payload.admin()) {
            if (volume < 4) {
                PacketDistributor.sendToPlayer(serverPlayer,
                    new SelectionResponsePayload(false, "territory.msg.volume_too_small", List.of()));
                return;
            }
            if (volume > 100000) {
                PacketDistributor.sendToPlayer(serverPlayer,
                    new SelectionResponsePayload(false, "territory.msg.volume_too_large",
                        List.of(String.valueOf(volume))));
                return;
            }
        }

        PacketDistributor.sendToPlayer(serverPlayer,
            new SelectionResponsePayload(true, "territory.msg.validated",
                List.of(String.valueOf(volume))));

        ServerSelectionCache.put(
            serverPlayer.getUUID(),
            pos1, pos2,
            payload.dimensionKey(),
            true
        );
    }

    private static void handleSelectionResponse(SelectionResponsePayload payload, IPayloadContext context) {
        if (context.player() instanceof net.minecraft.client.player.LocalPlayer localPlayer) {
            ItemStack mainHand = localPlayer.getMainHandItem();
            boolean isAdminWand = mainHand.is(Territory.ADMIN_TERRITORY_WAND.get());
            ClientSelectionState state = isAdminWand ? ClientSelectionState.getAdmin() : ClientSelectionState.get();
            state.setValidated(payload.valid());

            Component msg = payload.args().isEmpty()
                    ? Component.translatable(payload.message())
                    : Component.translatable(payload.message(), payload.args().toArray());
            ChatFormatting color = payload.valid() ? ChatFormatting.GREEN : ChatFormatting.RED;
            context.player().displayClientMessage(msg.copy().withStyle(color), false);
        }
    }
}
