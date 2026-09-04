package io.zershyan.damagestats.stats.view;

import io.zershyan.damagestats.stats.focus.FocusSelectionSlot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/** 固定 50 行的服务端实体候选页面。 */
public record EntityChoicePage(
        FocusSelectionSlot slot,
        long focusVersion,
        int requestId,
        boolean allowed,
        Optional<ResourceLocation> typeFilter,
        List<EntityChoiceView> entries,
        boolean hasNext
) {
    public static final int PAGE_SIZE = 50;
    private static final StreamCodec<RegistryFriendlyByteBuf, List<EntityChoiceView>> LIST_CODEC =
            EntityChoiceView.STREAM_CODEC.apply(ByteBufCodecs.list(PAGE_SIZE));

    public static final StreamCodec<RegistryFriendlyByteBuf, EntityChoicePage> STREAM_CODEC =
            StreamCodec.of(EntityChoicePage::encode, EntityChoicePage::decode);

    private static void encode(RegistryFriendlyByteBuf buf, EntityChoicePage page) {
        FocusSelectionSlot.STREAM_CODEC.encode(buf, page.slot);
        buf.writeVarLong(page.focusVersion);
        buf.writeVarInt(page.requestId);
        buf.writeBoolean(page.allowed);
        ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).encode(buf, page.typeFilter);
        LIST_CODEC.encode(buf, page.entries);
        buf.writeBoolean(page.hasNext);
    }

    private static EntityChoicePage decode(RegistryFriendlyByteBuf buf) {
        return new EntityChoicePage(
                FocusSelectionSlot.STREAM_CODEC.decode(buf),
                buf.readVarLong(),
                buf.readVarInt(),
                buf.readBoolean(),
                ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC).decode(buf),
                LIST_CODEC.decode(buf),
                buf.readBoolean());
    }
}
