package com.autofarm.client.gui;

import com.autofarm.client.config.AutoFarmConfig;
import com.autofarm.client.config.ConfigManager;
import com.autofarm.client.container.ContainerCompat;
import com.autofarm.client.harvest.CropHarvester;
import com.autofarm.client.inventory.InventoryScanner;
import com.autofarm.client.task.FarmTaskManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Locale;

/**
 * AutoFarm 的主控制面板。
 *
 * <p>版面三段式（由 {@link ScrollPanelScreen} 提供）：固定标题 / 可滚动内容 / 固定页脚。
 *
 * <p>所有改动先落在 draft（草稿）上，点「保存并关闭」才写回配置文件，
 * 点「取消」或按 Esc 直接丢弃。右侧那个小调色板图标会进一个次级面板去改高亮外观，
 * 它编辑的是同一个 draft，所以回到本界面时不会互相覆盖。
 */
public class AutoFarmScreen extends ScrollPanelScreen {

    // ---------------- 内容区布局（相对内容区顶部） ----------------
    private static final int SEC_STATUS_Y = 0, SEC_STATUS_H = 62;
    private static final int SEC_HARVEST_Y = 66, SEC_HARVEST_H = 136;
    private static final int SEC_PATH_Y = 206, SEC_PATH_H = 180;
    private static final int SEC_DISPLAY_Y = 390, SEC_DISPLAY_H = 76;
    private static final int SEC_SUPPLY_Y = 470, SEC_SUPPLY_H = 118;
    /** 安全与提示：HUD、低血量停机、敌对生物停机 —— 三项都追加在最后，前面的分区不用挪 */
    private static final int SEC_SAFETY_Y = 592, SEC_SAFETY_H = 76;
    /** 作物与筛选：补种空耕地 + 进次级面板挑作物。同样追加在最后 */
    private static final int SEC_CROP_Y = 674, SEC_CROP_H = 64;
    /** 附加功能：默认全关的增强项，单独一页。统计面板也放这里，追加在最后 */
    private static final int SEC_EXTRAS_Y = 742, SEC_EXTRAS_H = 64;

    private static final int ROW_ENABLED = 82;
    private static final int ROW_RADIUS = 102;
    private static final int ROW_SCAN_INTERVAL = 122;
    private static final int ROW_REPLANT = 142;
    private static final int ROW_REFILL = 162;
    private static final int ROW_DEPOSIT = 182;
    private static final int ROW_OPTIMIZE = 222;
    private static final int ROW_SHOW_PATH = 242;
    private static final int ROW_LOCK_SPRINT = 262;
    private static final int ROW_AUTO_EAT = 282;
    private static final int ROW_EAT_FULL = 302;
    private static final int ROW_ALLOW_MINE = 322;
    private static final int ROW_AUTO_TOOL = 342;
    private static final int ROW_ALLOW_PLACE = 362;
    private static final int ROW_VISUAL = 406;
    private static final int ROW_THROUGH_WALLS = 426;
    private static final int ROW_FREECAM = 446;
    /** 补给点分区：先一行状态提示，再两个选项，最后坐标输入和按钮 */
    private static final int ROW_SUPPLY_HINT = 486;
    private static final int ROW_DEFAULT_SEED = 502;
    private static final int ROW_STORAGE_POLICY = 522;
    private static final int ROW_FIELDS = 542;
    private static final int ROW_SUPPLY_BTN = 564;

    /** 安全与提示分区的三行 */
    private static final int ROW_SHOW_HUD = 608;
    private static final int ROW_LOW_HEALTH = 628;
    private static final int ROW_MOBS_NEAR = 648;

    /** 作物与筛选分区的两行 */
    private static final int ROW_FILL_EMPTY = 690;
    private static final int ROW_CROP_FILTER = 710;

    /** 附加功能分区的两行：附加功能、统计 */
    private static final int ROW_EXTRAS = 758;
    private static final int ROW_STATS = 778;

    private static final int AXIS_LABEL_W = 14;
    private static final int ICON_SIZE = ROW_H;
    /** 「可视化」分区标题右边那句提示的横坐标偏移 */
    private static final int DISPLAY_HINT_X = 62;

    private final AutoFarmConfig draft;

