package com.autofarm.client.gui;

import com.autofarm.client.config.AutoFarmConfig;
import com.autofarm.client.config.ConfigManager;
import com.autofarm.client.container.ContainerCompat;
import com.autofarm.client.harvest.CropHarvester;
import com.autofarm.client.inventory.InventoryScanner;
import com.autofarm.client.task.FarmTaskManager;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * AutoFarm 的主控制面板 —— 和「附加功能」一样，用<b>顶部大类 tab</b>分页。
 *
 * <h2>为什么也改成分页</h2>
 * 主面板原本是一张越接越长的滚动页（收割 / 路径 / 可视化 / 补给 / 安全 / 作物 / 附加…），
 * 选项一多，想改某一项就得上下拖半天。改成分页之后每一页只放同一类东西：
 * <ol>
 *   <li><b>收割</b> —— 总开关、扫描范围与频率、补种、取种子、满包即存、瞄准模式；</li>
 *   <li><b>移动</b> —— 路径优化与显示、疾跑、进食、挖 / 垫；</li>
 *   <li><b>显示</b> —— 可视化、穿透、自由视角停机，以及安全停机两项；</li>
 *   <li><b>补给</b> —— 默认种子槽、存放策略、四个专用容器、补给点坐标；</li>
 *   <li><b>更多</b> —— 作物筛选、附加功能、统计。</li>
 * </ol>
 *
 * <p>「运行状态」那一块固定显示在每一页最上方 —— 不管你在哪一页，都能看到它此刻在干嘛。
 *
 * <p>所有改动先落在 draft（草稿）上，点「保存并关闭」才写回配置文件，
 * 点「取消」或按 Esc 直接丢弃。切页时草稿原样带过去，也不会把输入框里的坐标弄丢。
 */
public class AutoFarmScreen extends ScrollPanelScreen {

    // ---------------- tab 定义 ----------------
    private static final int TAB_HARVEST = 0;
    private static final int TAB_MOVE = 1;
    private static final int TAB_DISPLAY = 2;
    private static final int TAB_SUPPLY = 3;
    private static final int TAB_MORE = 4;
    private static final int TAB_COUNT = 5;

    // ---------------- 内容区布局（相对内容区顶部） ----------------
    private static final int ROW_FIRST = 18;
    private static final int ROW_GAP = 20;
    private static final int SECTION_GAP = 6;

    /** 运行状态：每一页顶部都显示的一份只读快照 */
    private static final int SEC_STATUS_Y = TAB_BAR_H;
    private static final int SEC_STATUS_H = 58;
    /** 下面所有分区的起始位置都从它开始 */
    private static final int BODY_Y = SEC_STATUS_Y + SEC_STATUS_H + SECTION_GAP;

    // ---- 收割（tab 0）----
    private static final int HARVEST_ROWS = 8;
    private static final int SEC_HARVEST_Y = BODY_Y;
    private static final int SEC_HARVEST_H = ROW_FIRST + HARVEST_ROWS * ROW_GAP + 4;

    // ---- 移动（tab 1）----
    private static final int MOVE_ROWS = 8;
    private static final int SEC_MOVE_Y = BODY_Y;
    private static final int SEC_MOVE_H = ROW_FIRST + MOVE_ROWS * ROW_GAP + 4;

    // ---- 显示（tab 2）----
    private static final int DISPLAY_ROWS = 3;
    private static final int SEC_DISPLAY_Y = BODY_Y;
    private static final int SEC_DISPLAY_H = ROW_FIRST + DISPLAY_ROWS * ROW_GAP + 4;
    private static final int SAFETY_ROWS = 3;
    private static final int SEC_SAFETY_Y = SEC_DISPLAY_Y + SEC_DISPLAY_H + SECTION_GAP;
    private static final int SEC_SAFETY_H = ROW_FIRST + SAFETY_ROWS * ROW_GAP + 4;

    // ---- 补给（tab 3）----
    private static final int SUPPLY_ROWS = 5;
    private static final int SEC_SUPPLY_Y = BODY_Y;
    private static final int SEC_SUPPLY_H = ROW_FIRST + SUPPLY_ROWS * ROW_GAP + 4;

    // ---- 更多（tab 4）----
    private static final int CROP_ROWS = 1;
    private static final int SEC_CROP_Y = BODY_Y;
    private static final int SEC_CROP_H = ROW_FIRST + CROP_ROWS * ROW_GAP + 4;
    private static final int MORE_ROWS = 2;
    private static final int SEC_MORE_Y = SEC_CROP_Y + SEC_CROP_H + SECTION_GAP;
    private static final int SEC_MORE_H = ROW_FIRST + MORE_ROWS * ROW_GAP + 4;

    private static final int AXIS_LABEL_W = 14;
    private static final int ICON_SIZE = ROW_H;
    /** 「可视化」分区标题右边那句提示的横坐标偏移 */
    private static final int DISPLAY_HINT_X = 62;

    private final AutoFarmConfig draft;

