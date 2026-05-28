package com.frontleaves.mods.territory.network;

import com.frontleaves.mods.territory.Territory;
import com.frontleaves.mods.territory.client.ClientSelectionState;
import com.frontleaves.mods.territory.client.TerritoryBookScreen;
import com.frontleaves.mods.territory.geometry.AABB;
import com.frontleaves.mods.territory.storage.ServerSelectionCache;
import com.frontleaves.mods.territory.storage.TerritoryData;
import com.frontleaves.mods.territory.storage.TerritoryDataManager;
import com.frontleaves.mods.territory.util.TeleportCooldownManager;
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

        registrar.playToServer(
            TerritoryBookOpenPayload.TYPE,
            TerritoryBookOpenPayload.STREAM_CODEC,
            TerritoryPayloads::handleBookOpen
        );

        registrar.playToClient(
            TerritoryListResponsePayload.TYPE,
            TerritoryListResponsePayload.STREAM_CODEC,
            TerritoryPayloads::handleListResponse
        );

        registrar.playToServer(
            TerritoryTeleportRequestPayload.TYPE,
            TerritoryTeleportRequestPayload.STREAM_CODEC,
            TerritoryPayloads::handleTeleportRequest
        );

        registrar.playToServer(
            TerritoryWandSpawnSetPayload.TYPE,
            TerritoryWandSpawnSetPayload.STREAM_CODEC,
            TerritoryPayloads::handleWandSpawnSet
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

    private static void handleBookOpen(TerritoryBookOpenPayload payload, IPayloadContext context) {
        var player = context.player();
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        String playerUuid = serverPlayer.getUUID().toString();
        var manager = TerritoryDataManager.getInstance();

        java.util.List<TerritoryData> ownedData = manager.findTerritoriesByOwner(playerUuid);
        java.util.List<TerritoryListResponsePayload.TerritoryEntry> owned = new java.util.ArrayList<>();
        for (TerritoryData td : ownedData) {
            owned.add(new TerritoryListResponsePayload.TerritoryEntry(
                td.uuid(), td.name(), td.worldKey(),
                TerritoryDataManager.calculateArea(td), td.hasSpawn()
            ));
        }

        java.util.List<TerritoryData> sharedData = manager.findTerritoriesByMember(playerUuid);
        java.util.List<TerritoryListResponsePayload.TerritoryEntry> shared = new java.util.ArrayList<>();
        for (TerritoryData td : sharedData) {
            shared.add(new TerritoryListResponsePayload.TerritoryEntry(
                td.uuid(), td.name(), td.worldKey(),
                TerritoryDataManager.calculateArea(td), td.hasSpawn()
            ));
        }

        PacketDistributor.sendToPlayer(serverPlayer,
            new TerritoryListResponsePayload(owned, shared));
    }

    private static void handleListResponse(TerritoryListResponsePayload payload, IPayloadContext context) {
        if (context.player() instanceof net.minecraft.client.player.LocalPlayer) {
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                if (net.minecraft.client.Minecraft.getInstance().screen instanceof TerritoryBookScreen screen) {
                    screen.setTerritoryData(payload.owned(), payload.shared());
                }
            });
        }
    }

    private static void handleTeleportRequest(TerritoryTeleportRequestPayload payload, IPayloadContext context) {
        var player = context.player();
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        var cooldownMgr = TeleportCooldownManager.getInstance();
        java.util.UUID playerUuid = serverPlayer.getUUID();

        if (!cooldownMgr.canTeleport(playerUuid)) {
            int remaining = cooldownMgr.getRemainingSeconds(playerUuid);
            serverPlayer.displayClientMessage(
                Component.translatable("territory.msg.teleport_cooldown", remaining)
                    .withStyle(ChatFormatting.RED), false);
            return;
        }

        String territoryUuid = payload.territoryUuid();
        var manager = TerritoryDataManager.getInstance();
        TerritoryData target = null;

        for (TerritoryData td : manager.getAllTerritories()) {
            if (td.uuid().equals(territoryUuid)) {
                target = td;
                break;
            }
        }

        if (target == null) {
            serverPlayer.displayClientMessage(
                Component.translatable("territory.msg.teleport_not_found")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }

        String playerUuidStr = playerUuid.toString();
        boolean hasPermission = target.ownerUuid().equals(playerUuidStr);
        if (!hasPermission) {
            for (TerritoryData.MemberEntry member : target.members()) {
                if (member.playerUuid().equals(playerUuidStr) &&
                    ("admin".equals(member.role()) || "member".equals(member.role()))) {
                    hasPermission = true;
                    break;
                }
            }
        }

        if (!hasPermission) {
            serverPlayer.displayClientMessage(
                Component.translatable("territory.msg.teleport_no_permission")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }

        net.minecraft.world.phys.Vec3 destination;
        if (target.hasSpawn()) {
            destination = new net.minecraft.world.phys.Vec3(target.spawnX(), target.spawnY(), target.spawnZ());
        } else {
            var serverLevel = serverPlayer.serverLevel();
            destination = TerritoryDataManager.calculateFallbackSpawn(target, serverLevel);
        }

        serverPlayer.teleportTo(destination.x, destination.y, destination.z);
        cooldownMgr.setCooldown(playerUuid, 30);

        serverPlayer.displayClientMessage(
            Component.translatable("territory.msg.teleport_success", target.name())
                .withStyle(ChatFormatting.GREEN), false);
    }

    private static void handleWandSpawnSet(TerritoryWandSpawnSetPayload payload, IPayloadContext context) {
        var player = context.player();
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        var pos = payload.clickedPos();
        var manager = TerritoryDataManager.getInstance();

        var territoryOpt = manager.findTerritoryAt(
            serverPlayer.level().dimension().location().toString(),
            pos.getX(), pos.getY(), pos.getZ()
        );

        if (territoryOpt.isEmpty()) {
            serverPlayer.displayClientMessage(
                Component.translatable("territory.msg.spawn_not_in_territory")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }

        var territory = territoryOpt.get();
        String playerUuid = serverPlayer.getUUID().toString();

        boolean canSetSpawn;
        if (payload.isAdminWand()) {
            canSetSpawn = true;
        } else {
            canSetSpawn = territory.ownerUuid().equals(playerUuid);
            if (!canSetSpawn) {
                for (TerritoryData.MemberEntry member : territory.members()) {
                    if (member.playerUuid().equals(playerUuid) && "admin".equals(member.role())) {
                        canSetSpawn = true;
                        break;
                    }
                }
            }
        }

        if (!canSetSpawn) {
            serverPlayer.displayClientMessage(
                Component.translatable("territory.msg.spawn_no_permission")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }

        territory.spawnX(pos.getX() + 0.5);
        territory.spawnY(pos.getY() + 1.0);
        territory.spawnZ(pos.getZ() + 0.5);

        manager.updateTerritory(territory);

        serverPlayer.displayClientMessage(
            Component.translatable("territory.msg.spawn_set_success", territory.name())
                .withStyle(ChatFormatting.GREEN), false);
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
