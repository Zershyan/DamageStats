package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.client.ui.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * 弹出式操作菜单的公共外壳：居中面板 + 竖排动作按钮。子类只描述有哪些动作。
 * 按钮的可用状态每帧重算，导出这类会随外部进度变化的状态不会停在打开菜单那一刻。
 */
abstract class ActionListScreen extends UiScreen {
    private static final int PANEL_MAX_WIDTH = 208;
    private static final int PANEL_MIN_WIDTH = 132;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 5;
    private static final int PADDING = 14;
    private static final int LINE_HEIGHT = 11;

    /** 一个动作。enabled 每帧求值一次，可用来跟随外部状态。 */
    protected record Action(Component label, @Nullable UiIcon icon, boolean danger,
                            BooleanSupplier enabled, Runnable execute) {
        protected Action(Component label, @Nullable UiIcon icon, Runnable execute) {
            this(label, icon, false, () -> true, execute);
        }

        protected static Action danger(Component label, @Nullable UiIcon icon, Runnable execute) {
            return new Action(label, icon, true, () -> true, execute);
        }
    }

    private final Screen returnScreen;
    private final List<UiButton> actionButtons = new ArrayList<>();

    private List<Action> actions = List.of();
    private List<FormattedCharSequence> titleLines = List.of();
    private UiLayout.Rect panel = new UiLayout.Rect(0, 0, 0, 0);

    protected ActionListScreen(Screen returnScreen, Component title) {
        super(title);
        this.returnScreen = returnScreen;
    }

    /** 子类给出面板里的动作列表。 */
    protected abstract List<Action> buildActions();

    @Override
    protected void init() {
        if(minecraft == null) return;
        actions = buildActions();
        actionButtons.clear();
        // 折行与量宽都按加粗后的文本算，原版粗体会撑开字距
        titleLines = wrapLines(UiText.bolded(title), Math.max(1, Math.clamp(width - 16, 1, PANEL_MAX_WIDTH) - PADDING * 2));
        panel = layout();
        int y = panel.y() + PADDING + titleLines.size() * LINE_HEIGHT + 10;
        for(Action action : actions) {
            UiButton button = UiButton.builder(action.label(), pressed -> action.execute().run())
                    .bounds(new UiLayout.Rect(panel.x() + PADDING, y,
                            Math.max(1, panel.width() - PADDING * 2), BUTTON_HEIGHT))
                    .variant(action.danger() ? UiButton.Variant.DANGER : UiButton.Variant.SECONDARY)
                    .align(UiButton.Align.LEFT)
                    .icon(action.icon())
                    .build();
            button.active = action.enabled().getAsBoolean();
            actionButtons.add(addRenderableWidget(button));
            y += BUTTON_HEIGHT + BUTTON_GAP;
        }
        addRenderableWidget(UiButton.builder(CommonComponents.GUI_CANCEL, button -> minecraft.setScreen(returnScreen))
                .bounds(new UiLayout.Rect(panel.x() + PADDING, y + 2,
                        Math.max(1, panel.width() - PADDING * 2), BUTTON_HEIGHT))
                .variant(UiButton.Variant.GHOST).build());
    }

    @Override
    protected void renderContent(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderModalScrim(graphics);
        renderCard(graphics, panel, true);
        int centerX = panel.centerX();
        int y = panel.y() + PADDING;
        for(FormattedCharSequence line : titleLines) {
            UiText.centered(graphics, line, centerX, y, UiTheme.TEXT_STRONG);
            y += LINE_HEIGHT;
        }
        // 标题下的强调短线，让面板顶部不至于是一整块空白；线性风格下用实色
        UiDraw.rect(graphics, centerX - 12F, y + 4F, 24F, 1F, UiTheme.ACCENT);
        graphics.flush();
        for(int index = 0; index < actionButtons.size() && index < actions.size(); index++) {
            actionButtons.get(index).active = actions.get(index).enabled().getAsBoolean();
        }
    }

    private UiLayout.Rect layout() {
        int hardLimit = Math.max(1, width - 16);
        int preferred = Math.max(PANEL_MIN_WIDTH, measuredWidth());
        int panelWidth = Math.clamp(preferred, Math.min(PANEL_MIN_WIDTH, hardLimit),
                Math.max(1, Math.min(PANEL_MAX_WIDTH, hardLimit)));
        int panelHeight = PADDING * 2 + titleLines.size() * LINE_HEIGHT + 10
                + (actions.size() + 1) * (BUTTON_HEIGHT + BUTTON_GAP) + 2;
        return modalPanel(width, height, panelWidth, panelHeight);
    }

    /** 面板宽度取标题与按钮文字里更宽的那个，短菜单也不会显得空荡。 */
    private int measuredWidth() {
        int widest = 0;
        for(FormattedCharSequence line : titleLines) widest = Math.max(widest, font.width(line));
        for(Action action : actions) widest = Math.max(widest, font.width(action.label()) + 34);
        return widest + PADDING * 2 + 12;
    }
}
