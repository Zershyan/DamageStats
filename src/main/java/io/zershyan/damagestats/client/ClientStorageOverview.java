package io.zershyan.damagestats.client;

import io.zershyan.damagestats.client.screen.StorageOverviewScreen;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.registry.packet.StorageOverviewPacket;
import io.zershyan.damagestats.stats.save.StorageCleanupTarget;
import io.zershyan.damagestats.stats.save.StorageOverview;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** 客户端只显示服务端推送的存储摘要，不接触服务器磁盘。 */
public final class ClientStorageOverview {
    private static StorageOverview overview = StorageOverview.EMPTY;
    private static StorageCleanupTarget completedCleanup = StorageCleanupTarget.NONE;

    private ClientStorageOverview() {}

    public static void accept(StorageOverviewPacket payload) {
        Minecraft minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if(!payload.allowed()) {
            if(player != null) player.displayClientMessage(DSKeyLang.StatsPrivate.copy(), false);
            return;
        }
        overview = payload.overview();
        completedCleanup = payload.completedCleanup();
        if(completedCleanup != StorageCleanupTarget.NONE && player != null) {
            Component message = payload.cleanupSucceeded()
                    ? DSKeyLang.StorageCleanupDone.get(cleanupLabel(completedCleanup))
                    : DSKeyLang.StorageCleanupFailed.get(cleanupLabel(completedCleanup));
            player.displayClientMessage(message, false);
        }
        if(payload.openScreen()) minecraft.setScreen(new StorageOverviewScreen());
    }

    public static StorageOverview overview() {
        return overview;
    }

    public static StorageCleanupTarget completedCleanup() {
        return completedCleanup;
    }

    public static void clear() {
        overview = StorageOverview.EMPTY;
        completedCleanup = StorageCleanupTarget.NONE;
    }

    public static Component cleanupLabel(StorageCleanupTarget target) {
        return switch (target) {
            case TEMPORARY_AND_BACKUPS -> DSKeyLang.ScreenStorageCleanupTemporary.copy();
            case EXPORTS -> DSKeyLang.ScreenStorageCleanupExports.copy();
            case ALL_RECORDS -> DSKeyLang.ScreenStorageCleanupAll.copy();
            case NONE -> Component.empty();
        };
    }
}
