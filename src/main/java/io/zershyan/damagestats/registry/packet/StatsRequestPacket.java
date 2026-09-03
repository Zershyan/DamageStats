package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.config.DSConfig;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.EntityRef;
import io.zershyan.damagestats.stats.ServerStats;
import io.zershyan.damagestats.stats.filter.DamageTypeGrouping;
import io.zershyan.damagestats.stats.filter.EntitySelector;
import io.zershyan.damagestats.stats.filter.StatsFilter;
import io.zershyan.damagestats.stats.filter.StatsSubjectSlot;
import io.zershyan.damagestats.stats.view.StatsViewBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 客户端打开界面或改动筛选条件时请求一次完整数据，避免把大包塞进每秒的推送里 */
public record StatsRequestPacket(
        StatsFilter filter,
        StatsSubjectSlot subjectSlot,
        DamageTypeGrouping typeGrouping
) implements CustomPacketPayload {
    private static final long REQUEST_COOLDOWN_TICKS = 2;
    private static final Map<UUID, Long> LAST_REQUEST_TICKS = new HashMap<>();

    public static final Type<StatsRequestPacket> TYPE = new Type<>(DamageStats.id("stats_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StatsRequestPacket> STREAM_CODEC = StreamCodec.composite(
            StatsFilter.STREAM_CODEC, StatsRequestPacket::filter,
            StatsSubjectSlot.STREAM_CODEC, StatsRequestPacket::subjectSlot,
            DamageTypeGrouping.STREAM_CODEC, StatsRequestPacket::typeGrouping,
            StatsRequestPacket::new
    );

    public static StatsRequestPacket forSelf(EntityRef self) {
        return new StatsRequestPacket(
                StatsFilter.fromSource(new EntitySelector.Instance(self)),
                StatsSubjectSlot.SOURCE,
                DamageTypeGrouping.CATEGORY);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StatsRequestPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if(!(context.player() instanceof ServerPlayer player)) return;
            DamageTracker tracker = ServerStats.tracker();
            if(tracker == null) return;
            if(rateLimited(player)) return;
            StatsFilter filter = authorize(player, payload.filter());
            StatsSubjectSlot subjectSlot = payload.subjectSlot().resolve(filter);
            PacketDistributor.sendToPlayer(player, new StatsSnapshotPacket(
                    StatsViewBuilder.snapshotFor(tracker, player, filter, subjectSlot, payload.typeGrouping())));
        });
    }

    public static void clearRateLimits() {
        LAST_REQUEST_TICKS.clear();
    }

    private static boolean rateLimited(ServerPlayer player) {
        long now = player.level().getGameTime();
        UUID playerId = player.getUUID();
        Long previous = LAST_REQUEST_TICKS.get(playerId);
        if(previous != null && now >= previous && now - previous < REQUEST_COOLDOWN_TICKS) return true;
        LAST_REQUEST_TICKS.put(playerId, now);
        return false;
    }

    /**
     * 两道闸：四个实体槽全空是全局统计，只放管理员过；
     * 关掉公开之后来源和目标至少有一头得是自己——查别人打的东西，对手明细里就会露出其他玩家的输出。
     * 不放行时降级成「我造成的」而不是报错。
     */
    private static StatsFilter authorize(ServerPlayer player, StatsFilter filter) {
        boolean operator = player.hasPermissions(2);
        EntitySelector self = new EntitySelector.Instance(EntityRef.of(player));
        if(filter.source().isEmpty() && filter.target().isEmpty()) {
            return operator ? filter : StatsFilter.fromSource(self);
        }
        if(DSConfig.PublicStats.get() || operator) return filter;
        boolean involvesSelf = filter.source().filter(self::equals).isPresent()
                || filter.target().filter(self::equals).isPresent();
        return involvesSelf ? filter : StatsFilter.fromSource(self);
    }
}
