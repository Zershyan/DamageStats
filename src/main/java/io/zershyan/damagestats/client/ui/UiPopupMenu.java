package io.zershyan.damagestats.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 弹出菜单。由界面持有并在最后绘制（会临时抬到最高层），不做成控件是因为它必须盖住所有兄弟控件。
 * 空间不足时自动翻到锚点上方，行数超出可视区时可滚动。
 */
public class UiPopupMenu {
    /** 菜单项。danger 仅影响配色，方便把不可逆操作在视觉上拉开。 */
    public record Item(Component label, @Nullable UiIcon icon, boolean enabled, boolean danger, Runnable action) {
        public Item(Component label, @Nullable UiIcon icon, Runnable action) {
            this(label, icon, true, false, action);
        }

        public static Item danger(Component label, @Nullable UiIcon icon, Runnable action) {
            return new Item(label, icon, true, true, action);
        }

        public static Item disabled(Component label) {
            return new Item(label, null, false, false, () -> {});
        }
    }

    private static final int ITEM_HEIGHT = 16;
    private static final int PADDING = 4;
    private static final int MAX_VISIBLE_ROWS = 8;
    private static final int MIN_WIDTH = 96;
    private static final int MAX_WIDTH = 220;

    private final List<Item> items;
    private final int screenWidth;
    private final int screenHeight;

    private boolean open;
    private UiLayout.Rect bounds = new UiLayout.Rect(0, 0, 0, 0);
    private int scrollOffset;
    private int hoveredIndex = -1;
    private float openAmount;

    public UiPopupMenu(List<Item> items, int screenWidth, int screenHeight) {
        this.items = List.copyOf(items);
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;
        scrollOffset = 0;
        hoveredIndex = -1;
    }

    public void toggle(UiLayout.Rect anchor) {
        if(open) close();
        else open(anchor);
    }

    /** 在锚点下方展开；下方放不下就翻到上方，两边都放不下时取空间更大的一侧并裁剪。 */
    public void open(UiLayout.Rect anchor) {
        if(items.isEmpty()) return;
        open = true;
        scrollOffset = 0;
        int rows = Math.min(MAX_VISIBLE_ROWS, items.size());
        int width = MIN_WIDTH;
        Font font = UiTheme.font();
        for(Item item : items) {
            int iconSpace = item.icon() == null ? 0 : ITEM_HEIGHT;
            width = Math.max(width, font.width(item.label()) + iconSpace + PADDING * 2 + 14);
        }
        width = Math.min(width, Math.min(MAX_WIDTH, Math.max(1, screenWidth - 4)));
        int height = rows * ITEM_HEIGHT + PADDING * 2;
        int left = Mth.clamp(anchor.x(), 2, Math.max(2, screenWidth - width - 2));
        int below = anchor.bottom() + 2;
        int above = anchor.y() - 2 - height;
        int top;
        if(below + height <= screenHeight - 2) top = below;
        else if(above >= 2) top = above;
        else top = Mth.clamp(below, 2, Math.max(2, screenHeight - height - 2));
        bounds = new UiLayout.Rect(left, top, width, Math.min(height, Math.max(1, screenHeight - top - 2)));
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if(!open) {
            openAmount = 0F;
            return;
        }
        openAmount = UiTheme.approach(openAmount, 1F, UiTheme.ANIM_FAST, UiTheme.delta());
        float progress = UiTheme.easeOut(openAmount);
        Font font = UiTheme.font();
        int rows = visibleRows();
        hoveredIndex = bounds.contains(mouseX, mouseY) ? rowAt(mouseY) : -1;

        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0F, 0F, 600F);
        int slide = Math.round((1F - progress) * 4F);
        float cardHeight = Math.max(1F, bounds.height() * progress);
        UiDraw.roundedRect(graphics, bounds.x(), bounds.y() + slide, bounds.width(),
                cardHeight, UiTheme.RADIUS_LG, UiTheme.alpha(UiTheme.SURFACE_RAISED, progress));
        UiDraw.roundedBorder(graphics, bounds.x() + 0.5F, bounds.y() + slide + 0.5F, bounds.width() - 1F,
                cardHeight - 1F, UiTheme.RADIUS_LG, 1F, UiTheme.alpha(UiTheme.BORDER_STRONG, progress));
        graphics.flush();
        if(progress < 0.6F) {
            graphics.pose().popPose();
            return;
        }