    private ButtonWidget enabledButton;
    private LabeledIntSlider radiusSlider;
    private LabeledIntSlider scanIntervalSlider;
    private ButtonWidget replantButton;
    private ButtonWidget refillButton;
    private ButtonWidget depositButton;
    private ButtonWidget optimizeButton;
    private ButtonWidget showPathButton;
    private ButtonWidget lockSprintButton;
    private ButtonWidget autoEatButton;
    private ButtonWidget eatFullButton;
    private ButtonWidget allowMineButton;
    private ButtonWidget autoToolButton;
    private ButtonWidget allowPlaceButton;
    private ButtonWidget visualizeButton;
    private ButtonWidget throughWallsButton;
    private ButtonWidget freecamButton;
    private ButtonWidget defaultSeedButton;
    private ButtonWidget storagePolicyButton;
    private ButtonWidget showHudButton;
    private ButtonWidget lowHealthButton;
    private ButtonWidget mobsNearButton;
    private ButtonWidget fillEmptyButton;
    private ButtonWidget cropFilterButton;
    private ButtonWidget extrasButton;
    private ButtonWidget statsButton;
    private PaletteIconButton paletteButton;

    private TextFieldWidget fieldX;
    private TextFieldWidget fieldY;
    private TextFieldWidget fieldZ;
    private ButtonWidget recordButton;
    private ButtonWidget clearButton;

    /** 三个坐标轴标签的 x 坐标（init 时算好） */
    private final int[] axisLabelX = new int[3];

    public AutoFarmScreen(Screen parent) {
        super(parent, Text.translatable("autofarm.title"));
        this.draft = ConfigManager.get().copy();
    }

    @Override
    protected int contentHeight() {
        return SEC_EXTRAS_Y + SEC_EXTRAS_H + 4;
    }

    // ================= 建控件 =================

