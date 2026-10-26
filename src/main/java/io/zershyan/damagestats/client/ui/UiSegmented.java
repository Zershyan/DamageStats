package io.zershyan.damagestats.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.IntConsumer;

/** 分段控件：把互斥的几个选项平铺成一行，选中的那一段有一块滑动的高亮底板。 */
public class UiSegmented extends AbstractWidget implements UiTruncatable {
    /** 一个分段。enabled 为 false 时该段不可点，用于当前数据不支持的模式。 */
    public record Option(Component label, @Nullable UiIcon icon, boolean enabled) {
        public Option(Component label) {
            this(label, null, true);
        }
    }

    /** 标签左右合计留白 */
    private static final int LABEL_PADDING = 8;

    private final List<Option> options;
    private final IntConsumer onSelect;
    private final boolean pill;

    private int selected;
    private int hoveredIndex = -1;
    private float thumbPosition;
    private float hoverAmount;
    private boolean initialized;

    public UiSegmented(UiLayout.Rect bounds, List<Option> options, int selected, IntConsumer onSelect) {
        this(bounds, options, selected, false, onSelect);
    }

    public UiSegmented(UiLayout.Rect bounds, List<Option> options, int selected, boolean pill,
                       IntConsumer onSelect) {
        super(bounds.x(), bounds.y(), bounds.width(), bounds.height(), Component.empty());
        this.options = List.copyOf(options);
        this.selected = Math.clamp(selected, 0, Math.max(0, options.size() - 1));
        this.pill = pill;
        this.onSelect = onSelect;
    }

    /** 界面每帧同步一次真实状态，保证控件的选中项和服务端数据一致。 */
    public void setSelected(int index) {
        this.selected = Math.clamp(index, 0, Math.max(0, options.size() - 1));
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if(options.isEmpty()) return;
        Font font = UiTheme.font();
        float delta = UiTheme.delta();
        float radius = UiTheme.RADIUS_MD;
        hoveredIndex = isHovered ? indexAt(mouseX) : -1;
        hoverAmount = UiTheme.approach(hoverAmount, isHovered ? 1F : 0F, UiTheme.ANIM_FAST, delta);

        if(!initialized) {
            thumbPosition = selected;
            initialized = true;
        }
        thumbPosition = UiTheme.approach(thumbPosition, selected, UiTheme.ANIM_BASE, delta);
        if(Math.abs(thumbPosition - selected) < 0.002F) thumbPosition = selected;

        boolean interactive = this.active;
        float segmentWidth = width / (float) options.size();
        float thumbX = getX() + segmentWidth * thumbPosition;
        if(pill) {
            // 胶囊分段：实色凹槽，滑块是实色小圆角块，靠描边与明度分层，没有渐变与半透明
            UiDraw.roundedRect(graphics, getX(), getY(), width, height, radius,
                    interactive ? UiTheme.SURFACE : UiTheme.BACKDROP);
            UiDraw.roundedBorder(graphics, getX() + 0.5F, getY() + 0.5F, width - 1F, height - 1F,
                    Math.max(1F, radius - 0.5F), 1F, UiTheme.BORDER);
            UiDraw.roundedRect(graphics, thumbX + 1F, getY() + 1F, segmentWidth - 2F, height - 2F,
                    Math.max(1F, radius - 1F),
                    UiTheme.mix(UiTheme.SURFACE_RAISED, UiTheme.SURFACE_HOVER, hoverAmount));
            UiDraw.roundedBorder(graphics, thumbX + 1F, getY() + 1F, segmentWidth - 2F, height - 2F,
                    Math.max(1F, radius - 1F), 1F, UiTheme.ACCENT);
        } else {
            // 标签页：实色底线，选中项底下跟一根会滑动的实色指示条——网页 tab 的标准语汇
            UiDraw.rect(graphics, getX(), getY() + height - 1F, width, 1F, UiTheme.BORDER);
            float indicatorWidth = Math.max(8F, segmentWidth * 0.42F);
            UiDraw.roundedRect(graphics, thumbX + (segmentWidth - indicatorWidth) / 2F, getY() + height - 2.5F,
                    indicatorWidth, 2.5F, 1.25F, interactive ? UiTheme.ACCENT : UiTheme.ACCENT_SOFT);
        }

        for(int index = 0; index < options.size(); index++) {
            Option option = options.get(index);
            UiLayout.Rect segment = segmentRect(index);
            boolean active = option.enabled() && interactive;
            int color;
            if(!active) {
                color = UiTheme.TEXT_DISABLED;
            } else if(index == selected) {
                color = UiTheme.TEXT_STRONG;
            } else if(index == hoveredIndex) {
                color = UiTheme.mix(UiTheme.TEXT_MUTED, UiTheme.TEXT, 0.7F);
            } else {
                color = UiTheme.TEXT_MUTED;
            }
            float iconSize = iconSize(height);
            int iconSpace = iconSpace(option, height);
            // 选中项在标签页样式里再加粗，选中状态就不只靠颜色区分
            Component label = isBold(index)
                    ? UiText.truncateBold(option.label(), labelSpace(index))
                    : UiText.truncate(option.label(), labelSpace(index));
            int contentWidth = font.width(label) + iconSpace;
            int left = segment.centerX() - contentWidth / 2;
            if(option.icon() != null) {
                option.icon().draw(graphics, left + iconSize / 2F, segment.centerY(),
                        iconSize, Math.max(1F, iconSize / 7F), color);
                left += iconSpace;
            }
            int labelY = segment.centerY() - font.lineHeight / 2 + 1;
            if(isBold(index)) {
                UiText.bold(graphics, label, left, labelY, color);
            } else {
                UiText.plain(graphics, label, left, labelY, color);
            }
        }
        graphics.flush();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if(!active || !visible || button != 0 || !isMouseOver(mouseX, mouseY)) return false;
        int index = indexAt((int) mouseX);
        if(index < 0 || index >= options.size() || !options.get(index).enabled()) return true;
        if(index != selected) {
            playDownSound(Minecraft.getInstance().getSoundManager());
            selected = index;
            onSelect.accept(index);
        }
        return true;
    }

