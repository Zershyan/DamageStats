package io.zershyan.damagestats.stats.view;

import io.zershyan.damagestats.network.codec.ByteBufCodecs;
import io.zershyan.damagestats.network.codec.StreamCodec;
import io.zershyan.damagestats.stats.filter.DamageTypeGrouping;
import io.zershyan.damagestats.stats.focus.EntityGrouping;
import io.zershyan.damagestats.stats.focus.FocusChartDimension;
import io.zershyan.damagestats.stats.focus.FocusChartScope;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;

/** 服务器排序后的焦点图表页面，客户端不会收到未经请求的完整分组。 */
public record FocusChartPage(
        long focusVersion,
        int requestId,
        long snapshotId,
        String cursor,
        String nextCursor,
        boolean allowed,
        FocusChartDimension dimension,
        FocusChartScope scope,
        DamageTypeGrouping typeGrouping,
        EntityGrouping entityGrouping,
        List<GroupView> rows,
        boolean hasNext
) {
    public static final int PAGE_SIZE = 50;
    private static final StreamCodec<FriendlyByteBuf, List<GroupView>> ROWS_CODEC =
            GroupView.STREAM_CODEC.apply(ByteBufCodecs.list(PAGE_SIZE));

    public static final StreamCodec<FriendlyByteBuf, FocusChartPage> STREAM_CODEC =
            StreamCodec.of(FocusChartPage::encode, FocusChartPage::decode);

    private static void encode(FriendlyByteBuf buf, FocusChartPage page) {
        buf.writeVarLong(page.focusVersion);
        buf.writeVarInt(page.requestId);
        buf.writeVarLong(page.snapshotId);
        buf.writeUtf(page.cursor, 128);
        buf.writeUtf(page.nextCursor, 128);
        buf.writeBoolean(page.allowed);
        FocusChartDimension.STREAM_CODEC.encode(buf, page.dimension);
        FocusChartScope.STREAM_CODEC.encode(buf, page.scope);
        DamageTypeGrouping.STREAM_CODEC.encode(buf, page.typeGrouping);
        EntityGrouping.STREAM_CODEC.encode(buf, page.entityGrouping);
        ROWS_CODEC.encode(buf, page.rows);
        buf.writeBoolean(page.hasNext);
    }

    private static FocusChartPage decode(FriendlyByteBuf buf) {
        return new FocusChartPage(buf.readVarLong(), buf.readVarInt(), buf.readVarLong(), buf.readUtf(128),
                buf.readUtf(128), buf.readBoolean(),
                FocusChartDimension.STREAM_CODEC.decode(buf), FocusChartScope.STREAM_CODEC.decode(buf),
                DamageTypeGrouping.STREAM_CODEC.decode(buf), EntityGrouping.STREAM_CODEC.decode(buf),
                ROWS_CODEC.decode(buf), buf.readBoolean());
    }

    public FocusChartPage withRequestId(int newRequestId) {
        return new FocusChartPage(focusVersion, newRequestId, snapshotId, cursor, nextCursor, allowed, dimension, scope,
                typeGrouping, entityGrouping, rows, hasNext);
    }
}