        int firstRow = scrollOffset;
        int lastRow = Math.min(items.size(), firstRow + rows);
        for(int index = firstRow; index < lastRow; index++) {
            Item item = items.get(index);
            UiLayout.Rect row = rowRect(index - firstRow).inset(PADDING / 2, 0);
            boolean hovered = index == hoveredIndex && item.enabled();
            if(hovered) {
                int fill = item.danger() ? UiTheme.DANGER_SOFT : UiTheme.SURFACE_HOVER;
                UiDraw.roundedRect(graphics, row.x(), row.y() + slide, row.width(), row.height(),
                        UiTheme.RADIUS_SM, fill);
            }
            int color = !item.enabled() ? UiTheme.TEXT_DISABLED
                    : item.danger() ? (hovered ? UiTheme.DANGER_BRIGHT : UiTheme.DANGER)
                    : (hovered ? UiTheme.TEXT_STRONG : UiTheme.TEXT_SECONDARY);
            int contentLeft = row.x() + PADDING + 2;
            if(item.icon() != null) {
                float iconSize = ITEM_HEIGHT - 7F;
                item.icon().draw(graphics, contentLeft + iconSize / 2F, row.centerY() + slide, iconSize, 1F, color);
                contentLeft += (int) iconSize + 4;
            }
            int available = Math.max(1, row.right() - PADDING - contentLeft);
            Component label = font.width(item.label()) > available
                    ? Component.literal(font.plainSubstrByWidth(item.label().getString(),
                    Math.max(1, available - 4)) + "…")
                    : item.label();
            UiText.plain(graphics, label, contentLeft, row.centerY() + slide - font.lineHeight / 2 + 1, color);
        }
        if(items.size() > rows) {
            // 行数超出时右侧给一条细滚动指示，不做成可拖拽的滚动条，避免与主界面的滚动抢事件
            int trackTop = bounds.y() + PADDING;
            int trackHeight = bounds.height() - PADDING * 2;
            float thumbHeight = Math.max(6F, trackHeight * rows / (float) items.size());
            float thumbTop = trackTop + (trackHeight - thumbHeight) * scrollOffset
                    / (float) Math.max(1, items.size() - rows);
            UiDraw.roundedRect(graphics, bounds.right() - 2F, thumbTop, 1.5F, thumbHeight, 0.75F,
                    UiTheme.alpha(UiTheme.ACCENT, 0.5F));
        }
        graphics.flush();
        graphics.pose().popPose();
    }

    /**
     * 返回 true 表示事件已被菜单吃掉。落在菜单外时不做处理也不关闭——
     * 调用方需要区分「点在自己按钮上」和「点在空白处」两种情况，收起由调用方决定。
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if(!open || button != 0 || !bounds.contains(mouseX, mouseY)) return false;
        int index = scrollOffset + rowAt(mouseY);
        if(index >= 0 && index < items.size() && items.get(index).enabled()) {
            Runnable action = items.get(index).action();
            close();
            action.run();
        }
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if(!open || !bounds.contains(mouseX, mouseY)) return false;
        int max = Math.max(0, items.size() - visibleRows());
        scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(scrollY), 0, max);
        return true;
    }

    private int visibleRows() {
        return Math.max(1, Math.min(MAX_VISIBLE_ROWS, items.size()));
    }

    private int rowAt(double mouseY) {
        int index = (int) ((mouseY - bounds.y() - PADDING) / ITEM_HEIGHT);
        return index >= 0 && index < visibleRows() ? index : -1;
    }

    private UiLayout.Rect rowRect(int row) {
        return new UiLayout.Rect(bounds.x(), bounds.y() + PADDING + row * ITEM_HEIGHT, bounds.width(), ITEM_HEIGHT);
    }

    /** 菜单占用屏幕时，界面应跳过其下方的点击处理。 */
    public boolean covers(double mouseX, double mouseY) {
        return open && bounds.contains(mouseX, mouseY);
    }
}