    private int currentTab = TAB_HARVEST;
    private final Button[] tabButtons = new Button[TAB_COUNT];

    private Button enabledButton;
    private LabeledIntSlider radiusSlider;
    private LabeledIntSlider scanIntervalSlider;
    private Button replantButton;
    private Button refillButton;
    private Button depositButton;
    private Button aimModeButton;
    private Button optimizeButton;
    private Button showPathButton;
    private Button lockSprintButton;
    private Button autoEatButton;
    private Button eatFullButton;
    private Button allowMineButton;
    private Button autoToolButton;
    private Button allowPlaceButton;
    private Button visualizeButton;
    private Button throughWallsButton;
    private Button freecamButton;
    private Button showHudButton;
    private Button lowHealthButton;
    private Button mobsNearButton;
    private Button fillEmptyButton;
    private Button cropFilterButton;
    private Button extrasButton;
    private Button statsButton;
    private Button defaultSeedButton;
    private Button storagePolicyButton;
    private Button containersButton;
    private PaletteIconButton paletteButton;

    private EditBox fieldX;
    private EditBox fieldY;
    private EditBox fieldZ;
    private Button recordButton;
    private Button clearButton;

    /** 三个坐标轴标签的 x 坐标（init 时算好） */
    private final int[] axisLabelX = new int[3];

    public AutoFarmScreen(Screen parent) {
        this(parent, ConfigManager.get().copy());
    }

    /** 换页时用的构造：把同一份草稿带过去，未保存的改动不会丢 */
    private AutoFarmScreen(Screen parent, AutoFarmConfig draft) {
        super(parent, Component.translatable("autofarm.title"));
        this.draft = draft;
    }

    @Override
    protected int contentHeight() {
        return switch (currentTab) {
            case TAB_MOVE -> SEC_MOVE_Y + SEC_MOVE_H + 8;
            case TAB_DISPLAY -> SEC_SAFETY_Y + SEC_SAFETY_H + 8;
            case TAB_SUPPLY -> SEC_SUPPLY_Y + SEC_SUPPLY_H + 8;
            case TAB_MORE -> SEC_MORE_Y + SEC_MORE_H + 8;
            default -> SEC_HARVEST_Y + SEC_HARVEST_H + 8;
        };
    }

    // ================= 建控件 =================

    @Override
    protected void buildContent() {
        buildTabBar();
        switch (currentTab) {
            case TAB_MOVE -> buildMoveTab();
            case TAB_DISPLAY -> buildDisplayTab();
            case TAB_SUPPLY -> buildSupplyTab();
            case TAB_MORE -> buildMoreTab();
            default -> buildHarvestTab();
        }

        int fullW = contentWidth;
        int halfW = (fullW - 4) / 2;
        int halfRightX = contentX + halfW + 4;
        addFooter(
                Button.builder(Component.translatable("autofarm.button.save"), b -> onSave())
                        .bounds(contentX, 0, halfW, ROW_H).build(),
                Button.builder(Component.translatable("autofarm.button.cancel"), b -> onCancel())
                        .bounds(halfRightX, 0, halfW, ROW_H).build()
        );
    }

    /** 顶部那条大类 tab 栏 */
    private void buildTabBar() {
        int tabW = (contentWidth - (TAB_COUNT - 1) * 2) / TAB_COUNT;
        for (int i = 0; i < TAB_COUNT; i++) {
            final int tab = i;
            Button btn = Button.builder(tabLabel(tab), b -> switchTab(tab))
                    .bounds(contentX + i * (tabW + 2), 0, tabW, ROW_H).build();
            addScrolling(btn);
            tabButtons[i] = btn;
        }
    }

    /** 换一页。草稿原样带过去（先把输入框里的坐标收回草稿，免得切页丢掉） */
    private void switchTab(int tab) {
        if (tab == currentTab) {
            return;
        }
        captureFields();
        if (this.minecraft != null) {
            AutoFarmScreen next = new AutoFarmScreen(this.parent, draft);
            next.currentTab = tab;
            this.minecraft.setScreen(next);
        }
    }

    private static Component tabLabel(int tab) {
        return Component.translatable("autofarm.tab." + switch (tab) {
            case TAB_MOVE -> "move";
            case TAB_DISPLAY -> "display";
            case TAB_SUPPLY -> "supply";
            case TAB_MORE -> "more";
            default -> "harvest";
        });
    }

    // ---- 各 tab 的控件创建 ----

