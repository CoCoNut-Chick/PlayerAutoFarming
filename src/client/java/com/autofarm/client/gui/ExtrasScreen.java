package com.autofarm.client.gui;

import com.autofarm.client.config.AutoFarmConfig;
import com.autofarm.client.scan.CropRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 「附加功能」次级面板 —— 仿 Mod Menu 的<b>顶部大类 tab</b>布局。
 *
 * <h2>为什么改成分类 tab</h2>
 * 附加功能越加越多（现在十三个分区、几十个选项），一张无限长的滚动页要找半天。
 * 改成顶部一排大类，点一下只看到那一类的几个开关，和 Mod Menu 的「分类 → 内容」一致。
 *
 * <p>五个大类：
 * <ol>
 *   <li><b>战斗</b> —— 自动打怪（高风险，警示配色 + 风险确认屏）；</li>
 *   <li><b>隐蔽</b> —— 玩家接近停机、防踢模式、行为真实感；</li>
 *   <li><b>范围</b> —— 圈定工作区域、环境与作息；</li>
 *   <li><b>生存</b> —— 危险撤退、受击反制、环境自救、掉线重连、工具维护、容错统计、掉落物；</li>
 *   <li><b>资源</b> —— 瓜类与自定义作物、资源自给、交互便利。</li>
 * </ol>
 *
 * <h2>和主面板的关系</h2>
 * 老规矩：它编辑的是主面板递进来的<b>同一个草稿</b>。点「完成」回主面板，
 * 最终由主面板的「保存并关闭」落盘；点「取消」或 Esc 只把这里的十几项
 * 恢复成进来之前的样子，不碰草稿里的其他设置。
 */
public class ExtrasScreen extends ScrollPanelScreen {

    // ---------------- tab 定义 ----------------
    private static final int TAB_COMBAT = 0;
    private static final int TAB_STEALTH = 1;
    private static final int TAB_AREA = 2;
    private static final int TAB_SURVIVAL = 3;
    private static final int TAB_RESOURCE = 4;
    private static final int TAB_COUNT = 5;

    // ---------------- 内容区布局（相对内容区顶部） ----------------
    /** 分区内第一行相对分区顶部的 y（让开标题那一行） */
    private static final int ROW_FIRST = 18;
    /** 相邻两行的间距 */
    private static final int ROW_GAP = 20;
    /** 两个分区之间的留白 */
    private static final int SECTION_GAP = 6;
    /** 参数行的左缩进，视觉上表示「从属于上一行」 */
    private static final int INDENT = 8;
    /** 攻击间隔行的左侧标签宽度 / 输入框宽度 */
    private static final int INTERVAL_LABEL_W = 60;
    private static final int INTERVAL_FIELD_W = 56;
    /** 「转向时间」输入框右边的「确认」按钮：宽度与左侧留白 */
    private static final int TURN_OK_W = 46;
    private static final int TURN_OK_GAP = 4;

    // ---- 战斗（tab 0）----
    private static final int COMBAT_ROWS = 6;
    private static final int COMBAT_WARN_H = 26;
    private static final int SEC_COMBAT_Y = TAB_BAR_H;
    private static final int SEC_COMBAT_H = ROW_FIRST + COMBAT_ROWS * ROW_GAP + COMBAT_WARN_H;

    // ---- 隐蔽（tab 1）----
    private static final int STEALTH_ROWS = 6;
    private static final int SEC_STEALTH_Y = TAB_BAR_H;
    private static final int SEC_STEALTH_H = ROW_FIRST + STEALTH_ROWS * ROW_GAP + 4;
    private static final int ANTIKICK_ROWS = 2;
    private static final int SEC_ANTIKICK_Y = SEC_STEALTH_Y + SEC_STEALTH_H + SECTION_GAP;
    private static final int SEC_ANTIKICK_H = ROW_FIRST + ANTIKICK_ROWS * ROW_GAP + 4;
    private static final int BEHAVIOR_ROWS = 8;
    private static final int SEC_BEHAVIOR_Y = SEC_ANTIKICK_Y + SEC_ANTIKICK_H + SECTION_GAP;
    /** 底部留一点给「转向时间过小」的警告行 */
    private static final int SEC_BEHAVIOR_H = ROW_FIRST + BEHAVIOR_ROWS * ROW_GAP + 16;

    // ---- 范围（tab 2）----
    private static final int WORKAREA_ROWS = 5;
    private static final int SEC_WORKAREA_Y = TAB_BAR_H;
    private static final int SEC_WORKAREA_H = ROW_FIRST + WORKAREA_ROWS * ROW_GAP + 4;
    private static final int ENV_ROWS = 5;
    private static final int SEC_ENV_Y = SEC_WORKAREA_Y + SEC_WORKAREA_H + SECTION_GAP;
    private static final int SEC_ENV_H = ROW_FIRST + ENV_ROWS * ROW_GAP + 4;

    // ---- 生存（tab 3）----
    private static final int SURVIVAL_ROWS = 9;
    private static final int SEC_SURVIVAL_Y = TAB_BAR_H;
    private static final int SEC_SURVIVAL_H = ROW_FIRST + SURVIVAL_ROWS * ROW_GAP + 4;
    private static final int TOOL_ROWS = 2;
    private static final int SEC_TOOL_Y = SEC_SURVIVAL_Y + SEC_SURVIVAL_H + SECTION_GAP;
    private static final int SEC_TOOL_H = ROW_FIRST + TOOL_ROWS * ROW_GAP + 4;
    private static final int MISC_ROWS = 3;
    private static final int SEC_MISC_Y = SEC_TOOL_Y + SEC_TOOL_H + SECTION_GAP;
    private static final int SEC_MISC_H = ROW_FIRST + MISC_ROWS * ROW_GAP + 4;
    private static final int PICKUP_ROWS = 2;
    private static final int SEC_PICKUP_Y = SEC_MISC_Y + SEC_MISC_H + SECTION_GAP;
    private static final int SEC_PICKUP_H = ROW_FIRST + PICKUP_ROWS * ROW_GAP + 4;

