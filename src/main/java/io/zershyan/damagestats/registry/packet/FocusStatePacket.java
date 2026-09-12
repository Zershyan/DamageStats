package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.client.ClientStats;
import io.zershyan.damagestats.stats.focus.FocusChangeResult;
import io.zershyan.damagestats.stats.view.FocusSummary;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** 焦点设置后的完整回执，拒绝时仍携带当前实际生效的焦点，避免客户端误判。 */
public record FocusStatePacket(
        FocusChangeResult result,
        FocusSummary summary,
        int requestId
) implements CustomPacketPayload {
    public static final Type<FocusStatePacket> TYPE = new Type<>(DamageStats.id("focus_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, FocusStatePacket> STREAM_CODEC =
            StreamCodec.of(FocusStatePacket::encode, FocusStatePacket::decode);

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ClientStats.acceptFocusState(result(), summary(), requestId()));
    }

    private static void encode(RegistryFriendlyByteBuf buf, FocusStatePacket packet) {
        buf.writeEnum(packet.result);
        FocusSummary.STREAM_CODEC.encode(buf, packet.summary);
        buf.writeVarInt(packet.requestId);
    }

    private static FocusStatePacket decode(RegistryFriendlyByteBuf buf) {
        return new FocusStatePacket(buf.readEnum(FocusChangeResult.class), FocusSummary.STREAM_CODEC.decode(buf), buf.readVarInt());
    }
}
