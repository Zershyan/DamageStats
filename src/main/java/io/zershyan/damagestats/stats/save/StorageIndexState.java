package io.zershyan.damagestats.stats.save;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** 事件段索引在当前服务周期内的恢复状态。 */
public enum StorageIndexState {
    EMPTY,
    LOADED,
    REBUILT,
    FAILED;

    public static final StreamCodec<ByteBuf, StorageIndexState> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(StorageIndexState::fromOrdinal, StorageIndexState::ordinal);

    private static StorageIndexState fromOrdinal(int ordinal) {
        StorageIndexState[] values = values();
        if(ordinal < 0 || ordinal >= values.length) throw new IllegalArgumentException("非法存储索引状态");
        return values[ordinal];
    }
}