    private void buildHarvestTab() {
        int fullW = contentWidth;

        this.enabledButton = toggle("autofarm.option.enabled", draft.enabled,
                v -> draft.enabled = v, "autofarm.enabled.tip");

        this.radiusSlider = new LabeledIntSlider(contentX, 0, fullW, ROW_H,
                "autofarm.option.radius", 4, 48, draft.scanRadius,
                v -> draft.scanRadius = v);
        addScrolling(radiusSlider);

        // 扫描间隔用 tick 存，但显示成秒 —— 对玩家来说「每 1.00 秒扫一次」比「20 tick」直观
        this.scanIntervalSlider = new LabeledIntSlider(contentX, 0, fullW, ROW_H,
                "autofarm.option.scan_interval", 2, 200, draft.scanIntervalTicks,
                AutoFarmScreen::formatSeconds, v -> draft.scanIntervalTicks = v);
        scanIntervalSlider.setTooltip(Tooltip.create(Component.translatable("autofarm.scan_interval.tip")));
        addScrolling(scanIntervalSlider);

        this.replantButton = toggle("autofarm.option.replant", draft.replant,
                v -> draft.replant = v, null);

        // 补种空耕地：除了「刚收掉的那一格」，把附近空着的耕地也种上。会消耗种子
        this.fillEmptyButton = toggle("autofarm.option.fill_empty", draft.fillEmptyFarmland,
                v -> draft.fillEmptyFarmland = v, "autofarm.fill_empty.tip");

        this.refillButton = toggle("autofarm.option.refill", draft.refillSeeds,
                v -> draft.refillSeeds = v, "autofarm.refill.tip");

        this.depositButton = toggle("autofarm.option.deposit", draft.depositWhenFull,
                v -> draft.depositWhenFull = v, "autofarm.deposit.tip");

        // 瞄准模式：精准 / 效率 / 极效率 三档循环。切到「极效率」要先过风险确认屏。
        this.aimModeButton = Button.builder(aimModeLabel(), b -> onAimModeClicked())
                .bounds(contentX, 0, fullW, ROW_H).build();
        aimModeButton.setTooltip(Tooltip.create(Component.translatable("autofarm.aim_mode.tip")));
        addScrolling(aimModeButton);
    }

    private void buildMoveTab() {
        this.optimizeButton = toggle("autofarm.option.optimize", draft.optimizePath,
                v -> draft.optimizePath = v, "autofarm.optimize.tip");

        this.showPathButton = toggle("autofarm.option.show_path", draft.showPath,
                v -> draft.showPath = v, "autofarm.show_path.tip");

        // 锁定疾跑：赶路时替玩家按住 Ctrl。只加快移动，不改挖掘速度，代价是饿得快。
        this.lockSprintButton = toggle("autofarm.option.lock_sprint", draft.lockSprint,
                v -> draft.lockSprint = v, "autofarm.lock_sprint.tip");

        this.autoEatButton = toggle("autofarm.option.auto_eat", draft.autoEat,
                v -> draft.autoEat = v, "autofarm.auto_eat.tip");

        this.eatFullButton = toggle("autofarm.option.eat_full", draft.eatUntilFull,
                v -> draft.eatUntilFull = v, "autofarm.eat_full.tip");

        // 挖 / 垫：打开后寻路可以把挡路的方块挖穿、在脚下垫方块铺路。默认都是关的
        this.allowMineButton = toggle("autofarm.option.allow_mine", draft.allowMining,
                v -> draft.allowMining = v, "autofarm.allow_mine.tip");

        this.autoToolButton = toggle("autofarm.option.auto_tool", draft.autoSelectTool,
                v -> draft.autoSelectTool = v, "autofarm.auto_tool.tip");

        this.allowPlaceButton = toggle("autofarm.option.allow_place", draft.allowPlacing,
                v -> draft.allowPlacing = v, "autofarm.allow_place.tip");
    }

    private void buildDisplayTab() {
        int fullW = contentWidth;
        int subW = fullW - ICON_SIZE - 4;

        // 可视化：左边开关，右边留一个调色板图标进次级面板
        this.visualizeButton = toggle("autofarm.option.visualize", draft.visualize,
                v -> draft.visualize = v, "autofarm.display.hint");
        visualizeButton.setWidth(subW);

        // 颜色是现取 draft 的，所以在次级面板里改完回来，图标颜色立刻就变了
        this.paletteButton = new PaletteIconButton(
                contentX + fullW - ICON_SIZE, 0, ICON_SIZE,
                () -> draft.highlightColor,
                b -> openHighlightStyle());
        addScrolling(paletteButton);

        // 穿透显示：描边无视方块遮挡，隔着墙也能看见成熟作物
        this.throughWallsButton = toggle("autofarm.option.through_walls",
                draft.highlightThroughWalls, v -> draft.highlightThroughWalls = v,
                "autofarm.through_walls.tip");

        // 自由视角（masa 全家桶的「灵魂出窍」）兼容：开着的时候先停手
        this.freecamButton = toggle("autofarm.option.freecam_pause", draft.pauseOnFreecam,
                v -> draft.pauseOnFreecam = v, "autofarm.freecam_pause.tip");

        // 游戏内 HUD：不开面板也能知道它在干嘛
        this.showHudButton = toggle("autofarm.option.show_hud", draft.showHud,
                v -> draft.showHud = v, "autofarm.show_hud.tip");

        // 低血量停机：0 = 关，其余是「心」数。循环按钮，不用滑块
        this.lowHealthButton = Button.builder(healthLabel(draft.lowHealthStopHearts), b -> {
            draft.lowHealthStopHearts = nextHealthStop(draft.lowHealthStopHearts);
            b.setMessage(healthLabel(draft.lowHealthStopHearts));
        }).bounds(contentX, 0, fullW, ROW_H).build();
        lowHealthButton.setTooltip(Tooltip.create(Component.translatable("autofarm.low_health.tip")));
        addScrolling(lowHealthButton);

        this.mobsNearButton = toggle("autofarm.option.mobs_near", draft.pauseWhenMobsNear,
                v -> draft.pauseWhenMobsNear = v, "autofarm.mobs_near.tip");
    }

