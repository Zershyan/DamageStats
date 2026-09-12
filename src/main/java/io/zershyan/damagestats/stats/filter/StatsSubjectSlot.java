package io.zershyan.damagestats.stats.filter;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * 统计视图的主体来自哪个实体槽位。双槽筛选时它决定标题、会话和对手列表的方向，
 * 不能再从“哪个槽位非空”反推。
 */
public enum StatsSubjectSlot {
    SOURCE,
    TARGET,
    GLOBAL;

    public static final StreamCodec<ByteBuf, StatsSubjectSlot> STREAM_CODEC = StreamCodec.of(
            (buf, slot) -> buf.writeByte(slot.ordinal()),
            buf -> switch (buf.readByte()) {
                case 0 -> SOURCE;
                case 1 -> TARGET;
                default -> GLOBAL;
            }
    );

    /** 权限校验会改写筛选条件，因此必须在校验后收敛到真实存在的槽位。 */
    public StatsSubjectSlot resolve(StatsFilter filter) {
        boolean sourcePresent = sourcePresent(filter);
        boolean targetPresent = targetPresent(filter);
        if(sourcePresent && targetPresent) return this == GLOBAL ? SOURCE : this;
        return sourcePresent ? SOURCE : targetPresent ? TARGET : GLOBAL;
    }

    private static boolean sourcePresent(StatsFilter filter) {
        return filter.source().isPresent();
    }

    private static boolean targetPresent(StatsFilter filter) {
        return filter.target().isPresent();
    }
}
