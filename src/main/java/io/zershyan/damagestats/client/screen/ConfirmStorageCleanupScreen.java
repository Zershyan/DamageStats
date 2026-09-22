package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.client.ClientStorageOverview;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.stats.save.StorageCleanupTarget;
import net.minecraft.network.chat.Component;

/** 存储清理必须在独立确认页再次确认，全部记录清理不会被普通按钮误触。 */
public final class ConfirmStorageCleanupScreen extends ConfirmDialogScreen {
    private final StorageOverviewScreen parent;
    private final StorageCleanupTarget target;

    public ConfirmStorageCleanupScreen(StorageOverviewScreen parent, StorageCleanupTarget target) {
        super(parent, DSKeyLang.ScreenStorageConfirmTitle.copy(), description(target),
                DSKeyLang.ScreenConfirm.copy());
        this.parent = parent;
        this.target = target;
    }

    @Override
    protected void onConfirm() {
        parent.cleanup(target);
    }

    private static Component description(StorageCleanupTarget target) {
        return switch(target) {
            case TEMPORARY_AND_BACKUPS -> DSKeyLang.ScreenStorageConfirmTemporary.copy();
            case EXPORTS -> DSKeyLang.ScreenStorageConfirmExports.copy();
            case ALL_RECORDS -> DSKeyLang.ScreenStorageConfirmAll.copy();
            case NONE -> ClientStorageOverview.cleanupLabel(target);
        };
    }
}