    private void buildSupplyTab() {
        int fullW = contentWidth;
        int halfW = (fullW - 4) / 2;
        int halfRightX = contentX + halfW + 4;

        // 默认种子槽位：补种时优先用这一格
        this.defaultSeedButton = Button.builder(slotLabel(draft.defaultSeedSlot), b -> {
            draft.defaultSeedSlot = draft.defaultSeedSlot >= 9 ? 1 : draft.defaultSeedSlot + 1;
            b.setMessage(slotLabel(draft.defaultSeedSlot));
        }).bounds(contentX, 0, fullW, ROW_H).build();
        defaultSeedButton.setTooltip(Tooltip.create(Component.translatable("autofarm.default_seed.tip")));
        addScrolling(defaultSeedButton);

        // 存放策略：进次级面板逐类设置「不存储 / 完全存储 / 保留 n 组」
        this.storagePolicyButton = Button.builder(
                Component.translatable("autofarm.option.storage_policy"), b -> openStoragePolicy())
                .bounds(contentX, 0, fullW, ROW_H).build();
        storagePolicyButton.setTooltip(Tooltip.create(Component.translatable("autofarm.storage.tip")));
        addScrolling(storagePolicyButton);

        // 扩展存储与补给：四个专用容器（输出 / 种子 / 工具 / 杂物）各自的坐标与开关
        this.containersButton = Button.builder(
                Component.translatable("autofarm.option.containers"), b -> openContainers())
                .bounds(contentX, 0, fullW, ROW_H).build();
        containersButton.setTooltip(Tooltip.create(Component.translatable("autofarm.container.tip")));
        addScrolling(containersButton);

        // 补给点坐标：X / Y / Z 三格 + 记录 / 清除
        int colW = Math.max(56, (fullW - 12) / 3);
        for (int i = 0; i < 3; i++) {
            axisLabelX[i] = contentX + i * (colW + 6);
        }
        int fieldW = colW - AXIS_LABEL_W;
        this.fieldX = coordField(axisLabelX[0] + AXIS_LABEL_W, fieldW, draft.supplyX);
        this.fieldY = coordField(axisLabelX[1] + AXIS_LABEL_W, fieldW, draft.supplyY);
        this.fieldZ = coordField(axisLabelX[2] + AXIS_LABEL_W, fieldW, draft.supplyZ);
        addScrolling(fieldX);
        addScrolling(fieldY);
        addScrolling(fieldZ);

        this.recordButton = Button.builder(Component.translatable("autofarm.supply.record"), b -> {
            if (this.minecraft != null && this.minecraft.player != null) {
                BlockPos p = this.minecraft.player.blockPosition();
                fieldX.setValue(Integer.toString(p.getX()));
                fieldY.setValue(Integer.toString(p.getY()));
                fieldZ.setValue(Integer.toString(p.getZ()));
            }
        }).bounds(contentX, 0, halfW, ROW_H).build();
        addScrolling(recordButton);

        this.clearButton = Button.builder(Component.translatable("autofarm.supply.clear"), b -> {
            fieldX.setValue("");
            fieldY.setValue("");
            fieldZ.setValue("");
        }).bounds(halfRightX, 0, halfW, ROW_H).build();
        addScrolling(clearButton);
    }

    private void buildMoreTab() {
        int fullW = contentWidth;

        // 作物筛选：进次级面板挑「哪些作物要收」以及「空地补种哪一种」
        this.cropFilterButton = Button.builder(
                Component.translatable("autofarm.option.crop_filter"), b -> openCropFilter())
                .bounds(contentX, 0, fullW, ROW_H).build();
        cropFilterButton.setTooltip(Tooltip.create(Component.translatable("autofarm.crop.tip")));
        addScrolling(cropFilterButton);

        // 附加功能：默认全关的增强项，单独一页
        this.extrasButton = Button.builder(
                Component.translatable("autofarm.option.extras"), b -> openExtras())
                .bounds(contentX, 0, fullW, ROW_H).build();
        extrasButton.setTooltip(Tooltip.create(Component.translatable("autofarm.extras.tip")));
        addScrolling(extrasButton);

        // 统计面板（E 组）：不是改配置，只是看本轮和累计的账本
        this.statsButton = Button.builder(
                Component.translatable("autofarm.option.stats"), b -> openStats())
                .bounds(contentX, 0, fullW, ROW_H).build();
        statsButton.setTooltip(Tooltip.create(Component.translatable("autofarm.stats.tip")));
        addScrolling(statsButton);
    }

