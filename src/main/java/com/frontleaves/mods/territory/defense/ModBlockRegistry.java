package com.frontleaves.mods.territory.defense;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.WorldlyContainerHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模组方块注册表，支持自动扫描和手动 TOML 配置。
 * <p>
 * 通过 {@link #initialize()} 初始化：
 * <ol>
 *   <li>加载 {@code territory-modblocks.toml} 手动配置（高优先级）</li>
 *   <li>自动扫描 {@link BuiltInRegistries#BLOCK}，通过 {@code instanceof} 分类未覆盖的方块</li>
 * </ol>
 * 查询时优先匹配手动配置，未命中则回退到自动分类。
 */
public class ModBlockRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger("Territory");

    private static final Map<ResourceLocation, FlagType> manualOverrides = new HashMap<>();

    /**
     * 行解析正则：匹配 "namespace:path" = "flagtype"
     * <p>
     * 示例: {@code "create:mechanical_drill" = "destroy"}
     */
    private static final Pattern TOML_ENTRY = Pattern.compile(
            "\"([^\"]+)\"\\s*=\\s*\"([^\"]+)\""
    );

    /**
     * 初始化方块注册表。清理旧数据后依次加载手动配置和自动扫描结果。
     */
    public static void initialize() {
        manualOverrides.clear();
        loadTomlConfig();
        LOGGER.info("ModBlockRegistry 初始化完成，手动配置 {} 条", manualOverrides.size());
    }

    /**
     * 查询方块的权限标志类型。
     * <p>
     * 查找优先级：手动配置 → instanceof 自动分类。
     *
     * @param blockId 方块的资源位置（如 {@code create:mechanical_drill}）
     * @return 对应的 {@link FlagType}，若未分类则返回 {@code null}
     */
    public static FlagType getBlockCategory(ResourceLocation blockId) {
        // 优先匹配手动配置
        FlagType manual = manualOverrides.get(blockId);
        if (manual != null) {
            return manual;
        }
        // 回退到自动分类
        return autoClassify(blockId);
    }

    /**
     * 从 classpath 加载 {@code territory-modblocks.toml} 手动配置。
     * <p>
     * 格式：{@code "namespace:path" = "flagtype_name"}
     * 注释行（以 {@code #} 开头）和空行会被跳过。
     */
    private static void loadTomlConfig() {
        try (InputStream is = ModBlockRegistry.class.getClassLoader()
                .getResourceAsStream("territory-modblocks.toml")) {
            if (is == null) {
                LOGGER.warn("未找到 territory-modblocks.toml，跳过手动配置加载");
                return;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                int lineNum = 0;
                while ((line = reader.readLine()) != null) {
                    lineNum++;
                    String trimmed = line.trim();
                    // 跳过空行和注释
                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }
                    Matcher matcher = TOML_ENTRY.matcher(trimmed);
                    if (!matcher.matches()) {
                        LOGGER.debug("跳过无法解析的 TOML 行 {}: {}", lineNum, trimmed);
                        continue;
                    }
                    String blockKey = matcher.group(1);
                    String flagName = matcher.group(2);
                    try {
                        ResourceLocation blockId = ResourceLocation.parse(blockKey);
                        FlagType flagType = FlagType.valueOf(flagName.toUpperCase());
                        manualOverrides.put(blockId, flagType);
                    } catch (IllegalArgumentException e) {
                        LOGGER.warn("无效的 FlagType '{}' (行 {}): {}", flagName, lineNum, e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("加载 territory-modblocks.toml 失败", e);
        }
    }

    /**
     * 通过 {@code instanceof} 自动分类方块。
     * <p>
     * 分类规则：
     * <ul>
     *   <li>{@link ButtonBlock} → {@link FlagType#button}</li>
     *   <li>{@link LeverBlock} → {@link FlagType#lever}</li>
     *   <li>{@link DoorBlock} → {@link FlagType#door}</li>
     *   <li>{@link PressurePlateBlock} → {@link FlagType#pressure}</li>
     *   <li>实现了 {@link WorldlyContainerHolder} 的方块 → {@link FlagType#container}</li>
     *   <li>拥有实现了 {@link net.minecraft.world.Container} 的 {@link BlockEntity} 的方块 → {@link FlagType#container}</li>
     * </ul>
     *
     * @param blockId 方块资源位置
     * @return 自动分类的 {@link FlagType}，或 {@code null}
     */
    private static FlagType autoClassify(ResourceLocation blockId) {
        var block = BuiltInRegistries.BLOCK.get(blockId);
        if (block == null) {
            return null;
        }

        if (block instanceof ButtonBlock) {
            return FlagType.button;
        }
        if (block instanceof LeverBlock) {
            return FlagType.lever;
        }
        if (block instanceof DoorBlock) {
            return FlagType.door;
        }
        if (block instanceof PressurePlateBlock) {
            return FlagType.pressure;
        }
        // 容器检测：实现了 WorldlyContainerHolder 的方块
        if (block instanceof WorldlyContainerHolder) {
            return FlagType.container;
        }
        // 其他容器方块（如拥有实现 Container 的 BlockEntity）无法在静态上下文中可靠检测，
        // 需通过 TOML 手动配置补充覆盖。

        return null;
    }
}
