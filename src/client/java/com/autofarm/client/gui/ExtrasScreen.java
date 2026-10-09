package com.autofarm.client.gui;

import com.autofarm.client.config.AutoFarmConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

/**
 * 「附加功能」次级面板：一堆<b>默认全部关闭</b>的增强项，集中放在这里。
 *
 * <h2>为什么要单开一页、而且默认全关</h2>
 * 主面板上的每一项都是「收割本身需要的东西」—— 扫描、寻路、补种、存放、安全。
 * 而这里放的是一层<b>可有可无的锦上添花</b>：有的改变动作节奏（拟人化、定时休息），
 * 有的改变安全边界（别的玩家靠近就停手），有的只是顺手多干点活
 * （工具快坏了换一把、失败的作物过会儿再试、HUD 多显示点数字）。
 *
 * <p>它们共同的风险是「玩家没要，却擅自改变了行为」。所以默认**一个都不开**：
 * 不碰它们，模组的行为和以前一模一样。想用哪个再单独打开。
 *
 * <h2>四段</h2>
 * <ol>
 *   <li><b>安全与隐蔽</b> —— 玩家接近停机、拟人化节奏、定时随机休息；</li>
 *   <li><b>工具维护</b> —— 工具耐久自动换新；</li>
 *   <li><b>容错与统计</b> —— 失败目标暂时拉黑、HUD 详细统计；</li>
 *   <li><b>掉落物</b> —— 优先拾取散落在田里的作物掉落物。</li>
 * </ol>
 * 每个「功能开关」下面跟着的参数行（半径、周期、阈值……）都用循环按钮调，
 * 点一下换一档，不用滑块 —— 和主面板的「默认种子槽位」一个习惯。
 *
 * <h2>和主面板的关系</h2>
 * 老规矩：它编辑的是主面板递进来的<b>同一个草稿</b>。点「完成」回主面板，
 * 最终由主面板的「保存并关闭」落盘；点「取消」或 Esc 只把这里的十几项
 * 恢复成进来之前的样子，不碰草稿里的其他设置。
 */
public class ExtrasScreen extends ScrollPanelScreen {

    // ---------------- 内容区布局（相对内容区顶部） ----------------
    /** 分区内第一行相对分区顶部的 y（让开标题那一行） */
    private static final int ROW_FIRST = 18;
    /** 相邻两行的间距 */
    private static final int ROW_GAP = 20;
    /** 两个分区之间的留白 */
    private static final int SECTION_GAP = 6;
    /** 参数行的左缩进，视觉上表示「从属于上一行」 */
    private static final int INDENT = 8;

    private static final int STEALTH_ROWS = 6;
    private static final int SEC_STEALTH_Y = 0;
    private static final int SEC_STEALTH_H = ROW_FIRST + STEALTH_ROWS * ROW_GAP + 4;

    private static final int TOOL_ROWS = 2;
    private static final int SEC_TOOL_Y = SEC_STEALTH_H + SECTION_GAP;
    private static final int SEC_TOOL_H = ROW_FIRST + TOOL_ROWS * ROW_GAP + 4;

    private static final int MISC_ROWS = 3;
    private static final int SEC_MISC_Y = SEC_TOOL_Y + SEC_TOOL_H + SECTION_GAP;
    private static final int SEC_MISC_H = ROW_FIRST + MISC_ROWS * ROW_GAP + 4;

    private static final int PICKUP_ROWS = 2;
    private static final int SEC_PICKUP_Y = SEC_MISC_Y + SEC_MISC_H + SECTION_GAP;
    private static final int SEC_PICKUP_H = ROW_FIRST + PICKUP_ROWS * ROW_GAP + 4;

    /** 最底下那句「默认全关」的总说明 */
    private static final int NOTE_Y = SEC_PICKUP_Y + SEC_PICKUP_H + 8;
    private static final int NOTE_H = 24;

    // ---------------- 循环档位（都含出厂默认值，见 AutoFarmConfig） ----------------
    private static final int[] RADIUS_CHOICES = {8, 12, 16, 24, 32};
    private static final int[] REST_INTERVAL_CHOICES = {5, 10, 15, 20, 30};
    private static final int[] REST_DURATION_CHOICES = {10, 20, 30, 60};
    private static final int[] DURABILITY_CHOICES = {5, 10, 15, 20, 30};
    private static final int[] COOLDOWN_CHOICES = {5, 10, 15, 30, 60};
    private static final int[] PICKUP_DELAY_CHOICES = {1, 2, 3, 5};

    private final AutoFarmConfig draft;
    /** 进来时的快照，「取消」时用它还原附加功能这几项 */
    private final AutoFarmConfig old;

