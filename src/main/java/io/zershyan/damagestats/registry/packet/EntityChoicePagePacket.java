package io.zershyan.damagestats.registry.packet;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.client.ClientStats;
import io.zershyan.damagestats.network.CustomPacketPayload;
import io.zershyan.damagestats.network.codec.StreamCodec;
import io.zershyan.damagestats.stats.view.EntityChoicePage;
import net.minecraft.network.FriendlyByteBuf;
import org.jetbrains.annotations.NotNull;

/** 实体候选分页响应。 */
public record EntityChoicePagePacket(EntityChoicePage page) implements CustomPacketPayload {
    public static final Type<EntityChoicePagePacket> TYPE = new Type<>(DamageStats.id("entity_choice_page"));
    public static final StreamCodec<FriendlyByteBuf, EntityChoicePagePacket> STREAM_CODEC = StreamCodec.composite(
            EntityChoicePage.STREAM_CODEC, EntityChoicePagePacket::page,
            EntityChoicePagePacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext context) {
        context.enqueueWork(() -> ClientStats.acceptEntityChoices(page()));
    }
}
