package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.handler.common.StatsSyncHandler;
import io.zershyan.damagestats.stats.DamageTracker;
import io.zershyan.damagestats.stats.ServerStats;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/** GUI 设置 Overlay 的目标实体类型，空值表示恢复为玩家对任意目标的伤害 */
public record OverlayTargetTypePacket(Optional<ResourceLocation> targetType) implements CustomPacketPayload {
    public static final OverlayTargetTypePacket CLEAR = new OverlayTargetTypePacket(Optional.empty());

    public static final Type<OverlayTargetTypePacket> TYPE = new Type<>(DamageStats.id("overlay_target_type"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OverlayTargetTypePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), OverlayTargetTypePacket::targetType,
                    OverlayTargetTypePacket::new);

    public static OverlayTargetTypePacket of(ResourceLocation targetType) {
        return new OverlayTargetTypePacket(Optional.of(targetType));
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OverlayTargetTypePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if(!(context.player() instanceof ServerPlayer player)) return;
            DamageTracker tracker = ServerStats.tracker();
            if(tracker == null) return;
            tracker.setOverlayTargetType(player.getUUID(), payload.targetType().orElse(null));
            StatsSyncHandler.pushTo(tracker, player);
        });
    }
}