    /** 一个开关按钮的样板：点一下翻转，标签跟着变（开着显示绿色） */
    private Button toggle(String optionKey, boolean value,
                                java.util.function.Consumer<Boolean> setter, String tipKey) {
        boolean[] state = {value};
        Button button = Button.builder(toggleLabel(optionKey, state[0]), b -> {
            state[0] = !state[0];
            setter.accept(state[0]);
            b.setMessage(toggleLabel(optionKey, state[0]));
        }).bounds(contentX, 0, contentWidth, ROW_H).build();
        if (tipKey != null) {
            button.setTooltip(Tooltip.create(Component.translatable(tipKey)));
        }
        addScrolling(button);
        return button;
    }

    @Override
    protected void positionContent(int originY) {
        // tab 栏
        for (int i = 0; i < TAB_COUNT; i++) {
            setRowY(tabButtons[i], originY + 2);
        }

        switch (currentTab) {
            case TAB_MOVE -> positionMove(originY);
            case TAB_DISPLAY -> positionDisplay(originY);
            case TAB_SUPPLY -> positionSupply(originY);
            case TAB_MORE -> positionMore(originY);
            default -> positionHarvest(originY);
        }
    }

    private void positionHarvest(int originY) {
        int y = originY + SEC_HARVEST_Y;
        setRowY(enabledButton, y + ROW_FIRST);
        setRowY(radiusSlider, y + ROW_FIRST + ROW_GAP);
        setRowY(scanIntervalSlider, y + ROW_FIRST + 2 * ROW_GAP);
        setRowY(replantButton, y + ROW_FIRST + 3 * ROW_GAP);
        setRowY(fillEmptyButton, y + ROW_FIRST + 4 * ROW_GAP);
        setRowY(refillButton, y + ROW_FIRST + 5 * ROW_GAP);
        setRowY(depositButton, y + ROW_FIRST + 6 * ROW_GAP);
        setRowY(aimModeButton, y + ROW_FIRST + 7 * ROW_GAP);
    }

    private void positionMove(int originY) {
        int y = originY + SEC_MOVE_Y;
        setRowY(optimizeButton, y + ROW_FIRST);
        setRowY(showPathButton, y + ROW_FIRST + ROW_GAP);
        setRowY(lockSprintButton, y + ROW_FIRST + 2 * ROW_GAP);
        setRowY(autoEatButton, y + ROW_FIRST + 3 * ROW_GAP);
        setRowY(eatFullButton, y + ROW_FIRST + 4 * ROW_GAP);
        setRowY(allowMineButton, y + ROW_FIRST + 5 * ROW_GAP);
        setRowY(autoToolButton, y + ROW_FIRST + 6 * ROW_GAP);
        setRowY(allowPlaceButton, y + ROW_FIRST + 7 * ROW_GAP);
    }

    private void positionDisplay(int originY) {
        int d = originY + SEC_DISPLAY_Y;
        setRowY(visualizeButton, d + ROW_FIRST);
        setRowY(paletteButton, d + ROW_FIRST);
        setRowY(throughWallsButton, d + ROW_FIRST + ROW_GAP);
        setRowY(freecamButton, d + ROW_FIRST + 2 * ROW_GAP);

        int s = originY + SEC_SAFETY_Y;
        setRowY(showHudButton, s + ROW_FIRST);
        setRowY(lowHealthButton, s + ROW_FIRST + ROW_GAP);
        setRowY(mobsNearButton, s + ROW_FIRST + 2 * ROW_GAP);
    }

    private void positionSupply(int originY) {
        int y = originY + SEC_SUPPLY_Y;
        setRowY(defaultSeedButton, y + ROW_FIRST);
        setRowY(storagePolicyButton, y + ROW_FIRST + ROW_GAP);
        setRowY(containersButton, y + ROW_FIRST + 2 * ROW_GAP);
        setRowY(fieldX, y + ROW_FIRST + 3 * ROW_GAP);
        setRowY(fieldY, y + ROW_FIRST + 3 * ROW_GAP);
        setRowY(fieldZ, y + ROW_FIRST + 3 * ROW_GAP);
        setRowY(recordButton, y + ROW_FIRST + 4 * ROW_GAP);
        setRowY(clearButton, y + ROW_FIRST + 4 * ROW_GAP);
    }

    private void positionMore(int originY) {
        int c = originY + SEC_CROP_Y;
        setRowY(cropFilterButton, c + ROW_FIRST);

        int m = originY + SEC_MORE_Y;
        setRowY(extrasButton, m + ROW_FIRST);
        setRowY(statsButton, m + ROW_FIRST + ROW_GAP);
    }

    private EditBox coordField(int x, int width, Integer value) {
        EditBox field = new EditBox(this.font, x, 0, width, ROW_H, Component.empty());
        field.setMaxLength(7);
        field.setValue(value == null ? "" : Integer.toString(value));
        return field;
    }