    private ButtonWidget pausePlayerButton;
    private ButtonWidget radiusButton;
    private ButtonWidget humanizeButton;
    private ButtonWidget randomRestButton;
    private ButtonWidget restIntervalButton;
    private ButtonWidget restDurationButton;
    private ButtonWidget replaceToolButton;
    private ButtonWidget durabilityButton;
    private ButtonWidget failureCooldownButton;
    private ButtonWidget cooldownButton;
    private ButtonWidget verboseHudButton;
    private ButtonWidget pickupDropsButton;
    private ButtonWidget pickupDelayButton;

    public ExtrasScreen(Screen parent, AutoFarmConfig sharedDraft) {
        super(parent, Text.translatable("autofarm.extras.title"));
        this.draft = sharedDraft;
        this.old = sharedDraft.copy();
    }

    @Override
    protected int contentHeight() {
        return NOTE_Y + NOTE_H;
    }

    // ================= 建控件 =================

    @Override
    protected void buildContent() {
        int fullW = contentWidth;
        int subX = contentX + INDENT;
        int subW = contentWidth - INDENT;

        // ---- 安全与隐蔽 ----

        this.pausePlayerButton = toggle("autofarm.option.pause_player",
                draft.pauseWhenPlayerNear, value -> draft.pauseWhenPlayerNear = value,
                "autofarm.pause_player.tip");
        layout(pausePlayerButton, contentX, 0, fullW);

        this.radiusButton = ButtonWidget.builder(radiusLabel(), b -> {
            draft.playerNearRadius = nextChoice(RADIUS_CHOICES, draft.playerNearRadius);
            b.setMessage(radiusLabel());
        }).dimensions(subX, 0, subW, ROW_H).build();
        radiusButton.setTooltip(Tooltip.of(Text.translatable("autofarm.value_radius.tip")));
        addScrolling(radiusButton);

        this.humanizeButton = toggle("autofarm.option.humanize",
                draft.humanizeTiming, value -> draft.humanizeTiming = value,
                "autofarm.humanize.tip");
        layout(humanizeButton, contentX, 0, fullW);

        this.randomRestButton = toggle("autofarm.option.random_rest",
                draft.randomRest, value -> draft.randomRest = value,
                "autofarm.random_rest.tip");
        layout(randomRestButton, contentX, 0, fullW);

        this.restIntervalButton = ButtonWidget.builder(restIntervalLabel(), b -> {
            draft.restIntervalMinutes = nextChoice(REST_INTERVAL_CHOICES, draft.restIntervalMinutes);
            b.setMessage(restIntervalLabel());
        }).dimensions(subX, 0, subW, ROW_H).build();
        restIntervalButton.setTooltip(Tooltip.of(Text.translatable("autofarm.rest_interval.tip")));
        addScrolling(restIntervalButton);

        this.restDurationButton = ButtonWidget.builder(restDurationLabel(), b -> {
            draft.restDurationSeconds = nextChoice(REST_DURATION_CHOICES, draft.restDurationSeconds);
            b.setMessage(restDurationLabel());
        }).dimensions(subX, 0, subW, ROW_H).build();
        restDurationButton.setTooltip(Tooltip.of(Text.translatable("autofarm.rest_duration.tip")));
        addScrolling(restDurationButton);

        // ---- 工具维护 ----

        this.replaceToolButton = toggle("autofarm.option.replace_tool",
                draft.autoReplaceTool, value -> draft.autoReplaceTool = value,
                "autofarm.replace_tool.tip");
        layout(replaceToolButton, contentX, 0, fullW);

        this.durabilityButton = ButtonWidget.builder(durabilityLabel(), b -> {
            draft.toolDurabilityPercent = nextChoice(DURABILITY_CHOICES, draft.toolDurabilityPercent);
            b.setMessage(durabilityLabel());
        }).dimensions(subX, 0, subW, ROW_H).build();
        durabilityButton.setTooltip(Tooltip.of(Text.translatable("autofarm.durability.tip")));
        addScrolling(durabilityButton);

        // ---- 容错与统计 ----

        this.failureCooldownButton = toggle("autofarm.option.failure_cooldown",
                draft.failureCooldown, value -> draft.failureCooldown = value,
                "autofarm.failure_cooldown.tip");
        layout(failureCooldownButton, contentX, 0, fullW);

        this.cooldownButton = ButtonWidget.builder(cooldownLabel(), b -> {
            draft.failureCooldownSeconds = nextChoice(COOLDOWN_CHOICES, draft.failureCooldownSeconds);
            b.setMessage(cooldownLabel());
        }).dimensions(subX, 0, subW, ROW_H).build();
        cooldownButton.setTooltip(Tooltip.of(Text.translatable("autofarm.cooldown.tip")));
        addScrolling(cooldownButton);

        this.verboseHudButton = toggle("autofarm.option.verbose_hud",
                draft.verboseHud, value -> draft.verboseHud = value,
                "autofarm.verbose_hud.tip");
        layout(verboseHudButton, contentX, 0, fullW);

        // ---- 掉落物 ----

        this.pickupDropsButton = toggle("autofarm.option.pickup_drops",
                draft.pickupDroppedItems, value -> draft.pickupDroppedItems = value,
                "autofarm.pickup_drops.tip");
        layout(pickupDropsButton, contentX, 0, fullW);

        this.pickupDelayButton = ButtonWidget.builder(pickupDelayLabel(), b -> {
            draft.pickupDelaySeconds = nextChoice(PICKUP_DELAY_CHOICES, draft.pickupDelaySeconds);
            b.setMessage(pickupDelayLabel());
        }).dimensions(subX, 0, subW, ROW_H).build();
        pickupDelayButton.setTooltip(Tooltip.of(Text.translatable("autofarm.pickup_delay.tip")));
        addScrolling(pickupDelayButton);

        // ---- 页脚 ----
        int btnW = (contentWidth - 8) / 3;
        addFooter(
                ButtonWidget.builder(Text.translatable("autofarm.extras.reset"), b -> resetAllOff())
                        .dimensions(contentX, 0, btnW, ROW_H).build(),
                ButtonWidget.builder(Text.translatable("autofarm.button.cancel"), b -> onCancel())
                        .dimensions(contentX + btnW + 4, 0, btnW, ROW_H).build(),
                ButtonWidget.builder(Text.translatable("autofarm.highlight.done"), b -> backToParent())
                        .dimensions(contentX + 2 * (btnW + 4), 0, btnW, ROW_H).build()
        );
    }

