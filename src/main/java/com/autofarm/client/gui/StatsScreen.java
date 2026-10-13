package com.autofarm.client.gui;

import com.autofarm.client.stats.StatsManager;
import com.autofarm.client.task.FarmTaskManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 统计面板（E 组）：把「本轮」和「累计」两套数字摆在一起看。
 *
 * <h2>和 HUD 的关系</h2>
 * HUD 是干活时瞄一眼用的，只给三两个数；这里是一张完整的表，包括累计统计
 * （重进游戏还在）和异常次数。没事的时候不用开它，事后想看看「这一局到底收了多少、
 * 总共干了几小时、出过多少次岔子」，来这儿一趟就清楚了。
 *
 * <h2>数据来源</h2>
 * <ul>
 *   <li><b>本轮</b> —— {@link FarmTaskManager} 的收割数 / 补种数 / 干活时长，关一次开关清零；</li>
 *   <li><b>累计</b> —— {@link StatsManager} 持久化到 {@code config/autofarm-stats.json}，
 *       重进游戏还在；</li>
 *   <li><b>异常</b> —— {@link StatsManager} 记进 {@code config/autofarm.log} 的条数。</li>
 * </ul>
 *
 * <p>底部两个操作：<b>清零累计</b>、<b>清空日志</b>。都是「把账本翻回第一页」——
 * 不影响模组行为，只影响这里显示的数字。
 */
public class StatsScreen extends Screen {

    private static final int PANEL_W = 320;
    private static final int PAD = 12;
    private static final int BTN_H = 20;
    private static final int LINE_H = 11;
    /** 两个小节（本轮 / 累计）各自的行数 */
    private static final int SESSION_ROWS = 3;
    private static final int TOTAL_ROWS = 7;

    private static final int COLOR_PANEL = 0xF0181818;
    private static final int COLOR_EDGE = 0xFF6A6A6A;
    private static final int COLOR_TITLE = 0xFFFFFFFF;
    private static final int COLOR_HEAD = 0xFFFFC46B;
    private static final int COLOR_LABEL = 0xFFA0A0A0;
    private static final int COLOR_VALUE = 0xFFE0E0E0;

    private final Screen parent;

    private int left;
    private int top;
    private int panelW;
    private int panelH;

    public StatsScreen(Screen parent) {
        super(Component.translatable("autofarm.stats.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.panelW = Math.min(PANEL_W, Math.max(220, this.width - 20));

        // 标题 + 两个小节标题 + (3 + 7) 行数据 + 节间留白 + 按钮
        int rows = 1 + 1 + SESSION_ROWS + 1 + TOTAL_ROWS;
        this.panelH = PAD + 14 + rows * LINE_H + 8 + BTN_H + PAD;
        this.left = (this.width - panelW) / 2;
        this.top = Math.max(4, (this.height - panelH) / 2);

        int btnY = top + panelH - PAD - BTN_H;
        int third = (panelW - PAD * 2 - 12) / 3;
        addRenderableWidget(Button.builder(Component.translatable("autofarm.stats.clear"),
                        b -> StatsManager.get().clear())
                .bounds(left + PAD, btnY, third, BTN_H).build());
        addRenderableWidget(Button.builder(Component.translatable("autofarm.stats.clear_log"),
                        b -> StatsManager.get().clearLog())
                .bounds(left + PAD + third + 6, btnY, third, BTN_H).build());
        addRenderableWidget(Button.builder(Component.translatable("autofarm.stats.done"), b -> onClose())
                .bounds(left + PAD + 2 * (third + 6), btnY, third, BTN_H).build());
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        context.fill(left, top, left + panelW, top + panelH, COLOR_PANEL);
        drawRectOutline(context, left, top, left + panelW, top + panelH, COLOR_EDGE);

        context.drawCenteredString(this.font, this.title,
                left + panelW / 2, top + PAD, COLOR_TITLE);

        int y = top + PAD + 14;
        int valueX = left + PAD + 120;

        y += LINE_H; // 空一行，标题下方留白
        context.drawString(this.font,
                Component.translatable("autofarm.stats.session").withStyle(ChatFormatting.BOLD),
                left + PAD, y, COLOR_HEAD);
        y += LINE_H;
        y = row(context, y, valueX, "autofarm.stats.harvested",
                FarmTaskManager.getHarvestedCount());
        y = row(context, y, valueX, "autofarm.stats.planted",
                FarmTaskManager.getPlantedCount());
        y = row(context, y, valueX, "autofarm.stats.work_time",
                duration(FarmTaskManager.getSessionSeconds()));

        y += LINE_H; // 两个小节之间留白
        context.drawString(this.font,
                Component.translatable("autofarm.stats.total").withStyle(ChatFormatting.BOLD),
                left + PAD, y, COLOR_HEAD);
        y += LINE_H;
        StatsManager s = StatsManager.get();
        y = row(context, y, valueX, "autofarm.stats.harvested", s.getTotalHarvested());
        y = row(context, y, valueX, "autofarm.stats.planted", s.getTotalPlanted());
        y = row(context, y, valueX, "autofarm.stats.picked_up", s.getTotalPickedUp());
        y = row(context, y, valueX, "autofarm.stats.bone_meal", s.getTotalBoneMeal());
        y = row(context, y, valueX, "autofarm.stats.supply_runs", s.getTotalSupplyRuns());
        y = row(context, y, valueX, "autofarm.stats.work_time", duration((int) s.getTotalWorkSeconds()));
        row(context, y, valueX, "autofarm.stats.issues", s.getIssueCount());

        super.render(context, mouseX, mouseY, delta);
    }

    /** 画一行「标签 …… 值」，返回下一行的 y */
    private int row(GuiGraphics context, int y, int valueX, String labelKey, long value) {
        return drawRow(context, y, valueX, labelKey, Component.literal(Long.toString(value)));
    }

    /** 值本身已经是文本（时长这类带单位的），走这个重载 */
    private int row(GuiGraphics context, int y, int valueX, String labelKey, String value) {
        return drawRow(context, y, valueX, labelKey, Component.literal(value));
    }

    private int drawRow(GuiGraphics context, int y, int valueX, String labelKey, Component value) {
        context.drawString(this.font, Component.translatable(labelKey),
                left + PAD + 4, y, COLOR_LABEL);
        context.drawString(this.font, value, valueX, y, COLOR_VALUE);
        return y + LINE_H;
    }

    /** 秒数 → 「1h23m」「2m5s」「45s」这样的短格式 */
    private static String duration(int seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        if (seconds < 3600) {
            return (seconds / 60) + "m" + (seconds % 60) + "s";
        }
        return (seconds / 3600) + "h" + (seconds % 3600 / 60) + "m";
    }

    /** 画一圈 1 像素的描边（DrawContext 没有现成的描边 API，用四条填充拼） */
    private static void drawRectOutline(GuiGraphics context, int x1, int y1, int x2, int y2, int color) {
        context.fill(x1, y1, x2, y1 + 1, color);
        context.fill(x1, y2 - 1, x2, y2, color);
        context.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
        context.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }
}
