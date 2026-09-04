package io.zershyan.damagestats.stats.view;

import io.zershyan.damagestats.stats.filter.DamageTypeGrouping;
import io.zershyan.damagestats.stats.focus.FocusChartDimension;
import io.zershyan.damagestats.stats.focus.FocusChartScope;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/** 服务器排序后的焦点图表页面，客户端不会收到未经请求的完整分组。 */
public record FocusChartPage(
        long focusVersion,
        int requestId,
        boolean allowed,
        FocusChartDimension dimension,
        FocusChartScope scope,
        DamageTypeGrouping typeGrouping,
        List<GroupView> rows,
        boolean hasNext
) {
    public static final int PAGE_SIZE = 50;
    private static final StreamCodec<RegistryFriendlyByteBuf, List<GroupView>> ROWS_CODEC =
            GroupView.STREAM_CODEC.apply(ByteBufCodecs.list(PAGE_SIZE));

    public static final StreamCodec<RegistryFriendlyByteBuf, FocusChartPage> STREAM_CODEC =
            StreamCodec.of(FocusChartPage::encode, FocusChartPage::decode);

    private static void encode(RegistryFriendlyByteBuf buf, FocusChartPage page) {
        buf.writeVarLong(page.focusVersion);
        buf.writeVarInt(page.requestId);
        buf.writeBoolean(page.allowed);
        FocusChartDimension.STREAM_CODEC.encode(buf, page.dimension);
        FocusChartScope.STREAM_CODEC.encode(buf, page.scope);
        DamageTypeGrouping.STREAM_CODEC.encode(buf, page.typeGrouping);
        ROWS_CODEC.encode(buf, page.rows);
        buf.writeBoolean(page.hasNext);
    }

    private static FocusChartPage decode(RegistryFriendlyByteBuf buf) {
        return new FocusChartPage(buf.readVarLong(), buf.readVarInt(), buf.readBoolean(),
                FocusChartDimension.STREAM_CODEC.decode(buf), FocusChartScope.STREAM_CODEC.decode(buf),
                DamageTypeGrouping.STREAM_CODEC.decode(buf), ROWS_CODEC.decode(buf), buf.readBoolean());
    }

    public FocusChartPage withRequestId(int newRequestId) {
        return new FocusChartPage(focusVersion, newRequestId, allowed, dimension, scope, typeGrouping, rows, hasNext);
    }
}
