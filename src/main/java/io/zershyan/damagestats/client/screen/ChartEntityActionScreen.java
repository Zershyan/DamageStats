package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.client.ui.UiDraw;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.stats.filter.EntitySelector;
import io.zershyan.damagestats.stats.filter.FilterKey;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** 图表实体行的操作菜单，类型必须先进入实例列表后才能成为具体直接来源。 */
public final class ChartEntityActionScreen extends ActionListScreen {
    private final StatsScreen parent;
    private final FilterKey key;
    private final EntitySelector selector;
    private final Component entityName;

    public ChartEntityActionScreen(StatsScreen parent, FilterKey key, Component entityName) {
        super(parent, DSKeyLang.ScreenChooseTypeAction.get(entityName));
        this.parent = parent;
        this.key = key;
        this.selector = selector(key);
        this.entityName = entityName;
    }

    @Override
    protected List<Action> buildActions() {
        List<Action> actions = new ArrayList<>(4);
        if(key instanceof FilterKey.Direct) {
            actions.add(new Action(DSKeyLang.ScreenFilter.copy(), UiDraw::funnel, () -> {
                parent.toggleDirectFilter(selector, entityName);
                returnToParent();
            }));
        }
        if(selector instanceof EntitySelector.Instance) {
            if(key instanceof FilterKey.Direct) {
                actions.add(asSource(true));
            } else if(parent.canChooseAnySource() && (key instanceof FilterKey.Source || key instanceof FilterKey.Target)) {
                actions.add(asSource(false));
            }
            actions.add(new Action(DSKeyLang.ScreenSetAsTarget.copy(), UiDraw::target, () -> {
                parent.selectAsTarget(selector, entityName);
                returnToParent();
            }));
        } else if(selector instanceof EntitySelector.Type type) {
            ResourceLocation typeId = type.typeId();
            if(key instanceof FilterKey.Source) {
                if(parent.canChooseAnySource()) actions.add(asSource(false));
                actions.add(asTarget());
            } else if(key instanceof FilterKey.Target) {
                actions.add(asTarget());
                if(parent.canChooseAnySource()) actions.add(asSource(false));
            }
            actions.add(new Action(DSKeyLang.ScreenInstances.copy(), UiDraw::database, () ->
                    parent.openInstances(new EntitySelector.Type(typeId), key, this)));
        }
        return List.copyOf(actions);
    }

    private void returnToParent() {
        if(minecraft != null) minecraft.setScreen(parent);
    }

    private Action asSource(boolean directSource) {
        return new Action(DSKeyLang.ScreenSetAsSource.copy(), UiDraw::target, () -> {
            if(directSource) parent.selectAsDirectSource(selector, entityName);
            else parent.selectAsSource(selector, entityName);
            returnToParent();
        });
    }

    private Action asTarget() {
        return new Action(DSKeyLang.ScreenSetAsTarget.copy(), UiDraw::target, () -> {
            parent.selectAsTarget(selector, entityName);
            returnToParent();
        });
    }

    private static EntitySelector selector(FilterKey key) {
        if(key instanceof FilterKey.Source source) return source.selector();
        if(key instanceof FilterKey.Target target) return target.selector();
        if(key instanceof FilterKey.Direct direct) return direct.selector();
        throw new IllegalArgumentException("实体操作菜单不能处理伤害类型");
    }
}
