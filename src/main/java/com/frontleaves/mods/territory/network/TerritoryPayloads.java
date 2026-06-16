package com.frontleaves.mods.territory.network;

import com.frontleaves.mods.territory.Territory;
import com.frontleaves.mods.territory.client.ClientSelectionState;
import com.frontleaves.mods.territory.client.TerritoryBookScreen;
import com.frontleaves.mods.territory.client.TerritoryTableScreen;
import com.frontleaves.mods.territory.config.TerritoryConfig;
import com.frontleaves.mods.territory.defense.TerritoryLogEntry;
import com.frontleaves.mods.territory.defense.TerritoryRole;
import com.frontleaves.mods.territory.geometry.AABB;
import com.frontleaves.mods.territory.gui.TerritoryBookMenu;
import com.frontleaves.mods.territory.storage.ServerSelectionCache;
import com.frontleaves.mods.territory.storage.TerritoryData;
import com.frontleaves.mods.territory.storage.TerritoryDataManager;
import com.frontleaves.mods.territory.network.TerritoryNearbyRequestPayload;
import com.frontleaves.mods.territory.network.TerritoryNearbySyncPayload;
import com.frontleaves.mods.territory.util.TeleportCooldownManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
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

        registrar.playToServer(
            TerritoryWandShiftPayload.TYPE,
            TerritoryWandShiftPayload.STREAM_CODEC,
            TerritoryPayloads::handleWandShift
        );

        registrar.playToClient(
            SelectionClearPayload.TYPE,
            SelectionClearPayload.STREAM_CODEC,
            TerritoryPayloads::handleClearSelection
        );

        registrar.playToClient(
            TerritoryNearbySyncPayload.TYPE,
            TerritoryNearbySyncPayload.STREAM_CODEC,
            TerritoryPayloads::handleNearbySync
        );

        registrar.playToServer(
            TerritoryNearbyRequestPayload.TYPE,
            TerritoryNearbyRequestPayload.STREAM_CODEC,
            TerritoryPayloads::handleNearbyRequest
        );

        registrar.playToServer(
            TerritoryGuiActionPayload.TYPE,
            TerritoryGuiActionPayload.STREAM_CODEC,
            TerritoryPayloads::handleGuiAction
        );

        registrar.playToClient(
            TerritoryGuiSyncPayload.TYPE,
            TerritoryGuiSyncPayload.STREAM_CODEC,
            TerritoryPayloads::handleGuiSync
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

        // Sync nearby territory boundaries to the client
        var manager = TerritoryDataManager.getInstance();
        String worldKey = payload.dimensionKey();
        String playerUuid = serverPlayer.getUUID().toString();
        int syncDistance = TerritoryConfig.SYNC_DISTANCE.get();
        var nearby = manager.getTerritoriesNearby(worldKey, box.minX(), box.minZ(), syncDistance, playerUuid, payload.admin());
        PacketDistributor.sendToPlayer(serverPlayer, new TerritoryNearbySyncPayload(nearby));

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

        serverPlayer.openMenu(new SimpleMenuProvider(
            (containerId, playerInventory, p) -> new TerritoryBookMenu(containerId, playerInventory, serverPlayer),
            Component.translatable("gui.territory.book.title")
        ));

        // 容器建立后推送列表数据（延迟一 tick，确保 Menu 已注册到玩家）
        serverPlayer.server.execute(() -> {
            if (serverPlayer.containerMenu instanceof TerritoryBookMenu bookMenu) {
                bookMenu.syncBookList();
            }
        });
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

    private static void handleNearbySync(TerritoryNearbySyncPayload payload, IPayloadContext context) {
        if (context.player() instanceof net.minecraft.client.player.LocalPlayer) {
            ClientSelectionState.get().setNearbyTerritories(payload.boundaries());
            ClientSelectionState.getAdmin().setNearbyTerritories(payload.boundaries());
        }
    }

    private static void handleNearbyRequest(TerritoryNearbyRequestPayload payload, IPayloadContext context) {
        var player = context.player();
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        // 校验手持领地法杖（防止伪造请求）
        ItemStack mainHand = serverPlayer.getMainHandItem();
        boolean isAdminWand = mainHand.is(Territory.ADMIN_TERRITORY_WAND.get());
        boolean isNormalWand = mainHand.is(Territory.TERRITORY_WAND.get());
        if (!isAdminWand && !isNormalWand) return;

        // 使用服务端追踪的玩家位置（不信任客户端坐标）
        BlockPos serverPos = serverPlayer.blockPosition();
        String actualDimension = serverPlayer.level().dimension().location().toString();

        // 校验维度匹配
        if (!actualDimension.equals(payload.dimensionKey())) return;

        // 校验 isAdminWand 与实际手持物品一致
        if (payload.isAdminWand() != isAdminWand) return;

        // 查询附近领地
        var manager = TerritoryDataManager.getInstance();
        int syncDistance = TerritoryConfig.SYNC_DISTANCE.get();
        String playerUuid = serverPlayer.getUUID().toString();
        var nearby = manager.getTerritoriesNearby(actualDimension, serverPos.getX(), serverPos.getZ(),
            syncDistance, playerUuid, isAdminWand);

        // 截断至最多 64 条
        if (nearby.size() > 64) {
            nearby = nearby.subList(0, 64);
        }

        PacketDistributor.sendToPlayer(serverPlayer, new TerritoryNearbySyncPayload(nearby));
    }

    private static void handleClearSelection(SelectionClearPayload payload, IPayloadContext context) {
        ClientSelectionState.get().clearSelection();
        ClientSelectionState.getAdmin().clearSelection();
    }

    private static void handleWandShift(TerritoryWandShiftPayload payload, IPayloadContext context) {
        var player = context.player();
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        BlockPos pos = payload.clickedPos();
        var manager = TerritoryDataManager.getInstance();
        String worldKey = serverPlayer.level().dimension().location().toString();

        var territoryOpt = manager.findTerritoryAt(worldKey, pos.getX(), pos.getY(), pos.getZ());

        if (territoryOpt.isEmpty()) {
            // 不在任何领地内 → 清除选区
            ServerSelectionCache.remove(serverPlayer.getUUID());
            PacketDistributor.sendToPlayer(serverPlayer, new SelectionClearPayload());
            serverPlayer.displayClientMessage(
                Component.translatable("territory.msg.selection_cleared").withStyle(ChatFormatting.YELLOW), false);
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
                for (var member : territory.members()) {
                    if (member.playerUuid().equals(playerUuid) && "admin".equals(member.role())) {
                        canSetSpawn = true;
                        break;
                    }
                }
            }
        }

        if (!canSetSpawn) {
            serverPlayer.displayClientMessage(
                Component.translatable("territory.msg.spawn_no_permission").withStyle(ChatFormatting.RED), false);
            return;
        }

        // 设置传送点 — 复用与 handleWandSpawnSet 相同的逻辑
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

    private static void handleGuiAction(TerritoryGuiActionPayload payload, IPayloadContext context) {
        var player = context.player();
        if (!(player instanceof ServerPlayer serverPlayer)) return;

        var manager = TerritoryDataManager.getInstance();
        var territoryOpt = manager.findTerritoryByUuid(payload.territoryUuid());

        if (territoryOpt.isEmpty()) return;

        TerritoryData territory = territoryOpt.get();
        String action = payload.actionType();

        switch (action) {
            case "SET_FLAG" -> {
                String flagName = payload.targetData().get("flag");
                String flagValue = payload.targetData().get("value");
                if (flagName != null && flagValue != null) {
                    territory.flags().put(flagName, Boolean.parseBoolean(flagValue));
                    manager.updateTerritory(territory);
                    manager.addLog(territory.uuid(), new TerritoryLogEntry(
                        serverPlayer.getUUID().toString(), "SET_FLAG",
                        java.time.Instant.now(), flagName + "=" + flagValue
                    ));
                }
            }

            case "ADD_MEMBER" -> {
                String memberUuid = payload.targetData().get("playerUuid");
                String role = payload.targetData().get("role");
                if (memberUuid != null && role != null) {
                    boolean exists = territory.members().stream()
                        .anyMatch(m -> m.playerUuid().equals(memberUuid));
                    if (!exists) {
                        manager.addMember(territory.uuid(),
                            new TerritoryData.MemberEntry(memberUuid, role));
                        manager.addLog(territory.uuid(), new TerritoryLogEntry(
                            serverPlayer.getUUID().toString(), "ADD_MEMBER",
                            java.time.Instant.now(), memberUuid + " as " + role
                        ));
                    }
                }
            }

            case "REMOVE_MEMBER" -> {
                String removeUuid = payload.targetData().get("playerUuid");
                if (removeUuid != null && !removeUuid.equals(territory.ownerUuid())) {
                    manager.removeMember(territory.uuid(), removeUuid);
                    manager.addLog(territory.uuid(), new TerritoryLogEntry(
                        serverPlayer.getUUID().toString(), "REMOVE_MEMBER",
                        java.time.Instant.now(), removeUuid
                    ));
                }
            }

            case "SET_ROLE" -> {
                String roleUuid = payload.targetData().get("playerUuid");
                String newRole = payload.targetData().get("role");
                if (roleUuid != null && newRole != null) {
                    TerritoryRole tr = TerritoryRole.valueOf(newRole.toUpperCase());
                    manager.setMemberRole(territory.uuid(), roleUuid, tr);
                    manager.addLog(territory.uuid(), new TerritoryLogEntry(
                        serverPlayer.getUUID().toString(), "SET_ROLE",
                        java.time.Instant.now(), roleUuid + " to " + newRole
                    ));
                }
            }

            case "RENAME" -> {
                String newName = payload.targetData().get("name");
                if (newName != null && territory.ownerUuid().equals(serverPlayer.getUUID().toString())) {
                    territory.name(newName);
                    manager.updateTerritory(territory);
                    manager.addLog(territory.uuid(), new TerritoryLogEntry(
                        serverPlayer.getUUID().toString(), "RENAME",
                        java.time.Instant.now(), newName
                    ));
                }
            }

            case "DELETE" -> {
                if (territory.ownerUuid().equals(serverPlayer.getUUID().toString())) {
                    manager.deleteTerritory(territory.ownerUuid(), territory.uuid());
                }
            }

            case "TRANSFER" -> {
                String newOwnerUuid = payload.targetData().get("newOwnerUuid");
                if (newOwnerUuid != null && territory.ownerUuid().equals(serverPlayer.getUUID().toString())) {
                    String oldOwnerUuid = territory.ownerUuid();
                    territory.ownerUuid(newOwnerUuid);
                    territory.members().add(new TerritoryData.MemberEntry(oldOwnerUuid, "admin"));
                    manager.updateTerritory(territory);
                    manager.addLog(territory.uuid(), new TerritoryLogEntry(
                        serverPlayer.getUUID().toString(), "TRANSFER",
                        java.time.Instant.now(), "to " + newOwnerUuid
                    ));
                }
            }

            default -> { /* unknown action */ }
        }
    }

    private static void handleGuiSync(TerritoryGuiSyncPayload payload, IPayloadContext context) {
        if (context.player() instanceof net.minecraft.client.player.LocalPlayer) {
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                var screen = net.minecraft.client.Minecraft.getInstance().screen;
                if (payload.pageType().equals("BOOK_LIST")) {
                    // 领地之书列表同步
                    if (screen instanceof TerritoryBookScreen bookScreen) {
                        var menu = bookScreen.getMenu();
                        menu.receiveBookList(
                            parseBookList(payload.pageData().get("owned")),
                            parseBookList(payload.pageData().get("shared"))
                        );
                        bookScreen.onSyncReceived();
                    }
                } else if (screen instanceof TerritoryTableScreen tableScreen) {
                    tableScreen.receiveSyncData(payload.pageType(), payload.pageData());
                }
            });
        }
    }

    /** 将 sync payload 中的字符串编码解析为 List<String[]>（领地之书列表）。 */
    private static java.util.List<String[]> parseBookList(Object encoded) {
        java.util.List<String[]> result = new java.util.ArrayList<>();
        if (encoded instanceof String s && !s.isEmpty()) {
            for (String line : s.split("\n")) {
                if (!line.isEmpty()) {
                    result.add(TerritoryBookMenu.decodeEntry(line));
                }
            }
        }
        return result;
    }
}