    // ---- 资源（tab 4）----
    private static final int GOURD_ROWS = 4;
    private static final int SEC_GOURD_Y = TAB_BAR_H;
    private static final int SEC_GOURD_H = ROW_FIRST + GOURD_ROWS * ROW_GAP + 4;
    private static final int SUPPLY_ROWS = 5;
    private static final int SEC_SUPPLY_Y = SEC_GOURD_Y + SEC_GOURD_H + SECTION_GAP;
    private static final int SEC_SUPPLY_H = ROW_FIRST + SUPPLY_ROWS * ROW_GAP + 4;
    private static final int NOTIFY_ROWS = 2;
    private static final int SEC_NOTIFY_Y = SEC_SUPPLY_Y + SEC_SUPPLY_H + SECTION_GAP;
    private static final int SEC_NOTIFY_H = ROW_FIRST + NOTIFY_ROWS * ROW_GAP + 4;

    // ---------------- 循环档位（都含出厂默认值，见 AutoFarmConfig） ----------------
    private static final int[] RADIUS_CHOICES = {8, 12, 16, 24, 32};
    private static final int[] REST_INTERVAL_CHOICES = {5, 10, 15, 20, 30};
    private static final int[] REST_DURATION_CHOICES = {10, 20, 30, 60};
    private static final int[] DURABILITY_CHOICES = {5, 10, 15, 20, 30};
    private static final int[] COOLDOWN_CHOICES = {5, 10, 15, 30, 60};
    private static final int[] PICKUP_DELAY_CHOICES = {1, 2, 3, 5};
    private static final double[] ATTACK_RANGE_CHOICES = {2.5, 2.9, 3.5, 4.5, 6.0};
    private static final int[] VIOLENCE_CHOICES = {0, 1, 2};
    private static final int[] ANTIKICK_LEVEL_CHOICES = {0, 1, 2, 3};
    private static final int[] REACTION_CHOICES = {4, 10, 20, 30};
    private static final int[] HOUR_CHOICES = buildHours();
    private static final int[] AREA_MODE_CHOICES = {0, 1};
    private static final int[] WORK_AREA_RADIUS_CHOICES = {8, 12, 16, 24, 32, 48};
    private static final int[] FATIGUE_WORK_CHOICES = {15, 30, 60, 120, 240};
    private static final int[] FATIGUE_REST_CHOICES = {0, 5, 10, 15, 30, 60};
    private static final int[] RETREAT_HEALTH_CHOICES = {3, 4, 5, 6, 8, 10};
    private static final int[] RETREAT_DISTANCE_CHOICES = {8, 12, 16, 24, 32};
    private static final int[] HIT_REACTION_CHOICES = {1, 2, 3, 5};
    private static final int[] RECONNECT_DELAY_CHOICES = {5, 10, 20, 30, 60};
    private static final int[] RECONNECT_ATTEMPT_CHOICES = {0, 3, 5, 10, 20};
    private static final int[] BONE_MEAL_LIMIT_CHOICES = {1, 2, 4, 6, 10};
    private static final int[] BONE_MEAL_THRESHOLD_CHOICES = {8, 16, 32, 64};

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

    private int currentTab = TAB_COMBAT;
    private ButtonWidget[] tabButtons = new ButtonWidget[TAB_COUNT];

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
    private TextFieldWidget turnTimeField;
    /** 「转向时间」右边那颗确认按钮 */
    private ButtonWidget turnTimeOkButton;
    /**
     * 「转向时间」输入框里有没有一个<b>还没确认的危险值</b>（小于安全阈值、含 0）。
     *
     * <p>危险值不会直接写进草稿 —— 必须先按 Enter 或点「确认」，过一次风险提示屏才生效。
     * 这样「填 0 然后保存」就不可能悄悄发生：要么在离页前被拦下来确认，要么根本没写进去。
     */
    private boolean turnTimePending;
    private ButtonWidget instantActionsButton;
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
    private ButtonWidget retreatButton;
    private ButtonWidget retreatHealthButton;
    private ButtonWidget retreatDistanceButton;
    private ButtonWidget hitReactionButton;
    private ButtonWidget hitReactionTimeButton;
    private ButtonWidget selfRescueButton;
    private ButtonWidget reconnectButton;
    private ButtonWidget reconnectDelayButton;
    private ButtonWidget reconnectAttemptsButton;
    private ButtonWidget melonHarvestButton;
    private ButtonWidget addCustomBlockButton;
    private ButtonWidget customBlocksButton;
    private ButtonWidget boneMealBoostButton;
    private ButtonWidget boneMealLimitButton;
    private ButtonWidget autoSuppliesButton;
    private ButtonWidget boneMealThresholdButton;
    private ButtonWidget autoFetchToolButton;
    private ButtonWidget notifySoundsButton;
    private ButtonWidget notifyToastsButton;

    public ExtrasScreen(Screen parent, AutoFarmConfig sharedDraft) {
        super(parent, Text.translatable("autofarm.extras.title"));
        this.draft = sharedDraft;
        this.old = sharedDraft.copy();
    }

    @Override
    protected int contentHeight() {
        return switch (currentTab) {
            case TAB_COMBAT -> SEC_COMBAT_Y + SEC_COMBAT_H + 8;
            case TAB_STEALTH -> SEC_BEHAVIOR_Y + SEC_BEHAVIOR_H + 8;
            case TAB_AREA -> SEC_ENV_Y + SEC_ENV_H + 8;
            case TAB_SURVIVAL -> SEC_PICKUP_Y + SEC_PICKUP_H + 8;
            case TAB_RESOURCE -> SEC_NOTIFY_Y + SEC_NOTIFY_H + 8;
            default -> TAB_BAR_H + 8;
        };
    }

    // ================= 建控件 =================

    @Override
    protected void buildContent() {
        buildTabBar();
        switch (currentTab) {
            case TAB_COMBAT -> buildCombatTab();
            case TAB_STEALTH -> buildStealthTab();
            case TAB_AREA -> buildAreaTab();
            case TAB_SURVIVAL -> buildSurvivalTab();
            case TAB_RESOURCE -> buildResourceTab();
            default -> {
            }
        }

        int btnW = (contentWidth - 8) / 3;
        addFooter(
                ButtonWidget.builder(Text.translatable("autofarm.extras.reset"), b -> resetAllOff())
                        .dimensions(contentX, 0, btnW, ROW_H).build(),
                ButtonWidget.builder(Text.translatable("autofarm.button.cancel"), b -> onCancel())
                        .dimensions(contentX + btnW + 4, 0, btnW, ROW_H).build(),
                ButtonWidget.builder(Text.translatable("autofarm.highlight.done"), b -> onDone())
                        .dimensions(contentX + 2 * (btnW + 4), 0, btnW, ROW_H).build()
        );
    }

