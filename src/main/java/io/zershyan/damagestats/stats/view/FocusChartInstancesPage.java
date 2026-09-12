package io.zershyan.damagestats.stats.view;

import io.zershyan.damagestats.stats.filter.DamageTypeGrouping;
import io.zershyan.damagestats.stats.filter.FilterKey;
import io.zershyan.damagestats.stats.filter.StatsFilter;
import io.zershyan.damagestats.stats.focus.FocusChartDimension;
import io.zershyan.damagestats.stats.focus.FocusChartScope;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/** 焦点图表某个实体类型展开后的实例子行页面。 */
public record FocusChartInstancesPage(
        long focusVersion,
        int requestId,
        long snapshotId,
        String cursor,
        String nextCursor,
        boolean allowed,
        FocusChartDimension dimension,
        FocusChartScope scope,
        DamageTypeGrouping typeGrouping,
        StatsFilter filter,
        FilterKey parentKey,
        List<GroupView> rows,
        boolean hasNext
) {
    private static final StreamCodec<RegistryFriendlyByteBuf, List<GroupView>> ROWS_CODEC =
            GroupView.STREAM_CODEC.apply(ByteBufCodecs.list(FocusChartPage.PAGE_SIZE));

    public static final StreamCodec<RegistryFriendlyByteBuf, FocusChartInstancesPage> STREAM_CODEC =
            StreamCodec.of(FocusChartInstancesPage::encode, FocusChartInstancesPage::decode);

    private static void encode(RegistryFriendlyByteBuf buf, FocusChartInstancesPage page) {
        buf.writeVarLong(page.focusVersion);
        buf.writeVarInt(page.requestId);
        buf.writeVarLong(page.snapshotId);
        buf.writeUtf(page.cursor, 128);
        buf.writeUtf(page.nextCursor, 128);
        buf.writeBoolean(page.allowed);
        FocusChartDimension.STREAM_CODEC.encode(buf, page.dimension);
        FocusChartScope.STREAM_CODEC.encode(buf, page.scope);
        DamageTypeGrouping.STREAM_CODEC.encode(buf, page.typeGrouping);
        StatsFilter.STREAM_CODEC.encode(buf, page.filter);
        FilterKey.STREAM_CODEC.encode(buf, page.parentKey);
        ROWS_CODEC.encode(buf, page.rows);
        buf.writeBoolean(page.hasNext);
    }

    private static FocusChartInstancesPage decode(RegistryFriendlyByteBuf buf) {
        return new FocusChartInstancesPage(
                buf.readVarLong(), buf.readVarInt(), buf.readVarLong(), buf.readUtf(128), buf.readUtf(128),
                buf.readBoolean(), FocusChartDimension.STREAM_CODEC.decode(buf),
                FocusChartScope.STREAM_CODEC.decode(buf), DamageTypeGrouping.STREAM_CODEC.decode(buf),
                StatsFilter.STREAM_CODEC.decode(buf), FilterKey.STREAM_CODEC.decode(buf),
                ROWS_CODEC.decode(buf), buf.readBoolean());
    }

    public FocusChartInstancesPage withRequestId(int newRequestId) {
        return new FocusChartInstancesPage(focusVersion, newRequestId, snapshotId, cursor, nextCursor, allowed,
                dimension, scope, typeGrouping, filter, parentKey, rows, hasNext);
    }
}
