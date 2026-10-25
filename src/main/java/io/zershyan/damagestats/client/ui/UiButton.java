package io.zershyan.damagestats.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/** 现代风格按钮：圆角、渐变底、悬停过渡动画，可带前后置图标。 */
public class UiButton extends AbstractWidget {
    /** 视觉变体。语义由调用方决定，控件只负责观感。 */
    public enum Variant {
        /** 填充强调色，用于每一屏的主操作。 */
        PRIMARY,
        /** 常规灰面，用于大部分操作。 */
        SECONDARY,
        /** 无底色，悬停才浮出背景，用于低权重操作。 */
        GHOST,
        /** 危险操作，红色描边与文字。 */
        DANGER,
        /** 只有描边，用于次级入口。 */
        OUTLINE,
        /** 胶囊标签，用于展示当前筛选条件。 */
        CHIP
    }

    /** 内容排布。 */
    public enum Align { CENTER, LEFT }

    /** 点击回调。参数是按钮自身，方便在回调里直接改自己的文案。 */
    @FunctionalInterface
    public interface OnPress {
        void onPress(UiButton button);
    }

    private final Variant variant;
    private final Align align;
    private final @Nullable UiIcon icon;
    private final @Nullable UiIcon trailingIcon;
    private final OnPress onPress;

    private float hoverAmount;
    private boolean pressed;

    private UiButton(Builder builder) {
        super(builder.bounds.x(), builder.bounds.y(), builder.bounds.width(), builder.bounds.height(),
                builder.message == null ? Component.empty() : builder.message);
        this.variant = builder.variant;
        this.align = builder.align;
        this.icon = builder.icon;
        this.trailingIcon = builder.trailingIcon;
        this.onPress = builder.onPress;
    }

    public static Builder builder(Component message, OnPress onPress) {
        return new Builder(message, onPress);
    }

    /** 构造器式写法，避免每个调用点都写一长串链。 */
    public static final class Builder {
        private final @Nullable Component message;
        private final OnPress onPress;
        private UiLayout.Rect bounds = new UiLayout.Rect(0, 0, 1, 1);
        private Variant variant = Variant.SECONDARY;
        private Align align = Align.CENTER;
        private @Nullable UiIcon icon;
        private @Nullable UiIcon trailingIcon;

        private Builder(@Nullable Component message, OnPress onPress) {
            this.message = message;
            this.onPress = onPress;
        }

        public Builder bounds(UiLayout.Rect rect) {
            this.bounds = rect;
            return this;
        }

        public Builder variant(Variant variant) {
            this.variant = variant;
            return this;
        }

        public Builder align(Align align) {
            this.align = align;
            return this;
        }

        public Builder icon(@Nullable UiIcon icon) {
            this.icon = icon;
            return this;
        }

        public Builder trailingIcon(@Nullable UiIcon trailingIcon) {
            this.trailingIcon = trailingIcon;
            return this;
        }