    /** 每段都放得下最宽标签时的总宽度。标签页样式的选中项会加粗，按加粗宽度算。 */
    public static int fitWidth(List<Option> options, int height, boolean pill) {
        int widest = 0;
        for(Option option : options) {
            int labelWidth = pill ? UiText.width(option.label()) : UiText.boldWidth(option.label());
            widest = Math.max(widest, labelWidth + iconSpace(option, height));
        }
        return options.size() * (widest + LABEL_PADDING);
    }

    /** 悬停取指针下的段，键盘聚焦取选中段；该段文字被截断时交出完整标签。 */
    @Override
    public @Nullable Component focusedFullText() {
        int index = isHovered ? hoveredIndex : UiTruncatable.keyboardFocused(this) ? selected : -1;
        if(index < 0 || index >= options.size()) return null;
        Component label = options.get(index).label();
        int labelWidth = isBold(index) ? UiText.boldWidth(label) : UiText.width(label);
        return labelWidth > labelSpace(index) ? label : null;
    }

    private boolean isBold(int index) {
        return index == selected && !pill;
    }

    private int labelSpace(int index) {
        return Math.max(1, segmentRect(index).width() - LABEL_PADDING - iconSpace(options.get(index), height));
    }

    private static int iconSpace(Option option, int height) {
        return option.icon() == null ? 0 : (int) iconSize(height) + 3;
    }

    private static float iconSize(int height) {
        return Math.clamp(height - 8, 6, 12);
    }

    private UiLayout.Rect segmentRect(int index) {
        float segmentWidth = width / (float) options.size();
        int left = Math.round(getX() + segmentWidth * index);
        int right = Math.round(getX() + segmentWidth * (index + 1));
        return new UiLayout.Rect(left, getY(), Math.max(0, right - left), height);
    }

    /** 命中的分段下标。纵向是否在控件内由调用方用 isMouseOver 判断，这里只管横坐标落在哪一段。 */
    private int indexAt(double mouseX) {
        if(options.isEmpty()) return -1;
        int index = (int) ((mouseX - getX()) / (width / (double) options.size()));
        return index >= 0 && index < options.size() ? index : -1;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        defaultButtonNarrationText(narration);
    }
}
