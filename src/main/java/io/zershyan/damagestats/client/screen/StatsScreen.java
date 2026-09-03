package io.zershyan.damagestats.client.screen;

import io.zershyan.damagestats.DamageStats;
import io.zershyan.damagestats.client.ClientStats;
import io.zershyan.damagestats.datagen.init.DSKeyLang;
import io.zershyan.damagestats.registry.packet.OverlayTargetTypePacket;
import io.zershyan.damagestats.registry.packet.ResetStatsPacket;
import io.zershyan.damagestats.registry.packet.StatsRequestPacket;
import io.zershyan.damagestats.stats.EntityRef;
import io.zershyan.damagestats.stats.filter.*;
import io.zershyan.damagestats.stats.save.StatsExporter;
import io.zershyan.damagestats.stats.view.*;
import io.zershyan.damagestats.util.StatsNames;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 统计主界面。数据全部来自服务端推来的快照（{@link ClientStats}），本界面只负责摆放、绘制和拼筛选条件。
 * 三个分组列表里的每一行都能点：点下去就把那一项加进筛选，再点一次取消，
 * 于是「只看这个玩家用箭打出的伤害」就是连点两行的事。
 */
public class StatsScreen extends Screen {
    private enum Dimension { TYPES, SOURCES, OPPONENTS }

    /** 主体是谁。ALL 就是四个槽位全空的全局统计，服务端只放管理员过 */
    private enum Subject { SELF, INSTANCE, ALL, TYPE }

    private static final int WIDTH = 320;
    private static final int HEIGHT = 257;
    private static final int MARGIN = 8;
    private static final int LINE_HEIGHT = 11;
    private static final int ROW_HEIGHT = 10;
    private static final int VISIBLE_ROWS = 5;
    private static final int LIST_TOP = 171;
    private static final int SMALL_BUTTON_HEIGHT = 16;
    private static final int TOGGLE_WIDTH = 52;
    private static final int DIMENSION_WIDTH = 68;
    private static final int BOTTOM_BUTTON_WIDTH = 72;
    private static final int BOTTOM_BUTTON_HEIGHT = 20;
    private static final int TYPE_CHOICE_WIDTH = 230;
    private static final int TYPE_CHOICE_HEIGHT = 48;
    private static final int BACKGROUND = 0xE0101010;
    private static final int BORDER = 0xFF5A5A5A;
    private static final int SEPARATOR = 0xFF3A3A3A;
    private static final int ROW_HOVER = 0x40FFFFFF;
    private static final int LABEL_COLOR = 0xFFAAAAAA;
    private static final int VALUE_COLOR = 0xFFFFFFFF;
    private static final int ACCENT_COLOR = 0xFF55FFFF;

    private boolean outgoing = true;
    private Subject subject = Subject.SELF;
    private boolean sessionScope = true;
    private boolean showDetails;
    private boolean showHistory;
    private Dimension dimension = Dimension.TYPES;
    private StatsFilter filter = StatsFilter.NONE;
    private @Nullable ResourceLocation subjectType;
    private @Nullable EntityRef subjectInstance;
    private @Nullable Component subjectInstanceName;
    private DamageTypeGrouping typeGrouping = DamageTypeGrouping.CATEGORY;
    private @Nullable FilterKey pendingTypeSelection;
    private @Nullable Component pendingSelectionName;
    private int scrollOffset;

    /** 上一次已经采纳过筛选的那份快照，用来认出「服务端回了新的」 */
    private @Nullable StatsSnapshot adopted;

    private @Nullable Button outgoingButton;
    private @Nullable Button incomingButton;
    private @Nullable Button sessionButton;
    private @Nullable Button lifetimeButton;
    private @Nullable Button subjectButton;
    private @Nullable Button typesButton;
    private @Nullable Button sourcesButton;
    private @Nullable Button opponentsButton;
    private @Nullable Button detailsButton;
    private @Nullable Button historyButton;
    private @Nullable Button typeGroupingButton;
    private @Nullable Button typeSubjectButton;
    private @Nullable Button typeFilterButton;

    public StatsScreen() {
        super(DSKeyLang.ScreenTitle.copy());
    }

