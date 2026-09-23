package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.client.ClientFocusPreferences;
import io.zershyan.damagestats.client.ClientStats;
import io.zershyan.damagestats.client.overlay.StatsOverlay;
import io.zershyan.damagestats.client.ui.*;
import io.zershyan.damagestats.config.DSClientConfig;
import io.zershyan.damagestats.config.OverlayMetricScope;
import io.zershyan.damagestats.datagen.init.DSConfigLang;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.stats.EntityRef;
import io.zershyan.damagestats.stats.filter.EntitySelector;
import io.zershyan.damagestats.stats.filter.StatsFilter;
import io.zershyan.damagestats.stats.focus.FocusSelectionSlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.common.ForgeConfigSpec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Overlay 的位置、外观和字段设置页；每个指标可独立选择本场或累计范围。
 * 控制面板靠右，把左侧整块留成拖拽画布——浮层默认就在左侧，两者不会互相遮挡。
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class OverlayPositionScreen extends UiScreen {
    private static final int PANEL_WIDTH = 190;
    private static final int PANEL_MIN_SCREEN_WIDTH = 300;
    private static final int SLIDER_HEIGHT = 20;
    private static final int FIELD_ROW_HEIGHT = 20;
    private static final int SCOPE_WIDTH = 46;
    private static final int PANEL_PADDING = 10;
    private static final int SLIDER_GAP = 5;
    private static final int ROW_GAP = 7;
    private static final int SECTION_GAP = 9;
    private static final int SECTION_LABEL_HEIGHT = 11;
    /** 字段区上方留给分隔线与分组标题的高度，两个偏移必须和 computeLayout 里的累加一致。 */
    private static final int DIVIDER_OFFSET = 18;
    private static final int LABEL_OFFSET = 14;
    private static final int PREVIEW_BORDER = 0xFFFFD700;

    private record OverlayField(
            DSConfigLang.ConfigEntry label,
            ForgeConfigSpec.BooleanValue visible,
            @Nullable ForgeConfigSpec.EnumValue<OverlayMetricScope> scope
    ) {}

    private static final List<OverlayField> FIELDS = List.of(
            new OverlayField(DSConfigLang.OverlayShowFocus, DSClientConfig.OverlayShowFocus, null),
            new OverlayField(DSConfigLang.OverlayShowActualDamage, DSClientConfig.OverlayShowActualDamage,
                    DSClientConfig.OverlayActualDamageScope),
            new OverlayField(DSConfigLang.OverlayShowOriginalDamage, DSClientConfig.OverlayShowOriginalDamage,
                    DSClientConfig.OverlayOriginalDamageScope),
            new OverlayField(DSConfigLang.OverlayShowReduction, DSClientConfig.OverlayShowReduction,
                    DSClientConfig.OverlayReductionScope),
            new OverlayField(DSConfigLang.OverlayShowActualAverageDps, DSClientConfig.OverlayShowActualAverageDps,
                    DSClientConfig.OverlayActualAverageDpsScope),
            new OverlayField(DSConfigLang.OverlayShowActualRealtimeDps, DSClientConfig.OverlayShowActualRealtimeDps,
                    DSClientConfig.OverlayActualRealtimeDpsScope),
            new OverlayField(DSConfigLang.OverlayShowOriginalAverageDps, DSClientConfig.OverlayShowOriginalAverageDps,
                    DSClientConfig.OverlayOriginalAverageDpsScope),
            new OverlayField(DSConfigLang.OverlayShowOriginalRealtimeDps, DSClientConfig.OverlayShowOriginalRealtimeDps,
                    DSClientConfig.OverlayOriginalRealtimeDpsScope),
            new OverlayField(DSConfigLang.OverlayShowHits, DSClientConfig.OverlayShowHits, DSClientConfig.OverlayHitsScope),
            new OverlayField(DSConfigLang.OverlayShowAverageHit, DSClientConfig.OverlayShowAverageHit,
                    DSClientConfig.OverlayAverageHitScope),
            new OverlayField(DSConfigLang.OverlayShowMaxOriginal, DSClientConfig.OverlayShowMaxOriginal,
                    DSClientConfig.OverlayMaxOriginalScope),
            new OverlayField(DSConfigLang.OverlayShowMaxActual, DSClientConfig.OverlayShowMaxActual,
                    DSClientConfig.OverlayMaxActualScope),
            new OverlayField(DSConfigLang.OverlayShowTopDamageType, DSClientConfig.OverlayShowTopDamageType,
                    DSClientConfig.OverlayTopDamageTypeScope),
            new OverlayField(DSConfigLang.OverlayShowTopDirectSource, DSClientConfig.OverlayShowTopDirectSource,
                    DSClientConfig.OverlayTopDirectSourceScope),
            new OverlayField(DSConfigLang.OverlayShowSessionStatus, DSClientConfig.OverlayShowSessionStatus, null)
    );

    private final @Nullable Screen returnScreen;

    private double ratioX;
    private double ratioY;
    private boolean dragging;
    private int grabOffsetX;
    private int grabOffsetY;
    private final UiScroll fieldScroll = new UiScroll();
    /** 字段控件已经搬到的偏移，用来算这一帧还要再挪多少 */
    private int fieldAppliedOffset;
    private final List<AbstractWidget> fieldWidgets = new ArrayList<>();
    private @Nullable UiButton targetButton;

    private UiLayout.Rect panel = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect fieldViewport = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect closeSlot = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect resetSlot = new UiLayout.Rect(0, 0, 0, 0);
    private UiLayout.Rect doneSlot = new UiLayout.Rect(0, 0, 0, 0);

    public OverlayPositionScreen(@Nullable Screen returnScreen) {
        super(DSKeyLang.EditPositionTitle.copy());
        this.returnScreen = returnScreen;
        ratioX = DSClientConfig.OverlayX.get();
        ratioY = DSClientConfig.OverlayY.get();
    }

    @Override
    protected void init() {
        if(minecraft == null) return;
        computeLayout();
        targetButton = null;
        fieldWidgets.clear();
        fieldAppliedOffset = 0;
        addRenderableWidget(new UiSlider(panelRow(0), DSClientConfig.OverlayScale.get(), 0.5D, 2.0D,
                OverlayPositionScreen::scaleMessage, DSClientConfig.OverlayScale::set));
        addRenderableWidget(new UiSlider(panelRow(1), DSClientConfig.OverlayBackgroundOpacity.get(), 0.0D, 1.0D,
                OverlayPositionScreen::opacityMessage, DSClientConfig.OverlayBackgroundOpacity::set));

        List<UiLayout.Rect> targetRow = UiLayout.row(panelRow(2), 4, UiLayout.FILL, 62);
        targetButton = addRenderableWidget(UiButton.builder(Component.empty(), button -> openTargetSelector())
                .bounds(targetRow.get(0)).variant(UiButton.Variant.SECONDARY)
                .align(UiButton.Align.LEFT).icon(UiDraw::target).build());
        addRenderableWidget(UiButton.builder(DSKeyLang.OverlayAllTargets.copy(), button -> setOverlayTarget(Optional.empty()))
                .bounds(targetRow.get(1)).variant(UiButton.Variant.GHOST).build());

        addFields();
        addRenderableWidget(UiButton.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(doneSlot).variant(UiButton.Variant.PRIMARY).build());
        addRenderableWidget(UiButton.builder(DSKeyLang.EditPositionReset.copy(), button -> resetPosition())
                .bounds(resetSlot).variant(UiButton.Variant.GHOST).icon(UiDraw::move).build());
        addRenderableWidget(UiButton.builder(Component.empty(), button -> onClose())
                .bounds(closeSlot).variant(UiButton.Variant.GHOST).icon(UiDraw::cross).build());
    }

    /** 字段行全部创建在「未滚动」的位置，每帧由 {@link #syncFieldScroll} 按平滑偏移搬移。 */
    private void addFields() {
        for(int index = 0; index < FIELDS.size(); index++) {
            OverlayField field = FIELDS.get(index);
            UiLayout.Rect row = fieldRow(index);
            var scope = field.scope();
            List<UiLayout.Rect> split = scope == null ? List.of(row)
                    : UiLayout.row(row, 4, UiLayout.FILL, Math.min(SCOPE_WIDTH, Math.max(1, row.width() - 40)));
            UiCheckbox checkbox = new UiCheckbox(split.get(0),
                    Component.translatable(field.label().getKey()), field.visible().get(),
                    value -> field.visible().set(value));
            fieldWidgets.add(addClippedWidget(checkbox));
            if(scope == null) continue;
            UiSegmented scopeBox = new UiSegmented(split.get(1), List.of(
                    new UiSegmented.Option(DSKeyLang.OverlayScopeSession.copy()),
                    new UiSegmented.Option(DSKeyLang.OverlayScopeLifetime.copy())),
                    scope.get() == OverlayMetricScope.SESSION ? 0 : 1, true,
                    selection -> {
                        OverlayMetricScope next = selection == 0
                                ? OverlayMetricScope.SESSION : OverlayMetricScope.LIFETIME;
                        if(scope.get() == next) return;
                        scope.set(next);
                    });
            fieldWidgets.add(addClippedWidget(scopeBox));
        }
    }

    /** 平滑滚动字段列表：先搬位置，再按可视范围把行外的控件藏掉。 */
    private void syncFieldScroll() {
        fieldScroll.measure(FIELDS.size() * FIELD_ROW_HEIGHT, fieldViewport.height());
        fieldScroll.tick();
        int offset = fieldScroll.offset();
        int delta = offset - fieldAppliedOffset;
        if(delta != 0) {
            fieldAppliedOffset = offset;
            for(AbstractWidget widget : fieldWidgets) {
                widget.setY(widget.getY() - delta);
            }
        }
        for(AbstractWidget widget : fieldWidgets) {
            widget.visible = widget.getY() + widget.getHeight() > fieldViewport.y()
                    && widget.getY() < fieldViewport.bottom();
        }
    }

    @Override
    protected void renderContent(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTopBar(graphics, title, null, null, false, 34);
        renderCard(graphics, panel, false);
        UiDraw.rectGradient(graphics, fieldViewport.x(), fieldViewport.y() - DIVIDER_OFFSET,
                fieldViewport.width(), 1F, UiTheme.alpha(UiTheme.BORDER, 0.9F),
                UiTheme.alpha(UiTheme.BORDER, 0.2F));
        renderSectionLabel(graphics, new UiLayout.Rect(fieldViewport.x(), fieldViewport.y() - LABEL_OFFSET,
                fieldViewport.width(), SECTION_LABEL_HEIGHT), DSKeyLang.ScreenOverlayFields.copy());
        if(targetButton != null) {
            targetButton.setMessage(DSKeyLang.FilterTarget.get(ClientStats.summary().scope().targetName()));
        }
        syncFieldScroll();
        clipWidgetsTo(fieldViewport);
        fieldScroll.renderIndicator(graphics, new UiLayout.Rect(panel.right() - 4, fieldViewport.y(), 3,
                fieldViewport.height()));
        renderPreview(graphics);
    }

    /** 浮层预览。画在面板之后、控件之前，所以既能盖住面板，又不会挡住可点的按钮。 */
    private void renderPreview(GuiGraphics graphics) {
        int previewX = originX();
        int previewY = originY();
        int previewWidth = StatsOverlay.width();
        int previewHeight = StatsOverlay.height();
        graphics.flush();
        UiDraw.shadow(graphics, previewX, previewY, previewWidth, previewHeight, UiTheme.RADIUS_MD, 7F, 2F,
                UiTheme.SHADOW);
        StatsOverlay.renderBox(graphics, font, StatsOverlay.previewSummary(), previewX, previewY);
        graphics.flush();
        UiDraw.roundedBorder(graphics, previewX - 0.5F, previewY - 0.5F, previewWidth + 1F, previewHeight + 1F,
                UiTheme.RADIUS_MD, 1F, PREVIEW_BORDER);
        // 左上角一个拖拽手柄，明确这块东西可以拖
        UiDraw.roundedRect(graphics, previewX + 3F, previewY + 3F, 12F, 12F, 3F,
                UiTheme.alpha(UiTheme.BACKDROP, 0.75F));
        UiDraw.move(graphics, previewX + 9F, previewY + 9F, 7F, 1F, PREVIEW_BORDER);
        graphics.flush();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if(!fieldViewport.contains(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, scrollY);
        }
        fieldScroll.scrollBy(Math.signum(scrollY), FIELD_ROW_HEIGHT);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 拖拽优先于控件：预览和面板重叠时，点击落在预览上就是拖它
        if(button == 0 && isInsidePreview(mouseX, mouseY)) {
            dragging = true;
            grabOffsetX = (int) (mouseX - originX());
            grabOffsetY = (int) (mouseY - originY());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if(!dragging) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        ratioX = clampRatio((mouseX - grabOffsetX) / width, StatsOverlay.width(), width);
        ratioY = clampRatio((mouseY - grabOffsetY) / height, StatsOverlay.height(), height);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        DSClientConfig.OverlayX.set(clampRatio(ratioX, StatsOverlay.width(), width));
        DSClientConfig.OverlayY.set(clampRatio(ratioY, StatsOverlay.height(), height));
        DSClientConfig.OverlayX.save();
        DSClientConfig.OverlayY.save();
        DSClientConfig.OverlayScale.save();
        DSClientConfig.OverlayBackgroundOpacity.save();
        FIELDS.forEach(field -> {
            field.visible().save();
            var scope = field.scope();
            if(scope != null) scope.save();
        });
        if(returnScreen != null) minecraft.setScreen(returnScreen);
        else super.onClose();
    }

    private void resetPosition() {
        ratioX = DSClientConfig.DefaultOverlayX;
        ratioY = DSClientConfig.DefaultOverlayY;
    }

    private void openTargetSelector() {
        if(minecraft == null) return;
        var player = minecraft.player;
        if(player == null) return;
        EntitySelector self = new EntitySelector.Instance(EntityRef.of(player));
        minecraft.setScreen(new FocusEntitySelectorScreen(this, FocusSelectionSlot.TARGET,
                StatsFilter.fromSource(self),
                (selector, ignored) -> setOverlayTarget(Optional.of(selector))));
    }

    private void setOverlayTarget(Optional<EntitySelector> target) {
        if(minecraft == null) return;
        var player = minecraft.player;
        if(player == null) return;
        EntitySelector self = new EntitySelector.Instance(EntityRef.of(player));
        ClientFocusPreferences.requestFocus(Optional.of(self), target, false);
    }

    private boolean isInsidePreview(double mouseX, double mouseY) {
        int x = originX();
        int y = originY();
        return mouseX >= x && mouseX <= x + StatsOverlay.width()
                && mouseY >= y && mouseY <= y + StatsOverlay.height();
    }

    private int originX() {
        return (int) (width * clampRatio(ratioX, StatsOverlay.width(), width));
    }

    private int originY() {
        return (int) (height * clampRatio(ratioY, StatsOverlay.height(), height));
    }

    private static Component scaleMessage(double scale) {
        return DSKeyLang.EditPositionScale.copy().append(Component.literal(": "
                + Math.round(scale * 100) + "%"));
    }

    private static Component opacityMessage(double opacity) {
        return DSKeyLang.EditPositionOpacity.copy().append(Component.literal(": "
                + Math.round(opacity * 100) + "%"));
    }

    private static double clampRatio(double ratio, int elementSize, int screenSize) {
        if(screenSize <= elementSize) return 0;
        return Mth.clamp(ratio, 0, 1.0 - (double) elementSize / screenSize);
    }

    // ------------------------------------------------------------------ 布局

    private void computeLayout() {
        int footerHeight = 26;
        UiLayout.Rect page = new UiLayout.Rect(PAGE_PADDING, topBar().bottom() + 6,
                Math.max(0, width - PAGE_PADDING * 2),
                Math.max(0, height - topBar().bottom() - footerHeight - 12));
        int panelWidth = Math.min(PANEL_WIDTH, Math.max(0, page.width()));
        if(width < PANEL_MIN_SCREEN_WIDTH) panelWidth = page.width();
        panel = new UiLayout.Rect(page.right() - panelWidth, page.y(), panelWidth, page.height());

        int fieldsTop = panel.y() + PANEL_PADDING + SLIDER_HEIGHT + SLIDER_GAP + SLIDER_HEIGHT
                + ROW_GAP + SLIDER_HEIGHT + SECTION_GAP + SECTION_LABEL_HEIGHT + 3;
        fieldViewport = new UiLayout.Rect(panel.x() + PANEL_PADDING, fieldsTop,
                Math.max(0, panel.width() - PANEL_PADDING * 2), Math.max(0, panel.bottom() - 12 - fieldsTop));

        UiLayout.Rect footer = new UiLayout.Rect(PAGE_PADDING, height - footerHeight + 4,
                Math.max(0, width - PAGE_PADDING * 2), footerHeight - 8);
        List<UiLayout.Rect> rightSlots = UiLayout.rowRight(footer, 5, 60, 92);
        resetSlot = new UiLayout.Rect(footer.x(), footer.y(), Math.min(96, footer.width()), footer.height());
        doneSlot = rightSlots.get(1);
        closeSlot = new UiLayout.Rect(topBar().right() - PAGE_PADDING - 18, topBar().y() + 4, 18,
                Math.max(1, topBar().height() - 8));
    }

    /** 面板内第 index 行的位置：0 缩放、1 透明度、2 目标。三段高度一致，间隔由常量统一决定。 */
    private UiLayout.Rect panelRow(int index) {
        int top = panel.y() + PANEL_PADDING;
        int y = switch(index) {
            case 0 -> top;
            case 1 -> top + SLIDER_HEIGHT + SLIDER_GAP;
            default -> top + SLIDER_HEIGHT * 2 + SLIDER_GAP + ROW_GAP;
        };
        return new UiLayout.Rect(panel.x() + PANEL_PADDING, y,
                Math.max(0, panel.width() - PANEL_PADDING * 2), SLIDER_HEIGHT);
    }

    private UiLayout.Rect fieldRow(int row) {
        return new UiLayout.Rect(fieldViewport.x(), fieldViewport.y() + row * FIELD_ROW_HEIGHT,
                fieldViewport.width(), FIELD_ROW_HEIGHT - 2);
    }
}
