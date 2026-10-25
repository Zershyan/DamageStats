package io.zershyan.damagestats.client.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * 图标的纯几何定义：每个图标是一组笔画，笔画有四种——线段、圆弧、圆点、椭圆弧，线头与转角都是圆的。
 * 坐标以 0–1 的单位方框表示，原点在左上角，{@link UiDraw} 按 size 缩放到实际尺寸。
 * 不依赖 Minecraft 类，游戏渲染与离线预览图读取的是同一份定义。
 */
public final class UiGlyph {
    private UiGlyph() {}

    /** 笔画的几何种类：线段、圆弧、圆点、椭圆弧与实心多边形。线头与转角都是圆的。 */
    public sealed interface Stroke permits Line, Arc, Dot, Ellipse, Fill {}

    /** 一条线段，两端各带一个半径为线宽一半的圆头。 */
    public record Line(double x0, double y0, double x1, double y1, double width) implements Stroke {
        public Line(double x0, double y0, double x1, double y1) {
            this(x0, y0, x1, y1, STROKE);
        }
    }

    /** 一段圆弧，两端带圆头。start、end 为弧度，屏幕坐标下顺时针为正。 */
    public record Arc(double cx, double cy, double radius, double start, double end, double width) implements Stroke {
        public Arc(double cx, double cy, double radius, double start, double end) {
            this(cx, cy, radius, start, end, STROKE);
        }
    }

    /** 一个实心圆点，用于省略号等小构件。 */
    public record Dot(double cx, double cy, double radius) implements Stroke {}

    /** 一段椭圆弧，横向半径 rx、纵向半径 ry，从 start 扫到 end。用于数据库顶面这类扁圆。 */
    public record Ellipse(double cx, double cy, double rx, double ry, double start, double end, double width)
            implements Stroke {
        public Ellipse(double cx, double cy, double rx, double ry, double start, double end) {
            this(cx, cy, rx, ry, start, end, STROKE);
        }
    }

    /** 一个实心区域：按顺序连接的顶点构成闭合多边形，用于圆角实心三角这类需要填充的图形。 */
    public record Fill(double[][] vertices) implements Stroke {}

    /** 线宽占单位方框的比例。现代线性图标的常用粗细，缩到 9px 也不会糊。 */
    public static final double STROKE = 0.09;
    private static final double TAU = Math.PI * 2;

    /** 纹理相对设计像素的烘焙倍数：纹理按此倍数烘焙，运行时缩放到设计尺寸显示，由 mip 链抗锯齿。 */
    public static final int TEXTURE_SCALE = 32;
    /** 图标统一的烘焙边长（设计像素），取最大显示尺寸 26px，所有图标共用一个尺寸。 */
    public static final int ICON_PX = 26;
    public static final int TILE = ICON_PX * TEXTURE_SCALE;

    /** 烘焙的像素尺寸与帧数，运行时取帧按同样的数字。 */
    public static final int SPINNER_PX = 14;
    public static final int CHECK_FRAMES = 9;
    public static final int ALERT_PX = 26;

    /** 旋转圈：环宽 2px，弧端圆头圆心落在环带中线上。 */
    private static final double SPINNER_WIDTH = 2.0 / SPINNER_PX;
    private static final double SPINNER_RADIUS = 0.5 - SPINNER_WIDTH / 2;
    private static final double SPINNER_SWEEP = Math.PI * 0.55;

    /** 对勾：左端、底部拐点、右上端，线宽按 11px 复选框换算。 */
    private static final double[] CHECK_LEFT = {0.26, 0.50};
    private static final double[] CHECK_BOTTOM = {0.44, 0.68};
    private static final double[] CHECK_RIGHT = {0.76, 0.34};
    private static final double CHECK_WIDTH = 1.6 / 11;

    /** 确认框图标的圆环：1px，位于 26px 方框的最外圈。 */
    private static final double ALERT_RING = 1.0 / ALERT_PX;
    /** 圆角面与圆角环的烘焙半径，派生半径向下取最近一档。 */
    public static final int[] CORNER_RADII = {1, 2, 3, 4, 6};