    /** 一个开关按钮的样板：点一下翻转，标签跟着变 */
    private ButtonWidget toggle(String optionKey, boolean value,
                                java.util.function.Consumer<Boolean> setter, String tipKey) {
        ButtonWidget button = ButtonWidget.builder(toggleLabel(optionKey, value), b -> {
            boolean next = !currentToggle(optionKey);
            setter.accept(next);
            b.setMessage(toggleLabel(optionKey, next));
        }).dimensions(contentX, 0, contentWidth, ROW_H).build();
        button.setTooltip(Tooltip.of(Text.translatable(tipKey)));
        return button;
    }

    /** 从 draft 里读回当前开关值 —— 用来做「翻转」，保证和草稿永远一致 */
    private boolean currentToggle(String optionKey) {
        return switch (optionKey) {
            case "autofarm.option.pause_player" -> draft.pauseWhenPlayerNear;
            case "autofarm.option.humanize" -> draft.humanizeTiming;
            case "autofarm.option.random_rest" -> draft.randomRest;
            case "autofarm.option.replace_tool" -> draft.autoReplaceTool;
            case "autofarm.option.failure_cooldown" -> draft.failureCooldown;
            case "autofarm.option.verbose_hud" -> draft.verboseHud;
            case "autofarm.option.pickup_drops" -> draft.pickupDroppedItems;
            default -> false;
        };
    }

    private void layout(ButtonWidget widget, int x, int y, int width) {
        widget.setX(x);
        widget.setY(y);
        widget.setWidth(width);
        addScrolling(widget);
    }

    @Override
    protected void positionContent(int originY) {
        int stealth = originY + SEC_STEALTH_Y;
        setRowY(pausePlayerButton, stealth + ROW_FIRST);
        setRowY(radiusButton, stealth + ROW_FIRST + ROW_GAP);
        setRowY(humanizeButton, stealth + ROW_FIRST + 2 * ROW_GAP);
        setRowY(randomRestButton, stealth + ROW_FIRST + 3 * ROW_GAP);
        setRowY(restIntervalButton, stealth + ROW_FIRST + 4 * ROW_GAP);
        setRowY(restDurationButton, stealth + ROW_FIRST + 5 * ROW_GAP);

        int tool = originY + SEC_TOOL_Y;
        setRowY(replaceToolButton, tool + ROW_FIRST);
        setRowY(durabilityButton, tool + ROW_FIRST + ROW_GAP);

        int misc = originY + SEC_MISC_Y;
        setRowY(failureCooldownButton, misc + ROW_FIRST);
        setRowY(cooldownButton, misc + ROW_FIRST + ROW_GAP);
        setRowY(verboseHudButton, misc + ROW_FIRST + 2 * ROW_GAP);

        int pickup = originY + SEC_PICKUP_Y;
        setRowY(pickupDropsButton, pickup + ROW_FIRST);
        setRowY(pickupDelayButton, pickup + ROW_FIRST + ROW_GAP);
    }

    // ================= 自绘 =================