    @Override
    protected void buildContent() {
        int fullW = contentWidth;
        int halfW = (fullW - 4) / 2;
        int halfRightX = contentX + halfW + 4;

        // ---- 收割设置 ----
        this.enabledButton = ButtonWidget.builder(toggleLabel("autofarm.option.enabled", draft.enabled), b -> {
            draft.enabled = !draft.enabled;
            b.setMessage(toggleLabel("autofarm.option.enabled", draft.enabled));
        }).dimensions(contentX, 0, fullW, ROW_H).build();
        enabledButton.setTooltip(Tooltip.of(Text.translatable("autofarm.enabled.tip")));
        addScrolling(enabledButton);

        this.radiusSlider = new LabeledIntSlider(contentX, 0, fullW, ROW_H,
                "autofarm.option.radius", 4, 48, draft.scanRadius,
                v -> draft.scanRadius = v);
        addScrolling(radiusSlider);

        // 扫描间隔用 tick 存，但显示成秒 —— 对玩家来说「每 1.00 秒扫一次」比「20 tick」直观
        this.scanIntervalSlider = new LabeledIntSlider(contentX, 0, fullW, ROW_H,
                "autofarm.option.scan_interval", 2, 200, draft.scanIntervalTicks,
                AutoFarmScreen::formatSeconds, v -> draft.scanIntervalTicks = v);
        scanIntervalSlider.setTooltip(Tooltip.of(Text.translatable("autofarm.scan_interval.tip")));
        addScrolling(scanIntervalSlider);

        this.replantButton = ButtonWidget.builder(toggleLabel("autofarm.option.replant", draft.replant), b -> {
            draft.replant = !draft.replant;
            b.setMessage(toggleLabel("autofarm.option.replant", draft.replant));
        }).dimensions(contentX, 0, fullW, ROW_H).build();
        addScrolling(replantButton);

        this.refillButton = ButtonWidget.builder(toggleLabel("autofarm.option.refill", draft.refillSeeds), b -> {
            draft.refillSeeds = !draft.refillSeeds;
            b.setMessage(toggleLabel("autofarm.option.refill", draft.refillSeeds));
        }).dimensions(contentX, 0, fullW, ROW_H).build();
        refillButton.setTooltip(Tooltip.of(Text.translatable("autofarm.refill.tip")));
        addScrolling(refillButton);

        this.depositButton = ButtonWidget.builder(toggleLabel("autofarm.option.deposit", draft.depositWhenFull), b -> {
            draft.depositWhenFull = !draft.depositWhenFull;
            b.setMessage(toggleLabel("autofarm.option.deposit", draft.depositWhenFull));
        }).dimensions(contentX, 0, fullW, ROW_H).build();
        depositButton.setTooltip(Tooltip.of(Text.translatable("autofarm.deposit.tip")));
        addScrolling(depositButton);

        // ---- 路径规划 ----
        this.optimizeButton = ButtonWidget.builder(toggleLabel("autofarm.option.optimize", draft.optimizePath), b -> {
            draft.optimizePath = !draft.optimizePath;
            b.setMessage(toggleLabel("autofarm.option.optimize", draft.optimizePath));
        }).dimensions(contentX, 0, fullW, ROW_H).build();
        optimizeButton.setTooltip(Tooltip.of(Text.translatable("autofarm.optimize.tip")));
        addScrolling(optimizeButton);

        this.showPathButton = ButtonWidget.builder(toggleLabel("autofarm.option.show_path", draft.showPath), b -> {
            draft.showPath = !draft.showPath;
            b.setMessage(toggleLabel("autofarm.option.show_path", draft.showPath));
        }).dimensions(contentX, 0, fullW, ROW_H).build();
        showPathButton.setTooltip(Tooltip.of(Text.translatable("autofarm.show_path.tip")));
        addScrolling(showPathButton);

        // 锁定疾跑：赶路时替玩家按住 Ctrl。只加快移动，不改挖掘速度，代价是饿得快。
        this.lockSprintButton = ButtonWidget.builder(toggleLabel("autofarm.option.lock_sprint", draft.lockSprint), b -> {
            draft.lockSprint = !draft.lockSprint;
            b.setMessage(toggleLabel("autofarm.option.lock_sprint", draft.lockSprint));
        }).dimensions(contentX, 0, fullW, ROW_H).build();
        lockSprintButton.setTooltip(Tooltip.of(Text.translatable("autofarm.lock_sprint.tip")));
        addScrolling(lockSprintButton);

        // 自动进食：饿了（跑不动了）就吃快捷栏里回饱食度最多的那个
        this.autoEatButton = ButtonWidget.builder(toggleLabel("autofarm.option.auto_eat", draft.autoEat), b -> {
            draft.autoEat = !draft.autoEat;
            b.setMessage(toggleLabel("autofarm.option.auto_eat", draft.autoEat));
        }).dimensions(contentX, 0, fullW, ROW_H).build();
        autoEatButton.setTooltip(Tooltip.of(Text.translatable("autofarm.auto_eat.tip")));
        addScrolling(autoEatButton);

        // 一次吃到饱：饿到停不下来的时候，干脆坐下来吃满再走，省得每半分钟被打断一次
        this.eatFullButton = ButtonWidget.builder(toggleLabel("autofarm.option.eat_full", draft.eatUntilFull), b -> {
            draft.eatUntilFull = !draft.eatUntilFull;
            b.setMessage(toggleLabel("autofarm.option.eat_full", draft.eatUntilFull));
        }).dimensions(contentX, 0, fullW, ROW_H).build();
        eatFullButton.setTooltip(Tooltip.of(Text.translatable("autofarm.eat_full.tip")));
        addScrolling(eatFullButton);

        // 挖 / 垫：打开后寻路可以把挡路的方块挖穿、在脚下垫方块铺路。
        // 默认都是关的 —— 它们会改变世界和消耗材料，得玩家自己点头。
        this.allowMineButton = ButtonWidget.builder(toggleLabel("autofarm.option.allow_mine", draft.allowMining), b -> {
            draft.allowMining = !draft.allowMining;
            b.setMessage(toggleLabel("autofarm.option.allow_mine", draft.allowMining));
        }).dimensions(contentX, 0, fullW, ROW_H).build();
        allowMineButton.setTooltip(Tooltip.of(Text.translatable("autofarm.allow_mine.tip")));
        addScrolling(allowMineButton);

        // 自动换最佳工具：挖之前先在快捷栏里挑一把最合适的，挑不到更好的就保持原样。
        // 它不改地形也不消耗东西，所以默认是开的。
        this.autoToolButton = ButtonWidget.builder(toggleLabel("autofarm.option.auto_tool", draft.autoSelectTool), b -> {
            draft.autoSelectTool = !draft.autoSelectTool;
            b.setMessage(toggleLabel("autofarm.option.auto_tool", draft.autoSelectTool));
        }).dimensions(contentX, 0, fullW, ROW_H).build();
        autoToolButton.setTooltip(Tooltip.of(Text.translatable("autofarm.auto_tool.tip")));
        addScrolling(autoToolButton);

        this.allowPlaceButton = ButtonWidget.builder(toggleLabel("autofarm.option.allow_place", draft.allowPlacing), b -> {
            draft.allowPlacing = !draft.allowPlacing;
            b.setMessage(toggleLabel("autofarm.option.allow_place", draft.allowPlacing));
        }).dimensions(contentX, 0, fullW, ROW_H).build();
        allowPlaceButton.setTooltip(Tooltip.of(Text.translatable("autofarm.allow_place.tip")));
        addScrolling(allowPlaceButton);

        // ---- 可视化：左边开关，右边留一个调色板图标进次级面板 ----
        int visW = fullW - ICON_SIZE - 4;
        this.visualizeButton = ButtonWidget.builder(toggleLabel("autofarm.option.visualize", draft.visualize), b -> {
            draft.visualize = !draft.visualize;
            b.setMessage(toggleLabel("autofarm.option.visualize", draft.visualize));
        }).dimensions(contentX, 0, visW, ROW_H).build();
        visualizeButton.setTooltip(Tooltip.of(Text.translatable("autofarm.display.hint")));
        addScrolling(visualizeButton);

        // 颜色是现取 draft 的，所以在次级面板里改完回来，图标颜色立刻就变了
        this.paletteButton = new PaletteIconButton(
                contentX + fullW - ICON_SIZE, 0, ICON_SIZE,
                () -> draft.highlightColor,
                b -> openHighlightStyle());
        addScrolling(paletteButton);

        // 穿透显示：描边无视方块遮挡，隔着墙也能看见成熟作物。
        // 开启时 FarmTaskManager 会自动把扫描间隔放宽，抵掉多出来的绘制开销。
        this.throughWallsButton = ButtonWidget.builder(
                toggleLabel("autofarm.option.through_walls", draft.highlightThroughWalls), b -> {
                    draft.highlightThroughWalls = !draft.highlightThroughWalls;
                    b.setMessage(toggleLabel("autofarm.option.through_walls", draft.highlightThroughWalls));
                }).dimensions(contentX, 0, fullW, ROW_H).build();
        throughWallsButton.setTooltip(Tooltip.of(Text.translatable("autofarm.through_walls.tip")));
        addScrolling(throughWallsButton);

        // 自由视角（masa 全家桶的「灵魂出窍」）兼容：开着的时候先停手，
        // 免得屏幕里人不动、方块却在碎
        this.freecamButton = ButtonWidget.builder(
                toggleLabel("autofarm.option.freecam_pause", draft.pauseOnFreecam), b -> {
                    draft.pauseOnFreecam = !draft.pauseOnFreecam;
                    b.setMessage(toggleLabel("autofarm.option.freecam_pause", draft.pauseOnFreecam));
                }).dimensions(contentX, 0, fullW, ROW_H).build();
        freecamButton.setTooltip(Tooltip.of(Text.translatable("autofarm.freecam_pause.tip")));
        addScrolling(freecamButton);

        // 默认种子槽位：补种时优先用这一格；从箱子里取种子而背包又没空格时，
        // 也是往这一格塞。是个 1~9 的循环按钮 —— 用滑块调「第几格」太别扭，
        // 点一下换一格反而更好按。
        this.defaultSeedButton = ButtonWidget.builder(slotLabel(draft.defaultSeedSlot), b -> {
            draft.defaultSeedSlot = draft.defaultSeedSlot >= 9 ? 1 : draft.defaultSeedSlot + 1;
            b.setMessage(slotLabel(draft.defaultSeedSlot));
        }).dimensions(contentX, 0, fullW, ROW_H).build();
        defaultSeedButton.setTooltip(Tooltip.of(Text.translatable("autofarm.default_seed.tip")));
        addScrolling(defaultSeedButton);

        // 存放策略：进一个次级面板，逐类设置「不存储 / 完全存储 / 保留 n 组」。
        // 以前这里是个「备用保留组数」循环按钮，但能调的只有胡萝卜土豆那一类；
        // 想要「甜菜根也全存掉」就没辙了，所以改成一张能逐类下手的表。
        this.storagePolicyButton = ButtonWidget.builder(
                Text.translatable("autofarm.option.storage_policy"), b -> openStoragePolicy())
                .dimensions(contentX, 0, fullW, ROW_H).build();
        storagePolicyButton.setTooltip(Tooltip.of(Text.translatable("autofarm.storage.tip")));
        addScrolling(storagePolicyButton);

        // ---- 补给点坐标 ----
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

        this.recordButton = ButtonWidget.builder(Text.translatable("autofarm.supply.record"), b -> {
            if (this.client != null && this.client.player != null) {
                BlockPos p = this.client.player.getBlockPos();
                fieldX.setText(Integer.toString(p.getX()));
                fieldY.setText(Integer.toString(p.getY()));
                fieldZ.setText(Integer.toString(p.getZ()));
            }
        }).dimensions(contentX, 0, halfW, ROW_H).build();
        addScrolling(recordButton);

        this.clearButton = ButtonWidget.builder(Text.translatable("autofarm.supply.clear"), b -> {
            fieldX.setText("");
            fieldY.setText("");
            fieldZ.setText("");
        }).dimensions(halfRightX, 0, halfW, ROW_H).build();
        addScrolling(clearButton);

        // ---- 安全与提示 ----
        // 游戏内状态显示：不开面板也能知道它在干嘛
        this.showHudButton = ButtonWidget.builder(
                toggleLabel("autofarm.option.show_hud", draft.showHud), b -> {
                    draft.showHud = !draft.showHud;
                    b.setMessage(toggleLabel("autofarm.option.show_hud", draft.showHud));
                }).dimensions(contentX, 0, fullW, ROW_H).build();
        showHudButton.setTooltip(Tooltip.of(Text.translatable("autofarm.show_hud.tip")));
        addScrolling(showHudButton);

        // 低血量停机：0 = 关，其余是「心」数。同样是循环按钮，不用滑块
        this.lowHealthButton = ButtonWidget.builder(healthLabel(draft.lowHealthStopHearts), b -> {
            draft.lowHealthStopHearts = nextHealthStop(draft.lowHealthStopHearts);
            b.setMessage(healthLabel(draft.lowHealthStopHearts));
        }).dimensions(contentX, 0, fullW, ROW_H).build();
        lowHealthButton.setTooltip(Tooltip.of(Text.translatable("autofarm.low_health.tip")));
        addScrolling(lowHealthButton);

        this.mobsNearButton = ButtonWidget.builder(
                toggleLabel("autofarm.option.mobs_near", draft.pauseWhenMobsNear), b -> {
                    draft.pauseWhenMobsNear = !draft.pauseWhenMobsNear;
                    b.setMessage(toggleLabel("autofarm.option.mobs_near", draft.pauseWhenMobsNear));
                }).dimensions(contentX, 0, fullW, ROW_H).build();
        mobsNearButton.setTooltip(Tooltip.of(Text.translatable("autofarm.mobs_near.tip")));
        addScrolling(mobsNearButton);

        // ---- 作物与筛选 ----
        // 补种空耕地：除了「刚收掉的那一格」，把附近空着的耕地也种上。
        // 它会消耗种子，所以和垫方块一样默认关着。
        this.fillEmptyButton = ButtonWidget.builder(
                toggleLabel("autofarm.option.fill_empty", draft.fillEmptyFarmland), b -> {
                    draft.fillEmptyFarmland = !draft.fillEmptyFarmland;
                    b.setMessage(toggleLabel("autofarm.option.fill_empty", draft.fillEmptyFarmland));
                }).dimensions(contentX, 0, fullW, ROW_H).build();
        fillEmptyButton.setTooltip(Tooltip.of(Text.translatable("autofarm.fill_empty.tip")));
        addScrolling(fillEmptyButton);

        // 作物筛选：进次级面板挑「哪些作物要收」以及「空地补种哪一种」
        this.cropFilterButton = ButtonWidget.builder(
                Text.translatable("autofarm.option.crop_filter"), b -> openCropFilter())
                .dimensions(contentX, 0, fullW, ROW_H).build();
        cropFilterButton.setTooltip(Tooltip.of(Text.translatable("autofarm.crop.tip")));
        addScrolling(cropFilterButton);

        // ---- 附加功能 ----
        // 默认全部关闭的增强项，单独一页。放在最后，是因为它们不是「收割必需」，
        // 而是可选的锦上添花 —— 打开面板第一眼看到的仍然该是主流程那几项。
        this.extrasButton = ButtonWidget.builder(
                Text.translatable("autofarm.option.extras"), b -> openExtras())
                .dimensions(contentX, 0, fullW, ROW_H).build();
        extrasButton.setTooltip(Tooltip.of(Text.translatable("autofarm.extras.tip")));
        addScrolling(extrasButton);

        // 统计面板（E 组）：不是改配置，只是看一轮「收了多少、干了多久、出过几次岔子」，
        // 以及累计到现在的账本。和附加功能并排放在最后。
        this.statsButton = ButtonWidget.builder(
                Text.translatable("autofarm.option.stats"), b -> openStats())
                .dimensions(contentX, 0, fullW, ROW_H).build();
        statsButton.setTooltip(Tooltip.of(Text.translatable("autofarm.stats.tip")));
        addScrolling(statsButton);

        // ---- 页脚（固定在面板底部，不参与滚动） ----
        addFooter(
                ButtonWidget.builder(Text.translatable("autofarm.button.save"), b -> onSave())
                        .dimensions(contentX, 0, halfW, ROW_H).build(),
                ButtonWidget.builder(Text.translatable("autofarm.button.cancel"), b -> onCancel())
                        .dimensions(halfRightX, 0, halfW, ROW_H).build()
        );
    }