    /** 旋转圈轨道：整圈。 */
    public static List<Stroke> spinnerTrack() {
        return List.of(new Arc(0.5, 0.5, SPINNER_RADIUS, 0, TAU, SPINNER_WIDTH));
    }

    /** 旋转圈弧段：从 0 弧度起扫过 SPINNER_SWEEP，运行时用矩阵旋转到相位。 */
    public static List<Stroke> spinnerArc() {
        return List.of(new Arc(0.5, 0.5, SPINNER_RADIUS, 0, SPINNER_SWEEP, SPINNER_WIDTH));
    }

    /** 对勾，progress 为 0–1：左臂先画到底部拐点，右臂随后画到右上端。 */
    public static List<Stroke> check(double progress) {
        double leftT = Math.min(1, progress * 2);
        double rightT = Math.max(0, progress * 2 - 1);
        List<Stroke> s = new ArrayList<>(2);
        if(leftT > 0) {
            s.add(new Line(CHECK_LEFT[0], CHECK_LEFT[1],
                    lerp(CHECK_LEFT[0], CHECK_BOTTOM[0], leftT), lerp(CHECK_LEFT[1], CHECK_BOTTOM[1], leftT),
                    CHECK_WIDTH));
        }
        if(rightT > 0) {
            s.add(new Line(CHECK_BOTTOM[0], CHECK_BOTTOM[1],
                    lerp(CHECK_BOTTOM[0], CHECK_RIGHT[0], rightT), lerp(CHECK_BOTTOM[1], CHECK_RIGHT[1], rightT),
                    CHECK_WIDTH));
        }
        return s;
    }

    /** 确认框图标的三层：圆盘、圆环、感叹号，颜色由调用方分别给出。 */
    public static List<Stroke> alertDisc() {
        return List.of(new Dot(0.5, 0.5, 0.5));
    }

    /** 圆环用整圆的 Ellipse 画，首尾不带圆头，避免在 0° 处鼓出一个凸点。 */
    public static List<Stroke> alertRing() {
        return List.of(new Ellipse(0.5, 0.5, 0.5 - ALERT_RING / 2, 0.5 - ALERT_RING / 2, 0, TAU, ALERT_RING));
    }

    /** 感叹号：竖线端点内缩半个线宽，圆头外扩后仍覆盖原先的 6–14px。 */
    public static List<Stroke> alertMark() {
        return List.of(new Line(0.5, 7.0 / ALERT_PX, 0.5, 13.0 / ALERT_PX, 2.0 / ALERT_PX),
                new Dot(0.5, 18.0 / ALERT_PX, 1.2 / ALERT_PX));
    }

