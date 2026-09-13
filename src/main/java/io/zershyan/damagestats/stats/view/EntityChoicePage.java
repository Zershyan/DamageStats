package io.zershyan.damagestats.stats.view;

import io.zershyan.damagestats.network.codec.ByteBufCodecs;
import io.zershyan.damagestats.network.codec.StreamCodec;
import io.zershyan.damagestats.stats.focus.FocusSelectionSlot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/** 固定 50 行的服务端实体候选页面。 */
public record EntityChoicePage(
        FocusSelectionSlot slot,
        long focusVersion,
        int requestId,
        EntityChoiceSort sort,
        long snapshotId,
        String cursor,
        String nextCursor,
        boolean allowed,
        Optional<ResourceLocation> typeFilter,
        List<EntityChoiceView> entries,
        boolean hasNext
) {
    public static final int PAGE_SIZE = 50;
    private static final StreamCodec<FriendlyByteBuf, List<EntityChoiceView>> LIST_CODEC =
            EntityChoiceView.STREAM_CODEC.apply(ByteBufCodecs.list(PAGE_SIZE));

    public static final StreamCodec<FriendlyByteBuf, EntityChoicePage> STREAM_CODEC =
            StreamCodec.of(EntityChoicePage::encode, EntityChoicePage::decode);

    private static void encode(FriendlyByteBuf buf, EntityChoicePage page) {
        FocusSelectionSlot.STREAM_CODEC.encode(buf, page.slot);
        buf.writeVarLong(page.focusVersion);
        buf.writeVarInt(page.requestId);
        EntityChoiceSort.STREAM_CODEC.encode(buf, page.sort);
        buf.writeVarLong(page.snapshotId);
        buf.writeUtf(page.cursor, 128);
        buf.writeUtf(page.nextCursor, 128);
        buf.writeBoolean(page.allowed);
        ByteBufCodecs.optional(ByteBufCodecs.RESOURCE_LOCATION).encode(buf, page.typeFilter);
        LIST_CODEC.encode(buf, page.entries);
        buf.writeBoolean(page.hasNext);
    }

    private static EntityChoicePage decode(FriendlyByteBuf buf) {
        return new EntityChoicePage(
                FocusSelectionSlot.STREAM_CODEC.decode(buf),
                buf.readVarLong(),
                buf.readVarInt(),
                EntityChoiceSort.STREAM_CODEC.decode(buf),
                buf.readVarLong(),
                buf.readUtf(128),
                buf.readUtf(128),
                buf.readBoolean(),
                ByteBufCodecs.optional(ByteBufCodecs.RESOURCE_LOCATION).decode(buf),
                LIST_CODEC.decode(buf),
                buf.readBoolean());
    }
}