    @Override
    protected void renderContent(DrawContext context, int originY, int mouseX, int mouseY, float delta) {
        drawSection(context, originY, SEC_STEALTH_Y, SEC_STEALTH_H);
        drawHeader(context, originY, SEC_STEALTH_Y, "autofarm.extras.section.stealth");

        drawSection(context, originY, SEC_TOOL_Y, SEC_TOOL_H);
        drawHeader(context, originY, SEC_TOOL_Y, "autofarm.extras.section.tool");

        drawSection(context, originY, SEC_MISC_Y, SEC_MISC_H);
        drawHeader(context, originY, SEC_MISC_Y, "autofarm.extras.section.misc");

        drawSection(context, originY, SEC_PICKUP_Y, SEC_PICKUP_H);
        drawHeader(context, originY, SEC_PICKUP_Y, "autofarm.extras.section.pickup");

        // 最底下那句总说明：强调「默认全关」，让第一次进来的人放心
        int shown = 0;
        for (OrderedText line : this.textRenderer.wrapLines(
                Text.translatable("autofarm.extras.note"), contentWidth - 8)) {
            if (shown >= 2) {
                break;
            }
            context.drawTextWithShadow(this.textRenderer, line,
                    contentX + 4, originY + NOTE_Y + shown * 11, COLOR_HINT);
            shown++;
        }
    }

    // ================= 行为 =================

    /** 「恢复默认」＝把附加功能全部关掉（出厂就是全关） */
    private void resetAllOff() {
        draft.pauseWhenPlayerNear = false;
        draft.playerNearRadius = 16;
        draft.humanizeTiming = false;
        draft.randomRest = false;
        draft.restIntervalMinutes = 10;
        draft.restDurationSeconds = 20;
        draft.autoReplaceTool = false;
        draft.toolDurabilityPercent = 10;
        draft.failureCooldown = false;
        draft.failureCooldownSeconds = 15;
        draft.verboseHud = false;
        draft.pickupDroppedItems = false;
        draft.pickupDelaySeconds = 1;
        // 标签文字要跟着变，直接重建整页最省事
        if (this.client != null) {
            this.client.setScreen(new ExtrasScreen(this.parent, draft));
        }
    }

    @Override
    protected void onCancel() {
        // 只还原附加功能这几项，草稿里的其他设置（扫描半径、存放策略……）保持不动
        draft.pauseWhenPlayerNear = old.pauseWhenPlayerNear;
        draft.playerNearRadius = old.playerNearRadius;
        draft.humanizeTiming = old.humanizeTiming;
        draft.randomRest = old.randomRest;
        draft.restIntervalMinutes = old.restIntervalMinutes;
        draft.restDurationSeconds = old.restDurationSeconds;
        draft.autoReplaceTool = old.autoReplaceTool;
        draft.toolDurabilityPercent = old.toolDurabilityPercent;
        draft.failureCooldown = old.failureCooldown;
        draft.failureCooldownSeconds = old.failureCooldownSeconds;
        draft.verboseHud = old.verboseHud;
        draft.pickupDroppedItems = old.pickupDroppedItems;
        draft.pickupDelaySeconds = old.pickupDelaySeconds;
        backToParent();
    }

    // ================= 文案 =================

    private static int nextChoice(int[] choices, int current) {
        for (int i = 0; i < choices.length; i++) {
            if (choices[i] == current) {
                return choices[(i + 1) % choices.length];
            }
        }
        return choices[0];
    }

    /** 「选项：值」的通用拼法 */
    private static Text optionLabel(String optionKey, Text value) {
        return Text.translatable("autofarm.toggle", Text.translatable(optionKey), value);
    }

    private Text radiusLabel() {
        return optionLabel("autofarm.option.player_radius",
                Text.translatable("autofarm.value.blocks", draft.playerNearRadius));
    }

    private Text restIntervalLabel() {
        return optionLabel("autofarm.option.rest_interval",
                Text.translatable("autofarm.value.minutes", draft.restIntervalMinutes));
    }

    private Text restDurationLabel() {
        return optionLabel("autofarm.option.rest_duration",
                Text.translatable("autofarm.value.seconds", draft.restDurationSeconds));
    }

    private Text durabilityLabel() {
        return optionLabel("autofarm.option.tool_threshold",
                Text.translatable("autofarm.value.percent", draft.toolDurabilityPercent));
    }

    private Text cooldownLabel() {
        return optionLabel("autofarm.option.cooldown_seconds",
                Text.translatable("autofarm.value.seconds", draft.failureCooldownSeconds));
    }

    private Text pickupDelayLabel() {
        return optionLabel("autofarm.option.pickup_delay",
                Text.translatable("autofarm.value.seconds", draft.pickupDelaySeconds));
    }
}
