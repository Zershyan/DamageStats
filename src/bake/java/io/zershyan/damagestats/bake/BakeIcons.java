package io.zershyan.damagestats.bake;

import io.zershyan.damagestats.client.ui.UiAtlas;
import io.zershyan.damagestats.client.ui.UiGlyph;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 把 {@link UiGlyph} 的几何按 {@link UiGlyph#TEXTURE_SCALE} 倍烘焙为各图集族的 PNG，布局见 {@link UiAtlas}。
 * 图标、圆角面、圆角环与纯白块都是白色抗锯齿蒙版，颜色由运行时的顶点色或 shader 乘上。
 */
public final class BakeIcons {
    private static final int SCALE = UiGlyph.TEXTURE_SCALE;

    private BakeIcons() {}

    /** 参数为资源 gui 根目录，每个图集族输出为其下的 {sheet}.png。 */
    public static void main(String[] args) throws Exception {
        File gui = new File(args[0]);
        if(!gui.isDirectory() && !gui.mkdirs()) {
            throw new IllegalStateException("无法创建目录 " + gui);
        }
        Map<String, List<UiGlyph.Stroke>> shapes = shapes();
        for(UiAtlas.Sheet sheet : UiAtlas.Sheet.values()) {
            bakeSheet(new File(gui, sheet.fileName() + ".png"), sheet, shapes);
        }
        System.out.println("已烘焙到 " + gui.getPath());
    }

    /** 烘焙单族：画布为族尺寸，只绘制属于该族的区域，再统一白化。 */
    private static void bakeSheet(File output, UiAtlas.Sheet sheet, Map<String, List<UiGlyph.Stroke>> shapes)
            throws Exception {
        BufferedImage image = new BufferedImage(UiAtlas.width(sheet), UiAtlas.height(sheet),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.WHITE);
        for(Map.Entry<String, List<UiGlyph.Stroke>> entry : shapes.entrySet()) {
            UiAtlas.Region region = UiAtlas.region(entry.getKey());
            if(region.sheet() == sheet) paint(g, region, entry.getValue());
        }
        g.dispose();
        whiten(image);
        ImageIO.write(image, "png", output);
    }

    /** 图集中全部形状：图标、对勾帧、旋转圈、确认框、圆角面与环、纯白块。 */
    private static Map<String, List<UiGlyph.Stroke>> shapes() {
        Map<String, List<UiGlyph.Stroke>> shapes = new LinkedHashMap<>();
        shapes.put("chevron_left", UiGlyph.chevron(-1));
        shapes.put("chevron_right", UiGlyph.chevron(1));
        shapes.put("chevron_up", UiGlyph.chevronVertical(-1));
        shapes.put("chevron_down", UiGlyph.chevronVertical(1));
        shapes.put("caret_up", UiGlyph.caret(-1));
        shapes.put("caret_down", UiGlyph.caret(1));
        shapes.put("caret_left", UiGlyph.caret(-2));
        shapes.put("caret_right", UiGlyph.caret(2));
        shapes.put("cross", UiGlyph.cross());
        shapes.put("ellipsis", UiGlyph.ellipsis());
        shapes.put("refresh", UiGlyph.refresh());
        shapes.put("download", UiGlyph.download());
        shapes.put("funnel", UiGlyph.funnel());
        shapes.put("chart_line", UiGlyph.chartLine());
        shapes.put("clock", UiGlyph.clock());
        shapes.put("target", UiGlyph.target());
        shapes.put("move", UiGlyph.move());
        shapes.put("trash", UiGlyph.trash());
        shapes.put("database", UiGlyph.database());
        shapes.put("dot", List.of(new UiGlyph.Dot(0.50, 0.50, 0.50)));
        shapes.put("spinner_track", List.of(UiGlyph.spinnerTrack()).get(0));
        shapes.put("spinner_arc", List.of(UiGlyph.spinnerArc()).get(0));
        shapes.put("alert_disc", List.of(UiGlyph.alertDisc()).get(0));
        shapes.put("alert_ring", List.of(UiGlyph.alertRing()).get(0));
        shapes.put("alert_mark", List.of(UiGlyph.alertMark()).get(0));
        for(int frame = 0; frame < UiGlyph.CHECK_FRAMES; frame++) {
            shapes.put("check_" + frame, UiGlyph.check(frame / (double) (UiGlyph.CHECK_FRAMES - 1)));
        }
        for(int radius : UiGlyph.CORNER_RADII) {
            shapes.put("face_" + radius, UiGlyph.cornerFace(radius));
            shapes.put("ring_" + radius, UiGlyph.cornerRing(radius));
        }
        shapes.put(UiAtlas.SOLID, List.of(new UiGlyph.Fill(new double[][]{{0, 0}, {1, 0}, {1, 1}, {0, 1}})));
        return shapes;
    }

    /** 把笔画画进图集区域，区域的左上角与边长以纹理像素给出。 */
    private static void paint(Graphics2D g, UiAtlas.Region region, List<UiGlyph.Stroke> strokes) {
        g.setTransform(AffineTransform.getTranslateInstance(region.x(), region.y()));
        for(UiGlyph.Stroke stroke : strokes) {
            draw(g, stroke, region.width());
        }
    }

    /** RGB 统一为白，线性采样时透明像素的黑色不会渗出灰边。 */
    private static void whiten(BufferedImage image) {
        for(int y = 0; y < image.getHeight(); y++) {
            for(int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, image.getRGB(x, y) | 0x00FFFFFF);
            }
        }
    }

    private static void draw(Graphics2D g, UiGlyph.Stroke stroke, double size) {
        if(stroke instanceof UiGlyph.Line l) {
            g.setStroke(pen(l.width(), size));
            g.draw(new Line2D.Double(l.x0() * size, l.y0() * size, l.x1() * size, l.y1() * size));
        } else if(stroke instanceof UiGlyph.Arc a) {
            g.setStroke(pen(a.width(), size));
            double r = a.radius() * size;
            // UiGlyph 角度为弧度、屏幕坐标下顺时针为正；Java2D 为度、逆时针为正，这里取负换算
            g.draw(new Arc2D.Double(a.cx() * size - r, a.cy() * size - r, 2 * r, 2 * r,
                    -Math.toDegrees(a.start()), -Math.toDegrees(a.end() - a.start()), Arc2D.OPEN));
        } else if(stroke instanceof UiGlyph.Dot d) {
            double r = d.radius() * size;
            g.fill(new Ellipse2D.Double(d.cx() * size - r, d.cy() * size - r, 2 * r, 2 * r));
        } else if(stroke instanceof UiGlyph.Ellipse e) {
            g.setStroke(pen(e.width(), size));
            double rx = e.rx() * size, ry = e.ry() * size;
            g.draw(new Arc2D.Double(e.cx() * size - rx, e.cy() * size - ry, 2 * rx, 2 * ry,
                    -Math.toDegrees(e.start()), -Math.toDegrees(e.end() - e.start()), Arc2D.OPEN));
        } else if(stroke instanceof UiGlyph.Fill f) {
            Path2D.Double path = new Path2D.Double();
            double[][] v = f.vertices();
            for(int i = 0; i < v.length; i++) {
                if(i == 0) path.moveTo(v[i][0] * size, v[i][1] * size);
                else path.lineTo(v[i][0] * size, v[i][1] * size);
            }
            path.closePath();
            g.fill(path);
        }
    }

    private static BasicStroke pen(double width, double size) {
        return new BasicStroke((float) (width * size), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }
}