    /** 顶部那条大类 tab 栏 */
    private void buildTabBar() {
        int tabW = (contentWidth - (TAB_COUNT - 1) * 2) / TAB_COUNT;
        for (int i = 0; i < TAB_COUNT; i++) {
            final int tab = i;
            ButtonWidget btn = ButtonWidget.builder(tabLabel(tab), b -> switchTab(tab))
                    .dimensions(contentX + i * (tabW + 2), 0, tabW, ROW_H).build();
            addScrolling(btn);
            tabButtons[i] = btn;
        }
    }

    private void switchTab(int tab) {
        if (tab == currentTab) {
            return;
        }
        // 切 tab = 换一页：重建内容（滚动回到顶部是合理的），草稿原样保留
        if (this.client != null) {
            ExtrasScreen next = new ExtrasScreen(this.parent, draft);
            next.currentTab = tab;
            this.client.setScreen(next);
        }
    }

    private static Text tabLabel(int tab) {
        return Text.translatable("autofarm.extras.tab." + switch (tab) {
            case TAB_STEALTH -> "stealth";
            case TAB_AREA -> "area";
            case TAB_SURVIVAL -> "survival";
            case TAB_RESOURCE -> "resource";
            default -> "combat";
        });
    }

    // ---- 各 tab 的控件创建 ----

    private void buildCombatTab() {
        int fullW = contentWidth;
        int subX = contentX + INDENT;
        int subW = contentWidth - INDENT;

        this.combatButton = dangerToggle("autofarm.option.auto_attack",
                draft.autoAttack, value -> draft.autoAttack = value,
                "autofarm.auto_attack.tip");
        layout(combatButton, contentX, 0, fullW);

        this.attackIntervalField = intervalField(contentX + INTERVAL_LABEL_W);
        addScrolling(attackIntervalField);

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

        this.violentButton = dangerToggle("autofarm.option.auto_attack_violent",
                draft.autoAttackViolent, value -> draft.autoAttackViolent = value,
                "autofarm.auto_attack_violent.tip");
        layout(violentButton, subX, 0, subW);

        this.violenceLevelButton = ButtonWidget.builder(violenceLabel(), b -> onViolenceClicked())
                .dimensions(subX, 0, subW, ROW_H).build();
        violenceLevelButton.setTooltip(Tooltip.of(Text.translatable("autofarm.attack_violence.tip")));
        addScrolling(violenceLevelButton);
    }

    private void buildStealthTab() {
        int fullW = contentWidth;
        int subX = contentX + INDENT;
        int subW = contentWidth - INDENT;

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

        // 「丝滑转向时间」：填空，只收数字（毫秒）。0 = 不缓动。越界由 clamp() 兜底。
        this.turnTimeField = new TextFieldWidget(this.textRenderer,
                subX + INTERVAL_LABEL_W, 0, INTERVAL_FIELD_W, ROW_H, Text.empty());
        turnTimeField.setMaxLength(4);
        turnTimeField.setTextPredicate(s -> s.isEmpty() || s.matches("\\d{1,4}"));
        turnTimeField.setText(Integer.toString(draft.turnDurationMs));
        turnTimeField.setChangedListener(text -> onTurnTimeEdited(text));
        turnTimeField.setTooltip(Tooltip.of(Text.translatable("autofarm.turn_time.tip")));
        addScrolling(turnTimeField);

        // 输入框右边的「确认」：把刚填的值定下来。危险的（含 0）会弹一次风险提示屏。
        // 和直接按 Enter 是同一件事，看玩家顺手用哪个。
        this.turnTimeOkButton = ButtonWidget.builder(Text.translatable("autofarm.turn_time.ok"),
                        b -> commitTurnTime())
                .dimensions(subX + INTERVAL_LABEL_W + INTERVAL_FIELD_W + TURN_OK_GAP, 0, TURN_OK_W, ROW_H)
                .build();
        turnTimeOkButton.setTooltip(Tooltip.of(Text.translatable("autofarm.turn_time.ok.tip")));
        addScrolling(turnTimeOkButton);

        this.instantActionsButton = toggle("autofarm.option.instant_actions",
                draft.instantActions, value -> draft.instantActions = value,
                "autofarm.instant_actions.tip");
        layout(instantActionsButton, contentX, 0, fullW);

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
    }

