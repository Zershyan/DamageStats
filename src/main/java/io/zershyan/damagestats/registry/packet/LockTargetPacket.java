package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.EntityRef;
import io.zershyan.damagestats.stats.ServerStats;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 准星锁定 Overlay 的常显目标。用网络实体 id 而不是 UUID：客户端射线检测直接拿得到，
 * 服务端一句 getEntity 就能反查，还比 UUID 省字节。
 */
public record LockTargetPacket(int entityId) implements CustomPacketPayload {
    /** 解除锁定 */
    public static final LockTargetPacket CLEAR = new LockTargetPacket(-1);

    public static final Type<LockTargetPacket> TYPE = new Type<>(DamageStats.id("lock_target"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LockTargetPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LockTargetPacket::entityId,
            LockTargetPacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(LockTargetPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if(!(context.player() instanceof ServerPlayer player)) return;
            DamageTracker tracker = ServerStats.tracker();
            if(tracker == null) return;
            if(payload.entityId() < 0) {
                tracker.lockTarget(player.getUUID(), null);
                return;
            }
            Entity target = player.level().getEntity(payload.entityId());
            if(target != null) tracker.lockTarget(player.getUUID(), EntityRef.of(target));
        });
    }
}
