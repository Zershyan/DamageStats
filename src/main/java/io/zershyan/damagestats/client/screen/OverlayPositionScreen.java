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
public class OverlayPositionScreen extends Screen {
    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_GAP = 5;
    private static final int BUTTON_BOTTOM_MARGIN = 30;
    private static final int TITLE_Y = 20;
    private static final int DIM_BACKGROUND = 0x40000000;

    private final @Nullable Screen returnScreen;
    private static final int PREVIEW_BORDER = 0xFFFFD700;
    private static final int FIELD_TOP = 118;
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
    private Button targetButton;

    public OverlayPositionScreen() {
        this(null);
    }

    public OverlayPositionScreen(@Nullable Screen returnScreen) {
        super(DSKeyLang.EditPositionTitle.copy());
        this.returnScreen = returnScreen;
        ratioX = DSClientConfig.OverlayX.get();
        ratioY = DSClientConfig.OverlayY.get();
    }

    @Override
    protected void init() {
        int centerX = width / 2;
        int controlLeft = centerX - 110;
        addRenderableWidget(new AbstractSliderButton(controlLeft, 44, 220, BUTTON_HEIGHT, Component.empty(),
                (DSClientConfig.OverlayScale.get() - 0.5) / 1.5) {
            @Override
            protected void updateMessage() {
                setMessage(DSKeyLang.EditPositionScale.copy().append(Component.literal(": "
                        + Math.round((0.5 + value * 1.5) * 100) + "%")));
            }

            @Override
            protected void applyValue() {
                DSClientConfig.OverlayScale.set(0.5 + value * 1.5);
            }
        });
        addRenderableWidget(new AbstractSliderButton(controlLeft, 68, 220, BUTTON_HEIGHT, Component.empty(),
                DSClientConfig.OverlayBackgroundOpacity.get()) {
            @Override
            protected void updateMessage() {
                setMessage(DSKeyLang.EditPositionOpacity.copy().append(Component.literal(": "
                        + Math.round(value * 100) + "%")));
            }

            @Override
            protected void applyValue() {
                DSClientConfig.OverlayBackgroundOpacity.set(value);
            }
        });
        targetButton = addRenderableWidget(Button.builder(Component.empty(), button -> openTargetSelector())
                .bounds(controlLeft, 92, 142, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(DSKeyLang.OverlayAllTargets.copy(), button -> setOverlayTarget(Optional.empty()))
                .bounds(controlLeft + 146, 92, 74, BUTTON_HEIGHT).build());
        addFields();
        int buttonY = height - BUTTON_BOTTOM_MARGIN;
        addRenderableWidget(Button.builder(DSKeyLang.EditPositionDone.copy(), button -> onClose())
                .bounds(centerX - BUTTON_WIDTH - BUTTON_GAP, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(DSKeyLang.EditPositionReset.copy(), button -> resetPosition())
                .bounds(centerX + BUTTON_GAP, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    private void addFields() {
        int rows = visibleRows();
        fieldScroll = Mth.clamp(fieldScroll, 0, maxFieldScroll(rows));
        int fieldWidth = Math.min(FIELD_WIDTH, width - 16);
        for (int index = fieldScroll; index < FIELDS.size() && index < fieldScroll + rows; index++) {
            OverlayField field = FIELDS.get(index);
            int row = index - fieldScroll;
            int x = (width - fieldWidth) / 2;
            int y = FIELD_TOP + row * FIELD_HEIGHT;
            int checkboxWidth = field.scope() == null ? fieldWidth : fieldWidth - SCOPE_WIDTH - 2;
            Checkbox fieldCheckbox = Checkbox.builder(Component.translatable(field.label().getKey()), font)
                    .pos(x, y)
                    .selected(field.visible().get())
                    .onValueChange((ignored, selected) -> field.visible().set(selected))
                    .build();
            fieldCheckbox.setWidth(checkboxWidth);
            addRenderableWidget(fieldCheckbox);
            if(field.scope() == null) continue;
            addRenderableWidget(Button.builder(scopeLabel(field.scope().get()), button -> {
                        OverlayMetricScope next = field.scope().get() == OverlayMetricScope.SESSION
                                ? OverlayMetricScope.LIFETIME
                                : OverlayMetricScope.SESSION;
                        field.scope().set(next);
                        button.setMessage(scopeLabel(next));
                    })
                    .bounds(x + checkboxWidth + 2, y, SCOPE_WIDTH, 18)
                    .build());
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if(mouseY < FIELD_TOP || mouseY > height - BUTTON_BOTTOM_MARGIN - 4) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        int next = Mth.clamp(fieldScroll - (int) Math.signum(scrollY), 0, maxFieldScroll(visibleRows()));
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
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, TITLE_Y, 0xFFFFFFFF);
        if(targetButton != null) targetButton.setMessage(DSKeyLang.FilterTarget.get(ClientStats.summary().scope().targetName()));
        int x = originX();
        int y = originY();
        StatsOverlay.renderBox(graphics, font, StatsOverlay.previewSummary(), x, y);
        graphics.renderOutline(x, y, StatsOverlay.width(), StatsOverlay.height(), PREVIEW_BORDER);
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
            if(field.scope() != null) field.scope().save();
        });
        if(returnScreen != null) minecraft.setScreen(returnScreen);
        else super.onClose();
    }

    private void resetPosition() {
        ratioX = DSClientConfig.DefaultOverlayX;
        ratioY = DSClientConfig.DefaultOverlayY;
    }

    private void openTargetSelector() {
        if(minecraft.player == null) return;
        EntitySelector self = new EntitySelector.Instance(EntityRef.of(minecraft.player));
        minecraft.setScreen(new FocusEntitySelectorScreen(this, FocusSelectionSlot.TARGET,
                StatsFilter.fromSource(self),
                (selector, ignored) -> setOverlayTarget(Optional.of(selector))));
    }

    private void setOverlayTarget(Optional<EntitySelector> target) {
        if(minecraft.player == null) return;
        EntitySelector self = new EntitySelector.Instance(EntityRef.of(minecraft.player));
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

    private int visibleRows() {
        return Math.max(1, (height - BUTTON_BOTTOM_MARGIN - FIELD_TOP - 4) / FIELD_HEIGHT);
    }

    private static int maxFieldScroll(int rows) {
        return Math.max(0, FIELDS.size() - rows);
    }

    private static Component scopeLabel(OverlayMetricScope scope) {
        return scope == OverlayMetricScope.SESSION
                ? DSKeyLang.OverlayScopeSession.copy()
                : DSKeyLang.OverlayScopeLifetime.copy();
    }

    private static double clampRatio(double ratio, int elementSize, int screenSize) {
        if(screenSize <= elementSize) return 0;
        return Mth.clamp(ratio, 0, 1.0 - (double) elementSize / screenSize);
    }
}
