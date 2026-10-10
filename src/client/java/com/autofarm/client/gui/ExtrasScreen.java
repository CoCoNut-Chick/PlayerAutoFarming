package com.autofarm.client.gui;

import com.autofarm.client.config.AutoFarmConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

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
 * <h2>九段</h2>
 * <ol>
 *   <li><b>战斗（高风险）</b> —— 自动打怪、目标范围、攻击间隔（输入框）、攻击距离、
 *       暴力模式与暴力程度。默认关闭、警示配色、开启与调高参数都要过风险确认屏，
 *       而且必须先打开主面板的「自动收割」总开关；</li>
 *   <li><b>安全与隐蔽</b> —— 玩家接近停机、拟人化节奏、定时随机休息；</li>
 *   <li><b>防踢模式</b> —— 一整套「把机器味抹掉」的措施，带四档强度；</li>
 *   <li><b>行为真实感</b> —— 危险反应延迟、空闲时环顾四周、仅在指定时段工作；</li>
 *   <li><b>工作范围</b> —— 圈定自己那块地（圆形或两点矩形），区域外的目标不碰，
 *       人走出区域会自动走回来；</li>
 *   <li><b>环境与作息</b> —— 仅白天 / 仅晴天，以及「连续工作到上限就收工」的疲劳档；</li>
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
    /** 高风险分区专用的警示色：标题与开关文字都用它 */
    private static final int COLOR_DANGER = 0xFFFF5555;

    /** 攻击间隔行的左侧标签宽度 / 输入框宽度 —— 右侧那段留给「范围提示」文字 */
    private static final int INTERVAL_LABEL_W = 60;
    private static final int INTERVAL_FIELD_W = 56;

    /** 战斗分区：6 行（打怪 / 间隔 / 距离 / 目标 / 暴力 / 暴力程度）+ 底部两行警示 */
    private static final int COMBAT_ROWS = 6;
    private static final int COMBAT_WARN_H = 26;
    private static final int SEC_COMBAT_Y = 0;
    private static final int SEC_COMBAT_H = ROW_FIRST + COMBAT_ROWS * ROW_GAP + COMBAT_WARN_H;

    private static final int STEALTH_ROWS = 6;
    private static final int SEC_STEALTH_Y = SEC_COMBAT_H + SECTION_GAP;
    private static final int SEC_STEALTH_H = ROW_FIRST + STEALTH_ROWS * ROW_GAP + 4;

    private static final int ANTIKICK_ROWS = 2;
    private static final int SEC_ANTIKICK_Y = SEC_STEALTH_Y + SEC_STEALTH_H + SECTION_GAP;
    private static final int SEC_ANTIKICK_H = ROW_FIRST + ANTIKICK_ROWS * ROW_GAP + 4;

    private static final int BEHAVIOR_ROWS = 6;
    private static final int SEC_BEHAVIOR_Y = SEC_ANTIKICK_Y + SEC_ANTIKICK_H + SECTION_GAP;
    private static final int SEC_BEHAVIOR_H = ROW_FIRST + BEHAVIOR_ROWS * ROW_GAP + 4;

    /**
     * 工作范围：开关、方式、参数（圆形 = 半径 + 记录中心；矩形 = 记录 A + 记录 B），
     * 最后一行留给一句自绘说明。两种方式的参数行共占同样两行，用显隐切换。
     */
    private static final int WORKAREA_ROWS = 5;
    private static final int SEC_WORKAREA_Y = SEC_BEHAVIOR_Y + SEC_BEHAVIOR_H + SECTION_GAP;
    private static final int SEC_WORKAREA_H = ROW_FIRST + WORKAREA_ROWS * ROW_GAP + 4;

    /** 环境与作息：仅白天、仅晴天、疲劳收工 + 两个参数行 */
    private static final int ENV_ROWS = 5;
    private static final int SEC_ENV_Y = SEC_WORKAREA_Y + SEC_WORKAREA_H + SECTION_GAP;
    private static final int SEC_ENV_H = ROW_FIRST + ENV_ROWS * ROW_GAP + 4;

    private static final int TOOL_ROWS = 2;
    private static final int SEC_TOOL_Y = SEC_ENV_Y + SEC_ENV_H + SECTION_GAP;
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
    /** 战斗：攻击距离（格）。超过原版 3.0 的档位要先过风险确认屏才生效 */
    private static final double[] ATTACK_RANGE_CHOICES = {2.5, 2.9, 3.5, 4.5, 6.0};
    /** 战斗：暴力程度档位（和 AutoFarmConfig 的 VIOLENCE_* 常量一致） */
    private static final int[] VIOLENCE_CHOICES = {0, 1, 2};
    /** 防踢档位：0 = 关闭、1 = 轻度、2 = 中度、3 = 强力（和 Humanizer 的常量一致） */
    private static final int[] ANTIKICK_LEVEL_CHOICES = {0, 1, 2, 3};
    /** 行为真实感：反应延迟时长（tick，1 秒 = 20 tick） */
    private static final int[] REACTION_CHOICES = {4, 10, 20, 30};
    /** 工作时段：0~23 整点（循环按钮在这 24 档之间轮换） */
    private static final int[] HOUR_CHOICES = buildHours();
    /** 工作范围：范围方式的档位（0 = 圆形、1 = 两点矩形，和 AutoFarmConfig 的常量一致） */
    private static final int[] AREA_MODE_CHOICES = {0, 1};
    /** 工作范围：半径档位（格） */
    private static final int[] WORK_AREA_RADIUS_CHOICES = {8, 12, 16, 24, 32, 48};
    /** 疲劳收工：连续工作上限的档位（分钟） */
    private static final int[] FATIGUE_WORK_CHOICES = {15, 30, 60, 120, 240};
    /** 疲劳收工：休息时长的档位（分钟）；0 = 手动恢复 */
    private static final int[] FATIGUE_REST_CHOICES = {0, 5, 10, 15, 30, 60};

    private static int[] buildHours() {
        int[] hours = new int[24];
        for (int i = 0; i < hours.length; i++) {
            hours[i] = i;
        }
        return hours;
    }

    private final AutoFarmConfig draft;
    /** 进来时的快照，「取消」时用它还原附加功能这几项 */
    private final AutoFarmConfig old;

    private ButtonWidget combatButton;
    private TextFieldWidget attackIntervalField;
    private ButtonWidget attackRangeButton;
    private ButtonWidget attackTargetButton;
    private ButtonWidget violentButton;
    private ButtonWidget violenceLevelButton;
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
    private ButtonWidget antiKickButton;
    private ButtonWidget antiKickLevelButton;
    private ButtonWidget reactionDelayButton;
    private ButtonWidget reactionTimeButton;
    private ButtonWidget idleLookButton;
    private ButtonWidget scheduleButton;
    private ButtonWidget scheduleStartButton;
    private ButtonWidget scheduleEndButton;
    private ButtonWidget workAreaButton;
    private ButtonWidget workAreaModeButton;
    private ButtonWidget workAreaRadiusButton;
    private ButtonWidget workAreaCenterButton;
    private ButtonWidget workAreaCornerAButton;
    private ButtonWidget workAreaCornerBButton;
    private ButtonWidget daylightButton;
    private ButtonWidget weatherButton;
    private ButtonWidget fatigueButton;
    private ButtonWidget fatigueWorkButton;
    private ButtonWidget fatigueRestButton;

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

        // ---- 战斗（高风险）----
        // 全模组唯一会「替玩家出手攻击」的一组，所以从里到外都跟别的分区不一样：
        // 标题和开关文字标红、打开要先过一道风险确认屏、暴力模式还要等主子项开了才出现。

        this.combatButton = dangerToggle("autofarm.option.auto_attack",
                draft.autoAttack, value -> draft.autoAttack = value,
                "autofarm.auto_attack.tip");
        layout(combatButton, contentX, 0, fullW);

        // 攻击间隔：一个填空（不是循环按钮）。选项名画在左边，输入框摆中间，
        // 右边留给「范围 50–5000 毫秒」那段提示 —— 见 drawCombatExtras。
        this.attackIntervalField = intervalField(contentX + INTERVAL_LABEL_W);
        addScrolling(attackIntervalField);

        // 攻击距离：循环按钮，但超过原版 3.0 的档位要先过风险确认屏（onRangeClicked）
        this.attackRangeButton = ButtonWidget.builder(attackRangeLabel(), b -> onRangeClicked())
                .dimensions(subX, 0, subW, ROW_H).build();
        attackRangeButton.setTooltip(Tooltip.of(Text.translatable("autofarm.attack_range.tip")));
        addScrolling(attackRangeButton);

        this.attackTargetButton = ButtonWidget.builder(attackTargetLabel(), b -> {
            draft.attackHostileOnly = !draft.attackHostileOnly;
            b.setMessage(attackTargetLabel());
        }).dimensions(subX, 0, subW, ROW_H).build();
        attackTargetButton.setTooltip(Tooltip.of(Text.translatable("autofarm.attack_target.tip")));
        addScrolling(attackTargetButton);

        // 二级开关：主子项关着时它整个不显示（原位留一行灰字），打开之后才「弹」出来
        this.violentButton = dangerToggle("autofarm.option.auto_attack_violent",
                draft.autoAttackViolent, value -> draft.autoAttackViolent = value,
                "autofarm.auto_attack_violent.tip");
        layout(violentButton, subX, 0, subW);

        // 二级参数：暴力程度。只在「暴力模式」打开后才出现，提高档位要过确认屏
        this.violenceLevelButton = ButtonWidget.builder(violenceLabel(), b -> onViolenceClicked())
                .dimensions(subX, 0, subW, ROW_H).build();
        violenceLevelButton.setTooltip(Tooltip.of(Text.translatable("autofarm.attack_violence.tip")));
        addScrolling(violenceLevelButton);

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

        // ---- 防踢模式 ----

        this.antiKickButton = toggle("autofarm.option.antikick",
                draft.antiKickMode, value -> draft.antiKickMode = value,
                "autofarm.antikick.tip");
        layout(antiKickButton, contentX, 0, fullW);

        this.antiKickLevelButton = ButtonWidget.builder(antiKickLevelLabel(), b -> {
            draft.antiKickLevel = nextChoice(ANTIKICK_LEVEL_CHOICES, draft.antiKickLevel);
            b.setMessage(antiKickLevelLabel());
        }).dimensions(subX, 0, subW, ROW_H).build();
        antiKickLevelButton.setTooltip(Tooltip.of(Text.translatable("autofarm.antikick_level.tip")));
        addScrolling(antiKickLevelButton);

        // ---- 行为真实感 ----

        this.reactionDelayButton = toggle("autofarm.option.reaction_delay",
                draft.reactionDelay, value -> draft.reactionDelay = value,
                "autofarm.reaction_delay.tip");
        layout(reactionDelayButton, contentX, 0, fullW);

        this.reactionTimeButton = ButtonWidget.builder(reactionTimeLabel(), b -> {
            draft.reactionDelayTicks = nextChoice(REACTION_CHOICES, draft.reactionDelayTicks);
            b.setMessage(reactionTimeLabel());
        }).dimensions(subX, 0, subW, ROW_H).build();
        reactionTimeButton.setTooltip(Tooltip.of(Text.translatable("autofarm.reaction_time.tip")));
        addScrolling(reactionTimeButton);

        this.idleLookButton = toggle("autofarm.option.idle_look",
                draft.idleLookAround, value -> draft.idleLookAround = value,
                "autofarm.idle_look.tip");
        layout(idleLookButton, contentX, 0, fullW);

        this.scheduleButton = toggle("autofarm.option.schedule",
                draft.scheduleEnabled, value -> draft.scheduleEnabled = value,
                "autofarm.schedule.tip");
        layout(scheduleButton, contentX, 0, fullW);

        this.scheduleStartButton = ButtonWidget.builder(scheduleStartLabel(), b -> {
            draft.scheduleStartHour = nextChoice(HOUR_CHOICES, draft.scheduleStartHour);
            b.setMessage(scheduleStartLabel());
        }).dimensions(subX, 0, subW, ROW_H).build();
        scheduleStartButton.setTooltip(Tooltip.of(Text.translatable("autofarm.schedule_start.tip")));
        addScrolling(scheduleStartButton);

        this.scheduleEndButton = ButtonWidget.builder(scheduleEndLabel(), b -> {
            draft.scheduleEndHour = nextChoice(HOUR_CHOICES, draft.scheduleEndHour);
            b.setMessage(scheduleEndLabel());
        }).dimensions(subX, 0, subW, ROW_H).build();
        scheduleEndButton.setTooltip(Tooltip.of(Text.translatable("autofarm.schedule_end.tip")));
        addScrolling(scheduleEndButton);

        // ---- 工作范围 ----
        // 两种圈法：圆形（中心 + 半径）和两点矩形（角点 A + 角点 B）。
        // 两种方式的参数行共用同样两行，靠 relayout() 里的 visible 切换 ——
        // 这样分区的行数不随方式变，滚动手感稳定。

        this.workAreaButton = toggle("autofarm.option.work_area",
                draft.workAreaEnabled, value -> draft.workAreaEnabled = value,
                "autofarm.work_area.tip");
        layout(workAreaButton, contentX, 0, fullW);

        this.workAreaModeButton = ButtonWidget.builder(workAreaModeLabel(), b -> {
            draft.workAreaMode = nextChoice(AREA_MODE_CHOICES, draft.workAreaMode);
            // 换方式要重建整页：半径行和角点行的显隐跟着变，标签也得重算
            rebuild();
        }).dimensions(subX, 0, subW, ROW_H).build();
        workAreaModeButton.setTooltip(Tooltip.of(Text.translatable("autofarm.work_area_mode.tip")));
        addScrolling(workAreaModeButton);

        this.workAreaRadiusButton = ButtonWidget.builder(workAreaRadiusLabel(), b -> {
            draft.workAreaRadius = nextChoice(WORK_AREA_RADIUS_CHOICES, draft.workAreaRadius);
            b.setMessage(workAreaRadiusLabel());
        }).dimensions(subX, 0, subW, ROW_H).build();
        workAreaRadiusButton.setTooltip(Tooltip.of(Text.translatable("autofarm.work_area_radius.tip")));
        addScrolling(workAreaRadiusButton);

        this.workAreaCenterButton = ButtonWidget.builder(workAreaCenterLabel(), b -> {
            recordCurrentPos(false);
            b.setMessage(workAreaCenterLabel());
        }).dimensions(subX, 0, subW, ROW_H).build();
        workAreaCenterButton.setTooltip(Tooltip.of(Text.translatable("autofarm.work_area.record.tip")));
        addScrolling(workAreaCenterButton);

        this.workAreaCornerAButton = ButtonWidget.builder(workAreaCornerALabel(), b -> {
            recordCurrentPos(false);
            b.setMessage(workAreaCornerALabel());
            if (workAreaCornerBButton != null) {
                workAreaCornerBButton.setMessage(workAreaCornerBLabel());
            }
        }).dimensions(subX, 0, subW, ROW_H).build();
        workAreaCornerAButton.setTooltip(Tooltip.of(Text.translatable("autofarm.work_area.corner_a.tip")));
        addScrolling(workAreaCornerAButton);

        this.workAreaCornerBButton = ButtonWidget.builder(workAreaCornerBLabel(), b -> {
            recordCurrentPos(true);
            b.setMessage(workAreaCornerBLabel());
        }).dimensions(subX, 0, subW, ROW_H).build();
        workAreaCornerBButton.setTooltip(Tooltip.of(Text.translatable("autofarm.work_area.corner_b.tip")));
        addScrolling(workAreaCornerBButton);

        // ---- 环境与作息 ----

        this.daylightButton = toggle("autofarm.option.daylight",
                draft.workDaylightOnly, value -> draft.workDaylightOnly = value,
                "autofarm.daylight.tip");
        layout(daylightButton, contentX, 0, fullW);

        this.weatherButton = toggle("autofarm.option.clear_weather",
                draft.workClearWeatherOnly, value -> draft.workClearWeatherOnly = value,
                "autofarm.clear_weather.tip");
        layout(weatherButton, contentX, 0, fullW);

        this.fatigueButton = toggle("autofarm.option.fatigue",
                draft.fatigueStop, value -> draft.fatigueStop = value,
                "autofarm.fatigue.tip");
        layout(fatigueButton, contentX, 0, fullW);

        this.fatigueWorkButton = ButtonWidget.builder(fatigueWorkLabel(), b -> {
            draft.fatigueWorkMinutes = nextChoice(FATIGUE_WORK_CHOICES, draft.fatigueWorkMinutes);
            b.setMessage(fatigueWorkLabel());
        }).dimensions(subX, 0, subW, ROW_H).build();
        fatigueWorkButton.setTooltip(Tooltip.of(Text.translatable("autofarm.fatigue_work.tip")));
        addScrolling(fatigueWorkButton);

        this.fatigueRestButton = ButtonWidget.builder(fatigueRestLabel(), b -> {
            draft.fatigueRestMinutes = nextChoice(FATIGUE_REST_CHOICES, draft.fatigueRestMinutes);
            b.setMessage(fatigueRestLabel());
        }).dimensions(subX, 0, subW, ROW_H).build();
        fatigueRestButton.setTooltip(Tooltip.of(Text.translatable("autofarm.fatigue_rest.tip")));
        addScrolling(fatigueRestButton);

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

    /**
     * 高风险开关：文字标红，而且「打开」这一步被拆成两次 ——
     * 点开关只负责弹风险确认屏，真正写回草稿要等那边点过「确认」。
     * 「关闭」不设门槛：关掉一个危险功能不需要再被劝一次。
     */
    private ButtonWidget dangerToggle(String optionKey, boolean value,
                                      java.util.function.Consumer<Boolean> setter, String tipKey) {
        ButtonWidget button = ButtonWidget.builder(dangerLabel(optionKey, value), b -> {
            if (currentToggle(optionKey)) {
                setter.accept(false);
                // 自动打怪关掉时，它的二级项（暴力模式 / 暴力程度）也一并收掉，
                // 免得留下一个「主子项关着、暴力标记还亮着」的状态
                if ("autofarm.option.auto_attack".equals(optionKey)) {
                    draft.autoAttackViolent = false;
                    draft.attackViolence = AutoFarmConfig.VIOLENCE_LIGHT;
                }
                b.setMessage(dangerLabel(optionKey, false));
                return;
            }
            // 「自动打怪」不能脱离「自动收割」总开关单独开启 —— 否则就成了纯粹的作弊，
            // 与这个模组的定位不符（关掉则永远允许，不设门槛）。
            if ("autofarm.option.auto_attack".equals(optionKey) && !draft.enabled) {
                toast("autofarm.combat.warn.master_off");
                return;
            }
            // 打开：先不落实，交给确认屏；确认之后那边会重建这一页
            openDangerConfirm(setter);
        }).dimensions(contentX, 0, contentWidth, ROW_H).build();
        button.setTooltip(Tooltip.of(Text.translatable(tipKey)));
        return button;
    }

    /** 攻击间隔的填空：只收数字，改动实时写进草稿，越界由 clamp() 兜底 */
    private TextFieldWidget intervalField(int x) {
        TextFieldWidget field = new TextFieldWidget(this.textRenderer, x, 0,
                INTERVAL_FIELD_W, ROW_H, Text.empty());
        field.setMaxLength(4);
        field.setTextPredicate(s -> s.isEmpty() || s.matches("\\d{1,4}"));
        field.setText(Integer.toString(draft.attackIntervalMs));
        field.setChangedListener(text -> {
            if (text == null || text.isEmpty()) {
                return; // 清空时保持原值，等下一个合法数字
            }
            try {
                draft.attackIntervalMs = Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                // 理论上进不来（上面已经限定了只收数字），保险起见吞掉
            }
        });
        field.setTooltip(Tooltip.of(Text.translatable("autofarm.attack_interval.tip")));
        return field;
    }

    /** 攻击距离：循环下一档；超过原版交互距离时先弹风险确认屏，确认后才写回 */
    private void onRangeClicked() {
        double next = nextChoice(ATTACK_RANGE_CHOICES, draft.attackRange);
        if (next > AutoFarmConfig.ATTACK_RANGE_VANILLA) {
            confirmThen("autofarm.danger.range.subtitle", () -> {
                draft.attackRange = next;
                rebuild();
            }, "autofarm.danger.range.body1", "autofarm.danger.range.body2");
            return;
        }
        draft.attackRange = next;
        attackRangeButton.setMessage(attackRangeLabel());
    }

    /** 暴力程度：循环下一档；提高档位时先弹确认屏（降低不需要） */
    private void onViolenceClicked() {
        int next = nextChoice(VIOLENCE_CHOICES, draft.attackViolence);
        if (next > draft.attackViolence) {
            confirmThen("autofarm.danger.violence.subtitle", () -> {
                draft.attackViolence = next;
                rebuild();
            }, "autofarm.danger.violence.body1", "autofarm.danger.violence.body2");
            return;
        }
        draft.attackViolence = next;
        violenceLevelButton.setMessage(violenceLabel());
    }

    /** 弹一块自定义文案的风险确认屏；点确认后执行 apply（通常顺带重建本页） */
    private void confirmThen(String subtitleKey, Runnable apply, String... bodyKeys) {
        if (this.client == null) {
            return;
        }
        this.client.setScreen(new DangerConfirmScreen(this, apply,
                "autofarm.danger.title", subtitleKey, bodyKeys));
    }

    /** 重建本页（草稿原样带过去）。用于标签文字变了、或者某几行要显隐切换的时候 */
    private void rebuild() {
        if (this.client != null) {
            this.client.setScreen(new ExtrasScreen(this.parent, draft));
        }
    }

    /**
     * 战斗分区底部的警示行（最多显示两条）。
     *
     * <p>三条触发条件：总开关没开（这时打怪根本不会生效）、出手间隔过低、
     * 攻击距离超过原版。它们都画成红色，作为「风险确认屏」之外的持续提醒 ——
     * 确认屏只在你动手改参数时出现，而这几行只要参数处在危险区间就一直亮着。
     */
    private List<Text> combatWarnings() {
        List<Text> out = new ArrayList<>();
        if (!draft.enabled) {
            out.add(Text.translatable("autofarm.combat.warn.master_off"));
        }
        if (draft.autoAttack) {
            if (draft.attackIntervalMs < AutoFarmConfig.ATTACK_INTERVAL_SAFE_MS) {
                out.add(Text.translatable("autofarm.combat.warn.interval_low"));
            }
            if (draft.attackRange > AutoFarmConfig.ATTACK_RANGE_VANILLA) {
                out.add(Text.translatable("autofarm.combat.warn.range_far"));
            }
        }
        return out;
    }

    /** 弹出风险确认屏；确认后写回草稿并重建本页（暴力行的显隐要跟着变） */
    private void openDangerConfirm(java.util.function.Consumer<Boolean> setter) {
        if (this.client == null) {
            return;
        }
        this.client.setScreen(new DangerConfirmScreen(this, () -> {
            setter.accept(true);
            this.client.setScreen(new ExtrasScreen(this.parent, draft));
        }));
    }

    /** 高风险分区的小标题：用警示色而不是通用标题色 */
    private void drawDangerHeader(DrawContext context, int originY, int relY) {
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("autofarm.extras.section.combat"),
                contentX + 6, originY + relY + 4, COLOR_DANGER);
    }

    /**
     * 战斗分区里那几段「不属于按钮」的自绘内容：
     * 攻击间隔左侧的选项名与右侧的范围提示、暴力行未解锁时的灰字、
     * 以及底部的红色警示行。
     */
    private void drawCombatExtras(DrawContext context, int originY) {
        int combat = originY + SEC_COMBAT_Y;

        // 攻击间隔：左边写选项名（输入框摆在中段，见 buildContent），右边写范围提示
        int intervalRow = combat + ROW_FIRST + ROW_GAP;
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("autofarm.option.attack_interval"),
                contentX + 4, intervalRow + 5, COLOR_DANGER);
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("autofarm.attack_interval.hint",
                        AutoFarmConfig.ATTACK_INTERVAL_MIN_MS, AutoFarmConfig.ATTACK_INTERVAL_MAX_MS),
                contentX + INTERVAL_LABEL_W + INTERVAL_FIELD_W + 8, intervalRow + 5, COLOR_HINT);

        // 暴力开关 / 暴力程度没解锁：把接下来两行的位置留给一句灰字说明
        if (!draft.autoAttack) {
            context.drawTextWithShadow(this.textRenderer,
                    Text.translatable("autofarm.extras.violent_locked"),
                    contentX + INDENT + 4, combat + ROW_FIRST + 4 * ROW_GAP + 5, COLOR_HINT);
        }

        // 底部警示：总开关没开 / 参数进入危险区间时标红（最多两条）
        List<Text> warnings = combatWarnings();
        int warnY = combat + ROW_FIRST + COMBAT_ROWS * ROW_GAP + 2;
        for (int i = 0; i < warnings.size() && i < 2; i++) {
            context.drawTextWithShadow(this.textRenderer, warnings.get(i),
                    contentX + 4, warnY + i * 11, COLOR_DANGER);
        }
    }

    /** 「选项：开/关」，但选项名染成红色 */
    private static Text dangerLabel(String optionKey, boolean value) {
        return Text.translatable("autofarm.toggle",
                Text.translatable(optionKey).formatted(Formatting.RED),
                Text.translatable(value ? "autofarm.on" : "autofarm.off"));
    }

    /** 从 draft 里读回当前开关值 —— 用来做「翻转」，保证和草稿永远一致 */
    private boolean currentToggle(String optionKey) {
        return switch (optionKey) {
            case "autofarm.option.auto_attack" -> draft.autoAttack;
            case "autofarm.option.auto_attack_violent" -> draft.autoAttackViolent;
            case "autofarm.option.pause_player" -> draft.pauseWhenPlayerNear;
            case "autofarm.option.humanize" -> draft.humanizeTiming;
            case "autofarm.option.random_rest" -> draft.randomRest;
            case "autofarm.option.replace_tool" -> draft.autoReplaceTool;
            case "autofarm.option.failure_cooldown" -> draft.failureCooldown;
            case "autofarm.option.verbose_hud" -> draft.verboseHud;
            case "autofarm.option.pickup_drops" -> draft.pickupDroppedItems;
            case "autofarm.option.antikick" -> draft.antiKickMode;
            case "autofarm.option.reaction_delay" -> draft.reactionDelay;
            case "autofarm.option.idle_look" -> draft.idleLookAround;
            case "autofarm.option.schedule" -> draft.scheduleEnabled;
            case "autofarm.option.work_area" -> draft.workAreaEnabled;
            case "autofarm.option.daylight" -> draft.workDaylightOnly;
            case "autofarm.option.clear_weather" -> draft.workClearWeatherOnly;
            case "autofarm.option.fatigue" -> draft.fatigueStop;
            default -> false;
        };
    }

    private void layout(ButtonWidget widget, int x, int y, int width) {
        widget.setX(x);
        widget.setY(y);
        widget.setWidth(width);
        addScrolling(widget);
    }

    /**
     * 覆写：在通用的「滚出可视区就隐藏」之上，再叠一条本页自己的规则 ——
     * 暴力模式是二级开关，主子项关着时它整个不该出现（原位留一行灰字说明）。
     */
    @Override
    protected void relayout() {
        super.relayout();
        if (violentButton != null) {
            violentButton.visible = violentButton.visible && draft.autoAttack;
        }
        // 暴力程度是「暴力模式」的子项：暴力模式没开时它也不该出现
        if (violenceLevelButton != null) {
            violenceLevelButton.visible = violenceLevelButton.visible
                    && draft.autoAttack && draft.autoAttackViolent;
        }
        // 工作范围的两种圈法共用同样两行：圆形显示「半径 / 记录中心」，
        // 矩形显示「记录角点 A / 记录角点 B」，同一时刻只让一套露出来
        boolean box = draft.workAreaMode == AutoFarmConfig.AREA_MODE_BOX;
        if (workAreaRadiusButton != null) {
            workAreaRadiusButton.visible = workAreaRadiusButton.visible && !box;
        }
        if (workAreaCenterButton != null) {
            workAreaCenterButton.visible = workAreaCenterButton.visible && !box;
        }
        if (workAreaCornerAButton != null) {
            workAreaCornerAButton.visible = workAreaCornerAButton.visible && box;
        }
        if (workAreaCornerBButton != null) {
            workAreaCornerBButton.visible = workAreaCornerBButton.visible && box;
        }
    }

    @Override
    protected void positionContent(int originY) {
        int combat = originY + SEC_COMBAT_Y;
        setRowY(combatButton, combat + ROW_FIRST);
        setRowY(attackIntervalField, combat + ROW_FIRST + ROW_GAP);
        setRowY(attackRangeButton, combat + ROW_FIRST + 2 * ROW_GAP);
        setRowY(attackTargetButton, combat + ROW_FIRST + 3 * ROW_GAP);
        setRowY(violentButton, combat + ROW_FIRST + 4 * ROW_GAP);
        setRowY(violenceLevelButton, combat + ROW_FIRST + 5 * ROW_GAP);

        int stealth = originY + SEC_STEALTH_Y;
        setRowY(pausePlayerButton, stealth + ROW_FIRST);
        setRowY(radiusButton, stealth + ROW_FIRST + ROW_GAP);
        setRowY(humanizeButton, stealth + ROW_FIRST + 2 * ROW_GAP);
        setRowY(randomRestButton, stealth + ROW_FIRST + 3 * ROW_GAP);
        setRowY(restIntervalButton, stealth + ROW_FIRST + 4 * ROW_GAP);
        setRowY(restDurationButton, stealth + ROW_FIRST + 5 * ROW_GAP);

        int antiKick = originY + SEC_ANTIKICK_Y;
        setRowY(antiKickButton, antiKick + ROW_FIRST);
        setRowY(antiKickLevelButton, antiKick + ROW_FIRST + ROW_GAP);

        int behavior = originY + SEC_BEHAVIOR_Y;
        setRowY(reactionDelayButton, behavior + ROW_FIRST);
        setRowY(reactionTimeButton, behavior + ROW_FIRST + ROW_GAP);
        setRowY(idleLookButton, behavior + ROW_FIRST + 2 * ROW_GAP);
        setRowY(scheduleButton, behavior + ROW_FIRST + 3 * ROW_GAP);
        setRowY(scheduleStartButton, behavior + ROW_FIRST + 4 * ROW_GAP);
        setRowY(scheduleEndButton, behavior + ROW_FIRST + 5 * ROW_GAP);

        int work = originY + SEC_WORKAREA_Y;
        setRowY(workAreaButton, work + ROW_FIRST);
        setRowY(workAreaModeButton, work + ROW_FIRST + ROW_GAP);
        // 第 3 行：圆形 = 半径，矩形 = 角点 A（同一行，用 visible 二选一）
        setRowY(workAreaRadiusButton, work + ROW_FIRST + 2 * ROW_GAP);
        setRowY(workAreaCornerAButton, work + ROW_FIRST + 2 * ROW_GAP);
        // 第 4 行：圆形 = 记录中心，矩形 = 角点 B
        setRowY(workAreaCenterButton, work + ROW_FIRST + 3 * ROW_GAP);
        setRowY(workAreaCornerBButton, work + ROW_FIRST + 3 * ROW_GAP);

        int env = originY + SEC_ENV_Y;
        setRowY(daylightButton, env + ROW_FIRST);
        setRowY(weatherButton, env + ROW_FIRST + ROW_GAP);
        setRowY(fatigueButton, env + ROW_FIRST + 2 * ROW_GAP);
        setRowY(fatigueWorkButton, env + ROW_FIRST + 3 * ROW_GAP);
        setRowY(fatigueRestButton, env + ROW_FIRST + 4 * ROW_GAP);

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
        drawSection(context, originY, SEC_COMBAT_Y, SEC_COMBAT_H);
        drawDangerHeader(context, originY, SEC_COMBAT_Y);
        drawCombatExtras(context, originY);

        drawSection(context, originY, SEC_STEALTH_Y, SEC_STEALTH_H);
        drawHeader(context, originY, SEC_STEALTH_Y, "autofarm.extras.section.stealth");

        drawSection(context, originY, SEC_ANTIKICK_Y, SEC_ANTIKICK_H);
        drawHeader(context, originY, SEC_ANTIKICK_Y, "autofarm.extras.section.antikick");

        drawSection(context, originY, SEC_BEHAVIOR_Y, SEC_BEHAVIOR_H);
        drawHeader(context, originY, SEC_BEHAVIOR_Y, "autofarm.extras.section.behavior");

        drawSection(context, originY, SEC_WORKAREA_Y, SEC_WORKAREA_H);
        drawHeader(context, originY, SEC_WORKAREA_Y, "autofarm.extras.section.workarea");
        // 最后一行自绘一句说明：区域外的目标不收、人走出去会自动返回
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("autofarm.work_area.hint"),
                contentX + INDENT + 4, originY + SEC_WORKAREA_Y + ROW_FIRST + 4 * ROW_GAP + 5, COLOR_HINT);

        drawSection(context, originY, SEC_ENV_Y, SEC_ENV_H);
        drawHeader(context, originY, SEC_ENV_Y, "autofarm.extras.section.environment");

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
        draft.autoAttack = false;
        draft.autoAttackViolent = false;
        draft.attackViolence = AutoFarmConfig.VIOLENCE_LIGHT;
        draft.attackIntervalMs = 800;
        draft.attackRange = 2.9;
        draft.attackHostileOnly = true;
        draft.pauseWhenPlayerNear = false;
        draft.playerNearRadius = 16;
        draft.antiKickMode = false;
        draft.antiKickLevel = 2;
        draft.reactionDelay = false;
        draft.reactionDelayTicks = 10;
        draft.idleLookAround = false;
        draft.scheduleEnabled = false;
        draft.scheduleStartHour = 8;
        draft.scheduleEndHour = 23;
        // 工作范围：只把开关和参数复原，坐标留着 —— 那是玩家亲手记的，不该被「全部关闭」抹掉
        draft.workAreaEnabled = false;
        draft.workAreaMode = AutoFarmConfig.AREA_MODE_RADIUS;
        draft.workAreaRadius = 24;
        draft.workDaylightOnly = false;
        draft.workClearWeatherOnly = false;
        draft.fatigueStop = false;
        draft.fatigueWorkMinutes = 60;
        draft.fatigueRestMinutes = 15;
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
        rebuild();
    }

    @Override
    protected void onCancel() {
        // 只还原附加功能这几项，草稿里的其他设置（扫描半径、存放策略……）保持不动
        draft.autoAttack = old.autoAttack;
        draft.autoAttackViolent = old.autoAttackViolent;
        draft.attackViolence = old.attackViolence;
        draft.attackIntervalMs = old.attackIntervalMs;
        draft.attackRange = old.attackRange;
        draft.attackHostileOnly = old.attackHostileOnly;
        draft.pauseWhenPlayerNear = old.pauseWhenPlayerNear;
        draft.playerNearRadius = old.playerNearRadius;
        draft.antiKickMode = old.antiKickMode;
        draft.antiKickLevel = old.antiKickLevel;
        draft.reactionDelay = old.reactionDelay;
        draft.reactionDelayTicks = old.reactionDelayTicks;
        draft.idleLookAround = old.idleLookAround;
        draft.scheduleEnabled = old.scheduleEnabled;
        draft.scheduleStartHour = old.scheduleStartHour;
        draft.scheduleEndHour = old.scheduleEndHour;
        draft.workAreaEnabled = old.workAreaEnabled;
        draft.workAreaMode = old.workAreaMode;
        draft.workAreaX = old.workAreaX;
        draft.workAreaY = old.workAreaY;
        draft.workAreaZ = old.workAreaZ;
        draft.workAreaRadius = old.workAreaRadius;
        draft.workAreaX2 = old.workAreaX2;
        draft.workAreaY2 = old.workAreaY2;
        draft.workAreaZ2 = old.workAreaZ2;
        draft.workDaylightOnly = old.workDaylightOnly;
        draft.workClearWeatherOnly = old.workClearWeatherOnly;
        draft.fatigueStop = old.fatigueStop;
        draft.fatigueWorkMinutes = old.fatigueWorkMinutes;
        draft.fatigueRestMinutes = old.fatigueRestMinutes;
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

    /** 小数版的循环取下一档（攻击距离用）。比较带一点容差，免得浮点误差选不中 */
    private static double nextChoice(double[] choices, double current) {
        for (int i = 0; i < choices.length; i++) {
            if (Math.abs(choices[i] - current) < 1e-6) {
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

    private Text attackRangeLabel() {
        return optionLabel("autofarm.option.attack_range",
                Text.translatable("autofarm.value.blocks", String.format("%.1f", draft.attackRange)));
    }

    private Text attackTargetLabel() {
        return optionLabel("autofarm.option.attack_target",
                Text.translatable(draft.attackHostileOnly
                        ? "autofarm.attack_target.hostile"
                        : "autofarm.attack_target.all"));
    }

    private Text violenceLabel() {
        return optionLabel("autofarm.option.attack_violence",
                Text.translatable("autofarm.attack_violence.level." + violenceKey(draft.attackViolence)));
    }

    /** 暴力程度档位 → 语言键后缀。认不出来的一律当「轻度」显示 */
    private static String violenceKey(int level) {
        return switch (level) {
            case 1 -> "medium";
            case 2 -> "lifesaver";
            default -> "light";
        };
    }

    private Text antiKickLevelLabel() {
        return optionLabel("autofarm.option.antikick_level",
                Text.translatable("autofarm.antikick.level." + levelKey(draft.antiKickLevel)));
    }

    private Text reactionTimeLabel() {
        return optionLabel("autofarm.option.reaction_time",
                Text.translatable("autofarm.value.seconds",
                        String.format("%.1f", draft.reactionDelayTicks / 20.0)));
    }

    private Text scheduleStartLabel() {
        return optionLabel("autofarm.option.schedule_start", hourText(draft.scheduleStartHour));
    }

    private Text scheduleEndLabel() {
        return optionLabel("autofarm.option.schedule_end", hourText(draft.scheduleEndHour));
    }

    private static Text hourText(int hour) {
        return Text.translatable("autofarm.value.hour", String.format("%02d", hour));
    }

    private Text workAreaModeLabel() {
        return optionLabel("autofarm.option.work_area_mode",
                Text.translatable(draft.workAreaMode == AutoFarmConfig.AREA_MODE_BOX
                        ? "autofarm.area_mode.box"
                        : "autofarm.area_mode.radius"));
    }

    private Text workAreaRadiusLabel() {
        return optionLabel("autofarm.option.work_area_radius",
                Text.translatable("autofarm.value.blocks", draft.workAreaRadius));
    }

    private Text workAreaCenterLabel() {
        return Text.translatable("autofarm.work_area.record_center", coordText(draft.workAreaText()));
    }

    private Text workAreaCornerALabel() {
        return Text.translatable("autofarm.work_area.record_a", coordText(draft.workAreaText()));
    }

    private Text workAreaCornerBLabel() {
        return Text.translatable("autofarm.work_area.record_b", coordText(draft.workAreaSecondText()));
    }

    /** 坐标文本：没记过就显示「未设置」 */
    private static Text coordText(String text) {
        return text == null ? Text.translatable("autofarm.work_area.none") : Text.literal(text);
    }

    private Text fatigueWorkLabel() {
        return optionLabel("autofarm.option.fatigue_work",
                Text.translatable("autofarm.value.minutes", draft.fatigueWorkMinutes));
    }

    private Text fatigueRestLabel() {
        return optionLabel("autofarm.option.fatigue_rest",
                draft.fatigueRestMinutes <= 0
                        ? Text.translatable("autofarm.fatigue_rest.manual")
                        : Text.translatable("autofarm.value.minutes", draft.fatigueRestMinutes));
    }

    /** 把玩家当前所在的那一格记成工作区域的一个点（圆形 = 中心，矩形 = 角点） */
    private void recordCurrentPos(boolean second) {
        if (this.client == null || this.client.player == null) {
            return;
        }
        BlockPos pos = this.client.player.getBlockPos();
        if (second) {
            draft.setWorkAreaSecond(pos.getX(), pos.getY(), pos.getZ());
        } else {
            draft.setWorkArea(pos.getX(), pos.getY(), pos.getZ());
        }
    }

    /** 档位数字 → 语言键后缀。认不出来的档位一律当「关闭」显示 */
    private static String levelKey(int level) {
        return switch (level) {
            case 1 -> "light";
            case 2 -> "medium";
            case 3 -> "high";
            default -> "off";
        };
    }
}