    private static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }

    /** 圆角面：边长 2r+1 的正方形，四角为半径 r 的圆角，中心一行一列可拉伸。 */
    public static List<Stroke> cornerFace(int radius) {
        double size = 2 * radius + 1;
        double[][] square = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        return List.of(new Fill(sampleRoundedPolygon(square, radius / size, 8)));
    }

    /** 圆角环：与圆角面同尺寸，1px 外框画在像素中线上，圆角半径相应内缩 0.5px。 */
    public static List<Stroke> cornerRing(int radius) {
        double size = 2 * radius + 1;
        double inset = 0.5 / size;
        double far = 1 - inset;
        double arc = (radius - 0.5) / size;
        double width = 1 / size;
        return List.of(
                new Line(inset + arc, inset, far - arc, inset, width),
                new Line(far, inset + arc, far, far - arc, width),
                new Line(far - arc, far, inset + arc, far, width),
                new Line(inset, far - arc, inset, inset + arc, width),
                new Arc(inset + arc, inset + arc, arc, Math.PI, Math.PI * 1.5, width),
                new Arc(far - arc, inset + arc, arc, Math.PI * 1.5, TAU, width),
                new Arc(far - arc, far - arc, arc, 0, Math.PI * 0.5, width),
                new Arc(inset + arc, far - arc, arc, Math.PI * 0.5, Math.PI, width));
    }

    /** 实心圆角三角，顶点朝 direction 方向（-1 上、1 下、-2 左、其它为右）：沿倒角弧采样成闭合多边形后填充。 */
    public static List<Stroke> caret(int direction) {
        double[][] tri = switch(direction) {
            case -1 -> new double[][] {{0.50, 0.28}, {0.20, 0.68}, {0.80, 0.68}};
            case 1 -> new double[][] {{0.50, 0.72}, {0.20, 0.32}, {0.80, 0.32}};
            case -2 -> new double[][] {{0.28, 0.50}, {0.68, 0.20}, {0.68, 0.80}};
            default -> new double[][] {{0.72, 0.50}, {0.32, 0.20}, {0.32, 0.80}};
        };
        return List.of(new Fill(sampleRoundedPolygon(tri, 0.08, 8)));
    }

    /**
     * 把圆角多边形采样成闭合折线顶点：每个顶点的倒角弧分成 segments 段，直边连接相邻弧的端点。
     * 倒角几何：顶点 V，两邻点方向单位向量 u、v，半角 h = θ/2，切点距顶点 t = r / tan(h)，
     * 圆心距顶点 d = r / sin(h)，圆心位于角平分线上。这里只把弧换成若干采样点。
     */
    private static double[][] sampleRoundedPolygon(double[][] vertices, double radius, int segments) {
        int n = vertices.length;
        List<double[]> points = new ArrayList<>();
        for(int i = 0; i < n; i++) {
            double[] prev = vertices[(i - 1 + n) % n];
            double[] cur = vertices[i];
            double[] next = vertices[(i + 1) % n];
            double ux = prev[0] - cur[0], uy = prev[1] - cur[1];
            double vx = next[0] - cur[0], vy = next[1] - cur[1];
            double ul = Math.hypot(ux, uy), vl = Math.hypot(vx, vy);
            ux /= ul; uy /= ul; vx /= vl; vy /= vl;
            double theta = Math.acos(Math.max(-1, Math.min(1, ux * vx + uy * vy)));
            double h = theta / 2;
            double t = radius / Math.tan(h);
            double d = radius / Math.sin(h);
            double p1x = cur[0] + ux * t, p1y = cur[1] + uy * t;
            double p2x = cur[0] + vx * t, p2y = cur[1] + vy * t;
            double bx = ux + vx, by = uy + vy;
            double bl = Math.hypot(bx, by);
            double ccx = cur[0] + bx / bl * d, ccy = cur[1] + by / bl * d;
            double a1 = Math.atan2(p1y - ccy, p1x - ccx);
            double a2 = Math.atan2(p2y - ccy, p2x - ccx);
            double sweep = a2 - a1;
            while(sweep > Math.PI) sweep -= 2 * Math.PI;
            while(sweep < -Math.PI) sweep += 2 * Math.PI;
            for(int k = 0; k <= segments; k++) {
                double a = a1 + sweep * k / segments;
                points.add(new double[] {ccx + radius * Math.cos(a), ccy + radius * Math.sin(a)});
            }
        }
        return points.toArray(new double[0][]);
    }

    /** 折角箭头：两臂从顶点向外展开，左指为 -1，右指为 1。 */
    public static List<Stroke> chevron(int direction) {
        double tipX = direction < 0 ? 0.40 : 0.60;
        double armX = direction < 0 ? 0.60 : 0.40;
        List<Stroke> s = new ArrayList<>();
        s.add(new Line(armX, 0.30, tipX, 0.50));
        s.add(new Line(tipX, 0.50, armX, 0.70));
        return s;
    }

    /** 上指或下指折角箭头，上指为 -1，下指为 1。 */
    public static List<Stroke> chevronVertical(int direction) {
        double tipY = direction < 0 ? 0.40 : 0.60;
        double armY = direction < 0 ? 0.60 : 0.40;
        List<Stroke> s = new ArrayList<>();
        s.add(new Line(0.30, armY, 0.50, tipY));
        s.add(new Line(0.50, tipY, 0.70, armY));
        return s;
    }

    /** 叉号：两条圆头线段交于中心。 */
    public static List<Stroke> cross() {
        List<Stroke> s = new ArrayList<>();
        s.add(new Line(0.30, 0.30, 0.70, 0.70));
        s.add(new Line(0.70, 0.30, 0.30, 0.70));
        return s;
    }

    /**
     * 省略号：三个等距的实心圆点。圆点加大、间距拉开，使 10 像素下三点各自清楚、中间一点不被吞掉。
     */
    public static List<Stroke> ellipsis() {
        List<Stroke> s = new ArrayList<>();
        s.add(new Dot(0.20, 0.50, 0.10));
        s.add(new Dot(0.50, 0.50, 0.10));
        s.add(new Dot(0.80, 0.50, 0.10));
        return s;
    }

    /**
     * 刷新：约 270° 的圆弧，终点端是一个实心三角箭头，尖端沿圆的前进切线方向。
     * 箭头根部沿法线内收、压在弧上，消除小尺寸下的断口；三角加大，使 10 像素下方向仍可辨认。
     * 切线 t = (-sinθ, cosθ) 指向角度增大的方向（屏幕上为顺时针），朝向与绕行方向一致。
     */
    public static List<Stroke> refresh() {
        double cx = 0.50, cy = 0.50, r = 0.30;
        double start = Math.toRadians(0);
        double end = Math.toRadians(270);
        // 弧的终点在圆上
        double ex = cx + r * Math.cos(end);
        double ey = cy + r * Math.sin(end);
        // 前进方向的单位切线与法线（指向圆外）
        double tx = -Math.sin(end), ty = Math.cos(end);
        double nx = Math.cos(end), ny = Math.sin(end);
        // 箭头根部沿法线内收 0.05，压在弧上；尖端前伸 0.20，半宽 0.14，比原先大，小尺寸下才有面积
        double baseX = ex - 0.05 * nx, baseY = ey - 0.05 * ny;
        double tipLen = 0.20, halfWidth = 0.14;
        double[][] head = {
                {baseX + tipLen * tx, baseY + tipLen * ty},
                {baseX + halfWidth * nx, baseY + halfWidth * ny},
                {baseX - halfWidth * nx, baseY - halfWidth * ny}
        };
        List<Stroke> s = new ArrayList<>();
        s.add(new Arc(cx, cy, r, start, end));
        s.add(new Fill(head));
        return s;
    }

    /**
     * 下载：竖杆连到箭头顶点，箭头两臂向下收拢，托底的碗形圆弧（圆心抬高、半径收小）托住箭头，
     * 不再像笑脸的嘴。
     */
    public static List<Stroke> download() {
        List<Stroke> s = new ArrayList<>();
        s.add(new Line(0.50, 0.20, 0.50, 0.64));
        s.add(new Line(0.34, 0.48, 0.50, 0.64));
        s.add(new Line(0.66, 0.48, 0.50, 0.64));
        s.add(new Arc(0.50, 0.60, 0.26, Math.toRadians(20), Math.toRadians(160)));
        return s;
    }

    /**
     * 漏斗：上沿横线，两侧斜线汇聚到颈点，单根竖柄从颈点向下。颈点上移、竖柄加长，
     * 使 10 像素下斜线收口与颈部仍可分辨，不糊成一根棍。
     */
    public static List<Stroke> funnel() {
        List<Stroke> s = new ArrayList<>();
        s.add(new Line(0.18, 0.26, 0.82, 0.26));
        s.add(new Line(0.18, 0.26, 0.50, 0.50));
        s.add(new Line(0.82, 0.26, 0.50, 0.50));
        s.add(new Line(0.50, 0.50, 0.50, 0.84));
        return s;
    }

    /**
     * 折线图：底轴与纵轴加两段折线。折点间距与纵向落差加大，使 10 像素下每个折角可分辨，
     * 折线起点也离纵轴稍远，避免左下角与轴线融成一团。
     */
    public static List<Stroke> chartLine() {
        List<Stroke> s = new ArrayList<>();
        s.add(new Line(0.20, 0.78, 0.80, 0.78));
        s.add(new Line(0.20, 0.22, 0.20, 0.78));
        s.add(new Line(0.30, 0.66, 0.46, 0.40));
        s.add(new Line(0.46, 0.40, 0.58, 0.56));
        s.add(new Line(0.58, 0.56, 0.80, 0.26));
        return s;
    }

    /** 时钟：整圆表盘加两根指针，指针从中心指向 12 点与 3 点。 */
    public static List<Stroke> clock() {
        List<Stroke> s = new ArrayList<>();
        s.add(new Arc(0.50, 0.50, 0.30, 0, Math.PI * 2));
        s.add(new Line(0.50, 0.50, 0.50, 0.34));
        s.add(new Line(0.50, 0.50, 0.64, 0.50));
        return s;
    }

    /**
     * 目标：外圈加中心点。小尺寸（10–12 GUI 像素）下第二圈会被挤占成实心，所以去掉，
     * 只保留能辨认的外圈与醒目的中心点（方案一，与像素对比一致）。
     */
    public static List<Stroke> target() {
        List<Stroke> s = new ArrayList<>();
        s.add(new Arc(0.50, 0.50, 0.30, 0, Math.PI * 2));
        s.add(new Dot(0.50, 0.50, 0.10));
        return s;
    }

    /**
     * 移动：十字杆加四端箭头。每个箭头的尖端落在杆端，两臂沿杆的反方向以 ±40° 张开，
     * 臂长明显长于线宽（0.16 对 0.09），箭头能清楚辨认；四向由同一规则旋转生成，保证对称。
     */
    public static List<Stroke> move() {
        double c = 0.50, lo = 0.16, hi = 0.84, armLen = 0.16, spread = Math.toRadians(40);
        List<Stroke> s = new ArrayList<>();
        s.add(new Line(c, lo, c, hi));
        s.add(new Line(lo, c, hi, c));
        // 四个方向：尖端在杆端，沿 back 方向（指向杆内部）各张开 ±spread
        double[][] tips = {{c, lo}, {c, hi}, {lo, c}, {hi, c}};
        double[] backs = {Math.PI / 2, -Math.PI / 2, 0, Math.PI};
        for(int i = 0; i < 4; i++) {
            double[] tip = tips[i];
            double back = backs[i];
            for(double sign : new double[] {1, -1}) {
                double a = back + sign * spread;
                s.add(new Line(tip[0], tip[1], tip[0] + armLen * Math.cos(a), tip[1] + armLen * Math.sin(a)));
            }
        }
        return s;
    }

    /**
     * 垃圾桶：一条比桶身宽的横盖，盖上方一个矩形把手，桶身是上宽下窄的梯形，内部两道竖纹。
     * 把手是方的、桶身是梯形，所以不会读成滴管。
     */
    public static List<Stroke> trash() {
        List<Stroke> s = new ArrayList<>();
        // 盖子：比桶身上沿略宽
        s.add(new Line(0.18, 0.32, 0.82, 0.32));
        // 矩形把手
        s.add(new Line(0.40, 0.32, 0.40, 0.20));
        s.add(new Line(0.40, 0.20, 0.60, 0.20));
        s.add(new Line(0.60, 0.20, 0.60, 0.32));
        // 梯形桶身：上沿在 x = 0.26..0.74，下沿在 x = 0.32..0.68，关于 x = 0.5 对称
        s.add(new Line(0.26, 0.32, 0.32, 0.82));
        s.add(new Line(0.74, 0.32, 0.68, 0.82));
        s.add(new Line(0.32, 0.82, 0.68, 0.82));
        // 两道竖纹，关于中轴对称
        s.add(new Line(0.42, 0.44, 0.42, 0.70));
        s.add(new Line(0.58, 0.44, 0.58, 0.70));
        return s;
    }

    /**
     * 数据库：圆柱体。顶面是扁椭圆，两侧直壁连下，中间与底部各一条下半椭圆分界弧。
     * 纵向半径加大、分界弧间距拉开，使 10 像素下三层仍各自可辨，不融成一团。
     */
    public static List<Stroke> database() {
        List<Stroke> s = new ArrayList<>();
        s.add(new Ellipse(0.50, 0.22, 0.30, 0.14, 0, Math.PI * 2));
        s.add(new Line(0.20, 0.22, 0.20, 0.74));
        s.add(new Line(0.80, 0.22, 0.80, 0.74));
        s.add(new Ellipse(0.50, 0.46, 0.30, 0.14, 0, Math.PI));
        s.add(new Ellipse(0.50, 0.74, 0.30, 0.14, 0, Math.PI));
        return s;
    }
}
