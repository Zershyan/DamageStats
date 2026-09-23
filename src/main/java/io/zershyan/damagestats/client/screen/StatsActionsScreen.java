package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.client.ClientExportManager;
import io.zershyan.damagestats.client.ui.UiDraw;
import io.zershyan.damagestats.datagen.init.DSKeyLang;

import java.util.ArrayList;
import java.util.List;

/** 主界面之外的低频操作统一收在这里：详细指标、浮层、历史、导出与两类清空。 */
public final class StatsActionsScreen extends ActionListScreen {
    private final StatsScreen parent;

    public StatsActionsScreen(StatsScreen parent) {
        super(parent, DSKeyLang.ScreenActions.copy());
        this.parent = parent;
    }

    @Override
    protected List<Action> buildActions() {
        List<Action> actions = new ArrayList<>(6);
        actions.add(new Action(DSKeyLang.ScreenDetails.copy(), UiDraw::chartLine, parent::openDetails));
        actions.add(new Action(DSKeyLang.ScreenEditOverlay.copy(), UiDraw::move, parent::openOverlay));
        actions.add(new Action(DSKeyLang.ScreenHistory.copy(), UiDraw::clock, parent::openHistory));
        actions.add(new Action(DSKeyLang.ScreenExport.copy(), UiDraw::download, false,
                () -> !ClientExportManager.isBusy(), parent::requestExport));
        actions.add(Action.danger(DSKeyLang.ScreenReset.copy(), UiDraw::trash, parent::openResetConfirmation));
        if(parent.canResetAll()) {
            actions.add(Action.danger(DSKeyLang.ScreenResetAll.copy(), UiDraw::trash,
                    parent::openGlobalResetConfirmation));
        }
        return List.copyOf(actions);
    }
}
