package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.datagen.init.DSKeyLang;

/** 个人/全服统计清空必须经过单独确认，防止在日常浏览里误触。 */
public final class ConfirmResetScreen extends ConfirmDialogScreen {
    private final StatsScreen parent;
    private final boolean global;

    public ConfirmResetScreen(StatsScreen parent) {
        this(parent, false);
    }

    public ConfirmResetScreen(StatsScreen parent, boolean global) {
        super(parent,
                global ? DSKeyLang.ScreenConfirmResetAll.copy() : DSKeyLang.ScreenConfirmReset.copy(),
                global ? DSKeyLang.ScreenResetAllDescription.copy() : DSKeyLang.ScreenResetMineDescription.copy(),
                DSKeyLang.ScreenConfirm.copy());
        this.parent = parent;
        this.global = global;
    }

    @Override
    protected void onConfirm() {
        parent.confirmReset(global);
    }
}
