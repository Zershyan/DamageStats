package io.zershyan.damagestats.client.overlay;

import io.zershyan.damagestats.client.ClientStats;
import io.zershyan.damagestats.client.screen.OverlayPositionScreen;
import io.zershyan.damagestats.client.screen.StatsScreen;
import io.zershyan.damagestats.config.DSClientConfig;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.stats.view.OverlaySummary;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import org.jetbrains.annotations.NotNull;

/**
 * 屏幕常显浮层。默认关闭，纯展示不吃任何输入。
 * 位置从客户端配置里的屏幕比例算出来，所以换分辨率不会跑偏。
 */
public final class StatsOverlay {
    public static final int WIDTH = 112;
    private static final int LINE_HEIGHT = 10;
    private static final int PADDING = 4;
    private static final int TITLE_COLOR = 0xFFFFD700;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int INACTIVE_COLOR = 0xFF9E9E9E;

    public static int width() {
        return scaled(WIDTH);
    }

    public static int height() {
        return scaled(baseHeight());
    }

    public static void render(@NotNull GuiGraphics graphics, @NotNull DeltaTracker deltaTracker) {
        if(!DSClientConfig.OverlayVisible.get()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if(minecraft.options.hideGui) return;
        // 我们自己这两个界面里已经画了统计，Overlay 再画一遍就重叠了
        if(minecraft.screen instanceof OverlayPositionScreen || minecraft.screen instanceof StatsScreen) return;
        OverlaySummary summary = ClientStats.summary();
        if(summary.hitCount() == 0 || lineCount() == 0) return;
        renderBox(graphics, minecraft.font, summary,
                originX(graphics.guiWidth()), originY(graphics.guiHeight()));
    }

    public static int originX(int screenWidth) {
        return clampOrigin((int) (screenWidth * DSClientConfig.OverlayX.get()), width(), screenWidth);
    }

    public static int originY(int screenHeight) {
        return clampOrigin((int) (screenHeight * DSClientConfig.OverlayY.get()), height(), screenHeight);
    }

    private static int clampOrigin(int origin, int elementSize, int screenSize) {
        return screenSize <= elementSize ? 0 : Mth.clamp(origin, 0, screenSize - elementSize);
    }

    /** 位置编辑界面复用这个方法画预览，保证所见即所得 */
    public static void renderBox(GuiGraphics graphics, Font font, OverlaySummary summary, int x, int y) {
        if(lineCount() == 0) return;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        float scale = scale();
        graphics.pose().scale(scale, scale, 1);
        graphics.fill(0, 0, WIDTH, baseHeight(), background());
        int textX = PADDING;
        int textY = PADDING;
        int valueColor = summary.active() ? TEXT_COLOR : INACTIVE_COLOR;
        if(DSClientConfig.OverlayShowTarget.get()) {
            graphics.drawString(font, summary.targetName(), textX, textY, TITLE_COLOR);
            textY += LINE_HEIGHT;
        }
        if(DSClientConfig.OverlayShowDamage.get()) {
            graphics.drawString(font, DSKeyLang.OverlayDamage.getNumber1f(summary.totalDamage()), textX, textY, valueColor);
            textY += LINE_HEIGHT;
        }
        if(DSClientConfig.OverlayShowDps.get()) {
            graphics.drawString(font, DSKeyLang.OverlayDps.getNumber1f(summary.averageDps(), summary.realtimeDps()),
                    textX, textY, valueColor);
            textY += LINE_HEIGHT;
        }
        if(DSClientConfig.OverlayShowHits.get()) {
            graphics.drawString(font, DSKeyLang.OverlayHits.get(summary.hitCount()), textX, textY, valueColor);
        }
        graphics.pose().popPose();
    }

    /** 还没打过任何东西时给位置编辑界面一份样板数据，否则玩家看不出浮层占多大 */
    public static OverlaySummary previewSummary() {
        OverlaySummary current = ClientStats.summary();
        if(current.hitCount() > 0) return current;
        Component sampleTarget = EntityType.ZOMBIE.getDescription();
        return new OverlaySummary(sampleTarget, 1234.5f, 85.6f, 92.1f, 42, true);
    }

    private static int lineCount() {
        int lines = 0;
        if(DSClientConfig.OverlayShowTarget.get()) lines++;
        if(DSClientConfig.OverlayShowDamage.get()) lines++;
        if(DSClientConfig.OverlayShowDps.get()) lines++;
        if(DSClientConfig.OverlayShowHits.get()) lines++;
        return lines;
    }

    private static int baseHeight() {
        return PADDING * 2 + lineCount() * LINE_HEIGHT;
    }

    private static int scaled(int size) {
        return Math.max(1, (int) Math.ceil(size * scale()));
    }

    private static float scale() {
        return DSClientConfig.OverlayScale.get().floatValue();
    }

    private static int background() {
        return (int) Math.round(DSClientConfig.OverlayBackgroundOpacity.get() * 255) << 24;
    }
}
