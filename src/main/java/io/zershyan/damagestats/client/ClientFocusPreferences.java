package io.zershyan.damagestats.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import io.zershyan.damagestats.registry.packet.FocusSetPacket;
import io.zershyan.damagestats.stats.EntityRef;
import io.zershyan.damagestats.stats.filter.EntitySelector;
import io.zershyan.damagestats.stats.focus.FocusChangeResult;
import io.zershyan.damagestats.stats.focus.FocusScopeView;
import io.zershyan.damagestats.stats.view.FocusSummary;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** 只保存类型焦点；实体实例和直接来源下钻权限均限定在当前连接。 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public final class ClientFocusPreferences {
    private record SavedFocus(
            @Nullable String sourceTypeId,
            @Nullable String targetTypeId,
            boolean sourceUnrestricted
    ) {}

    private record PendingFocus(int requestId) {}

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FILE_NAME = "damagestats-focuses.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type FILE_TYPE = new TypeToken<Map<String, SavedFocus>>() {}.getType();
    private static final Map<String, SavedFocus> FOCUSES = new HashMap<>();

    private static boolean loaded;
    private static int nextRequestId;
    private static @Nullable String appliedKey;
    private static @Nullable PendingFocus pending;

    private ClientFocusPreferences() {}

    /** 手动设置和自动恢复均从这里发包，使保存动作只发生在服务端接受后。 */
    public static void requestFocus(Optional<EntitySelector> source, Optional<EntitySelector> target,
                                    boolean sourceIsDirectSource) {
        int requestId = ++nextRequestId;
        pending = new PendingFocus(requestId);
        PacketDistributor.sendToServer(new FocusSetPacket(source, target, sourceIsDirectSource, requestId));
    }

    /** 收到登录后的完整焦点摘要时，按服务器和世界标识恢复上次的类型焦点。 */
    public static void applyIfReady() {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if(player == null) return;
        String key = connectionKey(minecraft, ClientStats.summary().worldId());
        if(key == null || key.equals(appliedKey)) return;
        appliedKey = key;
        SavedFocus saved = focuses().get(key);
        if(saved == null) return;
        Optional<EntitySelector> sourceType = selector(saved.sourceTypeId());
        Optional<EntitySelector> targetType = selector(saved.targetTypeId());
        if(sourceType.isEmpty() && targetType.isEmpty() && !saved.sourceUnrestricted()) return;
        EntitySelector self = new EntitySelector.Instance(EntityRef.of(player));
        Optional<EntitySelector> source = saved.sourceUnrestricted()
                ? Optional.empty()
                : sourceType.isPresent() ? sourceType : Optional.of(self);
        requestFocus(source, targetType, false);
    }

    public static void acceptFocusState(FocusChangeResult result, FocusSummary summary, int requestId) {
        if(pending == null || pending.requestId() != requestId) return;
        pending = null;
        if(result != FocusChangeResult.ACCEPTED) return;
        String key = connectionKey(Minecraft.getInstance(), summary.worldId());
        if(key == null) return;
        remember(key, summary.scope());
    }

    /** 断线时清除瞬态请求和已应用标记，但保留类型偏好供下次同一世界恢复。 */
    public static void clearSession() {
        appliedKey = null;
        pending = null;
    }

    private static Map<String, SavedFocus> focuses() {
        if(loaded) return FOCUSES;
        loaded = true;
        Path file = file();
        if(!Files.exists(file)) {
            write();
            return FOCUSES;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<String, SavedFocus> restored = GSON.fromJson(reader, FILE_TYPE);
            if(restored != null) FOCUSES.putAll(restored);
        } catch (IOException | RuntimeException e) {
            LOGGER.error("读取类型焦点偏好失败，保留空偏好。删掉 {} 可在下次启动时重新生成", FILE_NAME, e);
        }
        return FOCUSES;
    }

    private static void remember(String key, FocusScopeView scope) {
        // 实例焦点只在当前连接有效，恢复时必须回到当前玩家，不能把它误存成全局来源。
        boolean sourceUnrestricted = scope.source().isEmpty() && !scope.sourceIsDirectSource();
        Optional<EntitySelector> source = scope.sourceIsDirectSource() ? Optional.empty() : scope.source();
        String sourceType = typeId(source).orElse(null);
        String targetType = typeId(scope.target()).orElse(null);
        if(sourceType == null && targetType == null) focuses().remove(key);
        else focuses().put(key, new SavedFocus(sourceType, targetType, sourceUnrestricted));
        write();
    }

    private static Optional<String> typeId(Optional<EntitySelector> selector) {
        return selector.flatMap(value -> value instanceof EntitySelector.Type(ResourceLocation typeId)
                ? Optional.of(typeId.toString()) : Optional.empty());
    }

    private static Optional<EntitySelector> selector(@Nullable String typeId) {
        if(typeId == null) return Optional.empty();
        ResourceLocation parsed = ResourceLocation.tryParse(typeId);
        return parsed == null ? Optional.empty() : Optional.of(new EntitySelector.Type(parsed));
    }

    private static @Nullable String connectionKey(Minecraft minecraft, String worldId) {
        if(worldId.isEmpty()) return null;
        var currentServer = minecraft.getCurrentServer();
        String server = currentServer == null ? "singleplayer" : currentServer.ip;
        return server + "|" + worldId;
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    private static void write() {
        Path file = file();
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                GSON.toJson(FOCUSES, FILE_TYPE, writer);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.error("写出类型焦点偏好失败：{}", file, e);
        } finally {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException e) {
                LOGGER.warn("清理类型焦点偏好临时文件失败：{}", temporary, e);
            }
        }
    }
}
