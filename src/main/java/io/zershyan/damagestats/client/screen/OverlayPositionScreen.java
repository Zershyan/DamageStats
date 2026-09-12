package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.client.ClientFocusPreferences;
import io.zershyan.damagestats.client.ClientStats;
import io.zershyan.damagestats.client.overlay.StatsOverlay;
import io.zershyan.damagestats.config.DSClientConfig;
import io.zershyan.damagestats.config.OverlayMetricScope;
import io.zershyan.damagestats.datagen.init.DSConfigLang;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.stats.EntityRef;
import io.zershyan.damagestats.stats.filter.EntitySelector;
import io.zershyan.damagestats.stats.filter.StatsFilter;
import io.zershyan.damagestats.stats.focus.FocusSelectionSlot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/** Overlay 的位置、外观和字段设置页；每个指标可独立选择本场或累计范围。 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
public class OverlayPositionScreen extends Screen {
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 20;
    private static final int TITLE_Y = 20;
    private static final int DIM_BACKGROUND = 0xFF15181C;

    private final @Nullable Screen returnScreen;
    private static final int PREVIEW_BORDER = 0xFFFFD700;
    private static final int PREVIEW_Z = 1000;
    private static final int CONTROLS_Z = 2000;
    private static final int FIELD_HEIGHT = 22;
    private static final int FIELD_WIDTH = 280;
    private static final int SCOPE_WIDTH = 44;

    private record OverlayField(
            DSConfigLang.ConfigEntry label,
            ModConfigSpec.BooleanValue visible,
            @Nullable ModConfigSpec.EnumValue<OverlayMetricScope> scope
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

    private double ratioX;
    private double ratioY;
    private boolean dragging;
    private int grabOffsetX;
    private int grabOffsetY;
    private int fieldScroll;
    private @Nullable Button targetButton;

    private record Layout(
            int controlLeft,
            int sliderWidth,
            int scaleY,
            int opacityY,
            List<ScreenLayout.Bounds> targetBounds,
            int fieldTop,
            int fieldBottom,
            int fieldWidth,
            ScreenLayout.Flow footer
    ) {}

    public OverlayPositionScreen(@Nullable Screen returnScreen) {
        super(DSKeyLang.EditPositionTitle.copy());
        this.returnScreen = returnScreen;
        ratioX = DSClientConfig.OverlayX.get();
        ratioY = DSClientConfig.OverlayY.get();
    }

    @Override
    protected void init() {
        Layout layout = layout();
        targetButton = null;
        addRenderableWidget(new AbstractSliderButton(layout.controlLeft(), layout.scaleY(), layout.sliderWidth(),
                BUTTON_HEIGHT, scaleMessage(DSClientConfig.OverlayScale.get()),
                (DSClientConfig.OverlayScale.get() - 0.5) / 1.5) {
            @Override
            protected void updateMessage() {
                setMessage(scaleMessage(0.5 + value * 1.5));
            }

            @Override
            protected void applyValue() {
                DSClientConfig.OverlayScale.set(0.5 + value * 1.5);
            }
        });
        addRenderableWidget(new AbstractSliderButton(layout.controlLeft(), layout.opacityY(), layout.sliderWidth(),
                BUTTON_HEIGHT, opacityMessage(DSClientConfig.OverlayBackgroundOpacity.get()),
                DSClientConfig.OverlayBackgroundOpacity.get()) {
            @Override
            protected void updateMessage() {
                setMessage(opacityMessage(value));
            }

            @Override
            protected void applyValue() {
                DSClientConfig.OverlayBackgroundOpacity.set(value);
            }
        });
        List<ScreenLayout.Bounds> targetBounds = layout.targetBounds();
        if(!targetBounds.isEmpty()) {
            ScreenLayout.Bounds target = targetBounds.getFirst();
            targetButton = addRenderableWidget(Button.builder(Component.empty(), button -> openTargetSelector())
                    .bounds(target.x(), target.y(), target.width(), target.height()).build());
            if(targetBounds.size() > 1) {
                ScreenLayout.Bounds allTargets = targetBounds.get(1);
                addRenderableWidget(Button.builder(DSKeyLang.OverlayAllTargets.copy(), button ->
                                setOverlayTarget(Optional.empty()))
                        .bounds(allTargets.x(), allTargets.y(), allTargets.width(), allTargets.height()).build());
            }
        }
        addFields(layout);
        ScreenLayout.Flow footer = layout.footer();
        ScreenLayout.Bounds done = footer.bounds().getFirst();
        ScreenLayout.Bounds reset = footer.bounds().getLast();
        addRenderableWidget(Button.builder(DSKeyLang.EditPositionDone.copy(), button -> onClose())
                .bounds(done.x(), done.y(), done.width(), done.height()).build());
        addRenderableWidget(Button.builder(DSKeyLang.EditPositionReset.copy(), button -> resetPosition())
                .bounds(reset.x(), reset.y(), reset.width(), reset.height()).build());
    }

    private void addFields(Layout layout) {
        int rows = visibleRows(layout);
        fieldScroll = Mth.clamp(fieldScroll, 0, maxFieldScroll(rows));
        int fieldWidth = layout.fieldWidth();
        for (int index = fieldScroll; index < FIELDS.size() && index < fieldScroll + rows; index++) {
            OverlayField field = FIELDS.get(index);
            int row = index - fieldScroll;
            int x = (width - fieldWidth) / 2;
            int y = layout.fieldTop() + row * FIELD_HEIGHT;
            int scopeWidth = field.scope() == null ? 0 : Math.clamp(fieldWidth / 3, 1, SCOPE_WIDTH);
            int checkboxWidth = field.scope() == null ? fieldWidth : Math.max(1, fieldWidth - scopeWidth - 2);
            Checkbox fieldCheckbox = Checkbox.builder(Component.translatable(field.label().getKey()), font)
                    .pos(x, y)
                    .selected(field.visible().get())
                    .onValueChange((ignored, selected) -> field.visible().set(selected))
                    .build();
            fieldCheckbox.setWidth(checkboxWidth);
            addRenderableWidget(fieldCheckbox);
            var scope = field.scope();
            if(scope == null) continue;
            addRenderableWidget(Button.builder(scopeLabel(scope.get()), button -> {
                        OverlayMetricScope next = scope.get() == OverlayMetricScope.SESSION
                                ? OverlayMetricScope.LIFETIME
                                : OverlayMetricScope.SESSION;
                        scope.set(next);
                        button.setMessage(scopeLabel(next));
                    })
                    .bounds(x + checkboxWidth + 2, y, scopeWidth, 18)
                    .build());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Layout layout = layout();
        if(mouseY < layout.fieldTop() || mouseY >= layout.fieldBottom()) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int next = Mth.clamp(fieldScroll - (int) Math.signum(scrollY), 0,
                maxFieldScroll(visibleRows(layout)));
        if(next == fieldScroll) return true;
        fieldScroll = next;
        clearWidgets();
        init();
        return true;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, DIM_BACKGROUND);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int previewX = originX();
        int previewY = originY();
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, PREVIEW_Z);
        StatsOverlay.renderBox(graphics, font, StatsOverlay.previewSummary(), previewX, previewY);
        graphics.renderOutline(previewX, previewY, StatsOverlay.width(), StatsOverlay.height(), PREVIEW_BORDER);
        graphics.pose().popPose();
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, CONTROLS_Z);
        graphics.drawCenteredString(font, title, width / 2,
                Math.clamp(TITLE_Y, 0, Math.max(0, height - 1)), 0xFFFFFFFF);
        if(targetButton != null) targetButton.setMessage(DSKeyLang.FilterTarget.get(ClientStats.summary().scope().targetName()));
        renderables.forEach(renderable -> renderable.render(graphics, mouseX, mouseY, partialTick));
        graphics.flush();
        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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

    private int visibleRows(Layout layout) {
        return Math.max(0, (layout.fieldBottom() - layout.fieldTop()) / FIELD_HEIGHT);
    }

    private static int maxFieldScroll(int rows) {
        return Math.max(0, FIELDS.size() - rows);
    }

    private static Component scopeLabel(OverlayMetricScope scope) {
        return scope == OverlayMetricScope.SESSION
                ? DSKeyLang.OverlayScopeSession.copy()
                : DSKeyLang.OverlayScopeLifetime.copy();
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

    private Layout layout() {
        ScreenLayout.Flow footer = ScreenLayout.bottomFlow(width, height, 8, BUTTON_HEIGHT, 4, 6,
                BUTTON_WIDTH, BUTTON_WIDTH);
        int controlWidth = ScreenLayout.width(width, 8);
        int sliderWidth = Math.clamp(controlWidth, 1, 220);
        int controlLeft = ScreenLayout.left(width, 8) + Math.max(0, (controlWidth - sliderWidth) / 2);
        int scaleY = Math.clamp(footer.top() - 48, 0, 44);
        int opacityY = scaleY + 24;
        int targetY = opacityY + 24;
        List<ScreenLayout.Bounds> targetBounds = ScreenLayout.flow(width, 8, targetY, BUTTON_HEIGHT, 4,
                142, 74);
        if(ScreenLayout.bottom(targetBounds, targetY) > footer.top() - 4) targetBounds = List.of();
        int controlsBottom = targetBounds.isEmpty() ? opacityY + BUTTON_HEIGHT
                : ScreenLayout.bottom(targetBounds, targetY);
        int fieldTop = controlsBottom + 6;
        int fieldBottom = Math.max(fieldTop, footer.top() - 6);
        int fieldWidth = Math.clamp(ScreenLayout.width(width, 8), 1, FIELD_WIDTH);
        return new Layout(controlLeft, sliderWidth, scaleY, opacityY, targetBounds,
                fieldTop, fieldBottom, fieldWidth, footer);
    }
}