    private void buildAreaTab() {
        int fullW = contentWidth;
        int subX = contentX + INDENT;
        int subW = contentWidth - INDENT;

        this.workAreaButton = toggle("autofarm.option.work_area",
                draft.workAreaEnabled, value -> draft.workAreaEnabled = value,
                "autofarm.work_area.tip");
        layout(workAreaButton, contentX, 0, fullW);

        this.workAreaModeButton = ButtonWidget.builder(workAreaModeLabel(), b -> {
            draft.workAreaMode = nextChoice(AREA_MODE_CHOICES, draft.workAreaMode);
            b.setMessage(workAreaModeLabel());
            // 半径/角点行的显隐和标签跟着变，但<b>不重建整页</b>（否则滚动位置会丢回顶部）
            workAreaRadiusButton.setMessage(workAreaRadiusLabel());
            workAreaCenterButton.setMessage(workAreaCenterLabel());
            workAreaCornerAButton.setMessage(workAreaCornerALabel());
            workAreaCornerBButton.setMessage(workAreaCornerBLabel());
            relayout();
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
    }

    private void buildSurvivalTab() {
        int fullW = contentWidth;
        int subX = contentX + INDENT;
        int subW = contentWidth - INDENT;

        this.retreatButton = toggle("autofarm.option.retreat",
                draft.retreatEnabled, value -> draft.retreatEnabled = value,
                "autofarm.retreat.tip");
        layout(retreatButton, contentX, 0, fullW);

        this.retreatHealthButton = ButtonWidget.builder(retreatHealthLabel(), b -> {
            draft.retreatHealthHearts = nextChoice(RETREAT_HEALTH_CHOICES, draft.retreatHealthHearts);
            b.setMessage(retreatHealthLabel());
        }).dimensions(subX, 0, subW, ROW_H).build();
        retreatHealthButton.setTooltip(Tooltip.of(Text.translatable("autofarm.retreat_health.tip")));
        addScrolling(retreatHealthButton);

        this.retreatDistanceButton = ButtonWidget.builder(retreatDistanceLabel(), b -> {
            draft.retreatDistance = nextChoice(RETREAT_DISTANCE_CHOICES, draft.retreatDistance);
            b.setMessage(retreatDistanceLabel());
        }).dimensions(subX, 0, subW, ROW_H).build();
        retreatDistanceButton.setTooltip(Tooltip.of(Text.translatable("autofarm.retreat_distance.tip")));
        addScrolling(retreatDistanceButton);

        this.hitReactionButton = toggle("autofarm.option.hit_reaction",
                draft.hitReactionEnabled, value -> draft.hitReactionEnabled = value,
                "autofarm.hit_reaction.tip");
        layout(hitReactionButton, contentX, 0, fullW);

        this.hitReactionTimeButton = ButtonWidget.builder(hitReactionTimeLabel(), b -> {
            draft.hitReactionSeconds = nextChoice(HIT_REACTION_CHOICES, draft.hitReactionSeconds);
            b.setMessage(hitReactionTimeLabel());
        }).dimensions(subX, 0, subW, ROW_H).build();
        hitReactionTimeButton.setTooltip(Tooltip.of(Text.translatable("autofarm.hit_reaction_time.tip")));
        addScrolling(hitReactionTimeButton);

        this.selfRescueButton = toggle("autofarm.option.self_rescue",
                draft.selfRescueEnabled, value -> draft.selfRescueEnabled = value,
                "autofarm.self_rescue.tip");
        layout(selfRescueButton, contentX, 0, fullW);

        this.reconnectButton = toggle("autofarm.option.auto_reconnect",
                draft.autoReconnectEnabled, value -> draft.autoReconnectEnabled = value,
                "autofarm.auto_reconnect.tip");
        layout(reconnectButton, contentX, 0, fullW);

        this.reconnectDelayButton = ButtonWidget.builder(reconnectDelayLabel(), b -> {
            draft.reconnectDelaySeconds = nextChoice(RECONNECT_DELAY_CHOICES, draft.reconnectDelaySeconds);
            b.setMessage(reconnectDelayLabel());
        }).dimensions(subX, 0, subW, ROW_H).build();
        reconnectDelayButton.setTooltip(Tooltip.of(Text.translatable("autofarm.reconnect_delay.tip")));
        addScrolling(reconnectDelayButton);

        this.reconnectAttemptsButton = ButtonWidget.builder(reconnectAttemptsLabel(), b -> {
            draft.reconnectMaxAttempts = nextChoice(RECONNECT_ATTEMPT_CHOICES, draft.reconnectMaxAttempts);
            b.setMessage(reconnectAttemptsLabel());
        }).dimensions(subX, 0, subW, ROW_H).build();
        reconnectAttemptsButton.setTooltip(Tooltip.of(Text.translatable("autofarm.reconnect_attempts.tip")));
        addScrolling(reconnectAttemptsButton);

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
    }

    private void buildResourceTab() {
        int fullW = contentWidth;
        int subX = contentX + INDENT;
        int subW = contentWidth - INDENT;

        this.melonHarvestButton = toggle("autofarm.option.melon_harvest",
                draft.melonHarvest, value -> draft.melonHarvest = value,
                "autofarm.melon_harvest.tip");
        layout(melonHarvestButton, contentX, 0, fullW);

        this.addCustomBlockButton = ButtonWidget.builder(
                        Text.translatable("autofarm.option.add_custom_block"),
                        b -> addLookedBlock())
                .dimensions(subX, 0, subW, ROW_H).build();
        addCustomBlockButton.setTooltip(Tooltip.of(Text.translatable("autofarm.add_custom_block.tip")));
        addScrolling(addCustomBlockButton);

        this.customBlocksButton = ButtonWidget.builder(customBlocksLabel(), b -> clearCustomBlocks())
                .dimensions(subX, 0, subW, ROW_H).build();
        customBlocksButton.setTooltip(Tooltip.of(Text.translatable("autofarm.custom_blocks.tip")));
        addScrolling(customBlocksButton);

        this.boneMealBoostButton = toggle("autofarm.option.bone_meal",
                draft.boneMealBoost, value -> draft.boneMealBoost = value,
                "autofarm.bone_meal.tip");
        layout(boneMealBoostButton, contentX, 0, fullW);

        this.boneMealLimitButton = ButtonWidget.builder(boneMealLimitLabel(), b -> {
            draft.boneMealLimit = nextChoice(BONE_MEAL_LIMIT_CHOICES, draft.boneMealLimit);
            b.setMessage(boneMealLimitLabel());
        }).dimensions(subX, 0, subW, ROW_H).build();
        boneMealLimitButton.setTooltip(Tooltip.of(Text.translatable("autofarm.bone_meal_limit.tip")));
        addScrolling(boneMealLimitButton);

        this.autoSuppliesButton = toggle("autofarm.option.auto_supplies",
                draft.autoFetchSupplies, value -> draft.autoFetchSupplies = value,
                "autofarm.auto_supplies.tip");
        layout(autoSuppliesButton, contentX, 0, fullW);

        this.boneMealThresholdButton = ButtonWidget.builder(boneMealThresholdLabel(), b -> {
            draft.boneMealThreshold = nextChoice(BONE_MEAL_THRESHOLD_CHOICES, draft.boneMealThreshold);
            b.setMessage(boneMealThresholdLabel());
        }).dimensions(subX, 0, subW, ROW_H).build();
        boneMealThresholdButton.setTooltip(Tooltip.of(
                Text.translatable("autofarm.bone_meal_threshold.tip")));
        addScrolling(boneMealThresholdButton);

        this.autoFetchToolButton = toggle("autofarm.option.auto_fetch_tool",
                draft.autoFetchTool, value -> draft.autoFetchTool = value,
                "autofarm.auto_fetch_tool.tip");
        layout(autoFetchToolButton, contentX, 0, fullW);

        this.notifySoundsButton = toggle("autofarm.option.notify_sounds",
                draft.notifySounds, value -> draft.notifySounds = value,
                "autofarm.notify_sounds.tip");
        layout(notifySoundsButton, contentX, 0, fullW);

        this.notifyToastsButton = toggle("autofarm.option.notify_toasts",
                draft.notifyToasts, value -> draft.notifyToasts = value,
                "autofarm.notify_toasts.tip");
        layout(notifyToastsButton, contentX, 0, fullW);
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

    /** 高风险开关：文字标红，打开要先过风险确认屏 */
    private ButtonWidget dangerToggle(String optionKey, boolean value,
                                      java.util.function.Consumer<Boolean> setter, String tipKey) {
        ButtonWidget button = ButtonWidget.builder(dangerLabel(optionKey, value), b -> {
            if (currentToggle(optionKey)) {
                setter.accept(false);
                if ("autofarm.option.auto_attack".equals(optionKey)) {
                    draft.autoAttackViolent = false;
                    draft.attackViolence = AutoFarmConfig.VIOLENCE_LIGHT;
                }
                b.setMessage(dangerLabel(optionKey, false));
                return;
            }
            if ("autofarm.option.auto_attack".equals(optionKey) && !draft.enabled) {
                toast("autofarm.combat.warn.master_off");
                return;
            }
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
                return;
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

    private void confirmThen(String subtitleKey, Runnable apply, String... bodyKeys) {
        if (this.client == null) {
            return;
        }
        this.client.setScreen(new DangerConfirmScreen(this, apply,
                "autofarm.danger.title", subtitleKey, bodyKeys));
    }

    /** 重建本页（草稿原样带过去）。用于需要「整页重排」的动作 */
    private void rebuild() {
        if (this.client != null) {
            ExtrasScreen next = new ExtrasScreen(this.parent, draft);
            next.currentTab = currentTab; // 重建后还停在原来那一页
            this.client.setScreen(next);
        }
    }

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

    private void openDangerConfirm(java.util.function.Consumer<Boolean> setter) {
        if (this.client == null) {
            return;
        }
        this.client.setScreen(new DangerConfirmScreen(this, () -> {
            setter.accept(true);
            this.client.setScreen(new ExtrasScreen(this.parent, draft));
        }));
    }

    private void drawDangerHeader(DrawContext context, int originY, int relY) {
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("autofarm.extras.section.combat"),
                contentX + 6, originY + relY + 4, COLOR_DANGER);
    }

    private void drawCombatExtras(DrawContext context, int originY) {
        int combat = originY + SEC_COMBAT_Y;

        int intervalRow = combat + ROW_FIRST + ROW_GAP;
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("autofarm.option.attack_interval"),
                contentX + 4, intervalRow + 5, COLOR_DANGER);
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("autofarm.attack_interval.hint",
                        AutoFarmConfig.ATTACK_INTERVAL_MIN_MS, AutoFarmConfig.ATTACK_INTERVAL_MAX_MS),
                contentX + INTERVAL_LABEL_W + INTERVAL_FIELD_W + 8, intervalRow + 5, COLOR_HINT);

        if (!draft.autoAttack) {
            context.drawTextWithShadow(this.textRenderer,
                    Text.translatable("autofarm.extras.violent_locked"),
                    contentX + INDENT + 4, combat + ROW_FIRST + 4 * ROW_GAP + 5, COLOR_HINT);
        }

        List<Text> warnings = combatWarnings();
        int warnY = combat + ROW_FIRST + COMBAT_ROWS * ROW_GAP + 2;
        for (int i = 0; i < warnings.size() && i < 2; i++) {
            context.drawTextWithShadow(this.textRenderer, warnings.get(i),
                    contentX + 4, warnY + i * 11, COLOR_DANGER);
        }
    }

    private static Text dangerLabel(String optionKey, boolean value) {
        return dangerToggleLabel(optionKey, value);
    }

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
            case "autofarm.option.instant_actions" -> draft.instantActions;
            case "autofarm.option.schedule" -> draft.scheduleEnabled;
            case "autofarm.option.work_area" -> draft.workAreaEnabled;
            case "autofarm.option.daylight" -> draft.workDaylightOnly;
            case "autofarm.option.clear_weather" -> draft.workClearWeatherOnly;
            case "autofarm.option.fatigue" -> draft.fatigueStop;
            case "autofarm.option.retreat" -> draft.retreatEnabled;
            case "autofarm.option.hit_reaction" -> draft.hitReactionEnabled;
            case "autofarm.option.self_rescue" -> draft.selfRescueEnabled;
            case "autofarm.option.auto_reconnect" -> draft.autoReconnectEnabled;
            case "autofarm.option.melon_harvest" -> draft.melonHarvest;
            case "autofarm.option.bone_meal" -> draft.boneMealBoost;
            case "autofarm.option.auto_supplies" -> draft.autoFetchSupplies;
            case "autofarm.option.auto_fetch_tool" -> draft.autoFetchTool;
            case "autofarm.option.notify_sounds" -> draft.notifySounds;
            case "autofarm.option.notify_toasts" -> draft.notifyToasts;
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
    protected void relayout() {
        super.relayout();
        if (violentButton != null) {
            violentButton.visible = violentButton.visible && draft.autoAttack;
        }
        if (violenceLevelButton != null) {
            violenceLevelButton.visible = violenceLevelButton.visible
                    && draft.autoAttack && draft.autoAttackViolent;
        }
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
        if (boneMealLimitButton != null) {
            boneMealLimitButton.visible = boneMealLimitButton.visible && draft.boneMealBoost;
        }
        if (boneMealThresholdButton != null) {
            boneMealThresholdButton.visible = boneMealThresholdButton.visible
                    && draft.autoFetchSupplies;
        }
    }

    @Override
    protected void positionContent(int originY) {
        // tab 栏
        int tabY = 2;
        for (int i = 0; i < TAB_COUNT; i++) {
            setRowY(tabButtons[i], originY + tabY);
        }

        switch (currentTab) {
            case TAB_COMBAT -> positionCombat(originY);
            case TAB_STEALTH -> positionStealth(originY);
            case TAB_AREA -> positionArea(originY);
            case TAB_SURVIVAL -> positionSurvival(originY);
            case TAB_RESOURCE -> positionResource(originY);
            default -> {
            }
        }
    }

    private void positionCombat(int originY) {
        int combat = originY + SEC_COMBAT_Y;
        setRowY(combatButton, combat + ROW_FIRST);
        setRowY(attackIntervalField, combat + ROW_FIRST + ROW_GAP);
        setRowY(attackRangeButton, combat + ROW_FIRST + 2 * ROW_GAP);
        setRowY(attackTargetButton, combat + ROW_FIRST + 3 * ROW_GAP);
        setRowY(violentButton, combat + ROW_FIRST + 4 * ROW_GAP);
        setRowY(violenceLevelButton, combat + ROW_FIRST + 5 * ROW_GAP);
    }

    private void positionStealth(int originY) {
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
        setRowY(turnTimeField, behavior + ROW_FIRST + 3 * ROW_GAP);
        setRowY(turnTimeOkButton, behavior + ROW_FIRST + 3 * ROW_GAP);
        setRowY(instantActionsButton, behavior + ROW_FIRST + 4 * ROW_GAP);
        setRowY(scheduleButton, behavior + ROW_FIRST + 5 * ROW_GAP);
        setRowY(scheduleStartButton, behavior + ROW_FIRST + 6 * ROW_GAP);
        setRowY(scheduleEndButton, behavior + ROW_FIRST + 7 * ROW_GAP);
    }

    private void positionArea(int originY) {
        int work = originY + SEC_WORKAREA_Y;
        setRowY(workAreaButton, work + ROW_FIRST);
        setRowY(workAreaModeButton, work + ROW_FIRST + ROW_GAP);
        setRowY(workAreaRadiusButton, work + ROW_FIRST + 2 * ROW_GAP);
        setRowY(workAreaCornerAButton, work + ROW_FIRST + 2 * ROW_GAP);
        setRowY(workAreaCenterButton, work + ROW_FIRST + 3 * ROW_GAP);
        setRowY(workAreaCornerBButton, work + ROW_FIRST + 3 * ROW_GAP);

        int env = originY + SEC_ENV_Y;
        setRowY(daylightButton, env + ROW_FIRST);
        setRowY(weatherButton, env + ROW_FIRST + ROW_GAP);
        setRowY(fatigueButton, env + ROW_FIRST + 2 * ROW_GAP);
        setRowY(fatigueWorkButton, env + ROW_FIRST + 3 * ROW_GAP);
        setRowY(fatigueRestButton, env + ROW_FIRST + 4 * ROW_GAP);
    }

    private void positionSurvival(int originY) {
        int survival = originY + SEC_SURVIVAL_Y;
        setRowY(retreatButton, survival + ROW_FIRST);
        setRowY(retreatHealthButton, survival + ROW_FIRST + ROW_GAP);
        setRowY(retreatDistanceButton, survival + ROW_FIRST + 2 * ROW_GAP);
        setRowY(hitReactionButton, survival + ROW_FIRST + 3 * ROW_GAP);
        setRowY(hitReactionTimeButton, survival + ROW_FIRST + 4 * ROW_GAP);
        setRowY(selfRescueButton, survival + ROW_FIRST + 5 * ROW_GAP);
        setRowY(reconnectButton, survival + ROW_FIRST + 6 * ROW_GAP);
        setRowY(reconnectDelayButton, survival + ROW_FIRST + 7 * ROW_GAP);
        setRowY(reconnectAttemptsButton, survival + ROW_FIRST + 8 * ROW_GAP);

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

    private void positionResource(int originY) {
        int gourd = originY + SEC_GOURD_Y;
        setRowY(melonHarvestButton, gourd + ROW_FIRST);
        setRowY(addCustomBlockButton, gourd + ROW_FIRST + ROW_GAP);
        setRowY(customBlocksButton, gourd + ROW_FIRST + 2 * ROW_GAP);

        int supply = originY + SEC_SUPPLY_Y;
        setRowY(boneMealBoostButton, supply + ROW_FIRST);
        setRowY(boneMealLimitButton, supply + ROW_FIRST + ROW_GAP);
        setRowY(autoSuppliesButton, supply + ROW_FIRST + 2 * ROW_GAP);
        setRowY(boneMealThresholdButton, supply + ROW_FIRST + 3 * ROW_GAP);
        setRowY(autoFetchToolButton, supply + ROW_FIRST + 4 * ROW_GAP);

        int notify = originY + SEC_NOTIFY_Y;
        setRowY(notifySoundsButton, notify + ROW_FIRST);
        setRowY(notifyToastsButton, notify + ROW_FIRST + ROW_GAP);
    }

    // ================= 自绘 =================

    @Override
    protected void renderContent(DrawContext context, int originY, int mouseX, int mouseY, float delta) {
        // tab 栏底下的分隔线 + 当前 tab 高亮（高亮那一套和主面板共用）
        context.fill(contentX, originY + TAB_BAR_H, contentX + contentWidth, originY + TAB_BAR_H + 1, 0x30FFFFFF);
        drawTabMarker(context, tabButtons, currentTab);

        switch (currentTab) {
            case TAB_COMBAT -> {
                drawSection(context, originY, SEC_COMBAT_Y, SEC_COMBAT_H);
                drawDangerHeader(context, originY, SEC_COMBAT_Y);
                drawCombatExtras(context, originY);
            }
            case TAB_STEALTH -> {
                drawSection(context, originY, SEC_STEALTH_Y, SEC_STEALTH_H);
                drawHeader(context, originY, SEC_STEALTH_Y, "autofarm.extras.section.stealth");
                drawSection(context, originY, SEC_ANTIKICK_Y, SEC_ANTIKICK_H);
                drawHeader(context, originY, SEC_ANTIKICK_Y, "autofarm.extras.section.antikick");
                drawSection(context, originY, SEC_BEHAVIOR_Y, SEC_BEHAVIOR_H);
                drawHeader(context, originY, SEC_BEHAVIOR_Y, "autofarm.extras.section.behavior");
                drawBehaviorExtras(context, originY);
            }
            case TAB_AREA -> {
                drawSection(context, originY, SEC_WORKAREA_Y, SEC_WORKAREA_H);
                drawHeader(context, originY, SEC_WORKAREA_Y, "autofarm.extras.section.workarea");
                context.drawTextWithShadow(this.textRenderer,
                        Text.translatable("autofarm.work_area.hint"),
                        contentX + INDENT + 4, originY + SEC_WORKAREA_Y + ROW_FIRST + 4 * ROW_GAP + 5, COLOR_HINT);
                drawSection(context, originY, SEC_ENV_Y, SEC_ENV_H);
                drawHeader(context, originY, SEC_ENV_Y, "autofarm.extras.section.environment");
            }
            case TAB_SURVIVAL -> {
                drawSection(context, originY, SEC_SURVIVAL_Y, SEC_SURVIVAL_H);
                drawHeader(context, originY, SEC_SURVIVAL_Y, "autofarm.extras.section.survival");
                drawSection(context, originY, SEC_TOOL_Y, SEC_TOOL_H);
                drawHeader(context, originY, SEC_TOOL_Y, "autofarm.extras.section.tool");
                drawSection(context, originY, SEC_MISC_Y, SEC_MISC_H);
                drawHeader(context, originY, SEC_MISC_Y, "autofarm.extras.section.misc");
                drawSection(context, originY, SEC_PICKUP_Y, SEC_PICKUP_H);
                drawHeader(context, originY, SEC_PICKUP_Y, "autofarm.extras.section.pickup");
            }
            case TAB_RESOURCE -> {
                drawSection(context, originY, SEC_GOURD_Y, SEC_GOURD_H);
                drawHeader(context, originY, SEC_GOURD_Y, "autofarm.extras.section.gourd");
                context.drawTextWithShadow(this.textRenderer,
                        Text.translatable("autofarm.gourd.hint"),
                        contentX + INDENT + 4, originY + SEC_GOURD_Y + ROW_FIRST + 3 * ROW_GAP + 5, COLOR_HINT);
                drawSection(context, originY, SEC_SUPPLY_Y, SEC_SUPPLY_H);
                drawHeader(context, originY, SEC_SUPPLY_Y, "autofarm.extras.section.supply");
                drawSection(context, originY, SEC_NOTIFY_Y, SEC_NOTIFY_H);
                drawHeader(context, originY, SEC_NOTIFY_Y, "autofarm.extras.section.notify");
            }
            default -> {
            }
        }
    }

    // ================= 行为 =================

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
        draft.turnDurationMs = 600;
        draft.instantActions = false;
        draft.scheduleEnabled = false;
        draft.scheduleStartHour = 8;
        draft.scheduleEndHour = 23;
        draft.workAreaEnabled = false;
        draft.workAreaMode = AutoFarmConfig.AREA_MODE_RADIUS;
        draft.workAreaRadius = 24;
        draft.workDaylightOnly = false;
        draft.workClearWeatherOnly = false;
        draft.fatigueStop = false;
        draft.fatigueWorkMinutes = 60;
        draft.fatigueRestMinutes = 15;
        draft.retreatEnabled = false;
        draft.retreatHealthHearts = 6;
        draft.retreatDistance = 16;
        draft.hitReactionEnabled = false;
        draft.hitReactionSeconds = 2;
        draft.selfRescueEnabled = false;
        draft.autoReconnectEnabled = false;
        draft.reconnectDelaySeconds = 10;
        draft.reconnectMaxAttempts = 3;
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
        draft.melonHarvest = false;
        draft.customHarvestBlocks = new ArrayList<>();
        draft.boneMealBoost = false;
        draft.boneMealLimit = 4;
        draft.autoFetchSupplies = false;
        draft.boneMealThreshold = 16;
        draft.autoFetchTool = false;
        draft.notifySounds = false;
        draft.notifyToasts = false;
        rebuild();
    }

    @Override
    protected void onCancel() {
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
        draft.turnDurationMs = old.turnDurationMs;
        draft.instantActions = old.instantActions;
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
        draft.retreatEnabled = old.retreatEnabled;
        draft.retreatHealthHearts = old.retreatHealthHearts;
        draft.retreatDistance = old.retreatDistance;
        draft.hitReactionEnabled = old.hitReactionEnabled;
        draft.hitReactionSeconds = old.hitReactionSeconds;
        draft.selfRescueEnabled = old.selfRescueEnabled;
        draft.autoReconnectEnabled = old.autoReconnectEnabled;
        draft.reconnectDelaySeconds = old.reconnectDelaySeconds;
        draft.reconnectMaxAttempts = old.reconnectMaxAttempts;
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
        draft.melonHarvest = old.melonHarvest;
        draft.customHarvestBlocks = new ArrayList<>(old.customHarvestBlocks);
        draft.boneMealBoost = old.boneMealBoost;
        draft.boneMealLimit = old.boneMealLimit;
        draft.autoFetchSupplies = old.autoFetchSupplies;
        draft.boneMealThreshold = old.boneMealThreshold;
        draft.autoFetchTool = old.autoFetchTool;
        draft.notifySounds = old.notifySounds;
        draft.notifyToasts = old.notifyToasts;
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

    private static double nextChoice(double[] choices, double current) {
        for (int i = 0; i < choices.length; i++) {
            if (Math.abs(choices[i] - current) < 1e-6) {
                return choices[(i + 1) % choices.length];
            }
        }
        return choices[0];
    }

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

    /** 「行为真实感」分区的自绘部分：转向时间的标签、范围提示、待确认 / 过小警告 */
    private void drawBehaviorExtras(DrawContext context, int originY) {
        int behavior = originY + SEC_BEHAVIOR_Y;
        int row = behavior + ROW_FIRST + 3 * ROW_GAP;
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("autofarm.option.turn_time"),
                contentX + 4, row + 5, 0xFFFFFFFF);
        context.drawTextWithShadow(this.textRenderer,
                Text.translatable("autofarm.turn_time.hint",
                        AutoFarmConfig.TURN_DURATION_MIN, AutoFarmConfig.TURN_DURATION_MAX),
                contentX + INDENT + INTERVAL_LABEL_W + INTERVAL_FIELD_W + TURN_OK_GAP
                        + TURN_OK_W + 6,
                row + 5, COLOR_HINT);
        // 分区底部那一行留给了「危险值」的提示。两种情况共用这一行：
        //   · 还没确认 → 暖色：填了但没生效，按 Enter / 点「确认」才应用；
        //   · 已经确认 → 红色：这个值会让转身太突兀，容易被看出是机器在操作。
        //   0 = 完全关掉平滑转向，同样按「危险值」对待。
        int warnY = behavior + ROW_FIRST + BEHAVIOR_ROWS * ROW_GAP + 2;
        if (turnTimePending) {
            context.drawTextWithShadow(this.textRenderer,
                    Text.translatable("autofarm.turn_time.pending"),
                    contentX + 4, warnY, COLOR_NOTICE);
        } else if (draft.turnDurationMs < AutoFarmConfig.TURN_DURATION_SAFE_MS) {
            context.drawTextWithShadow(this.textRenderer,
                    Text.translatable("autofarm.turn_time.warn", AutoFarmConfig.TURN_DURATION_SAFE_MS),
                    contentX + 4, warnY, COLOR_DANGER);
        }
    }

    // ================= 「转向时间」的确认流程 =================

    /**
     * 输入框被改动时：安全值<b>即时生效</b>，危险值（小于安全阈值，含 0）<b>先挂着</b>。
     *
     * <p>挂着的意思是「只在输入框里显示，不写进草稿」—— 于是哪怕玩家转头就去点保存，
     * 存下去的也是上一个安全值，绝不会是那个突兀的 0。
     */
    private void onTurnTimeEdited(String text) {
        Integer typed = parseTurnTime(text);
        if (typed == null) {
            return; // 空着 / 非法 → 不动草稿
        }
        int value = clampInt(typed, AutoFarmConfig.TURN_DURATION_MIN, AutoFarmConfig.TURN_DURATION_MAX);
        if (value >= AutoFarmConfig.TURN_DURATION_SAFE_MS) {
            draft.turnDurationMs = value;
            turnTimePending = false;
        } else {
            // 危险值：先不写。等 Enter / 「确认」/ 离页时统一过一次风险提示屏。
            turnTimePending = value != draft.turnDurationMs;
        }
    }

    /**
     * 把输入框里的值定下来。
     *
     * @return true 表示「弹了风险提示屏、正等着玩家答复」—— 调用方此时别再往下走
     */
    private boolean commitTurnTime() {
        if (turnTimeField == null) {
            return false;
        }
        Integer typed = parseTurnTime(turnTimeField.getText());
        if (typed == null) {
            // 空 / 非法：恢复成当前生效值，不用确认
            turnTimeField.setText(Integer.toString(draft.turnDurationMs));
            turnTimePending = false;
            return false;
        }
        int value = clampInt(typed, AutoFarmConfig.TURN_DURATION_MIN, AutoFarmConfig.TURN_DURATION_MAX);
        if (value != typed) {
            turnTimeField.setText(Integer.toString(value)); // 超出范围：拉回边界并回填
        }

        // 安全值 或 本来就是当前值 → 直接生效
        if (value >= AutoFarmConfig.TURN_DURATION_SAFE_MS || value == draft.turnDurationMs) {
            draft.turnDurationMs = value;
            turnTimePending = false;
            return false;
        }
        if (this.client == null) {
            draft.turnDurationMs = value;
            turnTimePending = false;
            return false;
        }

        // 危险值：过一次风险提示屏。确认才写进草稿；取消则什么都改（输入框会在重建时回到旧值）。
        turnTimePending = false;
        this.client.setScreen(new DangerConfirmScreen(this, () -> {
            draft.turnDurationMs = value;
            if (turnTimeField != null) {
                turnTimeField.setText(Integer.toString(value));
            }
        }, "autofarm.danger.title", "autofarm.turn_time.danger.subtitle",
                "autofarm.turn_time.danger.body1", "autofarm.turn_time.danger.body2"));
        return true;
    }

    /** 输入框里的整数；空 / 非法返回 null */
    private static Integer parseTurnTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 点「完成」：先把转向时间定下来（可能要过一次确认屏），再回上层 */
    private void onDone() {
        if (commitTurnTime()) {
            return; // 弹了确认屏，等玩家答复，先别走
        }
        backToParent();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 在「转向时间」输入框里按 Enter = 点「确认」：危险值在这里弹风险提示
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && turnTimeField != null && turnTimeField.isFocused()) {
            commitTurnTime();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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

    private Text retreatHealthLabel() {
        return optionLabel("autofarm.option.retreat_health",
                Text.translatable("autofarm.value.hearts", draft.retreatHealthHearts));
    }

    private Text retreatDistanceLabel() {
        return optionLabel("autofarm.option.retreat_distance",
                Text.translatable("autofarm.value.blocks", draft.retreatDistance));
    }

    private Text hitReactionTimeLabel() {
        return optionLabel("autofarm.option.hit_reaction_time",
                Text.translatable("autofarm.value.seconds", draft.hitReactionSeconds));
    }

    private Text reconnectDelayLabel() {
        return optionLabel("autofarm.option.reconnect_delay",
                Text.translatable("autofarm.value.seconds", draft.reconnectDelaySeconds));
    }

    private Text reconnectAttemptsLabel() {
        return optionLabel("autofarm.option.reconnect_attempts",
                draft.reconnectMaxAttempts <= 0
                        ? Text.translatable("autofarm.reconnect_attempts.unlimited")
                        : Text.translatable("autofarm.value.count", draft.reconnectMaxAttempts));
    }

    private Text customBlocksLabel() {
        int n = draft.customHarvestBlocks == null ? 0 : draft.customHarvestBlocks.size();
        return optionLabel("autofarm.option.custom_blocks",
                Text.translatable("autofarm.value.count", n));
    }

    private Text boneMealLimitLabel() {
        return optionLabel("autofarm.option.bone_meal_limit",
                Text.translatable("autofarm.value.count", draft.boneMealLimit));
    }

    private Text boneMealThresholdLabel() {
        return optionLabel("autofarm.option.bone_meal_threshold",
                Text.translatable("autofarm.value.count", draft.boneMealThreshold));
    }

    private void addLookedBlock() {
        if (this.client == null || this.client.world == null) {
            return;
        }
        HitResult hit = this.client.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            toast("autofarm.custom_block.no_target");
            return;
        }
        BlockState state = this.client.world.getBlockState(((BlockHitResult) hit).getBlockPos());
        Block block = state.getBlock();
        if (block == Blocks.AIR) {
            toast("autofarm.custom_block.no_target");
            return;
        }
        String id = CropRegistry.idOf(block);
        if (id == null) {
            toast("autofarm.custom_block.no_target");
            return;
        }
        if (draft.customHarvestBlocks == null) {
            draft.customHarvestBlocks = new ArrayList<>();
        }
        if (draft.customHarvestBlocks.contains(id)) {
            toast("autofarm.custom_block.duplicate");
            return;
        }
        confirmThen("autofarm.custom_block.warn.subtitle", () -> {
            draft.customHarvestBlocks.add(id);
            rebuild();
        }, "autofarm.custom_block.warn.body1",
                "autofarm.custom_block.warn.body2", "autofarm.custom_block.warn.body3");
    }

    private void clearCustomBlocks() {
        if (draft.customHarvestBlocks == null || draft.customHarvestBlocks.isEmpty()) {
            toast("autofarm.custom_block.empty");
            return;
        }
        confirmThen("autofarm.custom_block.clear.subtitle", () -> {
            draft.customHarvestBlocks.clear();
            rebuild();
        }, "autofarm.custom_block.clear.body1");
    }

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

    private static String levelKey(int level) {
        return switch (level) {
            case 1 -> "light";
            case 2 -> "medium";
            case 3 -> "high";
            default -> "off";
        };
    }
}