    // ================= 自绘 =================

    @Override
    protected void renderContent(GuiGraphics context, int originY, int mouseX, int mouseY, float delta) {
        // tab 栏底下的分隔线 + 当前 tab 高亮
        context.fill(contentX, originY + TAB_BAR_H, contentX + contentWidth, originY + TAB_BAR_H + 1, 0x30FFFFFF);
        drawTabMarker(context, tabButtons, currentTab);

        drawStatus(context, originY);

        switch (currentTab) {
            case TAB_MOVE -> {
                drawSection(context, originY, SEC_MOVE_Y, SEC_MOVE_H);
                drawHeader(context, originY, SEC_MOVE_Y, "autofarm.section.path");
                context.drawString(this.font,
                        Component.translatable("autofarm.path.hotbar", FarmTaskManager.getHotbarBlockCount()),
                        contentX + 78, originY + SEC_MOVE_Y + 4, COLOR_HINT);
            }
            case TAB_DISPLAY -> {
                drawSection(context, originY, SEC_DISPLAY_Y, SEC_DISPLAY_H);
                drawHeader(context, originY, SEC_DISPLAY_Y, "autofarm.section.display");
                context.drawString(this.font,
                        Component.translatable("autofarm.section.display.hint"),
                        contentX + DISPLAY_HINT_X, originY + SEC_DISPLAY_Y + 4, COLOR_HINT);

                drawSection(context, originY, SEC_SAFETY_Y, SEC_SAFETY_H);
                drawHeader(context, originY, SEC_SAFETY_Y, "autofarm.section.safety");
                context.drawString(this.font,
                        Component.translatable("autofarm.section.safety.hint"),
                        contentX + 62, originY + SEC_SAFETY_Y + 4, COLOR_HINT);
            }
            case TAB_SUPPLY -> drawSupply(context, originY);
            case TAB_MORE -> {
                drawSection(context, originY, SEC_CROP_Y, SEC_CROP_H);
                drawHeader(context, originY, SEC_CROP_Y, "autofarm.section.crop");
                // 一行摘要：现在是哪种筛选、附近有几格空地
                Component summary = Component.translatable("autofarm.crop.summary",
                        Component.translatable(cropModeKey(draft.cropFilterMode)),
                        Component.translatable(draft.fillEmptyFarmland ? "autofarm.on" : "autofarm.off"),
                        FarmTaskManager.getFarmlandCount());
                context.drawString(this.font, summary,
                        contentX + 62, originY + SEC_CROP_Y + 4,
                        draft.fillEmptyFarmland ? COLOR_TEXT : COLOR_HINT);

                drawSection(context, originY, SEC_MORE_Y, SEC_MORE_H);
                drawHeader(context, originY, SEC_MORE_Y, "autofarm.section.extras");
                // 一行摘要：现在开了几项附加功能
                int enabledExtras = draft.extrasEnabledCount();
                context.drawString(this.font,
                        Component.translatable("autofarm.extras.summary", enabledExtras),
                        contentX + 62, originY + SEC_MORE_Y + 4,
                        enabledExtras > 0 ? COLOR_NOTICE : COLOR_HINT);
            }
            default -> {
                drawSection(context, originY, SEC_HARVEST_Y, SEC_HARVEST_H);
                drawHeader(context, originY, SEC_HARVEST_Y, "autofarm.section.harvest");
            }
        }
    }

    /** 每一页顶部那份「此刻在干嘛」的快照 */
    private void drawStatus(GuiGraphics context, int originY) {
        drawSection(context, originY, SEC_STATUS_Y, SEC_STATUS_H);
        drawHeader(context, originY, SEC_STATUS_Y, "autofarm.section.status");
        context.drawString(this.font,
                Component.translatable("autofarm.status.current",
                        Component.translatable(FarmTaskManager.getState().getTranslationKey())),
                contentX + 6, originY + SEC_STATUS_Y + 15, COLOR_TEXT);
        context.drawString(this.font,
                Component.translatable("autofarm.status.mature", FarmTaskManager.getMatureCount()),
                contentX + 6, originY + SEC_STATUS_Y + 26, COLOR_TEXT);
        context.drawString(this.font, routeText(),
                contentX + 6, originY + SEC_STATUS_Y + 37,
                FarmTaskManager.isWalking() ? COLOR_TEXT : COLOR_HINT);
        context.drawString(this.font, harvestText(),
                contentX + 6, originY + SEC_STATUS_Y + 48,
                FarmTaskManager.getHarvestNotice() != null ? COLOR_NOTICE : COLOR_HINT);
    }

