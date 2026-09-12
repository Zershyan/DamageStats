package io.zershyan.damagestats.client;

import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.registry.packet.ExportChunkPacket;
import io.zershyan.damagestats.registry.packet.ExportStartPacket;
import io.zershyan.damagestats.stats.save.StatsExporter;
import io.zershyan.damagestats.stats.view.ExportSection;
import io.zershyan.damagestats.stats.view.StatsSnapshot;
import io.zershyan.damagestats.stats.view.StatsView;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

/** 客户端重组受限导出分块，并在网络完成后异步写入本地游戏目录。 */
public final class ClientExportManager {
    private static final long EXPORT_TIMEOUT_MILLIS = 10_000; // 10秒超时
    private static int requestId;
    private static long connectionEpoch;
    private static @Nullable ExportAssembly assembly;
    private static boolean writing;
    private static long assemblyStartTime;

    public static int nextRequestId() {
        return ++requestId;
    }

    public static boolean isBusy() {
        checkTimeout();
        return assembly != null || writing;
    }

    public static void acceptStart(ExportStartPacket start) {
        if(start.requestId() != requestId) return;
        if(!start.accepted()) {
            assembly = null;
            notifyPlayer(DSKeyLang.StatsPrivate.copy());
            return;
        }
        assembly = new ExportAssembly(start);
        assemblyStartTime = System.currentTimeMillis();
        if(start.chunkCount() == 0) finish(start.requestId());
    }

    public static void acceptChunk(ExportChunkPacket chunk) {
        ExportAssembly current = assembly;
        if(current == null || chunk.requestId() != requestId || chunk.requestId() != current.start().requestId()) return;
        if(chunk.index() < 0 || chunk.index() >= current.start().chunkCount()) return;
        current.chunks().put(chunk.index(), chunk);
        if(current.chunks().size() == current.start().chunkCount()) finish(chunk.requestId());
    }

    public static void clear() {
        assembly = null;
        connectionEpoch++;
    }

    /** 检查导出是否超时，超时则清空并通知玩家 */
    private static void checkTimeout() {
        ExportAssembly current = assembly;
        if(current == null) return;
        long elapsed = System.currentTimeMillis() - assemblyStartTime;
        if(elapsed > EXPORT_TIMEOUT_MILLIS) {
            assembly = null;
            notifyPlayer(DSKeyLang.ExportFailed.copy());
        }
    }

    private static void finish(int completedRequestId) {
        ExportAssembly completed = assembly;
        if(completed == null || completed.start().requestId() != completedRequestId) return;
        if(completed.chunks().size() != completed.start().chunkCount()) return;
        assembly = null;
        StatsSnapshot snapshot = completed.snapshot();
        Path baseDirectory = Minecraft.getInstance().gameDirectory.toPath().resolve("damagestats");
        long exportEpoch = connectionEpoch;
        writing = true;
        CompletableFuture.supplyAsync(() -> StatsExporter.export(snapshot, baseDirectory))
                .whenComplete((directory, error) -> Minecraft.getInstance().execute(() -> {
                    writing = false;
                    if(exportEpoch != connectionEpoch) return;
                    if(error != null || directory == null) {
                        notifyPlayer(DSKeyLang.ExportFailed.copy());
                        return;
                    }
                    notifyPlayer(DSKeyLang.ExportDone.get(directory.toString()));
                }));
    }

    private static void notifyPlayer(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if(player != null) player.displayClientMessage(message, false);
    }

    private record ExportAssembly(ExportStartPacket start, Map<Integer, ExportChunkPacket> chunks) {
        private ExportAssembly(ExportStartPacket start) {
            this(start, new TreeMap<>());
        }

        private StatsSnapshot snapshot() {
            EnumMap<ExportSection, List<io.zershyan.damagestats.stats.view.GroupView>> groups =
                    new EnumMap<>(ExportSection.class);
            List<io.zershyan.damagestats.stats.view.SessionView> history = new java.util.ArrayList<>();
            chunks.values().forEach(chunk -> {
                if(chunk.section() == ExportSection.HISTORY) history.addAll(chunk.history());
                else groups.merge(chunk.section(), chunk.groups(), (left, right) -> {
                    java.util.ArrayList<io.zershyan.damagestats.stats.view.GroupView> merged = new java.util.ArrayList<>(left);
                    merged.addAll(right);
                    return List.copyOf(merged);
                });
            });
            StatsView session = new StatsView(start.sessionMetrics(),
                    groups.getOrDefault(ExportSection.SESSION_DAMAGE_TYPE, List.of()),
                    groups.getOrDefault(ExportSection.SESSION_DIRECT_SOURCE, List.of()),
                    groups.getOrDefault(ExportSection.SESSION_OPPONENT, List.of()));
            StatsView lifetime = new StatsView(start.lifetimeMetrics(),
                    groups.getOrDefault(ExportSection.LIFETIME_DAMAGE_TYPE, List.of()),
                    groups.getOrDefault(ExportSection.LIFETIME_DIRECT_SOURCE, List.of()),
                    groups.getOrDefault(ExportSection.LIFETIME_OPPONENT, List.of()));
            return new StatsSnapshot(start.subjectName(), start.filter(), start.filterLabels(), session, lifetime,
                    List.copyOf(history));
        }
    }
}
