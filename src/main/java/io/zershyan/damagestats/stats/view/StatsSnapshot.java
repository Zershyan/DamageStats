package io.zershyan.damagestats.stats.view;

import io.zershyan.damagestats.stats.filter.DamageTypeGrouping;
import io.zershyan.damagestats.stats.filter.StatsFilter;
import io.zershyan.damagestats.stats.filter.StatsSubjectSlot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/**
 * GUI 打开或改动筛选条件时向服务端请求一次拿到的完整数据。
 *
 * @param appliedFilter 服务端**实际生效**的筛选。权限降级或重置之后它会和客户端手上那份不一样，
 *                      所以客户端收到快照就无条件采纳它，否则接下来的点击是拿陈旧条件在改
 * @param filterLabels  当前生效的每一项筛选，已经渲染好，客户端直接画成条件行
 * @param truncated     原始记录被淘汰过，带筛选的数字可能不完整
 * @param canViewAll    这个玩家能不能看全局统计。客户端自己判断权限不可靠，由服务端告知
 */
public record StatsSnapshot(
        Component subjectName,
        StatsFilter appliedFilter,
        StatsSubjectSlot subjectSlot,
        DamageTypeGrouping typeGrouping,
        List<Component> filterLabels,
        StatsView session,
        StatsView lifetime,
        List<SessionView> history,
        boolean truncated,
        boolean canViewAll
) {
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Component>> LABELS_CODEC =
            ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list());

    /** 字段数超过 StreamCodec.composite 的六个上限，只能手写 */
    public static final StreamCodec<RegistryFriendlyByteBuf, StatsSnapshot> STREAM_CODEC =
            StreamCodec.of(StatsSnapshot::encode, StatsSnapshot::decode);

    private static void encode(RegistryFriendlyByteBuf buf, StatsSnapshot snapshot) {
        ComponentSerialization.STREAM_CODEC.encode(buf, snapshot.subjectName);
        StatsFilter.STREAM_CODEC.encode(buf, snapshot.appliedFilter);
        StatsSubjectSlot.STREAM_CODEC.encode(buf, snapshot.subjectSlot);
        DamageTypeGrouping.STREAM_CODEC.encode(buf, snapshot.typeGrouping);
        LABELS_CODEC.encode(buf, snapshot.filterLabels);
        StatsView.STREAM_CODEC.encode(buf, snapshot.session);
        StatsView.STREAM_CODEC.encode(buf, snapshot.lifetime);
        SessionView.LIST_STREAM_CODEC.encode(buf, snapshot.history);
        buf.writeBoolean(snapshot.truncated);
        buf.writeBoolean(snapshot.canViewAll);
    }

    private static StatsSnapshot decode(RegistryFriendlyByteBuf buf) {
        return new StatsSnapshot(
                ComponentSerialization.STREAM_CODEC.decode(buf),
                StatsFilter.STREAM_CODEC.decode(buf),
                StatsSubjectSlot.STREAM_CODEC.decode(buf),
                DamageTypeGrouping.STREAM_CODEC.decode(buf),
                LABELS_CODEC.decode(buf),
                StatsView.STREAM_CODEC.decode(buf),
                StatsView.STREAM_CODEC.decode(buf),
                SessionView.LIST_STREAM_CODEC.decode(buf),
                buf.readBoolean(),
                buf.readBoolean());
    }
}