    /** 「补给」页那段自绘：背包空格数 + 识别到的容器 + 三个坐标轴标签 */
    private void drawSupply(GuiGraphics context, int originY) {
        drawSection(context, originY, SEC_SUPPLY_Y, SEC_SUPPLY_H);
        drawHeader(context, originY, SEC_SUPPLY_Y, "autofarm.section.supply");

        // 背包空格数：快满的时候会变暖色，这样你就知道它为什么突然调头去补给点了
        int free = freeSlots();
        context.drawString(this.font,
                Component.translatable("autofarm.supply.free_slots", free),
                contentX + 62, originY + SEC_SUPPLY_Y + 4,
                free <= 1 ? COLOR_NOTICE : COLOR_HINT);

        // 识别到的存储方块（判定是「附近有没有挂 Inventory 的方块实体」）
        AutoFarmConfig preview = previewSupply();
        BlockPos found = findSupplyContainer(preview);
        Component supplyText;
        int supplyColor;
        if (!preview.hasSupplyPoint()) {
            supplyText = Component.translatable("autofarm.supply.none");
            supplyColor = COLOR_HINT;
        } else if (found == null) {
            supplyText = Component.translatable("autofarm.supply.not_container");
            supplyColor = COLOR_NOTICE;
        } else {
            supplyText = Component.translatable("autofarm.supply.found",
                    found.getX() + ", " + found.getY() + ", " + found.getZ());
            supplyColor = COLOR_TEXT;
        }
        int fieldRow = originY + SEC_SUPPLY_Y + ROW_FIRST + 3 * ROW_GAP;
        context.drawString(this.font, supplyText, contentX + 6, fieldRow - 13, supplyColor);

        String[] axes = {"X", "Y", "Z"};
        for (int i = 0; i < axes.length; i++) {
            context.drawString(this.font, axes[i],
                    axisLabelX[i], fieldRow + 5, COLOR_HINT);
        }
    }

    /** 筛选模式的显示名（和「作物」次级面板用同一套键） */
    private static String cropModeKey(int mode) {
        return switch (mode) {
            case AutoFarmConfig.FILTER_WHITELIST -> "autofarm.crop.mode.whitelist";
            case AutoFarmConfig.FILTER_BLACKLIST -> "autofarm.crop.mode.blacklist";
            case AutoFarmConfig.FILTER_ONLY_REPLANTABLE -> "autofarm.crop.mode.only_replantable";
            default -> "autofarm.crop.mode.off";
        };
    }

    /** 输入框里的坐标附近认到的容器；没设坐标、或者附近真没有容器时返回 null */
    private BlockPos findSupplyContainer(AutoFarmConfig preview) {
        if (this.minecraft == null || this.minecraft.level == null || !preview.hasSupplyPoint()) {
            return null;
        }
        return ContainerCompat.findContainer(this.minecraft.level,
                new BlockPos(preview.supplyX, preview.supplyY, preview.supplyZ));
    }

    /** 背包还剩几个空格 —— 补给点那一栏会显示它，好知道它为什么突然跑去存东西 */
    private int freeSlots() {
        return this.minecraft == null || this.minecraft.player == null
                ? 0
                : InventoryScanner.countFreeSlots(this.minecraft.player);
    }

    /** 状态区第三行：当前这条路线还剩多少活要干 */
    private static Component routeText() {
        if (!FarmTaskManager.isWalking()) {
            return Component.translatable("autofarm.status.route.none");
        }
        return Component.translatable("autofarm.status.route",
                FarmTaskManager.getRouteRemaining(),
                String.format(Locale.ROOT, "%.1f", FarmTaskManager.getRemainingDistance()));
    }

    /** 状态区第四行：上一棵作物的结果，或者「为什么跳过它」 */
    private static Component harvestText() {
        String notice = FarmTaskManager.getHarvestNotice();
        if (notice != null) {
            return Component.translatable(notice);
        }
        if (CropHarvester.wasPicked()) {
            return Component.translatable("autofarm.status.picked");
        }
        if (CropHarvester.wasPlanted()) {
            return Component.translatable("autofarm.status.planted");
        }
        if (CropHarvester.wasHarvested()) {
            return Component.translatable(CropHarvester.wasReplanted()
                    ? "autofarm.status.harvest.done"
                    : "autofarm.status.harvest.done_noreplant");
        }
        return Component.translatable("autofarm.status.harvest.none");
    }

    /** 面板上方那行补给点状态，跟着输入框实时变，不必等保存 */
    private AutoFarmConfig previewSupply() {
        AutoFarmConfig preview = new AutoFarmConfig();
        preview.supplyX = parseOrNull(fieldX);
        preview.supplyY = parseOrNull(fieldY);
        preview.supplyZ = parseOrNull(fieldZ);
        return preview;
    }

    // ================= 行为 =================

    /** 把三个坐标输入框的内容收回草稿。不在「补给」页时输入框不存在，此时什么都不做 */
    private void captureFields() {
        if (fieldX == null) {
            return;
        }
        draft.supplyX = parseOrNull(fieldX);
        draft.supplyY = parseOrNull(fieldY);
        draft.supplyZ = parseOrNull(fieldZ);
    }

    /** 进「存放策略」次级面板。它编辑的是同一个 draft，所以回来时这里不用再同步 */
    private void openStoragePolicy() {
        captureFields();
        if (this.minecraft != null) {
            this.minecraft.setScreen(new StoragePolicyScreen(this, draft));
        }
    }