    @Override
    protected void init() {
        // 重置通知可能在界面关闭时到达，打开界面时本次初始化请求就足够了
        ClientStats.consumeSnapshotInvalidation();
        int left = left();
        int top = top();
        int toggleY = top + 31;
        outgoingButton = addRenderableWidget(Button.builder(DSKeyLang.TabOutgoing.copy(), button -> selectDirection(true))
                .bounds(left + MARGIN, toggleY, TOGGLE_WIDTH, SMALL_BUTTON_HEIGHT).build());
        incomingButton = addRenderableWidget(Button.builder(DSKeyLang.TabIncoming.copy(), button -> selectDirection(false))
                .bounds(left + MARGIN + TOGGLE_WIDTH + 2, toggleY, TOGGLE_WIDTH, SMALL_BUTTON_HEIGHT).build());

        // 三个档位挤不进这一行，做成一个显示当前档位的循环按钮
        subjectButton = addRenderableWidget(Button.builder(subjectLabel(), button -> cycleSubject())
                .bounds(left + MARGIN + TOGGLE_WIDTH * 2 + 6, toggleY, TOGGLE_WIDTH, SMALL_BUTTON_HEIGHT).build());

        sessionButton = addRenderableWidget(Button.builder(DSKeyLang.ScopeSession.copy(), button -> selectScope(true))
                .bounds(left + WIDTH - MARGIN - TOGGLE_WIDTH * 2 - 2, toggleY, TOGGLE_WIDTH, SMALL_BUTTON_HEIGHT).build());
        lifetimeButton = addRenderableWidget(Button.builder(DSKeyLang.ScopeLifetime.copy(), button -> selectScope(false))
                .bounds(left + WIDTH - MARGIN - TOGGLE_WIDTH, toggleY, TOGGLE_WIDTH, SMALL_BUTTON_HEIGHT).build());

        int dimensionY = top + 133;
        typesButton = addRenderableWidget(Button.builder(DSKeyLang.DimensionTypes.copy(), button -> selectDimension(Dimension.TYPES))
                .bounds(left + MARGIN, dimensionY, DIMENSION_WIDTH, SMALL_BUTTON_HEIGHT).build());
        sourcesButton = addRenderableWidget(Button.builder(DSKeyLang.DimensionSources.copy(), button -> selectDimension(Dimension.SOURCES))
                .bounds(left + MARGIN + DIMENSION_WIDTH + 2, dimensionY, DIMENSION_WIDTH, SMALL_BUTTON_HEIGHT).build());
        opponentsButton = addRenderableWidget(Button.builder(DSKeyLang.DimensionOpponents.copy(), button -> selectDimension(Dimension.OPPONENTS))
                .bounds(left + MARGIN + (DIMENSION_WIDTH + 2) * 2, dimensionY, DIMENSION_WIDTH, SMALL_BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(DSKeyLang.ScreenClearFilter.copy(), button -> clearFilter())
                .bounds(left + WIDTH - MARGIN - DIMENSION_WIDTH, dimensionY, DIMENSION_WIDTH, SMALL_BUTTON_HEIGHT).build());
        int secondaryY = top + 151;
        detailsButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenDetails.copy(), button -> toggleDetails())
                .bounds(left + MARGIN, secondaryY, DIMENSION_WIDTH, SMALL_BUTTON_HEIGHT).build());
        historyButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenHistory.copy(), button -> toggleHistory())
                .bounds(left + MARGIN + DIMENSION_WIDTH + 2, secondaryY, DIMENSION_WIDTH, SMALL_BUTTON_HEIGHT).build());
        typeGroupingButton = addRenderableWidget(Button.builder(typeGroupingLabel(), button -> toggleTypeGrouping())
                .bounds(left + MARGIN + (DIMENSION_WIDTH + 2) * 2, secondaryY,
                        DIMENSION_WIDTH, SMALL_BUTTON_HEIGHT).build());

        int bottomY = top + HEIGHT - 26;
        int rightEdge = left + WIDTH - MARGIN;
        addRenderableWidget(Button.builder(DSKeyLang.ScreenReset.copy(), button -> resetMine())
                .bounds(rightEdge - BOTTOM_BUTTON_WIDTH * 4 - 12, bottomY,
                        BOTTOM_BUTTON_WIDTH, BOTTOM_BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(DSKeyLang.ScreenExport.copy(), button -> exportSnapshot())
                .bounds(rightEdge - BOTTOM_BUTTON_WIDTH * 3 - 8, bottomY,
                        BOTTOM_BUTTON_WIDTH, BOTTOM_BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(DSKeyLang.ScreenEditOverlay.copy(),
                        button -> minecraft.setScreen(new OverlayPositionScreen()))
                .bounds(rightEdge - BOTTOM_BUTTON_WIDTH * 2 - 4, bottomY,
                        BOTTOM_BUTTON_WIDTH, BOTTOM_BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(rightEdge - BOTTOM_BUTTON_WIDTH, bottomY,
                        BOTTOM_BUTTON_WIDTH, BOTTOM_BUTTON_HEIGHT).build());

        int choiceLeft = typeChoiceLeft();
        int choiceTop = typeChoiceTop();
        typeSubjectButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenSetSubject.copy(),
                        button -> useSelectedTypeAsSubject())
                .bounds(choiceLeft + 6, choiceTop + 25, 106, SMALL_BUTTON_HEIGHT).build());
        typeFilterButton = addRenderableWidget(Button.builder(DSKeyLang.ScreenSetOtherFilter.copy(),
                        button -> useSelectedTypeAsOtherFilter())
                .bounds(choiceLeft + TYPE_CHOICE_WIDTH - 112, choiceTop + 25,
                        106, SMALL_BUTTON_HEIGHT).build());
        refreshTypeChoiceVisibility();

        // 界面重开时按当前状态补一次筛选条件，否则第一帧的筛选行是空的
        applySubject();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        if(ClientStats.consumeSnapshotInvalidation()) {
            adopted = null;
            request();
        }
        int left = left();
        int top = top();
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, BACKGROUND);
        graphics.renderOutline(left, top, WIDTH, HEIGHT, BORDER);

        StatsSnapshot snapshot = ClientStats.snapshot();
        if(snapshot != null && snapshot != adopted) adopt(snapshot);
        graphics.drawCenteredString(font, snapshot == null
                        ? title
                        : DSKeyLang.ScreenTitleOf.get(snapshot.subjectName()),
                left + WIDTH / 2, top + MARGIN, VALUE_COLOR);
        graphics.drawCenteredString(font, filterLine(snapshot), left + WIDTH / 2, top + 19, LABEL_COLOR);

        StatsView view = snapshot == null ? null : currentView(snapshot);
        if(view == null || (showHistory ? snapshot.history().isEmpty() : view.isEmpty())) {
            // 快照还没到，或者这套筛选条件下确实没数据，别摆一桌零出来
            graphics.drawCenteredString(font, DSKeyLang.NoData.copy(),
                    left + WIDTH / 2, top + LIST_TOP, LABEL_COLOR);
        } else {
            graphics.fill(left + MARGIN, top + 50, left + WIDTH - MARGIN, top + 51, SEPARATOR);
            renderMetrics(graphics, view.metrics(), left + MARGIN, top + 56);
            graphics.fill(left + MARGIN, top + 127, left + WIDTH - MARGIN, top + 128, SEPARATOR);
            if(showHistory) {
                renderHistory(graphics, snapshot.history(), left, top);
            } else {
                renderGroups(graphics, currentGroups(view), left, top, mouseX, mouseY);
            }
        }
        renderTypeChoice(graphics);
        // 面板背景在上面才画完，按钮必须放在它之后，否则会被盖住
        renderables.forEach(renderable -> renderable.render(graphics, mouseX, mouseY, partialTick));
    }

    /** 当前生效的筛选条件连成一行。什么都没筛就写「全部伤害」，原始记录被淘汰过就在末尾标一下 */
    private Component filterLine(@Nullable StatsSnapshot snapshot) {
        if(snapshot == null || snapshot.filterLabels().isEmpty()) return DSKeyLang.ScreenAllDamage.copy();
        MutableComponent line = Component.empty();
        List<Component> labels = snapshot.filterLabels();
        for (int index = 0; index < labels.size(); index++) {
            if(index > 0) line.append(Component.literal("  ·  "));
            line.append(labels.get(index));
        }
        if(snapshot.truncated()) {
            line.append(Component.literal(" "))
                    .append(DSKeyLang.ScreenTruncated.copy().withStyle(ChatFormatting.RED));
        }
        return line;
    }

    private void renderMetrics(GuiGraphics graphics, MetricsView metrics, int x, int y) {
        int column = x + 156;
        graphics.drawString(font, DSKeyLang.TotalDamage.getNumber2f(metrics.totalActual()), x, y, ACCENT_COLOR);
        graphics.drawString(font, DSKeyLang.HitCount.get(metrics.hitCount()), column, y, LABEL_COLOR);
        graphics.drawString(font, DSKeyLang.OverlayDps.getNumber1f(metrics.averageDps(), metrics.realtimeDps()),
                x, y + LINE_HEIGHT, ACCENT_COLOR);
        graphics.drawString(font, DSKeyLang.AverageDamage.getNumber2f(metrics.averageDamage()),
                column, y + LINE_HEIGHT, LABEL_COLOR);
        graphics.drawString(font, DSKeyLang.MaxSingle.getNumber2f(
                        metrics.maxSingle(), metrics.maxSingleTypeName(),
                        metrics.maxSingleDirectSourceName(), metrics.maxSingleTime()),
                x, y + LINE_HEIGHT * 2, LABEL_COLOR);
        if(!showDetails) return;
        graphics.drawString(font, DSKeyLang.OriginalDamage.getNumber2f(metrics.totalOriginal()),
                x, y + LINE_HEIGHT * 3, LABEL_COLOR);
        graphics.drawString(font, DSKeyLang.ReductionRate.getNumber2f(
                metrics.reducedDamage(), metrics.reductionRate() * 100), column, y + LINE_HEIGHT * 3, LABEL_COLOR);
        graphics.drawString(font, DSKeyLang.Duration.getNumber1f(metrics.durationSeconds()),
                x, y + LINE_HEIGHT * 4, LABEL_COLOR);
        graphics.drawString(font, DSKeyLang.KillCount.get(metrics.killCount()),
                column, y + LINE_HEIGHT * 4, LABEL_COLOR);
        graphics.drawString(font, DSKeyLang.MinSingle.getNumber2f(metrics.minSingle()),
                x, y + LINE_HEIGHT * 5, LABEL_COLOR);
        graphics.drawString(font, DSKeyLang.HitsPerSecond.getNumber2f(metrics.hitsPerSecond()),
                column, y + LINE_HEIGHT * 5, LABEL_COLOR);
    }

    private void renderGroups(GuiGraphics graphics, List<GroupView> groups,
                              int left, int top, int mouseX, int mouseY) {
        if(groups.isEmpty()) {
            graphics.drawString(font, DSKeyLang.NoData.copy(), left + MARGIN, top + LIST_TOP, LABEL_COLOR);
            return;
        }
        int rows = Math.min(VISIBLE_ROWS, groups.size() - scrollOffset);
        for (int row = 0; row < rows; row++) {
            GroupView group = groups.get(scrollOffset + row);
            int rowY = top + LIST_TOP + row * ROW_HEIGHT;
            // 行可点，所以给个悬停高亮，否则玩家不知道能点
            if(isInsideRow(mouseX, mouseY, left, rowY)) {
                graphics.fill(left + MARGIN, rowY - 1, left + WIDTH - MARGIN, rowY + ROW_HEIGHT - 1, ROW_HOVER);
            }
            graphics.drawString(font, DSKeyLang.DetailLine.getNumber2f(
                            group.name(), group.damage(), group.share() * 100, group.hitCount()),
                    left + MARGIN, rowY, LABEL_COLOR);
        }
    }

    private void renderHistory(GuiGraphics graphics, List<SessionView> history, int left, int top) {
        int rows = Math.min(VISIBLE_ROWS, history.size() - scrollOffset);
        for (int row = 0; row < rows; row++) {
            SessionView session = history.get(scrollOffset + row);
            graphics.drawString(font, DSKeyLang.SessionLine.getNumber2f(
                            scrollOffset + row + 1,
                            session.totalDamage(),
                            session.averageDps(),
                            session.durationSeconds(),
                            session.hitCount()),
                    left + MARGIN, top + LIST_TOP + row * ROW_HEIGHT, LABEL_COLOR);
        }
        if(history.isEmpty()) {
            graphics.drawString(font, DSKeyLang.NoData.copy(), left + MARGIN, top + LIST_TOP, LABEL_COLOR);
        }
    }

    /** 点列表里的一行就把那一项加进筛选，再点一次取消——需求里「点箭只看箭的伤害」就是这个 */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if(pendingTypeSelection != null) return clickTypeChoice(mouseX, mouseY, button);
        StatsSnapshot snapshot = ClientStats.snapshot();
        if(button == 0 && snapshot != null && toggleFilterLabel(snapshot, mouseX, mouseY)) return true;
        if(button == 0 && snapshot != null && !showHistory) {
            List<GroupView> groups = currentGroups(currentView(snapshot));
            int left = left();
            int top = top();
            int rows = Math.clamp(groups.size() - scrollOffset, 0, VISIBLE_ROWS);
            for (int row = 0; row < rows; row++) {
                if(!isInsideRow((int) mouseX, (int) mouseY, left, top + LIST_TOP + row * ROW_HEIGHT)) continue;
                FilterKey key = groups.get(scrollOffset + row).key();
                selectGroup(key);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 筛选标签与其对应的筛选键保持同一顺序，点标签即可单独撤销该条件。 */
    private boolean toggleFilterLabel(StatsSnapshot snapshot, double mouseX, double mouseY) {
        int lineY = top() + 19;
        if(mouseY < lineY - 2 || mouseY > lineY + font.lineHeight) return false;
        List<Component> labels = snapshot.filterLabels();
        List<FilterKey> keys = filterKeys();
        if(labels.size() != keys.size() || labels.isEmpty()) return false;
        int separatorWidth = font.width("  ·  ");
        int totalWidth = labels.stream().mapToInt(font::width).sum() + separatorWidth * (labels.size() - 1);
        int x = left() + (WIDTH - totalWidth) / 2;
        for (int index = 0; index < labels.size(); index++) {
            int width = font.width(labels.get(index));
            if(mouseX >= x && mouseX <= x + width) {
                toggleGroup(keys.get(index));
                return true;
            }
            x += width + separatorWidth;
        }
        return false;
    }

    private List<FilterKey> filterKeys() {
        List<FilterKey> keys = new java.util.ArrayList<>();
        filter.source().ifPresent(selector -> keys.add(new FilterKey.Source(selector)));
        filter.target().ifPresent(selector -> keys.add(new FilterKey.Target(selector)));
        filter.directSource().ifPresent(selector -> keys.add(new FilterKey.Direct(selector)));
        filter.damageType().ifPresent(selector -> keys.add(new FilterKey.Type(selector)));
        return keys;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if(pendingTypeSelection != null) return true;
        StatsSnapshot snapshot = ClientStats.snapshot();
        if(snapshot == null) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        int itemCount = showHistory
                ? snapshot.history().size()
                : currentGroups(currentView(snapshot)).size();
        int maxOffset = Math.max(0, itemCount - VISIBLE_ROWS);
        scrollOffset = Mth.clamp(scrollOffset - (int) Math.signum(scrollY), 0, maxOffset);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if(pendingTypeSelection == null) return super.keyPressed(keyCode, scanCode, modifiers);
        if(keyCode == GLFW.GLFW_KEY_ESCAPE) clearTypeChoice();
        return true;
    }

    private boolean isInsideRow(int mouseX, int mouseY, int left, int rowY) {
        return mouseX >= left + MARGIN && mouseX <= left + WIDTH - MARGIN
                && mouseY >= rowY - 1 && mouseY < rowY + ROW_HEIGHT - 1;
    }

    private void selectDirection(boolean toOutgoing) {
        outgoing = toOutgoing;
        applySubject();
    }

    private void selectGroup(FilterKey key) {
        if(isSelected(key) || selector(key) == null) {
            toggleGroup(key);
            return;
        }
        pendingTypeSelection = key;
        StatsSnapshot snapshot = ClientStats.snapshot();
        if(snapshot != null) {
            List<GroupView> groups = currentGroups(currentView(snapshot));
            pendingSelectionName = groups.stream()
                    .filter(group -> group.key().equals(key))
                    .findFirst()
                    .map(GroupView::name)
                    .orElse(null);
        }
        refreshTypeChoiceVisibility();
    }

    private boolean isSelected(FilterKey key) {
        return switch (key) {
            case FilterKey.Source(EntitySelector selector) -> filter.source().filter(selector::equals).isPresent();
            case FilterKey.Target(EntitySelector selector) -> filter.target().filter(selector::equals).isPresent();
            case FilterKey.Direct(EntitySelector selector) -> filter.directSource().filter(selector::equals).isPresent();
            case FilterKey.Type(DamageTypeSelector selector) -> filter.damageType().filter(selector::equals).isPresent();
        };
    }

    private void toggleGroup(FilterKey key) {
        filter = filter.toggle(key);
        syncOverlayTarget();
        scrollOffset = 0;
        request();
    }

    private boolean clickTypeChoice(double mouseX, double mouseY, int button) {
        if(button != 0) return true;
        if(typeSubjectButton != null && typeSubjectButton.isMouseOver(mouseX, mouseY)) {
            useSelectedTypeAsSubject();
            return true;
        }
        if(typeFilterButton != null && typeFilterButton.isMouseOver(mouseX, mouseY)) {
            useSelectedTypeAsOtherFilter();
            return true;
        }
        return true;
    }

    private void useSelectedTypeAsSubject() {
        FilterKey selection = pendingTypeSelection;
        EntitySelector selector = selection == null ? null : selector(selection);
        if(selector == null) return;
        boolean toOutgoing = selection instanceof FilterKey.Source;
        Component selectionName = pendingSelectionName;
        clearTypeChoice();
        outgoing = toOutgoing;
        subject = subjectForSelector(selector, selectionName);
        applySubject();
    }

    private void useSelectedTypeAsOtherFilter() {
        FilterKey selection = pendingTypeSelection;
        clearTypeChoice();
        if(selection != null) toggleGroup(selection);
    }

    private @Nullable EntitySelector selector(FilterKey key) {
        return switch (key) {
            case FilterKey.Source(EntitySelector entitySelector) -> entitySelector;
            case FilterKey.Target(EntitySelector entitySelector) -> entitySelector;
            default -> null;
        };
    }

    private void clearTypeChoice() {
        pendingTypeSelection = null;
        pendingSelectionName = null;
        refreshTypeChoiceVisibility();
    }

    /** 按一下换下一个可用档位。没权限就跳过「全部」 */
    private void cycleSubject() {
        Subject[] order = Subject.values();
        for (int step = 1; step <= order.length; step++) {
            Subject candidate = order[(subject.ordinal() + step) % order.length];
            if(!isAvailable(candidate)) continue;
            subject = candidate;
            break;
        }
        applySubject();
    }

    private boolean isAvailable(Subject candidate) {
        return switch (candidate) {
            case SELF -> true;
            case INSTANCE -> subjectInstance != null;
            case ALL -> canViewAll();
            case TYPE -> subjectType != null;
        };
    }

    /**
     * 服务端回来的那份筛选才是真生效的（权限降级、重置都会改它），无条件采纳，
     * 否则接下来点行是拿一份陈旧条件在 toggle。
     */
    private void adopt(StatsSnapshot snapshot) {
        adopted = snapshot;
        filter = snapshot.appliedFilter();
        typeGrouping = snapshot.typeGrouping();
        outgoing = snapshot.subjectSlot() != StatsSubjectSlot.TARGET;
        // 即使筛选内容没有变化，也要同步主体按钮，类型级主体不能被客户端旧状态覆盖
        syncSubjectToAppliedFilter(snapshot);
        refreshButtonStates();
    }

    private void syncSubjectToAppliedFilter(StatsSnapshot snapshot) {
        Optional<EntitySelector> selector = snapshot.subjectSlot() == StatsSubjectSlot.SOURCE
                ? filter.source()
                : filter.target();
        if(selector.isPresent()) {
            subject = subjectForSelector(selector.get(), snapshot.subjectName());
            return;
        }
        subject = Subject.ALL;
        subjectType = null;
        subjectInstance = null;
        subjectInstanceName = null;
    }

    private Subject subjectForSelector(EntitySelector selector, @Nullable Component name) {
        if(selector instanceof EntitySelector.Instance(EntityRef ref)) {
            subjectType = null;
            if(minecraft != null && minecraft.player != null
                    && ref.id().equals(minecraft.player.getUUID())) {
                subjectInstance = null;
                subjectInstanceName = null;
                return Subject.SELF;
            }
            subjectInstance = ref;
            subjectInstanceName = name;
            return Subject.INSTANCE;
        }
        if(selector instanceof EntitySelector.Type(ResourceLocation typeId)) {
            subjectType = typeId;
            subjectInstance = null;
            subjectInstanceName = null;
            return Subject.TYPE;
        }
        subjectType = null;
        return Subject.SELF;
    }

    private void selectScope(boolean toSession) {
        sessionScope = toSession;
        scrollOffset = 0;
        refreshButtonStates();
    }

    private void selectDimension(Dimension toDimension) {
        dimension = toDimension;
        showHistory = false;
        scrollOffset = 0;
        refreshButtonStates();
    }

    private void toggleTypeGrouping() {
        typeGrouping = typeGrouping.next();
        scrollOffset = 0;
        request();
        refreshButtonStates();
    }

    private void toggleDetails() {
        showDetails = !showDetails;
        refreshButtonStates();
    }

    private void toggleHistory() {
        showHistory = !showHistory;
        scrollOffset = 0;
        refreshButtonStates();
    }

    /**
     * 造成/承受决定主体填哪个槽，我/类型决定主体是谁。
     * 行点击加上的直接来源和伤害类型两项保留下来，换主体不该把它们一起清掉。
     */
    private void applySubject() {
        Optional<EntitySelector> selector = subjectSelector();
        if(subject == Subject.ALL) {
            filter = filter.withSource(Optional.empty()).withTarget(Optional.empty());
        } else if(outgoing) {
            filter = filter.withSource(selector);
        } else {
            filter = filter.withTarget(selector);
        }
        syncOverlayTarget();
        scrollOffset = 0;
        request();
        refreshButtonStates();
    }

    private void clearFilter() {
        filter = StatsFilter.NONE;
        showHistory = false;
        outgoing = true;
        subject = Subject.SELF;
        subjectType = null;
        subjectInstance = null;
        subjectInstanceName = null;
        applySubject();
    }

    private void resetMine() {
        PacketDistributor.sendToServer(ResetStatsPacket.INSTANCE);
        scrollOffset = 0;
    }

    private void request() {
        PacketDistributor.sendToServer(new StatsRequestPacket(filter, subjectSlot(), typeGrouping));
    }

    private StatsSubjectSlot subjectSlot() {
        if(subject == Subject.ALL) return StatsSubjectSlot.GLOBAL;
        return outgoing ? StatsSubjectSlot.SOURCE : StatsSubjectSlot.TARGET;
    }

    private Optional<EntitySelector> subjectSelector() {
        return switch (subject) {
            case SELF -> minecraft == null || minecraft.player == null
                    ? Optional.empty()
                    : Optional.of(new EntitySelector.Instance(EntityRef.of(minecraft.player)));
            case ALL -> Optional.empty();
            case INSTANCE -> Optional.ofNullable(subjectInstance).map(EntitySelector.Instance::new);
            case TYPE -> subjectType == null
                    ? Optional.empty()
                    : Optional.of(new EntitySelector.Type(subjectType));
        };
    }

    /** 客户端自己判断权限不可靠，由服务端在快照里告知 */
    private boolean canViewAll() {
        StatsSnapshot snapshot = ClientStats.snapshot();
        return snapshot != null && snapshot.canViewAll();
    }

    private Component subjectLabel() {
        return switch (subject) {
            case SELF -> DSKeyLang.ViewSelf.copy();
            case INSTANCE -> subjectInstanceName == null
                    ? DSKeyLang.ViewSelf.copy()
                    : subjectInstanceName.copy();
            case ALL -> DSKeyLang.ViewAll.copy();
            case TYPE -> subjectType == null
                    ? DSKeyLang.ViewSelf.copy()
                    : DSKeyLang.ViewType.get(StatsNames.entityType(subjectType));
        };
    }

    /** 客户端导出写到自己的游戏目录，不碰服务器的磁盘 */
    private void exportSnapshot() {
        StatsSnapshot snapshot = ClientStats.snapshot();
        if(snapshot == null || minecraft == null || minecraft.player == null) return;
        Path directory = StatsExporter.export(snapshot, FMLPaths.GAMEDIR.get().resolve(DamageStats.MODID));
        minecraft.player.displayClientMessage(directory == null
                ? DSKeyLang.ExportFailed.copy()
                : DSKeyLang.ExportDone.get(directory.toString()), false);
    }

    /** 当前选中的那个按钮置灰，看起来像被按下，省掉一套自绘高亮 */
    private void refreshButtonStates() {
        if(outgoingButton != null) outgoingButton.active = !outgoing;
        if(incomingButton != null) incomingButton.active = outgoing;
        if(sessionButton != null) sessionButton.active = !sessionScope;
        if(lifetimeButton != null) lifetimeButton.active = sessionScope;
        if(subjectButton != null) subjectButton.setMessage(subjectLabel());
        if(typesButton != null) typesButton.active = dimension != Dimension.TYPES;
        if(sourcesButton != null) sourcesButton.active = dimension != Dimension.SOURCES;
        if(opponentsButton != null) opponentsButton.active = dimension != Dimension.OPPONENTS;
        if(detailsButton != null) detailsButton.active = true;
        if(historyButton != null) {
            StatsSnapshot snapshot = ClientStats.snapshot();
            historyButton.active = showHistory || snapshot == null || !snapshot.history().isEmpty();
        }
        if(typeGroupingButton != null) {
            typeGroupingButton.visible = dimension == Dimension.TYPES;
            typeGroupingButton.active = true;
            typeGroupingButton.setMessage(typeGroupingLabel());
        }
    }

    private Component typeGroupingLabel() {
        return typeGrouping == DamageTypeGrouping.CATEGORY
                ? DSKeyLang.ScreenGroupingCategory.copy()
                : DSKeyLang.ScreenGroupingRegistry.copy();
    }

    private StatsView currentView(StatsSnapshot snapshot) {
        return sessionScope ? snapshot.session() : snapshot.lifetime();
    }

    private List<GroupView> currentGroups(StatsView view) {
        return switch (dimension) {
            case TYPES -> view.byType();
            case SOURCES -> view.bySource();
            case OPPONENTS -> view.byOpponent();
        };
    }

    private int left() {
        return (width - WIDTH) / 2;
    }

    private int top() {
        return (height - HEIGHT) / 2;
    }

    /** Overlay 只认造成视图里的目标类型；其余筛选变更都应清掉旧的类型限定 */
    private void syncOverlayTarget() {
        if(outgoing && filter.target().orElse(null) instanceof EntitySelector.Type type) {
            PacketDistributor.sendToServer(OverlayTargetTypePacket.of(type.typeId()));
        } else {
            PacketDistributor.sendToServer(OverlayTargetTypePacket.CLEAR);
        }
    }

    private void renderTypeChoice(GuiGraphics graphics) {
        if(pendingTypeSelection == null || pendingSelectionName == null) return;
        int left = typeChoiceLeft();
        int top = typeChoiceTop();
        graphics.fill(left, top, left + TYPE_CHOICE_WIDTH, top + TYPE_CHOICE_HEIGHT, BACKGROUND);
        graphics.renderOutline(left, top, TYPE_CHOICE_WIDTH, TYPE_CHOICE_HEIGHT, BORDER);
        graphics.drawCenteredString(font, DSKeyLang.ScreenChooseTypeAction.get(pendingSelectionName),
                left + TYPE_CHOICE_WIDTH / 2, top + 8, VALUE_COLOR);
    }

    private int typeChoiceLeft() {
        return left() + (WIDTH - TYPE_CHOICE_WIDTH) / 2;
    }

    private int typeChoiceTop() {
        return top() + 76;
    }

    private void refreshTypeChoiceVisibility() {
        boolean visible = pendingTypeSelection != null;
        if(typeSubjectButton != null) typeSubjectButton.visible = visible;
        if(typeFilterButton != null) typeFilterButton.visible = visible;
    }
}
