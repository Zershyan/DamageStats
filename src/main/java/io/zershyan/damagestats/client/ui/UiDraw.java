package io.zershyan.damagestats.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import io.zershyan.damagestats.DamageStats;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * 界面绘制原语。图形都是图集族中的区域：图标按区域 blit，圆角框按九宫格拉伸，
 * 纯色与线条是纯白块的拉伸，颜色由顶点色乘上。界面运行时不再生成几何。
 */
public final class UiDraw {
    /** 每张族图一个批次：纹理状态是 RenderType 的一部分，所以按族缓存。 */
    private static final Map<UiAtlas.Sheet, RenderType> BATCHES = new EnumMap<>(UiAtlas.Sheet.class);

    static {
        for(UiAtlas.Sheet sheet : UiAtlas.Sheet.values()) {
            BATCHES.put(sheet, RenderType.create("damagestats_" + sheet.name().toLowerCase(Locale.ROOT),
                    DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS, 256, false, false,
                    RenderType.CompositeState.builder()
                            .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorTexShader))
                            .setTextureState(new RenderStateShard.TextureStateShard(
                                    new ResourceLocation(DamageStats.MODID, sheet.texture()), false, false))
                            .setTransparencyState(new RenderStateShard.TransparencyStateShard("damagestats_blend",
                                    () -> { RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); },
                                    RenderSystem::disableBlend))
                            .setDepthTestState(new RenderStateShard.DepthTestStateShard("lequal", GL11.GL_LEQUAL))
                            .setCullState(new RenderStateShard.CullStateShard(false))
                            .setLightmapState(new RenderStateShard.LightmapStateShard(false))
                            .setWriteMaskState(new RenderStateShard.WriteMaskStateShard(true, true))
                            .createCompositeState(false)));
        }
    }

    // ---------------------------------------------------------------- 纯色与线

    public static void rect(GuiGraphics graphics, float x, float y, float width, float height, int color) {
        rectGradient(graphics, x, y, width, height, color, color);
    }

    /** 纯白块拉伸到矩形，上下两色由顶点色插值成渐变。 */
    public static void rectGradient(GuiGraphics graphics, float x, float y, float width, float height,
                                    int top, int bottom) {
        if(width <= 0F || height <= 0F || isInvisible(top) && isInvisible(bottom)) return;
        stretch(graphics, UiAtlas.region(UiAtlas.SOLID), x, y, width, height, top, bottom);
    }

    // ---------------------------------------------------------------- 圆角框

    public static void roundedRect(GuiGraphics graphics, float x, float y, float width, float height,
                                   float radius, int color) {
        roundedRect(graphics, x, y, width, height, radius, color, color);
    }

    /** 圆角面，上下两色渐变。半径取不超过 limit 的最大烘焙档，没有则退为直角。 */
    public static void roundedRect(GuiGraphics graphics, float x, float y, float width, float height,
                                   float radius, int top, int bottom) {
        if(width <= 0F || height <= 0F || isInvisible(top) && isInvisible(bottom)) return;
        int baked = UiAtlas.bakedRadius(Math.min(radius, Math.min(width, height) / 2F));
        if(baked == 0) {
            rectGradient(graphics, x, y, width, height, top, bottom);
            return;
        }
        nineSlice(graphics, UiAtlas.region("face_" + baked), baked, x, y, width, height, top, bottom);
    }

    /** 圆角描边，只支持 1px：调用点线宽全部为 1。 */
    public static void roundedBorder(GuiGraphics graphics, float x, float y, float width, float height,
                                     float radius, float thickness, int color) {
        if(width <= 0F || height <= 0F || thickness <= 0F || isInvisible(color)) return;
        int baked = UiAtlas.bakedRadius(Math.min(radius, Math.min(width, height) / 2F));
        if(baked == 0) {
            rectGradient(graphics, x, y, width, 1F, color, color);
            rectGradient(graphics, x, y + height - 1F, width, 1F, color, color);
            rectGradient(graphics, x, y + 1F, 1F, height - 2F, color, color);
            rectGradient(graphics, x + width - 1F, y + 1F, 1F, height - 2F, color, color);
            return;
        }
        nineSlice(graphics, UiAtlas.region("ring_" + baked), baked, x, y, width, height, color, color);
    }

    /**
     * 九宫格：四角按原尺寸，四边与中心拉伸。区域是边长 2r+1 的正方形，中心一行一列是纯色，拉伸不改变圆角。
     * 上下两色按屏幕 y 插值，纹理白色乘顶点色得到颜色。
     */
    private static void nineSlice(GuiGraphics graphics, UiAtlas.Region region, int radius,
                                  float x, float y, float width, float height, int top, int bottom) {
        int size = region.width();
        float right = x + width;
        float bottomEdge = y + height;
        float[] xs = {x, x + radius, right - radius, right};
        float[] ys = {y, y + radius, bottomEdge - radius, bottomEdge};
        int[] us = {region.x(), region.x() + radius, region.x() + radius + 1, region.x() + size};
        int[] vs = {region.y(), region.y() + radius, region.y() + radius + 1, region.y() + size};
        VertexConsumer consumer = batch(graphics, region.sheet());
        Matrix4f pose = graphics.pose().last().pose();
        for(int row = 0; row < 3; row++) {
            for(int col = 0; col < 3; col++) {
                if(xs[col + 1] <= xs[col] || ys[row + 1] <= ys[row]) continue;
                quad(consumer, pose, region.sheet(), xs[col], ys[row], xs[col + 1], ys[row + 1],
                        us[col], vs[row], us[col + 1], vs[row + 1], top, bottom, y, height);
            }
        }
    }

    /** 把整块区域拉伸到矩形，上下两色按 y 插值。 */
    private static void stretch(GuiGraphics graphics, UiAtlas.Region region, float x, float y, float width,
                                float height, int top, int bottom) {
        quad(batch(graphics, region.sheet()), graphics.pose().last().pose(), region.sheet(),
                x, y, x + width, y + height,
                region.x(), region.y(), region.x() + region.width(), region.y() + region.height(),
                top, bottom, y, height);
    }

    /** 一个图集四边形：把区域内的像素矩形映射到屏幕矩形，顶点色按 y 插值；UV 按所在族的尺寸归一化。 */
    private static void quad(VertexConsumer consumer, Matrix4f pose, UiAtlas.Sheet sheet,
                             float x0, float y0, float x1, float y1, int u0, int v0, int u1, int v1,
                             int top, int bottom, float shapeTop, float shapeHeight) {
        int c0 = colorAt(top, bottom, y0 - shapeTop, shapeHeight);
        int c1 = colorAt(top, bottom, y1 - shapeTop, shapeHeight);
        vertex(consumer, pose, sheet, x0, y0, u0, v0, c0);
        vertex(consumer, pose, sheet, x0, y1, u0, v1, c1);
        vertex(consumer, pose, sheet, x1, y1, u1, v1, c1);
        vertex(consumer, pose, sheet, x1, y0, u1, v0, c0);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, UiAtlas.Sheet sheet, float x, float y,
                               int u, int v, int color) {
        consumer.vertex(pose, x, y, 0F).color(color)
                .uv(u / (float) UiAtlas.width(sheet), v / (float) UiAtlas.height(sheet)).endVertex();
    }

    private static VertexConsumer batch(GuiGraphics graphics, UiAtlas.Sheet sheet) {
        return graphics.bufferSource().getBuffer(BATCHES.get(sheet));
    }

    // ---------------------------------------------------------------- 图标

    /** 按区域名 blit 图标，颜色通过 shader 乘上。 */
    private static void drawIcon(GuiGraphics graphics, String name, float centerX, float centerY, float size,
                                 int color) {
        UiAtlas.Region region = UiAtlas.region(name);
        int px = Math.round(size);
        int x = Math.round(centerX - px / 2F);
        int y = Math.round(centerY - px / 2F);
        begin(graphics, color);
        blit(graphics, region, x, y, px);
        end(graphics);
    }

    /** 绕中心旋转 rotation 弧度的图标；旋转由浮点矩阵完成，转动连续。 */
    private static void drawRotated(GuiGraphics graphics, String name, float centerX, float centerY, float size,
                                    float rotation, int color) {
        UiAtlas.Region region = UiAtlas.region(name);
        begin(graphics, color);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 0F);
        pose.mulPose(Axis.ZP.rotation(rotation));
        blit(graphics, region, Math.round(-size / 2F), Math.round(-size / 2F), Math.round(size));
        pose.popPose();
        end(graphics);
    }

    /** 把区域按像素 1:1 的 px 边长 blit 到 (x, y)，纹理取该区域所在的族图。 */
    private static void blit(GuiGraphics graphics, UiAtlas.Region region, int x, int y, int px) {
        graphics.blit(new ResourceLocation(DamageStats.MODID, region.sheet().texture()), x, y, px, px,
                region.x(), region.y(), region.width(), region.height(),
                UiAtlas.width(region.sheet()), UiAtlas.height(region.sheet()));
    }

    /** blit 即时绘制而面是缓冲的：先提交已缓冲的面，否则面会画在图标之上把它盖住。 */
    private static void begin(GuiGraphics graphics, int color) {
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(((color >>> 16) & 0xFF) / 255F, ((color >>> 8) & 0xFF) / 255F,
                (color & 0xFF) / 255F, ((color >>> 24) & 0xFF) / 255F);
    }

    private static void end(GuiGraphics graphics) {
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        graphics.flush();
    }

    /** 实心圆点，diameter 为直径。 */
    public static void dot(GuiGraphics graphics, float centerX, float centerY, float diameter, int color) {
        drawIcon(graphics, "dot", centerX, centerY, diameter, color);
    }

    /** 左右向的折角箭头，direction 为 -1 指左、1 指右。 */
    public static void chevron(GuiGraphics graphics, float centerX, float centerY, float size,
                               float thickness, int direction, int color) {
        drawIcon(graphics, direction < 0 ? "chevron_left" : "chevron_right", centerX, centerY, size, color);
    }

    /** 上下向的折角箭头，direction 为 -1 指上、1 指下。 */
    public static void chevronVertical(GuiGraphics graphics, float centerX, float centerY, float size,
                                       float thickness, int direction, int color) {
        drawIcon(graphics, direction < 0 ? "chevron_up" : "chevron_down", centerX, centerY, size, color);
    }

    /** 实心圆角三角，direction 为 -1 上、1 下、-2 左、其它为右。 */
    public static void caret(GuiGraphics graphics, float centerX, float centerY, float size, int direction,
                             int color) {
        String name = switch(direction) {
            case -1 -> "caret_up";
            case 1 -> "caret_down";
            case -2 -> "caret_left";
            default -> "caret_right";
        };
        drawIcon(graphics, name, centerX, centerY, size, color);
    }

    /** 叉号。 */
    public static void cross(GuiGraphics graphics, float centerX, float centerY, float size,
                             float thickness, int color) {
        drawIcon(graphics, "cross", centerX, centerY, size, color);
    }

    /** 省略号。 */
    public static void ellipsis(GuiGraphics graphics, float centerX, float centerY, float size,
                                float thickness, int color) {
        drawIcon(graphics, "ellipsis", centerX, centerY, size, color);
    }

    /** 刷新。 */
    public static void refresh(GuiGraphics graphics, float centerX, float centerY, float size,
                               float thickness, int color) {
        drawIcon(graphics, "refresh", centerX, centerY, size, color);
    }

    /** 下载。 */
    public static void download(GuiGraphics graphics, float centerX, float centerY, float size,
                                float thickness, int color) {
        drawIcon(graphics, "download", centerX, centerY, size, color);
    }

    /** 漏斗，用于筛选。 */
    public static void funnel(GuiGraphics graphics, float centerX, float centerY, float size,
                              float thickness, int color) {
        drawIcon(graphics, "funnel", centerX, centerY, size, color);
    }

    /** 折线图。 */
    public static void chartLine(GuiGraphics graphics, float centerX, float centerY, float size,
                                 float thickness, int color) {
        drawIcon(graphics, "chart_line", centerX, centerY, size, color);
    }

    /** 时钟。 */
    public static void clock(GuiGraphics graphics, float centerX, float centerY, float size,
                             float thickness, int color) {
        drawIcon(graphics, "clock", centerX, centerY, size, color);
    }

    /** 目标。 */
    public static void target(GuiGraphics graphics, float centerX, float centerY, float size,
                              float thickness, int color) {
        drawIcon(graphics, "target", centerX, centerY, size, color);
    }

    /** 移动。 */
    public static void move(GuiGraphics graphics, float centerX, float centerY, float size,
                            float thickness, int color) {
        drawIcon(graphics, "move", centerX, centerY, size, color);
    }

    /** 垃圾桶。 */
    public static void trash(GuiGraphics graphics, float centerX, float centerY, float size,
                             float thickness, int color) {
        drawIcon(graphics, "trash", centerX, centerY, size, color);
    }

    /** 数据库。 */
    public static void database(GuiGraphics graphics, float centerX, float centerY, float size,
                                float thickness, int color) {
        drawIcon(graphics, "database", centerX, centerY, size, color);
    }

    /** 对勾，progress 为 0–1，按进度取帧：从空到完整的 ✓。 */
    public static void check(GuiGraphics graphics, float centerX, float centerY, float size, float progress,
                             int color) {
        int frame = Math.round(Mth.clamp(progress, 0F, 1F) * (UiGlyph.CHECK_FRAMES - 1));
        if(frame > 0) drawIcon(graphics, "check_" + frame, centerX, centerY, size, color);
    }

    /** 旋转圈：整圈轨道加上绕中心旋转 phase（0–1，一圈为 1）的弧段，两层分色。 */
    public static void spinner(GuiGraphics graphics, float centerX, float centerY, float phase, int track, int arc) {
        float px = UiGlyph.SPINNER_PX;
        float pivotX = snapCenter(centerX, px);
        float pivotY = snapCenter(centerY, px);
        drawIcon(graphics, "spinner_track", pivotX, pivotY, px, track);
        drawRotated(graphics, "spinner_arc", pivotX, pivotY, px, phase * Mth.TWO_PI, arc);
    }

    /** 把中心吸附到与 drawIcon 相同的像素网格，使轨道与旋转弧段同心。 */
    private static float snapCenter(float center, float size) {
        return Math.round(center - size / 2F) + size / 2F;
    }

    /** 确认框图标：圆盘、圆环、感叹号三层，各层颜色独立。 */
    public static void alert(GuiGraphics graphics, float centerX, float centerY, int disc, int ring, int mark) {
        float px = UiGlyph.ALERT_PX;
        drawIcon(graphics, "alert_disc", centerX, centerY, px, disc);
        drawIcon(graphics, "alert_ring", centerX, centerY, px, ring);
        drawIcon(graphics, "alert_mark", centerX, centerY, px, mark);
    }

    private static int colorAt(int top, int bottom, float offset, float height) {
        if(height <= 0F || top == bottom) return top;
        return UiTheme.mix(top, bottom, offset / height);
    }

    private static boolean isInvisible(int color) {
        return (color >>> 24) == 0;
    }
}
