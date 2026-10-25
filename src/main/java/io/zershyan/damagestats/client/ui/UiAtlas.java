package io.zershyan.damagestats.client.ui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 界面纹理按图标族分图：同一图标的全部变化（方向、帧、档位、分层）放进同一张图，按网格 UV 取用。
 * 每张图位于 textures/gui/{sheet}.png，由 BakeIcons 烘焙；网格布局只在这里定义，烘焙与运行时共用。
 */
public final class UiAtlas {
    /** 图集族：每个族烘焙为一张独立的 PNG。 */
    public enum Sheet {
        CHEVRON("chevron"), CARET("caret"), ACTION("action"), CHECK("check"),
        SPINNER("spinner"), ALERT("alert"), DOT("dot"), FACE("face"), RING("ring"), SOLID("solid");

        private final String file;

        Sheet(String file) {
            this.file = file;
        }

        /** 图集文件名，不含扩展名与目录；烘焙与运行时由它派生路径。 */
        public String fileName() {
            return file;
        }

        public String texture() {
            return "textures/gui/" + file + ".png";
        }
    }

    /** 纯白块：纯色矩形与线条都是它的拉伸，颜色由顶点色给出。 */
    public static final String SOLID = "solid";
    /** 一个族内的网格列数，行列都按此排布。 */
    private static final int COLUMNS = 4;
    private static final Map<String, Region> REGIONS = new LinkedHashMap<>();
    private static final Map<Sheet, int[]> SIZES = new EnumMap<>(Sheet.class);

    /** 区域所在的图集、格子左上角、以及内容的宽高（纹理像素）；圆角内容小于格子，格子按族统一。 */
    public record Region(Sheet sheet, int x, int y, int width, int height) {}

    static {
        int tile = UiGlyph.TILE;
        // 圆角格按最大半径统一尺寸：边长 2r+1，r 取 CORNER_RADII 的最大值
        int cornerCell = (2 * UiGlyph.CORNER_RADII[UiGlyph.CORNER_RADII.length - 1] + 1) * UiGlyph.TEXTURE_SCALE;
        placeCorners(Sheet.FACE, "face_", cornerCell);
        placeCorners(Sheet.RING, "ring_", cornerCell);
        place(Sheet.SOLID, List.of(SOLID), UiGlyph.TEXTURE_SCALE);
        place(Sheet.CHEVRON, List.of("chevron_left", "chevron_right", "chevron_up", "chevron_down"), tile);
        place(Sheet.CARET, List.of("caret_up", "caret_down", "caret_left", "caret_right"), tile);
        place(Sheet.ACTION, List.of("cross", "ellipsis", "refresh", "download", "funnel", "chart_line", "clock",
                "target", "move", "trash", "database"), tile);
        place(Sheet.DOT, List.of("dot"), tile);
        place(Sheet.CHECK, checkNames(), tile);
        place(Sheet.SPINNER, List.of("spinner_track", "spinner_arc"), UiGlyph.SPINNER_PX * UiGlyph.TEXTURE_SCALE);
        place(Sheet.ALERT, List.of("alert_disc", "alert_ring", "alert_mark"), UiGlyph.ALERT_PX * UiGlyph.TEXTURE_SCALE);
    }

    /** 按名字取区域；名字不在图集中直接报错，避免静默画出空白。 */
    public static Region region(String name) {
        Region region = REGIONS.get(name);
        if(region == null) throw new IllegalArgumentException("图集中没有区域：" + name);
        return region;
    }

    /** 某个族的整图尺寸，用于把纹理像素归一化为 UV。 */
    public static int width(Sheet sheet) {
        return SIZES.get(sheet)[0];
    }

    public static int height(Sheet sheet) {
        return SIZES.get(sheet)[1];
    }

    /** 不超过 limit 的最大烘焙半径；没有则返回 0，调用方退为直角。 */
    public static int bakedRadius(float limit) {
        int result = 0;
        for(int r : UiGlyph.CORNER_RADII) {
            if(r <= limit && r > result) result = r;
        }
        return result;
    }

    /** 把一组格子按列排入族图，族图宽为 COLUMNS 格，高按行数取整；每个格子的内容边长即格子边长。 */
    private static void place(Sheet sheet, List<String> names, int cell) {
        int rows = (names.size() + COLUMNS - 1) / COLUMNS;
        SIZES.put(sheet, new int[]{COLUMNS * cell, rows * cell});
        for(int index = 0; index < names.size(); index++) {
            int column = index % COLUMNS;
            int row = index / COLUMNS;
            REGIONS.put(names.get(index), new Region(sheet, column * cell, row * cell, cell, cell));
        }
    }

    /** 圆角格：格子仍按统一边长排布，但内容只占边长 2r+1 的左上角。 */
    private static void placeCorners(Sheet sheet, String prefix, int cell) {
        int rows = (UiGlyph.CORNER_RADII.length + COLUMNS - 1) / COLUMNS;
        SIZES.put(sheet, new int[]{COLUMNS * cell, rows * cell});
        for(int index = 0; index < UiGlyph.CORNER_RADII.length; index++) {
            int radius = UiGlyph.CORNER_RADII[index];
            int content = (2 * radius + 1) * UiGlyph.TEXTURE_SCALE;
            int column = index % COLUMNS;
            int row = index / COLUMNS;
            REGIONS.put(prefix + radius, new Region(sheet, column * cell, row * cell, content, content));
        }
    }

    private static List<String> checkNames() {
        List<String> names = new ArrayList<>();
        for(int frame = 0; frame < UiGlyph.CHECK_FRAMES; frame++) names.add("check_" + frame);
        return names;
    }
}