        public UiButton build() {
            return new UiButton(this);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Font font = UiTheme.font();
        boolean interactive = this.active;
        float target = interactive && this.isHovered ? 1F : 0F;
        hoverAmount = UiTheme.approach(hoverAmount, target, UiTheme.ANIM_FAST, UiTheme.delta());
        float hover = hoverAmount > 0.5F ? 1F : 0F;
        float press = pressed && interactive ? 1F : 0F;
        float radius = UiTheme.RADIUS_SM;

        int fill = backgroundTop(hover, press);
        int border = borderColor(hover);
        int text = textColor(hover);
        int iconColor = UiButtonColors.icon(palette(), variant.ordinal());
        if(!interactive) {
            fill = fill == 0 ? 0 : UiTheme.alpha(fill, 0.5F);
            border = border == 0 ? 0 : UiTheme.alpha(border, 0.5F);
            text = UiTheme.TEXT_DISABLED;
            iconColor = UiTheme.TEXT_DISABLED;
        }

        // 面：实色圆角矩形，没有渐变与高光
        if((fill >>> 24) != 0) {
            UiDraw.roundedRect(graphics, getX(), getY(), width, height, radius, fill);
        }
        // 描边：1px 实色线，静止与悬停用不同颜色
        if((border >>> 24) != 0) {
            UiDraw.roundedBorder(graphics, getX(), getY(), width, height, radius, 1F, border);
        }
        // 键盘焦点环：网页靠它告诉用户 Tab 到了哪里，MC 的控件默认没有这个反馈，必须保留
        if(isFocused() && interactive) {
            UiDraw.roundedBorder(graphics, getX() - 2F, getY() - 2F, width + 4F, height + 4F,
                    radius + 2F, 1F, UiButtonColors.solid(UiTheme.ACCENT));
        }

        int offsetY = pressed && interactive ? 1 : 0;
        int iconSize = iconSize(height);
        int padding = 6;
        boolean hasLabel = !getMessage().getString().isEmpty();
        int iconSpace = 0;
        if(icon != null) iconSpace += iconSize + (hasLabel ? 3 : 0);
        if(trailingIcon != null) iconSpace += iconSize + (hasLabel ? 3 : 0);

        // 先把标签截到真正放得下的宽度，再拿截断后的宽度整体居中。
        // 否则窄按钮（只有图标的 ✕ / ⟳）会因为宽度算大而整体偏右，尾部图标还会被挤出按钮。
        int labelBudget = Math.max(1, width - padding * 2 - iconSpace);
        Component label = hasLabel ? fit(font, getMessage(), labelBudget) : Component.empty();
        int labelWidth = hasLabel ? font.width(label) : 0;
        int contentWidth = labelWidth + iconSpace;
        int contentLeft = align == Align.LEFT ? getX() + padding
                : getX() + Math.max(0, (width - contentWidth) / 2);
        int contentY = getY() + height / 2 + offsetY;

        if(icon != null) {
            icon.draw(graphics, contentLeft + iconSize / 2F, contentY, iconSize, 1F, iconColor);
            contentLeft += iconSize + (hasLabel ? 3 : 0);
        }
        if(hasLabel) {
            int labelY = getY() + (height - font.lineHeight) / 2 + 1 + offsetY;
            graphics.drawString(font, label, contentLeft, labelY, text, false);
            contentLeft += labelWidth;
        }
        if(trailingIcon != null) {
            trailingIcon.draw(graphics, contentLeft + (hasLabel ? 3 : 0) + iconSize / 2F, contentY,
                    iconSize, 1F, iconColor);
        }
        graphics.flush();
    }

    /** 图标边长随按钮高度增长，16px 高的侧栏行得到 12px 图标。 */
    private static int iconSize(int height) {
        return Mth.clamp(height - 4, 8, 14);
    }

    /** 从 UiTheme 取出按钮需要的基础色。颜色规则本身在 UiButtonColors，这里只负责把令牌交过去。 */
    private static UiButtonColors.Palette palette() {
        return new UiButtonColors.Palette(
                UiTheme.ACCENT, UiTheme.ACCENT_BRIGHT, UiTheme.ON_ACCENT, UiTheme.ICON_ACCENT,
                UiTheme.SURFACE, UiTheme.SURFACE_RAISED, UiTheme.SURFACE_HOVER, UiTheme.BACKDROP,
                UiTheme.DANGER, UiTheme.DANGER_BRIGHT, UiTheme.DANGER_SOFT,
                UiTheme.BORDER_STRONG, UiTheme.TEXT_STRONG, UiTheme.TEXT_SECONDARY, UiTheme.TEXT_MUTED,
                UiTheme.TEXT_DISABLED);
    }

    /** 底色：规则在 UiButtonColors.fill。 */
    private int backgroundTop(float hover, float press) {
        return UiButtonColors.fill(palette(), variant.ordinal(), hover > 0F, press > 0F);
    }

    /** 描边：规则在 UiButtonColors.border。 */
    private int borderColor(float hover) {
        return UiButtonColors.border(palette(), variant.ordinal(), hover > 0F);
    }

    /** 文字：规则在 UiButtonColors.text。 */
    private int textColor(float hover) {
        return UiButtonColors.text(palette(), variant.ordinal(), hover > 0F);
    }

    /** 宽度不足时截断文字，避免文字画出按钮边界。 */
    private static Component fit(Font font, Component text, int available) {
        if(available <= 0) return Component.empty();
        if(font.width(text) <= available) return text;
        String plain = text.getString();
        int ellipsis = font.width("…");
        String clipped = font.plainSubstrByWidth(plain, Math.max(1, available - ellipsis));
        return Component.literal(clipped + "…");
    }

    /** 文字被截断时返回完整文本，供界面挂悬停提示。 */
    public @Nullable Component truncatedLabel() {
        Font font = UiTheme.font();
        int iconSize = iconSize(height);
        int trailingSpace = trailingIcon == null ? 0 : iconSize + 3;
        int iconSpace = (icon == null ? 0 : iconSize + 3) + trailingSpace;
        int padding = 6;
        int available = Math.max(1, width - padding * 2 - iconSpace);
        return font.width(getMessage()) > available ? getMessage() : null;
    }

    public boolean isLabelTruncated() {
        return truncatedLabel() != null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        if(handled) pressed = true;
        return handled;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        pressed = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        onPress.onPress(this);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        defaultButtonNarrationText(narration);
    }
}