    @Override
    protected void positionContent(int originY) {
        setRowY(enabledButton, originY + ROW_ENABLED);
        setRowY(radiusSlider, originY + ROW_RADIUS);
        setRowY(scanIntervalSlider, originY + ROW_SCAN_INTERVAL);
        setRowY(replantButton, originY + ROW_REPLANT);
        setRowY(refillButton, originY + ROW_REFILL);
        setRowY(depositButton, originY + ROW_DEPOSIT);
        setRowY(optimizeButton, originY + ROW_OPTIMIZE);
        setRowY(showPathButton, originY + ROW_SHOW_PATH);
        setRowY(lockSprintButton, originY + ROW_LOCK_SPRINT);
        setRowY(autoEatButton, originY + ROW_AUTO_EAT);
        setRowY(eatFullButton, originY + ROW_EAT_FULL);
        setRowY(allowMineButton, originY + ROW_ALLOW_MINE);
        setRowY(autoToolButton, originY + ROW_AUTO_TOOL);
        setRowY(allowPlaceButton, originY + ROW_ALLOW_PLACE);
        setRowY(visualizeButton, originY + ROW_VISUAL);
        setRowY(paletteButton, originY + ROW_VISUAL);
        setRowY(throughWallsButton, originY + ROW_THROUGH_WALLS);
        setRowY(freecamButton, originY + ROW_FREECAM);
        setRowY(defaultSeedButton, originY + ROW_DEFAULT_SEED);
        setRowY(storagePolicyButton, originY + ROW_STORAGE_POLICY);
        setRowY(fieldX, originY + ROW_FIELDS);
        setRowY(fieldY, originY + ROW_FIELDS);
        setRowY(fieldZ, originY + ROW_FIELDS);
        setRowY(recordButton, originY + ROW_SUPPLY_BTN);
        setRowY(clearButton, originY + ROW_SUPPLY_BTN);
        setRowY(showHudButton, originY + ROW_SHOW_HUD);
        setRowY(lowHealthButton, originY + ROW_LOW_HEALTH);
        setRowY(mobsNearButton, originY + ROW_MOBS_NEAR);
        setRowY(fillEmptyButton, originY + ROW_FILL_EMPTY);
        setRowY(cropFilterButton, originY + ROW_CROP_FILTER);
        setRowY(extrasButton, originY + ROW_EXTRAS);
        setRowY(statsButton, originY + ROW_STATS);
    }

