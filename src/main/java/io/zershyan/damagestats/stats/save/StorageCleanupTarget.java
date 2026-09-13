package io.zershyan.damagestats.stats.save;

import io.zershyan.damagestats.network.codec.ByteBufCodecs;
import io.zershyan.damagestats.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;

/** 管理员在存储概览中可主动执行的清理范围。 */
public enum StorageCleanupTarget {
    NONE,
    TEMPORARY_AND_BACKUPS,
    EXPORTS,
    ALL_RECORDS;

    public static final StreamCodec<FriendlyByteBuf, StorageCleanupTarget> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(StorageCleanupTarget::fromOrdinal, StorageCleanupTarget::ordinal);

    private static StorageCleanupTarget fromOrdinal(int ordinal) {
        StorageCleanupTarget[] values = values();
        if(ordinal < 0 || ordinal >= values.length) throw new IllegalArgumentException("非法存储清理范围");
        return values[ordinal];
    }
}
