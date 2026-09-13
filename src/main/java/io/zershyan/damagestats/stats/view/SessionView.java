package io.zershyan.damagestats.stats.view;

import io.zershyan.damagestats.network.codec.ByteBufCodecs;
import io.zershyan.damagestats.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;

/** 一场已结束的战斗，只保留摘要——历史会话不需要三维分解 */
public record SessionView(float totalDamage, float averageDps, float durationSeconds, int hitCount) {
    public static final StreamCodec<FriendlyByteBuf, SessionView> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, SessionView::totalDamage,
            ByteBufCodecs.FLOAT, SessionView::averageDps,
            ByteBufCodecs.FLOAT, SessionView::durationSeconds,
            ByteBufCodecs.VAR_INT, SessionView::hitCount,
            SessionView::new
    );

    public static final StreamCodec<FriendlyByteBuf, List<SessionView>> LIST_STREAM_CODEC =
            STREAM_CODEC.apply(ByteBufCodecs.list());
}
