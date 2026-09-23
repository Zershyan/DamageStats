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
    private static final float SPINNER_THICKNESS = 2F;
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
        UiDraw.rectGradient(graphics, 0, 0, width, height, UiTheme.BACKDROP_TOP, UiTheme.BACKDROP);
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
        UiDraw.rectGradient(graphics, bar.x(), bar.y(), bar.width(), bar.height(),
                UiTheme.alpha(UiTheme.CHROME, 0.96F), UiTheme.alpha(UiTheme.CHROME, 0.72F));
        // 一条 1px 的强调色渐变细线，是整屏唯一的「品牌色」露出
        UiDraw.rectGradient(graphics, bar.x(), bar.bottom() - 1, bar.width(), 1F,
                UiTheme.alpha(UiTheme.ACCENT, 0.05F), UiTheme.alpha(UiTheme.ACCENT, 0.45F));
        int left = PAGE_PADDING;
        int available = Math.max(1, bar.width() - PAGE_PADDING * 2 - (statusBounds == null ? 0
                : statusBounds.width() + 8) - Math.max(0, rightInset));
        int titleY = bar.y() + (bar.height() - UiText.lineHeight()) / 2 + 1;
        UiDraw.circle(graphics, left + 3F, bar.centerY(), 3.5F, UiTheme.ACCENT);
        UiDraw.circle(graphics, left + 3.5F, bar.centerY(), 1.6F, UiTheme.alpha(UiTheme.ON_ACCENT, 0.9F));
        UiText.bold(graphics, UiText.truncateBold(heading, Math.max(1, available - 10)), left + 11, titleY,
                UiTheme.TEXT_STRONG);
        if(status != null && statusBounds != null) {
            renderStatusPill(graphics, statusBounds, status, statusHighlighted);
        }
        graphics.flush();
    }

    /** 页头右侧的状态胶囊，「当前焦点 / 正在浏览」用它区分。 */
    protected void renderStatusPill(GuiGraphics graphics, UiLayout.Rect bounds, Component status,
                                    boolean highlighted) {
        if(bounds.isEmpty()) return;
        int text = highlighted ? UiTheme.ACCENT_BRIGHT : UiTheme.mix(UiTheme.TEXT, UiTheme.WARNING, 0.7F);
        int background = highlighted ? UiTheme.ACCENT_FAINT : UiTheme.alpha(UiTheme.WARNING, 0.13F);
        UiDraw.roundedRect(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                bounds.height() / 2F, background);
        UiDraw.roundedBorder(graphics, bounds.x() + 0.5F, bounds.y() + 0.5F, bounds.width() - 1F,
                bounds.height() - 1F, bounds.height() / 2F, 1F, UiTheme.alpha(text, 0.4F));
        UiDraw.circle(graphics, bounds.x() + 7F, bounds.centerY(), 2.5F, text);
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
        UiDraw.rectGradient(graphics, bar.x(), bar.y(), bar.width(), bar.height(),
                UiTheme.alpha(UiTheme.CHROME, 0.55F), UiTheme.alpha(UiTheme.CHROME, 0.98F));
        UiDraw.rectGradient(graphics, bar.x(), bar.y(), bar.width(), 1F,
                UiTheme.alpha(UiTheme.BORDER, 0.9F), UiTheme.alpha(UiTheme.BORDER, 0.1F));
        graphics.flush();
    }

    /** 一张玻璃卡片：柔和投影 + 半透明面 + 内高光 + 顶部光照渐变。 */
    protected static void renderCard(GuiGraphics graphics, UiLayout.Rect card, boolean raised) {
        if(card.isEmpty()) return;
        float radius = UiTheme.RADIUS_LG;
        UiDraw.shadow(graphics, card.x(), card.y(), card.width(), card.height(), radius, 8F, 3F,
                UiTheme.SHADOW);
        int top = raised ? UiTheme.SURFACE_RAISED : UiTheme.SURFACE;
        int bottom = UiTheme.mix(top, UiTheme.BACKDROP, 0.35F);
        UiDraw.roundedRect(graphics, card.x(), card.y(), card.width(), card.height(), radius, top, bottom);
        UiDraw.sheen(graphics, card.x(), card.y(), card.width(), card.height(), radius, 0.045F);
        UiDraw.roundedBorder(graphics, card.x() + 0.5F, card.y() + 0.5F, card.width() - 1F, card.height() - 1F,
                Math.max(1F, radius - 0.5F), 1F, UiTheme.alpha(UiTheme.BORDER, 0.95F));
        // 顶部 1px 内高光，模拟玻璃边缘被光打亮
        UiDraw.roundedRect(graphics, card.x() + 1F, card.y() + 1F, card.width() - 2F, 1F,
                Math.max(1F, radius - 1F), UiTheme.HIGHLIGHT);
        graphics.flush();
    }

    /** 侧栏/分组的小标题：加粗短标签 + 右侧一条渐隐的细分隔线。 */
    protected void renderSectionLabel(GuiGraphics graphics, UiLayout.Rect area, Component label) {
        if(area.isEmpty()) return;
        Component display = UiText.truncateBold(label, Math.max(1, area.width() - 6));
        int textWidth = UiText.boldWidth(display);
        int y = area.y() + (area.height() - UiText.lineHeight()) / 2 + 1;
        UiText.bold(graphics, display, area.x(), y, UiTheme.TEXT_MUTED);
        int lineLeft = area.x() + textWidth + 5;
        if(lineLeft < area.right() - 2) {
            UiDraw.rectGradient(graphics, lineLeft, area.centerY() - 0.5F,
                    area.right() - lineLeft, 1F,
                    UiTheme.alpha(UiTheme.BORDER, 0.8F), UiTheme.alpha(UiTheme.BORDER, 0F));
        }
        graphics.flush();
    }

    /** 旋转的加载指示器。静态的「正在加载」是纯 MC 味道，转起来才有网页的呼吸感。 */
    protected void renderSpinner(GuiGraphics graphics, float centerX, float centerY) {
        float phase = (Util.getMillis() % SPINNER_PERIOD_MILLIS) / (float) SPINNER_PERIOD_MILLIS;
        float angle = phase * (float) (Math.PI * 2D);
        UiDraw.ringShape(graphics, centerX, centerY, SPINNER_RADIUS, SPINNER_THICKNESS,
                UiTheme.alpha(UiTheme.ACCENT, 0.16F));
        UiDraw.arc(graphics, centerX, centerY, SPINNER_RADIUS, SPINNER_THICKNESS, angle,
                (float) (Math.PI * 0.55D), UiTheme.ACCENT_BRIGHT);
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
        UiDraw.circle(graphics, area.centerX(), top + iconSize / 2F, iconSize * 0.72F,
                UiTheme.alpha(UiTheme.TEXT_MUTED, 0.10F));
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
