package io.zershyan.damagestats.client.overlay;

import io.zershyan.damagestats.client.ClientStats;
import io.zershyan.damagestats.client.screen.OverlayPositionScreen;
import io.zershyan.damagestats.config.DSClientConfig;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.stats.view.OverlaySummary;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.NotNull;

/**
 * 屏幕常显浮层。默认关闭，纯展示不吃任何输入。
 * 位置从客户端配置里的屏幕比例算出来，所以换分辨率不会跑偏。
 */
public final class StatsOverlay implements LayeredDraw.Layer {
    public static final StatsOverlay INSTANCE = new StatsOverlay();

    public static final int WIDTH = 112;
    private static final int LINE_COUNT = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int PADDING = 4;
    private static final int BACKGROUND = 0x80000000;
    private static final int TITLE_COLOR = 0xFFFFD700;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int INACTIVE_COLOR = 0xFF9E9E9E;

    public static int height() {
        return PADDING * 2 + LINE_COUNT * LINE_HEIGHT;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, @NotNull DeltaTracker deltaTracker) {
        if(!DSClientConfig.OverlayVisible.get()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if(minecraft.options.hideGui) return;
        // 编辑位置时由那个界面自己画预览，否则会重叠成两层
        if(minecraft.screen instanceof OverlayPositionScreen) return;
        OverlaySummary summary = ClientStats.summary();
        if(summary.hitCount() == 0) return;
        renderBox(graphics, minecraft.font, summary,
                originX(graphics.guiWidth()), originY(graphics.guiHeight()));
    }

    public static int originX(int screenWidth) {
        return (int) (screenWidth * DSClientConfig.OverlayX.get());
    }

    public static int originY(int screenHeight) {
        return (int) (screenHeight * DSClientConfig.OverlayY.get());
    }

    /** 位置编辑界面复用这个方法画预览，保证所见即所得 */
    public static void renderBox(GuiGraphics graphics, Font font, OverlaySummary summary, int x, int y) {
        graphics.fill(x, y, x + WIDTH, y + height(), BACKGROUND);
        int textX = x + PADDING;
        int textY = y + PADDING;
        int valueColor = summary.active() ? TEXT_COLOR : INACTIVE_COLOR;
        graphics.drawString(font, summary.targetName(), textX, textY, TITLE_COLOR);
        graphics.drawString(font, DSKeyLang.OverlayDamage.getNumber1f(summary.totalDamage()),
                textX, textY + LINE_HEIGHT, valueColor);
        graphics.drawString(font, DSKeyLang.OverlayDps.getNumber1f(summary.averageDps(), summary.realtimeDps()),
                textX, textY + LINE_HEIGHT * 2, valueColor);
        graphics.drawString(font, DSKeyLang.OverlayHits.get(summary.hitCount()),
                textX, textY + LINE_HEIGHT * 3, valueColor);
    }

    /** 还没打过任何东西时给位置编辑界面一份样板数据，否则玩家看不出浮层占多大 */
    public static OverlaySummary previewSummary() {
        OverlaySummary current = ClientStats.summary();
        if(current.hitCount() > 0) return current;
        Component sampleTarget = EntityType.ZOMBIE.getDescription();
        return new OverlaySummary(sampleTarget, 1234.5f, 85.6f, 92.1f, 42, true);
    }
}
