package io.zershyan.damagestats.stats.save;

import io.zershyan.damagestats.network.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;

/** 管理员存储概览的服务端快照，所有尺寸均以字节为单位。 */
public record StorageOverview(
        long eventCount,
        int segmentCount,
        long rawSegmentBytes,
        long compressedSegmentBytes,
        long indexBytes,
        long cacheBytes,
        long exportBytes,
        long temporaryBytes,
        long backupBytes,
        long totalBytes,
        StorageIndexState indexState
) {
    public static final StorageOverview EMPTY = new StorageOverview(0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            StorageIndexState.EMPTY);
    public static final StreamCodec<FriendlyByteBuf, StorageOverview> STREAM_CODEC =
            StreamCodec.of(StorageOverview::encode, StorageOverview::decode);

    private static void encode(FriendlyByteBuf buf, StorageOverview overview) {
        buf.writeVarLong(overview.eventCount);
        buf.writeVarInt(overview.segmentCount);
        buf.writeVarLong(overview.rawSegmentBytes);
        buf.writeVarLong(overview.compressedSegmentBytes);
        buf.writeVarLong(overview.indexBytes);
        buf.writeVarLong(overview.cacheBytes);
        buf.writeVarLong(overview.exportBytes);
        buf.writeVarLong(overview.temporaryBytes);
        buf.writeVarLong(overview.backupBytes);
        buf.writeVarLong(overview.totalBytes);
        StorageIndexState.STREAM_CODEC.encode(buf, overview.indexState);
    }

    private static StorageOverview decode(FriendlyByteBuf buf) {
        return new StorageOverview(
                buf.readVarLong(),
                buf.readVarInt(),
                buf.readVarLong(),
                buf.readVarLong(),
                buf.readVarLong(),
                buf.readVarLong(),
                buf.readVarLong(),
                buf.readVarLong(),
                buf.readVarLong(),
                buf.readVarLong(),
                StorageIndexState.STREAM_CODEC.decode(buf)
        );
    }
}
