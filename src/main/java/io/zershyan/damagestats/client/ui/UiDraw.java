package io.zershyan.damagestats.client.ui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * 自绘原语。直接往 {@link RenderType#gui()} 的顶点缓冲里写四边形，而不是调用 {@code GuiGraphics.fill}：
 * 后者每调用一次就 flush 一次批次，画一个圆角卡片要几十次 flush，代价无法接受。
 * <p>
 * 所有形状都做了环绕方向自校正——GUI 渲染类型默认开启背面剔除，扇形三角如果方向反了会被整片剔掉。
 */
public final class UiDraw {
    /** 圆角外侧的羽化宽度（GUI 单位）。缩放 >= 2 时能明显软化弧线锯齿，缩放 1 时只是一圈淡边。 */
    private static final float FEATHER = 0.5F;
    private static final float TAU = (float) (Math.PI * 2D);
    private static final int MIN_ARC_SEGMENTS = 3;
    private static final int MAX_ARC_SEGMENTS = 18;
    /** 阴影层用的圆弧细分。阴影本来就模糊，细分高了纯属浪费顶点。 */
    private static final int SHADOW_ARC_SEGMENTS = 4;

    // ---------------------------------------------------------------- 矩形

    public static void rect(GuiGraphics graphics, float x, float y, float width, float height, int color) {
        rectGradient(graphics, x, y, width, height, color, color);
    }

    public static void rectGradient(GuiGraphics graphics, float x, float y, float width, float height,
                                    int top, int bottom) {
        if(width <= 0F || height <= 0F || isInvisible(top) && isInvisible(bottom)) return;
        VertexConsumer consumer = consumer(graphics);
        Matrix4f pose = graphics.pose().last().pose();
        emitQuad(consumer, pose, x, y, top, x, y + height, bottom, x + width, y + height, bottom, x + width, y, top);
    }

    // ---------------------------------------------------------------- 圆角矩形

    public static void roundedRect(GuiGraphics graphics, float x, float y, float width, float height,
                                   float radius, int color) {
        roundedRect(graphics, x, y, width, height, radius, color, color);
    }

    /** 圆角矩形，半径超过一半短边时自动收敛成胶囊；上下边可用不同颜色做渐变。 */
    public static void roundedRect(GuiGraphics graphics, float x, float y, float width, float height,
                                   float radius, int top, int bottom) {
        roundedRect(graphics, x, y, width, height, radius, top, bottom, 0);
    }

    /**
     * @param segments 圆弧细分数，传 0 表示按半径自动取值；阴影这类本来就模糊的图层可以传很小的值省顶点
     */
    private static void roundedRect(GuiGraphics graphics, float x, float y, float width, float height,
                                    float radius, int top, int bottom, int segments) {
        if(width <= 0F || height <= 0F || isInvisible(top) && isInvisible(bottom)) return;
        float r = Mth.clamp(radius, 0F, Math.min(width, height) / 2F);
        if(r < 0.5F) {
            rectGradient(graphics, x, y, width, height, top, bottom);
            return;
        }
        VertexConsumer consumer = consumer(graphics);
        Matrix4f pose = graphics.pose().last().pose();
        // 主体拆成「中间竖条 + 左右两个去掉圆角的竖条」，圆弧只出现在四角，避免重叠叠加出深色块
        emitQuad(consumer, pose,
                x + r, y, top,
                x + r, y + height, bottom,
                x + width - r, y + height, bottom,
                x + width - r, y, top);
        emitQuad(consumer, pose,
                x, y + r, colorAt(top, bottom, r, height),
                x, y + height - r, colorAt(top, bottom, height - r, height),
                x + r, y + height - r, colorAt(top, bottom, height - r, height),
                x + r, y + r, colorAt(top, bottom, r, height));
        emitQuad(consumer, pose,
                x + width - r, y + r, colorAt(top, bottom, r, height),
                x + width - r, y + height - r, colorAt(top, bottom, height - r, height),
                x + width, y + height - r, colorAt(top, bottom, height - r, height),
                x + width, y + r, colorAt(top, bottom, r, height));
        // 四角：实心扇形 + 外侧一圈 alpha 从满到零的羽化环
        int arc = segments > 0 ? segments : segmentsFor(r);
        corner(consumer, pose, x + r, y + r, r, (float) Math.PI, top, bottom, y, height, arc);
        corner(consumer, pose, x + width - r, y + r, r, (float) (Math.PI * 1.5D), top, bottom, y, height, arc);
        corner(consumer, pose, x + width - r, y + height - r, r, 0F, top, bottom, y, height, arc);
        corner(consumer, pose, x + r, y + height - r, r, (float) (Math.PI * 0.5D), top, bottom, y, height, arc);
    }

    /** 圆角描边：画一圈宽度为 thickness 的圆角环。 */
    public static void roundedBorder(GuiGraphics graphics, float x, float y, float width, float height,
                                     float radius, float thickness, int color) {
        if(width <= 0F || height <= 0F || thickness <= 0F || isInvisible(color)) return;
        float r = Mth.clamp(radius, 0F, Math.min(width, height) / 2F);
        float t = Math.min(thickness, Math.min(width, height) / 2F);
        VertexConsumer consumer = consumer(graphics);
        Matrix4f pose = graphics.pose().last().pose();
        if(r < 0.5F) {
            emitQuad(consumer, pose, x, y, color, x, y + t, color, x + width, y + t, color, x + width, y, color);
            emitQuad(consumer, pose, x, y + height - t, color, x, y + height, color,
                    x + width, y + height, color, x + width, y + height - t, color);
            emitQuad(consumer, pose, x, y + t, color, x, y + height - t, color,
                    x + t, y + height - t, color, x + t, y + t, color);
            emitQuad(consumer, pose, x + width - t, y + t, color, x + width - t, y + height - t, color,
                    x + width, y + height - t, color, x + width, y + t, color);
            return;
        }
        band(consumer, pose, x + r, y, width - r * 2F, t, color);
        band(consumer, pose, x + r, y + height - t, width - r * 2F, t, color);
        band(consumer, pose, x, y + r, t, height - r * 2F, color);
        band(consumer, pose, x + width - t, y + r, t, height - r * 2F, color);
        ring(consumer, pose, x + r, y + r, r, t, (float) Math.PI, color);
        ring(consumer, pose, x + width - r, y + r, r, t, (float) (Math.PI * 1.5D), color);
        ring(consumer, pose, x + width - r, y + height - r, r, t, 0F, color);
        ring(consumer, pose, x + r, y + height - r, r, t, (float) (Math.PI * 0.5D), color);
    }

    /**
     * 柔和外阴影。分层堆叠：每层向外扩张一点、alpha 不同，叠加后得到连续的衰减曲线。
     * 阴影层用低细分圆角（外圈半径大、本来就看不清），8 层也只花一个普通圆角矩形的顶点量。
     */
    public static void shadow(GuiGraphics graphics, float x, float y, float width, float height,
                              float radius, float spread, float offsetY, int color) {
        if(width <= 0F || height <= 0F || spread <= 0F || isInvisible(color)) return;
        int steps = Math.max(3, Math.round(spread));
        float baseAlpha = ((color >>> 24) & 0xFF) / 255F;
        for(int layer = steps; layer >= 1; layer--) {
            float t = layer / (float) steps;
            float expand = spread * t;
            // (1-t)² 让 alpha 从外圈往内圈快速抬升，叠出来才是一条平滑的衰减曲线而不是一圈描边
            float strength = (1F - t) * (1F - t);
            float alpha = baseAlpha * (0.30F + strength * 1.4F) / steps;
            int argb = withAlpha(color, alpha);
            roundedRect(graphics, x - expand, y - expand + offsetY, width + expand * 2F,
                    height + expand * 2F, radius + expand, argb, argb, SHADOW_ARC_SEGMENTS);
        }
    }

    /** 玻璃高光：整块面从顶部的一点点白渐变到全透明，做出「被上方光源照亮」的观感。 */
    public static void sheen(GuiGraphics graphics, float x, float y, float width, float height,
                             float radius, float strength) {
        if(width <= 0F || height <= 0F || strength <= 0F) return;
        roundedRect(graphics, x, y, width, height, radius,
                UiTheme.alpha(0xFFFFFFFF, strength), UiTheme.alpha(0xFFFFFFFF, 0F));
    }


    // ---------------------------------------------------------------- 其它形状

    public static void circle(GuiGraphics graphics, float centerX, float centerY, float radius, int color) {
        disc(graphics, centerX, centerY, radius, color, true);
    }

    /** 圆环，用于环形进度与图标。 */
    public static void ringShape(GuiGraphics graphics, float centerX, float centerY, float radius,
                                 float thickness, int color) {
        VertexConsumer consumer = consumer(graphics);
        Matrix4f pose = graphics.pose().last().pose();
        ring(consumer, pose, centerX, centerY, radius, thickness, 0F, color);
        ring(consumer, pose, centerX, centerY, radius, thickness, (float) (Math.PI * 0.5D), color);
        ring(consumer, pose, centerX, centerY, radius, thickness, (float) Math.PI, color);
        ring(consumer, pose, centerX, centerY, radius, thickness, (float) (Math.PI * 1.5D), color);
    }

    /** 逆时针（屏幕坐标）扇形，用于 chevron、三角箭头一类图标；外弧带羽化。 */
    public static void sector(GuiGraphics graphics, float centerX, float centerY, float radius,
                              float startAngle, float sweep, int color) {
        if(radius <= 0F || isInvisible(color)) return;
        VertexConsumer consumer = consumer(graphics);
        Matrix4f pose = graphics.pose().last().pose();
        int segments = segmentsFor(radius * Math.abs(sweep) / (float) Math.PI);
        float step = sweep / segments;
        for(int index = 0; index < segments; index++) {
            float a0 = startAngle + step * index;
            float a1 = a0 + step;
            float cos0 = (float) Math.cos(a0);
            float sin0 = (float) Math.sin(a0);
            float cos1 = (float) Math.cos(a1);
            float sin1 = (float) Math.sin(a1);
            emitTriangle(consumer, pose,
                    centerX, centerY, color,
                    centerX + radius * cos0, centerY + radius * sin0, color,
                    centerX + radius * cos1, centerY + radius * sin1, color);
            float outer = radius + FEATHER;
            emitQuad(consumer, pose,
                    centerX + radius * cos0, centerY + radius * sin0, color,
                    centerX + radius * cos1, centerY + radius * sin1, color,
                    centerX + outer * cos1, centerY + outer * sin1, withAlpha(color, 0F),
                    centerX + outer * cos0, centerY + outer * sin0, withAlpha(color, 0F));
        }
    }

    /**
     * 环形圆弧，两端带圆头。用于加载指示器这类需要「一段可旋转的弧」的地方——
     * 角度是逐帧算出来的，所以不能像图标那样预先画死。
     */
    public static void arc(GuiGraphics graphics, float centerX, float centerY, float radius,
                           float thickness, float startAngle, float sweep, int color) {
        if(radius <= 0F || thickness <= 0F || Math.abs(sweep) < 0.0001F || isInvisible(color)) return;
        VertexConsumer consumer = consumer(graphics);
        Matrix4f pose = graphics.pose().last().pose();
        float inner = Math.max(0F, radius - thickness);
        int segments = Math.max(MIN_ARC_SEGMENTS,
                segmentsFor(radius * Math.abs(sweep) / (float) Math.PI));
        float step = sweep / segments;
        for(int index = 0; index < segments; index++) {
            float a0 = startAngle + step * index;
            float a1 = a0 + step;
            float cos0 = (float) Math.cos(a0);
            float sin0 = (float) Math.sin(a0);
            float cos1 = (float) Math.cos(a1);
            float sin1 = (float) Math.sin(a1);
            emitQuad(consumer, pose,
                    centerX + inner * cos0, centerY + inner * sin0, color,
                    centerX + inner * cos1, centerY + inner * sin1, color,
                    centerX + radius * cos1, centerY + radius * sin1, color,
                    centerX + radius * cos0, centerY + radius * sin0, color);
            float outer = radius + FEATHER;
            emitQuad(consumer, pose,
                    centerX + radius * cos0, centerY + radius * sin0, color,
                    centerX + radius * cos1, centerY + radius * sin1, color,
                    centerX + outer * cos1, centerY + outer * sin1, withAlpha(color, 0F),
                    centerX + outer * cos0, centerY + outer * sin0, withAlpha(color, 0F));
        }
        // 两端补圆头，短弧段看起来才不是被切断的
        float capRadius = thickness / 2F;
        disc(graphics, centerX + (inner + capRadius + (radius - inner) / 2F) * (float) Math.cos(startAngle),
                centerY + (inner + capRadius + (radius - inner) / 2F) * (float) Math.sin(startAngle),
                capRadius, color, true);
        float endAngle = startAngle + sweep;
        disc(graphics, centerX + (inner + capRadius + (radius - inner) / 2F) * (float) Math.cos(endAngle),
                centerY + (inner + capRadius + (radius - inner) / 2F) * (float) Math.sin(endAngle),
                capRadius, color, true);
    }

    /** 任意三角形，顶点按屏幕坐标给出；三条边都带羽化。 */
    public static void triangle(GuiGraphics graphics, float ax, float ay, float bx, float by,
                                float cx, float cy, int color) {
        if(isInvisible(color)) return;
        VertexConsumer consumer = consumer(graphics);
        Matrix4f pose = graphics.pose().last().pose();
        emitTriangle(consumer, pose, ax, ay, color, bx, by, color, cx, cy, color);
        edgeFeather(consumer, pose, ax, ay, bx, by, cx, cy, color);
        edgeFeather(consumer, pose, bx, by, cx, cy, ax, ay, color);
        edgeFeather(consumer, pose, cx, cy, ax, ay, bx, by, color);
    }

    /** 沿边 a→b 朝背离顶点 o 的一侧铺羽化带。 */
    private static void edgeFeather(VertexConsumer consumer, Matrix4f pose,
                                    float ax, float ay, float bx, float by,
                                    float ox, float oy, int color) {
        float dx = bx - ax;
        float dy = by - ay;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if(length < 0.0001F) return;
        float nx = -dy / length;
        float ny = dx / length;
        float midX = (ax + bx) / 2F;
        float midY = (ay + by) / 2F;
        // 法线取指向外侧的那一边，否则羽化带会盖到三角形内部
        if((ox - midX) * nx + (oy - midY) * ny > 0F) {
            nx = -nx;
            ny = -ny;
        }
        emitQuad(consumer, pose,
                ax, ay, color,
                bx, by, color,
                bx + nx * FEATHER, by + ny * FEATHER, withAlpha(color, 0F),
                ax + nx * FEATHER, ay + ny * FEATHER, withAlpha(color, 0F));
    }

    /** 任意四边形，顶点按屏幕坐标给出。 */
    public static void quad(GuiGraphics graphics, float ax, float ay, float bx, float by,
                            float cx, float cy, float dx, float dy, int color) {
        if(isInvisible(color)) return;
        emitQuad(consumer(graphics), graphics.pose().last().pose(),
                ax, ay, color, bx, by, color, cx, cy, color, dx, dy, color);
    }

    /**
     * 有宽度的线段。除主干外还沿法线两侧各铺一条 0.5 宽的羽化带——
     * 没有这一步，所有斜向图标（叉号、时钟指针、折线）在 MC 里都是硬台阶。
     */
    public static void line(GuiGraphics graphics, float x0, float y0, float x1, float y1,
                            float thickness, int color) {
        if(isInvisible(color) || thickness <= 0F) return;
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if(length < 0.0001F) return;
        float nx = -dy / length;
        float ny = dx / length;
        float half = thickness / 2F;
        quad(graphics, x0 + nx * half, y0 + ny * half, x1 + nx * half, y1 + ny * half,
                x1 - nx * half, y1 - ny * half, x0 - nx * half, y0 - ny * half, color);
        featherQuad(graphics, x0 + nx * half, y0 + ny * half, x1 + nx * half, y1 + ny * half,
                nx, ny, FEATHER, color);
        featherQuad(graphics, x0 - nx * half, y0 - ny * half, x1 - nx * half, y1 - ny * half,
                -nx, -ny, FEATHER, color);
    }

    /** 从一条边沿 (nx,ny) 向外铺一层 alpha 由满到零的带，抗锯齿就靠它。 */
    private static void featherQuad(GuiGraphics graphics, float ax, float ay, float bx, float by,
                                    float nx, float ny, float width, int color) {
        VertexConsumer consumer = consumer(graphics);
        Matrix4f pose = graphics.pose().last().pose();
        emitQuad(consumer, pose,
                ax, ay, color,
                bx, by, color,
                bx + nx * width, by + ny * width, withAlpha(color, 0F),
                ax + nx * width, ay + ny * width, withAlpha(color, 0F));
    }

    // ---------------------------------------------------------------- 图标

    /** 左右向的折角箭头，direction 为 -1 指左、1 指右。 */
    public static void chevron(GuiGraphics graphics, float centerX, float centerY, float size,
                               float thickness, int direction, int color) {
        float half = size / 2F;
        if(direction < 0) {
            line(graphics, centerX + half * 0.5F, centerY - half, centerX - half * 0.5F, centerY, thickness, color);
            line(graphics, centerX - half * 0.5F, centerY, centerX + half * 0.5F, centerY + half, thickness, color);
        } else {
            line(graphics, centerX - half * 0.5F, centerY - half, centerX + half * 0.5F, centerY, thickness, color);
            line(graphics, centerX + half * 0.5F, centerY, centerX - half * 0.5F, centerY + half, thickness, color);
        }
    }

    /** 上下向的折角箭头，direction 为 -1 指上、1 指下。 */
    public static void chevronVertical(GuiGraphics graphics, float centerX, float centerY, float size,
                                       float thickness, int direction, int color) {
        float half = size / 2F;
        if(direction < 0) {
            line(graphics, centerX - half, centerY + half * 0.5F, centerX, centerY - half * 0.5F, thickness, color);
            line(graphics, centerX, centerY - half * 0.5F, centerX + half, centerY + half * 0.5F, thickness, color);
        } else {
            line(graphics, centerX - half, centerY - half * 0.5F, centerX, centerY + half * 0.5F, thickness, color);
            line(graphics, centerX, centerY + half * 0.5F, centerX + half, centerY - half * 0.5F, thickness, color);
        }
    }

    /** 实心三角，用于展开/收起的指示器。direction 为 -1 指上、1 指下、-2 指左、2 指右。 */
    public static void caret(GuiGraphics graphics, float centerX, float centerY, float size, int direction,
                             int color) {
        float half = size / 2F;
        switch(direction) {
            case -1 -> triangle(graphics, centerX, centerY - half, centerX + half, centerY + half,
                    centerX - half, centerY + half, color);
            case 1 -> triangle(graphics, centerX, centerY + half, centerX - half, centerY - half,
                    centerX + half, centerY - half, color);
            case -2 -> triangle(graphics, centerX - half, centerY, centerX + half, centerY - half,
                    centerX + half, centerY + half, color);
            default -> triangle(graphics, centerX + half, centerY, centerX - half, centerY + half,
                    centerX - half, centerY - half, color);
        }
    }

    /** 叉号，用于清除类操作。 */
    public static void cross(GuiGraphics graphics, float centerX, float centerY, float size,
                             float thickness, int color) {
        float half = size / 2F;
        line(graphics, centerX - half, centerY - half, centerX + half, centerY + half, thickness, color);
        line(graphics, centerX + half, centerY - half, centerX - half, centerY + half, thickness, color);
    }

    /** 三个点的「更多」图标。 */
    public static void ellipsis(GuiGraphics graphics, float centerX, float centerY, float size,
                                float thickness, int color) {
        float dotRadius = Math.max(1F, size * 0.12F);
        float spacing = size * 0.36F;
        circle(graphics, centerX - spacing, centerY, dotRadius, color);
        circle(graphics, centerX, centerY, dotRadius, color);
        circle(graphics, centerX + spacing, centerY, dotRadius, color);
    }

    /** 环形箭头，用于刷新。缺口留在右上，缺口里补一个箭头尖。size 是外接方形边长。 */
    public static void refresh(GuiGraphics graphics, float centerX, float centerY, float size,
                               float thickness, int color) {
        float radius = Math.max(1F, size / 2F);
        ringShape(graphics, centerX, centerY, radius, thickness, color);
        // 用背景色感的缺口不好做，改成在右上角叠一个箭头三角，视觉上等效于刷新图标
        float angle = (float) (-Math.PI * 0.25D);
        float tipX = centerX + radius * (float) Math.cos(angle);
        float tipY = centerY + radius * (float) Math.sin(angle);
        caret(graphics, tipX, tipY - thickness, thickness * 2.6F, -1, color);
    }

    /** 向下的箭头落入托盘的导出图标。 */
    public static void download(GuiGraphics graphics, float centerX, float centerY, float size,
                                float thickness, int color) {
        float half = size / 2F;
        line(graphics, centerX, centerY - half, centerX, centerY + half * 0.15F, thickness, color);
        line(graphics, centerX - half * 0.55F, centerY - half * 0.3F, centerX, centerY + half * 0.25F,
                thickness, color);
        line(graphics, centerX + half * 0.55F, centerY - half * 0.3F, centerX, centerY + half * 0.25F,
                thickness, color);
        line(graphics, centerX - half, centerY + half * 0.72F, centerX + half, centerY + half * 0.72F,
                thickness, color);
    }

    /** 漏斗，用于筛选。 */
    public static void funnel(GuiGraphics graphics, float centerX, float centerY, float size,
                              float thickness, int color) {
        float half = size / 2F;
        line(graphics, centerX - half, centerY - half * 0.7F, centerX + half, centerY - half * 0.7F,
                thickness, color);
        line(graphics, centerX - half, centerY - half * 0.7F, centerX - half * 0.12F, centerY + half * 0.1F,
                thickness, color);
        line(graphics, centerX + half, centerY - half * 0.7F, centerX + half * 0.12F, centerY + half * 0.1F,
                thickness, color);
        line(graphics, centerX - half * 0.12F, centerY + half * 0.1F, centerX - half * 0.12F,
                centerY + half * 0.8F, thickness, color);
        line(graphics, centerX + half * 0.12F, centerY + half * 0.1F, centerX + half * 0.12F,
                centerY + half * 0.8F, thickness, color);
    }

    /** 三点连线上升的折线图，用于详细指标。 */
    public static void chartLine(GuiGraphics graphics, float centerX, float centerY, float size,
                                 float thickness, int color) {
        float half = size / 2F;
        line(graphics, centerX - half, centerY + half * 0.7F, centerX - half * 0.25F, centerY - half * 0.1F,
                thickness, color);
        line(graphics, centerX - half * 0.25F, centerY - half * 0.1F, centerX + half * 0.2F, centerY + half * 0.25F,
                thickness, color);
        line(graphics, centerX + half * 0.2F, centerY + half * 0.25F, centerX + half, centerY - half * 0.7F,
                thickness, color);
    }

    /** 时钟，用于历史战斗。size 是外接方形边长。 */
    public static void clock(GuiGraphics graphics, float centerX, float centerY, float size,
                             float thickness, int color) {
        float radius = Math.max(1F, size / 2F);
        ringShape(graphics, centerX, centerY, radius, thickness, color);
        line(graphics, centerX, centerY, centerX, centerY - radius * 0.55F, thickness, color);
        line(graphics, centerX, centerY, centerX + radius * 0.45F, centerY, thickness, color);
    }

    /** 目标靶心，用于焦点。size 是外接方形边长，和 {@link UiIcon} 的约定一致。 */
    public static void target(GuiGraphics graphics, float centerX, float centerY, float size,
                              float thickness, int color) {
        float radius = Math.max(1F, size / 2F);
        ringShape(graphics, centerX, centerY, radius, thickness, color);
        circle(graphics, centerX, centerY, Math.max(1F, radius * 0.28F), color);
    }

    /** 拖拽手柄，用于浮层位置。 */
    public static void move(GuiGraphics graphics, float centerX, float centerY, float size,
                            float thickness, int color) {
        float half = size / 2F;
        line(graphics, centerX - half, centerY, centerX + half, centerY, thickness, color);
        line(graphics, centerX, centerY - half, centerX, centerY + half, thickness, color);
        // 尖角整体内收，别顶出 size 给的外接方框
        caret(graphics, centerX - half * 0.82F, centerY, half * 0.5F, -2, color);
        caret(graphics, centerX + half * 0.82F, centerY, half * 0.5F, 2, color);
        caret(graphics, centerX, centerY - half * 0.82F, half * 0.5F, -1, color);
        caret(graphics, centerX, centerY + half * 0.82F, half * 0.5F, 1, color);
    }

    /** 垃圾桶，用于清空类操作。 */
    public static void trash(GuiGraphics graphics, float centerX, float centerY, float size,
                             float thickness, int color) {
        float half = size / 2F;
        line(graphics, centerX - half, centerY - half * 0.55F, centerX + half, centerY - half * 0.55F,
                thickness, color);
        line(graphics, centerX - half * 0.35F, centerY - half * 0.55F, centerX - half * 0.35F,
                centerY - half * 0.85F, thickness, color);
        line(graphics, centerX + half * 0.35F, centerY - half * 0.55F, centerX + half * 0.35F,
                centerY - half * 0.85F, thickness, color);
        line(graphics, centerX - half * 0.35F, centerY - half * 0.85F, centerX + half * 0.35F,
                centerY - half * 0.85F, thickness, color);
        line(graphics, centerX - half * 0.7F, centerY - half * 0.55F, centerX - half * 0.45F, centerY + half * 0.85F,
                thickness, color);
        line(graphics, centerX + half * 0.7F, centerY - half * 0.55F, centerX + half * 0.45F, centerY + half * 0.85F,
                thickness, color);
        line(graphics, centerX - half * 0.45F, centerY + half * 0.85F, centerX + half * 0.45F,
                centerY + half * 0.85F, thickness, color);
    }

    /** 数据库/存储图标。 */
    public static void database(GuiGraphics graphics, float centerX, float centerY, float size,
                                float thickness, int color) {
        float half = size / 2F;
        float ellipse = half * 0.42F;
        ringShape(graphics, centerX, centerY - half * 0.62F, half * 0.62F, thickness, color);
        line(graphics, centerX - half * 0.62F, centerY - half * 0.62F, centerX - half * 0.62F,
                centerY + half * 0.62F, thickness, color);
        line(graphics, centerX + half * 0.62F, centerY - half * 0.62F, centerX + half * 0.62F,
                centerY + half * 0.62F, thickness, color);
        sector(graphics, centerX, centerY + half * 0.62F, half * 0.62F, 0F, (float) Math.PI, color);
        line(graphics, centerX - half * 0.62F, centerY + ellipse * 0.1F, centerX + half * 0.62F,
                centerY + ellipse * 0.1F, thickness, color);
    }

    // ---------------------------------------------------------------- 内部实现

    private static void corner(VertexConsumer consumer, Matrix4f pose, float centerX, float centerY,
                               float radius, float startAngle, int top, int bottom,
                               float shapeTop, float shapeHeight, int segments) {
        float step = (float) (Math.PI * 0.5D) / segments;
        int centerColor = colorAt(top, bottom, centerY - shapeTop, shapeHeight);
        for(int index = 0; index < segments; index++) {
            float a0 = startAngle + step * index;
            float a1 = a0 + step;
            float cos0 = (float) Math.cos(a0);
            float sin0 = (float) Math.sin(a0);
            float cos1 = (float) Math.cos(a1);
            float sin1 = (float) Math.sin(a1);
            int color0 = colorAt(top, bottom, centerY + radius * sin0 - shapeTop, shapeHeight);
            int color1 = colorAt(top, bottom, centerY + radius * sin1 - shapeTop, shapeHeight);
            // 实心扇形。内缘与羽化环相接但面积不重叠，所以半透明卡片不会在接缝处叠出深色边
            emitTriangle(consumer, pose,
                    centerX, centerY, centerColor,
                    centerX + radius * cos0, centerY + radius * sin0, color0,
                    centerX + radius * cos1, centerY + radius * sin1, color1);
            // 羽化环：内缘保持满 alpha，外缘归零，得到一圈一到两个屏幕像素的抗锯齿过渡
            float outer = radius + FEATHER;
            emitQuad(consumer, pose,
                    centerX + radius * cos0, centerY + radius * sin0, color0,
                    centerX + radius * cos1, centerY + radius * sin1, color1,
                    centerX + outer * cos1, centerY + outer * sin1, withAlpha(color1, 0F),
                    centerX + outer * cos0, centerY + outer * sin0, withAlpha(color0, 0F));
        }
    }

    private static void band(VertexConsumer consumer, Matrix4f pose, float x, float y,
                             float width, float height, int color) {
        emitQuad(consumer, pose, x, y, color, x, y + height, color,
                x + width, y + height, color, x + width, y, color);
    }

    private static void ring(VertexConsumer consumer, Matrix4f pose, float centerX, float centerY,
                             float radius, float thickness, float startAngle, int color) {
        float inner = Math.max(0F, radius - thickness);
        int segments = segmentsFor(radius);
        float step = (float) (Math.PI * 0.5D) / segments;
        for(int index = 0; index < segments; index++) {
            float a0 = startAngle + step * index;
            float a1 = a0 + step;
            float cos0 = (float) Math.cos(a0);
            float sin0 = (float) Math.sin(a0);
            float cos1 = (float) Math.cos(a1);
            float sin1 = (float) Math.sin(a1);
            emitQuad(consumer, pose,
                    centerX + inner * cos0, centerY + inner * sin0, color,
                    centerX + inner * cos1, centerY + inner * sin1, color,
                    centerX + radius * cos1, centerY + radius * sin1, color,
                    centerX + radius * cos0, centerY + radius * sin0, color);
            float outer = radius + FEATHER;
            emitQuad(consumer, pose,
                    centerX + radius * cos0, centerY + radius * sin0, color,
                    centerX + radius * cos1, centerY + radius * sin1, color,
                    centerX + outer * cos1, centerY + outer * sin1, withAlpha(color, 0F),
                    centerX + outer * cos0, centerY + outer * sin0, withAlpha(color, 0F));
        }
    }

    private static void disc(GuiGraphics graphics, float centerX, float centerY, float radius, int color,
                             boolean feather) {
        if(radius <= 0F || isInvisible(color)) return;
        VertexConsumer consumer = consumer(graphics);
        Matrix4f pose = graphics.pose().last().pose();
        int segments = Math.max(MIN_ARC_SEGMENTS, segmentsFor(radius) * 4);
        float step = TAU / segments;
        for(int index = 0; index < segments; index++) {
            float a0 = step * index;
            float a1 = a0 + step;
            emitTriangle(consumer, pose,
                    centerX, centerY, color,
                    centerX + radius * (float) Math.cos(a0), centerY + radius * (float) Math.sin(a0), color,
                    centerX + radius * (float) Math.cos(a1), centerY + radius * (float) Math.sin(a1), color);
            if(!feather) continue;
            float outer = radius + FEATHER;
            emitQuad(consumer, pose,
                    centerX + radius * (float) Math.cos(a0), centerY + radius * (float) Math.sin(a0), color,
                    centerX + radius * (float) Math.cos(a1), centerY + radius * (float) Math.sin(a1), color,
                    centerX + outer * (float) Math.cos(a1), centerY + outer * (float) Math.sin(a1),
                    withAlpha(color, 0F),
                    centerX + outer * (float) Math.cos(a0), centerY + outer * (float) Math.sin(a0),
                    withAlpha(color, 0F));
        }
    }

    private static VertexConsumer consumer(GuiGraphics graphics) {
        return graphics.bufferSource().getBuffer(RenderType.gui());
    }

    /**
     * 屏幕坐标下叉积为正意味着在 GL 里是背面，会被剔除；这里自动翻转顶点顺序，
     * 调用方就不必关心每个形状的环绕方向。
     */
    private static void emitTriangle(VertexConsumer consumer, Matrix4f pose,
                                     float ax, float ay, int colorA,
                                     float bx, float by, int colorB,
                                     float cx, float cy, int colorC) {
        float cross = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
        if(cross > 0F) {
            consumer.vertex(pose, ax, ay, 0F).color(colorA).endVertex();
            consumer.vertex(pose, cx, cy, 0F).color(colorC).endVertex();
            consumer.vertex(pose, bx, by, 0F).color(colorB).endVertex();
        } else {
            consumer.vertex(pose, ax, ay, 0F).color(colorA).endVertex();
            consumer.vertex(pose, bx, by, 0F).color(colorB).endVertex();
            consumer.vertex(pose, cx, cy, 0F).color(colorC).endVertex();
        }
    }

    private static void emitQuad(VertexConsumer consumer, Matrix4f pose,
                                 float ax, float ay, int colorA,
                                 float bx, float by, int colorB,
                                 float cx, float cy, int colorC,
                                 float dx, float dy, int colorD) {
        float cross = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
        if(cross > 0F) {
            consumer.vertex(pose, ax, ay, 0F).color(colorA).endVertex();
            consumer.vertex(pose, dx, dy, 0F).color(colorD).endVertex();
            consumer.vertex(pose, cx, cy, 0F).color(colorC).endVertex();
            consumer.vertex(pose, bx, by, 0F).color(colorB).endVertex();
        } else {
            consumer.vertex(pose, ax, ay, 0F).color(colorA).endVertex();
            consumer.vertex(pose, bx, by, 0F).color(colorB).endVertex();
            consumer.vertex(pose, cx, cy, 0F).color(colorC).endVertex();
            consumer.vertex(pose, dx, dy, 0F).color(colorD).endVertex();
        }
    }

    private static int segmentsFor(float radius) {
        return Mth.clamp((int) Math.ceil(radius * 1.6F), MIN_ARC_SEGMENTS, MAX_ARC_SEGMENTS);
    }

    private static int colorAt(int top, int bottom, float offset, float height) {
        if(height <= 0F || top == bottom) return top;
        return UiTheme.mix(top, bottom, offset / height);
    }

    private static boolean isInvisible(int color) {
        return (color >>> 24) == 0;
    }

    private static int withAlpha(int color, float alpha) {
        return ((color >>> 24) & 0xFF) == 0 ? color
                : ((Math.round(Mth.clamp(alpha, 0F, 1F) * 255F) << 24) | (color & 0x00FFFFFF));
    }
}
