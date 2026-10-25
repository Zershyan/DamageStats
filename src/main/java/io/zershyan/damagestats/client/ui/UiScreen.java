package io.zershyan.damagestats.client.ui;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** 所有统计界面的公共外壳：统一背景、顶栏、底栏、卡片、分组标题与空/加载态的画法。 */
public abstract class UiScreen extends Screen {
    protected static final int TOP_BAR_HEIGHT = 26;
    protected static final int BOTTOM_BAR_HEIGHT = 26;
    protected static final int PAGE_PADDING = 10;

    /** 入场动画时长与位移。网页切页几乎都有这么一下，缺了就会显得「硬」。 */
    private static final float ENTRANCE_SECONDS = 0.18F;
    private static final float ENTRANCE_RISE = 7F;

    private static final float SPINNER_RADIUS = 7F;
    private static final long SPINNER_PERIOD_MILLIS = 1200L;

    private long openedMillis;

    /** 需要被裁剪区约束的控件。放在滚动区里的控件都要登记，否则滚出面板的整行会画到别的 UI 上。 */
    private final List<AbstractWidget> clippedWidgets = new ArrayList<>();
    private @Nullable UiLayout.Rect widgetClip;

    protected UiScreen(Component title) {
        super(title);
    }

    /**
     * 固定渲染顺序：推进动画计时 → 背景（虚化 + 玻璃底色）→ 内容与控件 → 浮层。
     * 内容和控件整体带一个入场位移，所以子类不要再自己画背景。
     */
    @Override
    public final void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiTheme.tick();
        widgetClip = null;
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.pose().pushPose();
        graphics.pose().translate(0F, (1F - entranceProgress()) * ENTRANCE_RISE, 0F);
        renderContent(graphics, mouseX, mouseY, partialTick);
        renderWidgets(graphics, mouseX, mouseY, partialTick);
        renderOverlay(graphics, mouseX, mouseY, partialTick);
        graphics.pose().popPose();
    }

    private void renderWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        UiLayout.Rect clip = widgetClip;
        for(Renderable renderable : renderables) {
            if(clippedWidgets.contains(renderable)) continue;
            renderable.render(graphics, mouseX, mouseY, partialTick);
        }
        if(clippedWidgets.isEmpty()) return;
        if(clip == null || clip.isEmpty()) {
            // 没声明裁剪区就照常画，否则登记过的控件会凭空消失
            for(AbstractWidget widget : clippedWidgets) {
                widget.render(graphics, mouseX, mouseY, partialTick);
            }
            return;
        }
        graphics.enableScissor(clip.x(), clip.y(), clip.right(), clip.bottom());
        for(AbstractWidget widget : clippedWidgets) {
            widget.render(graphics, mouseX, mouseY, partialTick);
        }
        graphics.disableScissor();
    }

    /** 注册一个受裁剪区约束的控件。它照常接收鼠标事件，只是绘制时被裁到 {@link #clipWidgetsTo} 给的范围。 */
    protected final <T extends AbstractWidget> T addClippedWidget(T widget) {
        addRenderableWidget(widget);
        clippedWidgets.add(widget);
        return widget;
    }

    /** 在 {@link #renderContent} 里调用，指定本帧裁剪区控件用的范围。 */
    protected final void clipWidgetsTo(@Nullable UiLayout.Rect clip) {
        this.widgetClip = clip;
    }

    /**
     * 必须和 renderables 一起清空。{@code rebuildWidgets()} 只清 renderables，
     * 留在 clippedWidgets 里的旧控件会变成「照样画得出来、却不在 children 里所以点不到」的幽灵，
     * 在窗口缩放或界面被复用重开时表现为文字重影、按钮点不动。
     */
    @Override
    protected void clearWidgets() {
        super.clearWidgets();
        clippedWidgets.clear();
        widgetClip = null;
    }

    @Override
    public void added() {
        super.added();
        openedMillis = Util.getMillis();
    }

    /** 入场进度，0 是刚打开、1 是稳定态。子类想让某些元素错开进场可以自行读取。 */
    protected final float entranceProgress() {
        float elapsed = (Util.getMillis() - openedMillis) / (ENTRANCE_SECONDS * 1000F);
        return UiTheme.easeOut(Mth.clamp(elapsed, 0F, 1F));
    }

    /** 界面自身的内容，绘制在所有控件之前。背景已经由 {@link #renderBackground} 画好了。 */
    protected abstract void renderContent(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    /** 控件之后的浮层，用于必须盖住一切的工具提示。 */
    protected void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    /**
     * 背景三层：先让 MC 把主帧缓冲做一次模糊后处理，再压一层暗色，最后盖上半透明的玻璃底色。
     * 模糊是这套观感的地基——没有它，面板后面透出来的就是清晰的方块，只会显得脏。
     */
    @Override
    public void renderBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if(minecraft != null) {
            renderBlurredBackground(partialTick);
        }
        UiDraw.rect(graphics, 0, 0, width, height, UiTheme.SCRIM);
        UiDraw.rect(graphics, 0, 0, width, height, UiTheme.BACKDROP);
        graphics.flush();
    }

    /** 顶部应用栏：标题、状态胶囊与右侧操作区。 */
    protected void renderTopBar(GuiGraphics graphics, Component heading, @Nullable Component status,
                                @Nullable UiLayout.Rect statusBounds, boolean statusHighlighted) {
        renderTopBar(graphics, heading, status, statusBounds, statusHighlighted, 30);
    }

    /**
     * @param rightInset 右侧为操作按钮预留的宽度，标题会据此提前截断，避免和按钮叠在一起
     */
    protected void renderTopBar(GuiGraphics graphics, Component heading, @Nullable Component status,
                                @Nullable UiLayout.Rect statusBounds, boolean statusHighlighted,
                                int rightInset) {
        UiLayout.Rect bar = topBar();
        if(bar.isEmpty()) return;
        UiDraw.rect(graphics, bar.x(), bar.y(), bar.width(), bar.height(), UiTheme.CHROME);
        // 顶栏底边一条实色线，把页头与内容区分开，品牌色只留给标题前的圆点
        UiDraw.rect(graphics, bar.x(), bar.bottom() - 1, bar.width(), 1F, UiTheme.BORDER);
        int left = PAGE_PADDING;
        int available = Math.max(1, bar.width() - PAGE_PADDING * 2 - (statusBounds == null ? 0
                : statusBounds.width() + 8) - Math.max(0, rightInset));
        int titleY = bar.y() + (bar.height() - UiText.lineHeight()) / 2 + 1;
        UiDraw.dot(graphics, left + 3F, bar.centerY(), 6F, UiTheme.ACCENT);
        UiText.bold(graphics, UiText.truncateBold(heading, Math.max(1, available - 10)), left + 11, titleY,
                UiTheme.TEXT_STRONG);
        if(status != null && statusBounds != null) {
            renderStatusPill(graphics, statusBounds, status, statusHighlighted);
        }
        graphics.flush();
    }

    /** 页头右侧的状态标签，「当前焦点 / 正在浏览」用它区分。实色底、1px 实色描边，圆角与卡片一致。 */
    protected void renderStatusPill(GuiGraphics graphics, UiLayout.Rect bounds, Component status,
                                    boolean highlighted) {
        if(bounds.isEmpty()) return;
        int text = highlighted ? UiTheme.ACCENT : UiTheme.WARNING;
        int background = highlighted ? UiTheme.ACCENT_FAINT : UiTheme.SURFACE_RAISED;
        UiDraw.roundedRect(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                UiTheme.RADIUS_MD, background);
        UiDraw.roundedBorder(graphics, bounds.x() + 0.5F, bounds.y() + 0.5F, bounds.width() - 1F,
                bounds.height() - 1F, UiTheme.RADIUS_MD, 1F, text);
        UiDraw.dot(graphics, bounds.x() + 7F, bounds.centerY(), 4F, text);
        Component display = UiText.truncate(status, Math.max(1, bounds.width() - 16));
        UiText.plain(graphics, display, bounds.x() + 12,
                bounds.centerY() - UiText.lineHeight() / 2 + 1, text);
        graphics.flush();
    }

    /** 底部操作栏，按钮由子类自己摆，这里只画底板。 */
    protected void renderBottomBar(GuiGraphics graphics) {
        int barHeight = Mth.clamp(BOTTOM_BAR_HEIGHT, 0, Math.max(0, height));
        UiLayout.Rect bar = new UiLayout.Rect(0, height - barHeight, width, barHeight);
        if(bar.isEmpty()) return;
        UiDraw.rect(graphics, bar.x(), bar.y(), bar.width(), bar.height(), UiTheme.CHROME);
        UiDraw.rect(graphics, bar.x(), bar.y(), bar.width(), 1F, UiTheme.BORDER);
        graphics.flush();
    }

    /** 线性卡片：实色面加 1px 线性描边，圆角 6px，没有阴影、内高光与渐变。raised 为 true 时面抬高一档。 */
    protected static void renderCard(GuiGraphics graphics, UiLayout.Rect card, boolean raised) {
        if(card.isEmpty()) return;
        float radius = UiTheme.RADIUS_MD;
        UiDraw.roundedRect(graphics, card.x(), card.y(), card.width(), card.height(), radius,
                raised ? UiTheme.SURFACE_RAISED : UiTheme.SURFACE);
        UiDraw.roundedBorder(graphics, card.x() + 0.5F, card.y() + 0.5F, card.width() - 1F, card.height() - 1F,
                Math.max(1F, radius - 0.5F), 1F, UiTheme.BORDER_STRONG);
        graphics.flush();
    }

    /** 侧栏/分组的小标题：加粗短标签 + 右侧一条实色细分隔线。 */
    protected void renderSectionLabel(GuiGraphics graphics, UiLayout.Rect area, Component label) {
        if(area.isEmpty()) return;
        Component display = UiText.truncateBold(label, Math.max(1, area.width() - 6));
        int textWidth = UiText.boldWidth(display);
        int y = area.y() + (area.height() - UiText.lineHeight()) / 2 + 1;
        UiText.bold(graphics, display, area.x(), y, UiTheme.TEXT_MUTED);
        int lineLeft = area.x() + textWidth + 5;
        if(lineLeft < area.right() - 2) {
            UiDraw.rect(graphics, lineLeft, area.centerY() - 0.5F, area.right() - lineLeft, 1F, UiTheme.BORDER);
        }
        graphics.flush();
    }

    /** 旋转的加载指示器。静态的「正在加载」是纯 MC 味道，转起来才有网页的呼吸感。 */
    protected void renderSpinner(GuiGraphics graphics, float centerX, float centerY) {
        float phase = (Util.getMillis() % SPINNER_PERIOD_MILLIS) / (float) SPINNER_PERIOD_MILLIS;
        UiDraw.spinner(graphics, centerX, centerY, phase, UiTheme.BORDER, UiTheme.ACCENT_BRIGHT);
        graphics.flush();
    }

    /** 加载态：转圈 + 一行说明，整体在区域里居中。 */
    protected void renderLoading(GuiGraphics graphics, UiLayout.Rect area, Component label) {
        if(area.isEmpty()) return;
        int blockHeight = (int) (SPINNER_RADIUS * 2F) + 8 + UiText.lineHeight();
        float top = area.centerY() - blockHeight / 2F;
        renderSpinner(graphics, area.centerX(), top + SPINNER_RADIUS);
        UiText.centered(graphics, UiText.truncate(label, Math.max(1, area.width() - 12)), area.centerX(),
                (int) (top + SPINNER_RADIUS * 2F) + 8, UiTheme.TEXT_SECONDARY);
        graphics.flush();
    }

    /** 空状态：图标 + 主文案 + 可选副文案。比一行灰字更能说清「为什么没有内容」。 */
    protected void renderEmptyState(GuiGraphics graphics, UiLayout.Rect area, UiIcon icon,
                                    Component title, @Nullable Component subtitle) {
        if(area.isEmpty()) return;
        float iconSize = 20F;
        int blockHeight = (int) iconSize + 9 + UiText.lineHeight()
                + (subtitle == null ? 0 : 3 + UiText.lineHeight());
        float top = area.centerY() - blockHeight / 2F;
        icon.draw(graphics, area.centerX(), top + iconSize / 2F, iconSize * 0.6F, 1.4F, UiTheme.TEXT_MUTED);
        int textY = (int) (top + iconSize) + 9;
        UiText.boldCentered(graphics, UiText.truncateBold(title, Math.max(1, area.width() - 16)),
                area.centerX(), textY, UiTheme.TEXT_SECONDARY);
        if(subtitle != null) {
            UiText.centered(graphics, UiText.truncate(subtitle, Math.max(1, area.width() - 16)),
                    area.centerX(), textY + UiText.lineHeight() + 3, UiTheme.TEXT_MUTED);
        }
        graphics.flush();
    }

    /** 模态层：在背景之上再压一层暗色，把视觉焦点收到弹窗上。 */
    protected void renderModalScrim(GuiGraphics graphics) {
        UiDraw.rect(graphics, 0, 0, width, height, 0x99A00000);
        graphics.flush();
    }

    /** 居中弹窗面板，四周留出安全边距，窗口再小也保证至少 1px 可见。 */
    protected static UiLayout.Rect modalPanel(int screenWidth, int screenHeight, int preferredWidth,
                                              int preferredHeight) {
        int panelWidth = Mth.clamp(preferredWidth, 1, Math.max(1, screenWidth - 16));
        int panelHeight = Mth.clamp(preferredHeight, 1, Math.max(1, screenHeight - 16));
        return new UiLayout.Rect((screenWidth - panelWidth) / 2, (screenHeight - panelHeight) / 2,
                panelWidth, panelHeight);
    }

    protected List<FormattedCharSequence> wrapLines(Component text, int maxWidth) {
        return font.split(text, Math.max(1, maxWidth));
    }

    protected Component truncate(Component text, int maxWidth) {
        return UiText.truncate(text, maxWidth);
    }

    protected UiLayout.Rect topBar() {
        int barHeight = Mth.clamp(TOP_BAR_HEIGHT, 0, Math.max(0, height));
        return new UiLayout.Rect(0, 0, width, barHeight);
    }
}
