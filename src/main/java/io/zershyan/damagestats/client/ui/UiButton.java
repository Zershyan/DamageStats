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
    private final boolean pill;

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
        this.pill = builder.pill;
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
        private boolean pill;

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

        public Builder pill() {
            this.pill = true;
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
        float hover = UiTheme.easeOut(hoverAmount);
        float press = pressed && interactive ? 1F : 0F;
        float radius = pill ? Math.min(width, height) / 2F : UiTheme.RADIUS_MD;

        int top = backgroundTop(hover, press);
        int bottom = backgroundBottom(hover, press);
        int border = borderColor(hover);
        int text = textColor(hover);
        if(!interactive) {
            top = UiTheme.alpha(top, 0.5F);
            bottom = UiTheme.alpha(bottom, 0.5F);
            border = UiTheme.alpha(border, 0.5F);
            text = UiTheme.TEXT_DISABLED;
        }

        if((top >>> 24) != 0 || (bottom >>> 24) != 0) {
            UiDraw.roundedRect(graphics, getX(), getY(), width, height, radius, top, bottom);
        }
        if((border >>> 24) != 0) {
            UiDraw.roundedBorder(graphics, getX(), getY(), width, height, radius, 1F, border);
        } else if(variant == Variant.SECONDARY && interactive) {
            // 灰面按钮默认没有描边，只靠一层内高光分出边界，比描边更贴近网页卡片的观感
            UiDraw.roundedBorder(graphics, getX() + 0.5F, getY() + 0.5F, width - 1F, height - 1F,
                    Math.max(1F, radius - 0.5F), 1F, UiTheme.alpha(UiTheme.HIGHLIGHT, 0.6F));
        }
        if(variant == Variant.PRIMARY && (top >>> 24) != 0) {
            // 强调色按钮顶部压一道高光，做出玻璃被照亮的效果
            UiDraw.roundedRect(graphics, getX() + 1F, getY() + 1F, width - 2F, height / 2F,
                    Math.max(1F, radius - 1F), UiTheme.alpha(0xFFFFFFFF, 0.16F + hover * 0.1F));
        }
        if(hover > 0.01F && interactive && variant != Variant.PRIMARY) {
            // 悬停时内侧一圈强调色微光，比整块背景变亮更克制
            UiDraw.roundedBorder(graphics, getX() + 0.5F, getY() + 0.5F, width - 1F, height - 1F,
                    Math.max(1F, radius - 0.5F), 1F, UiTheme.alpha(UiTheme.ACCENT, 0.28F * hover));
        }

        if(isFocused() && interactive) {
            // 键盘焦点环：网页里靠它告诉用户 Tab 到了哪里，MC 的控件默认完全没有这个反馈
            UiDraw.roundedBorder(graphics, getX() - 1.5F, getY() - 1.5F, width + 3F, height + 3F,
                    radius + 1.5F, 1F, UiTheme.alpha(UiTheme.ACCENT, 0.75F));
        }

        int offsetY = pressed && interactive ? 1 : 0;
        int iconSize = Mth.clamp(height - 8, 6, 12);
        float iconThickness = Math.max(1F, iconSize / 7F);
        int padding = pill ? Mth.clamp(height / 2, 2, 8) : 6;
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
            icon.draw(graphics, contentLeft + iconSize / 2F, contentY, iconSize, iconThickness, text);
            contentLeft += iconSize + (hasLabel ? 3 : 0);
        }
        if(hasLabel) {
            int labelY = getY() + (height - font.lineHeight) / 2 + 1 + offsetY;
            graphics.drawString(font, label, contentLeft, labelY, text, false);
            contentLeft += labelWidth;
        }
        if(trailingIcon != null) {
            trailingIcon.draw(graphics, contentLeft + (hasLabel ? 3 : 0) + iconSize / 2F, contentY,
                    iconSize, iconThickness, text);
        }
        graphics.flush();
    }

    private int backgroundTop(float hover, float press) {
        return switch(variant) {
            case PRIMARY -> UiTheme.lighten(UiTheme.ACCENT_BRIGHT, 0.06F * hover - 0.12F * press);
            case SECONDARY -> UiTheme.mix(UiTheme.SURFACE_RAISED, UiTheme.SURFACE_HOVER, Math.max(hover, press));
            case GHOST -> UiTheme.alpha(UiTheme.SURFACE_HOVER, Math.max(hover * 0.85F, press * 0.75F));
            case DANGER -> UiTheme.mix(UiTheme.DANGER_SOFT, UiTheme.DANGER, 0.22F * hover);
            case OUTLINE -> UiTheme.alpha(UiTheme.SURFACE_HOVER, Math.max(hover * 0.6F, press * 0.5F));
            case CHIP -> UiTheme.mix(UiTheme.ACCENT_FAINT, UiTheme.ACCENT_SOFT, Math.max(hover, 0.7F));
        };
    }

    private int backgroundBottom(float hover, float press) {
        return switch(variant) {
            case PRIMARY -> UiTheme.lighten(UiTheme.ACCENT, 0.06F * hover - 0.12F * press);
            case SECONDARY -> UiTheme.mix(UiTheme.SURFACE, UiTheme.SURFACE_HOVER, Math.max(hover, press) * 0.85F);
            case GHOST -> UiTheme.alpha(UiTheme.SURFACE_HOVER, Math.max(hover * 0.7F, press * 0.6F));
            case DANGER -> UiTheme.mix(UiTheme.DANGER_SOFT, UiTheme.DANGER, 0.12F * hover);
            case OUTLINE -> UiTheme.alpha(UiTheme.SURFACE_HOVER, Math.max(hover * 0.5F, press * 0.4F));
            case CHIP -> UiTheme.mix(UiTheme.ACCENT_FAINT, UiTheme.ACCENT_SOFT, Math.max(hover * 0.85F, 0.6F));
        };
    }

    private int borderColor(float hover) {
        return switch(variant) {
            case PRIMARY -> UiTheme.alpha(0x00000000, 0F);
            case SECONDARY -> UiTheme.mix(UiTheme.BORDER, UiTheme.ACCENT, 0.45F * hover);
            case GHOST -> UiTheme.alpha(UiTheme.ACCENT, 0.35F * hover);
            case DANGER -> UiTheme.mix(UiTheme.alpha(UiTheme.DANGER, 0.55F), UiTheme.DANGER_BRIGHT, hover);
            case OUTLINE -> UiTheme.mix(UiTheme.BORDER_STRONG, UiTheme.ACCENT, hover);
            case CHIP -> UiTheme.mix(UiTheme.alpha(UiTheme.ACCENT, 0.35F), UiTheme.ACCENT, hover);
        };
    }

    private int textColor(float hover) {
        return switch(variant) {
            case PRIMARY -> UiTheme.ON_ACCENT;
            case SECONDARY -> UiTheme.mix(UiTheme.TEXT_SECONDARY, UiTheme.TEXT_STRONG, hover);
            case GHOST -> UiTheme.mix(UiTheme.TEXT_MUTED, UiTheme.TEXT, hover);
            case DANGER -> UiTheme.mix(UiTheme.DANGER, UiTheme.DANGER_BRIGHT, hover);
            case OUTLINE -> UiTheme.mix(UiTheme.TEXT_SECONDARY, UiTheme.ACCENT_BRIGHT, hover);
            case CHIP -> UiTheme.ACCENT_BRIGHT;
        };
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

    /** 文字被截断时返回完整文本，供界面挂悬停提示。判定用的宽度必须和绘制时一致。 */
    public @Nullable Component truncatedLabel() {
        Font font = UiTheme.font();
        int iconSize = Mth.clamp(height - 8, 6, 12);
        int trailingSpace = trailingIcon == null ? 0 : iconSize + 3;
        int iconSpace = (icon == null ? 0 : iconSize + 3) + trailingSpace;
        int padding = pill ? Mth.clamp(height / 2, 2, 8) : 6;
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
    public void onClick(double mouseX, double mouseY) {
        onPress.onPress(this);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        defaultButtonNarrationText(narration);
    }
}