    private TextFieldWidget coordField(int x, int width, Integer value) {
        TextFieldWidget field = new TextFieldWidget(this.textRenderer, x, 0, width, ROW_H, Text.empty());
        field.setMaxLength(7);
        field.setText(value == null ? "" : Integer.toString(value));
        return field;
    }

    // ================= 自绘 =================

    @Override
    protected void renderContent(DrawContext context, int originY, int mouseX, int mouseY, float delta) {
        // ---- 运行状态 ----
        drawSection(context, originY, SEC_STATUS_Y, SEC_STATUS_H);
        drawHeader(context, originY, SEC_STATUS_Y, "autofarm.section.status");
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("autofarm.status.current",
                        Text.translatable(FarmTaskManager.getState().getTranslationKey())),
                contentX + 6, originY + SEC_STATUS_Y + 15, COLOR_TEXT);
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("autofarm.status.mature", FarmTaskManager.getMatureCount()),
                contentX + 6, originY + SEC_STATUS_Y + 26, COLOR_TEXT);
        context.drawTextWithShadow(this.textRenderer, routeText(),
                contentX + 6, originY + SEC_STATUS_Y + 37,
                FarmTaskManager.isWalking() ? COLOR_TEXT : COLOR_HINT);

        // 第四行：上一棵作物收得怎么样。「没种子」「挖不动」这类跳过原因都写在这里，
        // 不然你只会看到它绕开某一棵却不知道为什么。
        context.drawTextWithShadow(this.textRenderer, harvestText(),
                contentX + 6, originY + SEC_STATUS_Y + 48,
                FarmTaskManager.getHarvestNotice() != null ? COLOR_NOTICE : COLOR_HINT);

        // ---- 收割设置 ----
        drawSection(context, originY, SEC_HARVEST_Y, SEC_HARVEST_H);
        drawHeader(context, originY, SEC_HARVEST_Y, "autofarm.section.harvest");

        // ---- 路径规划 ----
        drawSection(context, originY, SEC_PATH_Y, SEC_PATH_H);
        drawHeader(context, originY, SEC_PATH_Y, "autofarm.section.path");
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("autofarm.path.hotbar", FarmTaskManager.getHotbarBlockCount()),
                contentX + 78, originY + SEC_PATH_Y + 4, COLOR_HINT);

        // ---- 可视化 ----
        drawSection(context, originY, SEC_DISPLAY_Y, SEC_DISPLAY_H);
        drawHeader(context, originY, SEC_DISPLAY_Y, "autofarm.section.display");
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("autofarm.section.display.hint"),
                contentX + DISPLAY_HINT_X, originY + SEC_DISPLAY_Y + 4, COLOR_HINT);

        // ---- 补给点 ----
        drawSection(context, originY, SEC_SUPPLY_Y, SEC_SUPPLY_H);
        drawHeader(context, originY, SEC_SUPPLY_Y, "autofarm.section.supply");

        // 背包空格数：快满的时候会变暖色，这样你就知道它为什么突然调头去补给点了
        int free = freeSlots();
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("autofarm.supply.free_slots", free),
                contentX + 62, originY + SEC_SUPPLY_Y + 4,
                free <= 1 ? COLOR_NOTICE : COLOR_HINT);

        // 识别到的存储方块。判定是「附近有没有挂 Inventory 的方块实体」，
        // 所以箱子、木桶、别的存储模组在这里都会显示成「已识别」；
        // 没设、或者那一格真不是容器，就用暖色把话说清楚。
        AutoFarmConfig preview = previewSupply();
        BlockPos found = findSupplyContainer(preview);
        Text supplyText;
        int supplyColor;
        if (!preview.hasSupplyPoint()) {
            supplyText = Text.translatable("autofarm.supply.none");
            supplyColor = COLOR_HINT;
        } else if (found == null) {
            supplyText = Text.translatable("autofarm.supply.not_container");
            supplyColor = COLOR_NOTICE;
        } else {
            supplyText = Text.translatable("autofarm.supply.found",
                    found.getX() + ", " + found.getY() + ", " + found.getZ());
            supplyColor = COLOR_TEXT;
        }
        context.drawTextWithShadow(this.textRenderer, supplyText,
                contentX + 6, originY + ROW_SUPPLY_HINT + 5, supplyColor);

        String[] axes = {"X", "Y", "Z"};
        for (int i = 0; i < axes.length; i++) {
            context.drawTextWithShadow(this.textRenderer, axes[i],
                    axisLabelX[i], originY + ROW_FIELDS + 5, COLOR_HINT);
        }

        // ---- 安全与提示 ----
        drawSection(context, originY, SEC_SAFETY_Y, SEC_SAFETY_H);
        drawHeader(context, originY, SEC_SAFETY_Y, "autofarm.section.safety");
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("autofarm.section.safety.hint"),
                contentX + 62, originY + SEC_SAFETY_Y + 4, COLOR_HINT);

        // ---- 作物与筛选 ----
        drawSection(context, originY, SEC_CROP_Y, SEC_CROP_H);
        drawHeader(context, originY, SEC_CROP_Y, "autofarm.section.crop");
        // 一行摘要：现在是哪种筛选、附近有几格空地 —— 不必进次级面板也能看个大概。
        // 空地的数目是活的（来自上一轮扫描），所以它同时也是「它到底扫到没有」的凭据。
        Text summary = Text.translatable("autofarm.crop.summary",
                Text.translatable(cropModeKey(draft.cropFilterMode)),
                Text.translatable(draft.fillEmptyFarmland ? "autofarm.on" : "autofarm.off"),
                FarmTaskManager.getFarmlandCount());
        context.drawTextWithShadow(this.textRenderer, summary,
                contentX + 62, originY + SEC_CROP_Y + 4,
                draft.fillEmptyFarmland ? COLOR_TEXT : COLOR_HINT);

        // ---- 附加功能 ----
        drawSection(context, originY, SEC_EXTRAS_Y, SEC_EXTRAS_H);
        drawHeader(context, originY, SEC_EXTRAS_Y, "autofarm.section.extras");
        // 一行摘要：现在开了几项。全关时压暗，开了就换成暖色，一眼能看出「有没有动过」
        int enabledExtras = draft.extrasEnabledCount();
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("autofarm.extras.summary", enabledExtras),
                contentX + 62, originY + SEC_EXTRAS_Y + 4,
                enabledExtras > 0 ? COLOR_NOTICE : COLOR_HINT);
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
        if (this.client == null || this.client.world == null || !preview.hasSupplyPoint()) {
            return null;
        }
        return ContainerCompat.findContainer(this.client.world,
                new BlockPos(preview.supplyX, preview.supplyY, preview.supplyZ));
    }

    /** 背包还剩几个空格 —— 补给点那一栏会显示它，好知道它为什么突然跑去存东西 */
    private int freeSlots() {
        return this.client == null || this.client.player == null
                ? 0
                : InventoryScanner.countFreeSlots(this.client.player);
    }

    /** 状态区第三行：当前这条路线还剩多少活要干 */
    private static Text routeText() {        if (!FarmTaskManager.isWalking()) {
            return Text.translatable("autofarm.status.route.none");
        }
        return Text.translatable("autofarm.status.route",
                FarmTaskManager.getRouteRemaining(),
                String.format(Locale.ROOT, "%.1f", FarmTaskManager.getRemainingDistance()));
    }

    /** 状态区第四行：上一棵作物的结果，或者「为什么跳过它」 */
    private static Text harvestText() {
        String notice = FarmTaskManager.getHarvestNotice();
        if (notice != null) {
            return Text.translatable(notice);
        }
        // 甜浆果、发光浆果这类是「摘」的：植株留着，所以没有补种那回事
        if (CropHarvester.wasPicked()) {
            return Text.translatable("autofarm.status.picked");
        }
        // 空地补种：没收到任何东西，但确实干了一件活
        if (CropHarvester.wasPlanted()) {
            return Text.translatable("autofarm.status.planted");
        }
        if (CropHarvester.wasHarvested()) {
            return Text.translatable(CropHarvester.wasReplanted()
                    ? "autofarm.status.harvest.done"
                    : "autofarm.status.harvest.done_noreplant");
        }
        return Text.translatable("autofarm.status.harvest.none");
    }

    /** 面板上方那行补给点状态，跟着输入框实时变，不必等保存 */
    private AutoFarmConfig previewSupply() {        AutoFarmConfig preview = new AutoFarmConfig();
        preview.supplyX = parseOrNull(fieldX);
        preview.supplyY = parseOrNull(fieldY);
        preview.supplyZ = parseOrNull(fieldZ);
        return preview;
    }

    // ================= 行为 =================

    /** 进「存放策略」次级面板。它编辑的是同一个 draft，所以回来时这里不用再同步 */
    private void openStoragePolicy() {
        // 先把输入框里的内容收进草稿，回来重建界面时才不会丢
        draft.supplyX = parseOrNull(fieldX);
        draft.supplyY = parseOrNull(fieldY);
        draft.supplyZ = parseOrNull(fieldZ);
        if (this.client != null) {
            this.client.setScreen(new StoragePolicyScreen(this, draft));
        }
    }

    /** 进「作物」次级面板。它编辑的是同一个 draft，所以回来时这里不用再同步 */
    private void openCropFilter() {
        // 先把输入框里的内容收进草稿，回来重建界面时才不会丢
        draft.supplyX = parseOrNull(fieldX);
        draft.supplyY = parseOrNull(fieldY);
        draft.supplyZ = parseOrNull(fieldZ);
        if (this.client != null) {
            this.client.setScreen(new CropFilterScreen(this, draft));
        }
    }

    /** 进「高亮样式」次级面板。它编辑的是同一个 draft，所以回来时这里不用再同步 */
    private void openHighlightStyle() {
        // 先把输入框里的内容收进草稿，回来重建界面时才不会丢
        draft.supplyX = parseOrNull(fieldX);
        draft.supplyY = parseOrNull(fieldY);
        draft.supplyZ = parseOrNull(fieldZ);
        if (this.client != null) {
            this.client.setScreen(new HighlightStyleScreen(this, draft));
        }
    }

    /** 进「附加功能」次级面板。它编辑的是同一个 draft，回来时这里不用再同步 */
    private void openExtras() {
        draft.supplyX = parseOrNull(fieldX);
        draft.supplyY = parseOrNull(fieldY);
        draft.supplyZ = parseOrNull(fieldZ);
        if (this.client != null) {
            this.client.setScreen(new ExtrasScreen(this, draft));
        }
    }

    /** 进「统计」面板。它不编辑 draft —— 只是看本轮和累计的账本 */
    private void openStats() {
        if (this.client != null) {
            this.client.setScreen(new StatsScreen(this));
        }
    }

    private void onSave() {
        draft.supplyX = parseOrNull(fieldX);
        draft.supplyY = parseOrNull(fieldY);
        draft.supplyZ = parseOrNull(fieldZ);
        ConfigManager.save(draft);
        toast("autofarm.message.saved");
        backToParent();
    }

    private static Integer parseOrNull(TextFieldWidget field) {
        if (field == null) {
            return null;
        }
        String raw = field.getText();
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
    private static Text slotLabel(int slot) {
        return Text.translatable("autofarm.toggle",
                Text.translatable("autofarm.option.default_seed"),
                Text.translatable("autofarm.value.slot", slot));
    }

    /**
     * 「低血量停机：4 心」。
     *
     * <p>用「心」而不是血量点来显示 —— 血条上画的就是心，玩家脑子里数的也是心。
     */
    private static Text healthLabel(int hearts) {
        return Text.translatable("autofarm.toggle",
                Text.translatable("autofarm.option.low_health"),
                hearts <= 0
                        ? Text.translatable("autofarm.value.off")
                        : Text.translatable("autofarm.value.hearts", hearts));
    }

    /**
     * 低血量门槛的下一个档位：关闭 → 3 心 → 4 心 → 5 心 → 6 心 → 关闭。
     *
     * <p>从「关闭」一步就是 3 心（最灵敏），然后往上走；到头再回到关闭。
     * 没有 1 心、2 心这两档 —— 那两档被打一下就停，实际用起来只剩烦躁。
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