    /** 进「扩展存储与补给」次级面板：四个专用容器的坐标与开关 */
    private void openContainers() {
        captureFields();
        if (this.minecraft != null) {
            this.minecraft.setScreen(new ContainerConfigScreen(this, draft));
        }
    }

    /** 进「作物」次级面板。它编辑的是同一个 draft，所以回来时这里不用再同步 */
    private void openCropFilter() {
        captureFields();
        if (this.minecraft != null) {
            this.minecraft.setScreen(new CropFilterScreen(this, draft));
        }
    }

    /** 进「高亮样式」次级面板。它编辑的是同一个 draft，所以回来时这里不用再同步 */
    private void openHighlightStyle() {
        captureFields();
        if (this.minecraft != null) {
            this.minecraft.setScreen(new HighlightStyleScreen(this, draft));
        }
    }

    /** 进「附加功能」次级面板。它编辑的是同一个 draft，回来时这里不用再同步 */
    private void openExtras() {
        captureFields();
        if (this.minecraft != null) {
            this.minecraft.setScreen(new ExtrasScreen(this, draft));
        }
    }

    /** 进「统计」面板。它不编辑 draft —— 只是看本轮和累计的账本 */
    private void openStats() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(new StatsScreen(this));
        }
    }

    // ================= 瞄准模式 =================

    /** 循环下一档瞄准模式；切到「极效率」先弹风险确认屏 */
    private void onAimModeClicked() {
        int next = (draft.aimMode + 1) % AutoFarmConfig.AIM_MODE_COUNT;
        if (next == AutoFarmConfig.AIM_EXTREME) {
            confirmAimExtreme(next);
            return;
        }
        draft.aimMode = next;
        aimModeButton.setMessage(aimModeLabel());
    }

    /** 极效率模式 = 不管准星直接挖，是最容易被服务器判作弊的一档，弹一次风险确认 */
    private void confirmAimExtreme(int next) {
        if (this.minecraft == null) {
            return;
        }
        this.minecraft.setScreen(new DangerConfirmScreen(this, () -> {
            draft.aimMode = next;
            if (aimModeButton != null) {
                aimModeButton.setMessage(aimModeLabel());
            }
        }, "autofarm.danger.title", "autofarm.danger.aim_extreme.subtitle",
                "autofarm.danger.aim_extreme.body1", "autofarm.danger.aim_extreme.body2"));
    }

    private Component aimModeLabel() {
        // 极效率是风险档：文字标红，和非风险的两档区分开
        MutableComponent value = Component.translatable("autofarm.aim_mode." + aimModeKey(draft.aimMode));
        if (draft.aimMode == AutoFarmConfig.AIM_EXTREME) {
            value = value.withStyle(ChatFormatting.RED);
        }
        return Component.translatable("autofarm.toggle",
                Component.translatable("autofarm.option.aim_mode"), value);
    }

    private static String aimModeKey(int mode) {
        return switch (mode) {
            case AutoFarmConfig.AIM_EFFICIENT -> "efficient";
            case AutoFarmConfig.AIM_EXTREME -> "extreme";
            default -> "normal";
        };
    }

    private void onSave() {
        captureFields();
        ConfigManager.save(draft);
        toast("autofarm.message.saved");
        backToParent();
    }

    private static Integer parseOrNull(EditBox field) {
        if (field == null) {
            return null;
        }
        String raw = field.getValue();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 把 tick 换算成秒来显示：正好整秒就不带小数，否则留两位 */
    private static String formatSeconds(int ticks) {
        double seconds = ticks / 20.0;
        String number = ticks % 20 == 0
                ? String.format(Locale.ROOT, "%.0f", seconds)
                : String.format(Locale.ROOT, "%.2f", seconds);
        return number + " s";
    }

    /** 「默认种子槽位：第 9 格」 */
    private static Component slotLabel(int slot) {
        return Component.translatable("autofarm.toggle",
                Component.translatable("autofarm.option.default_seed"),
                Component.translatable("autofarm.value.slot", slot));
    }

    /**
     * 「低血量停机：4 心」。
     *
     * <p>用「心」而不是血量点来显示 —— 血条上画的就是心，玩家脑子里数的也是心。
     */
    private static Component healthLabel(int hearts) {
        return Component.translatable("autofarm.toggle",
                Component.translatable("autofarm.option.low_health"),
                hearts <= 0
                        ? Component.translatable("autofarm.value.off")
                        : Component.translatable("autofarm.value.hearts", hearts));
    }

    /**
     * 低血量门槛的下一个档位：关闭 → 3 心 → 4 心 → 5 心 → 6 心 → 关闭。
     *
     * <p>从「关闭」一步就是 3 心（最灵敏），然后往上走；到头再回到关闭。
     */
    private static int nextHealthStop(int current) {
        return switch (current) {
            case 0 -> 3;
            case 3 -> 4;
            case 4 -> 5;
            case 5 -> 6;
            default -> 0;
        };
    }
}
