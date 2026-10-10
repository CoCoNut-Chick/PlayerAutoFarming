package com.autofarm.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * 「高风险功能」的二次确认屏 —— 目前只有「自动打怪」会用到它。
 *
 * <h2>为什么要有这么一道</h2>
 * 附加功能里其他每一项最多只是「让角色怪一点」；自动打怪不一样，
 * 它替玩家出手攻击生物，等价于常见的杀戮光环，是会让账号被封的那一类功能。
 * 开关藏在面板里、点一下就生效，太容易误开 ——
 * 所以把「打开」这个动作拆成两步：先在面板上点开关，再在这块屏上明确点一次确认。
 *
 * <p>屏上把三件事写清楚，而不是一句「风险自负」：<b>它属于作弊级功能、
 * 默认的非暴力模式也只是降低被识别的概率、而且因为出手慢角色真的会被打死</b>。
 * 看完还确认，才把开关打开。
 *
 * <p>按 Esc 或者点「取消」都走 {@link #close()}，回到上层面板、什么都不改。
 */
public class DangerConfirmScreen extends Screen {

    private static final int PANEL_W = 320;
    private static final int PAD = 12;
    private static final int BTN_H = 20;
    private static final int LINE_H = 11;

    private static final int COLOR_PANEL = 0xF0180808;
    private static final int COLOR_EDGE = 0xFFFF5555;
    private static final int COLOR_TITLE = 0xFFFF5555;
    private static final int COLOR_SUBTITLE = 0xFFFFAA33;
    private static final int COLOR_TEXT = 0xFFE0E0E0;

    private final Screen parent;
    private final Runnable onConfirm;

    /** 自动换行之后的正文行；null 表示一个空行（段落之间的间隔） */
    private List<OrderedText> bodyLines = new ArrayList<>();

    private int left;
    private int top;
    private int panelW;
    private int panelH;

    public DangerConfirmScreen(Screen parent, Runnable onConfirm) {
        super(Text.translatable("autofarm.danger.title"));
        this.parent = parent;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        this.panelW = Math.min(PANEL_W, Math.max(200, this.width - 20));
        int textW = panelW - PAD * 2;

        // 正文分三段（各自自动换行，段间留一个空行）——
        // 拆段是为了换行计算简单，也是为了让「功能性质 / 模式局限 / 建议」三层意思分开
        this.bodyLines = new ArrayList<>();
        String[] paragraphKeys = {
                "autofarm.danger.body1",
                "autofarm.danger.body2",
                "autofarm.danger.body3"
        };
        for (int i = 0; i < paragraphKeys.length; i++) {
            if (i > 0) {
                this.bodyLines.add(null);
            }
            this.bodyLines.addAll(this.textRenderer.wrapLines(
                    Text.translatable(paragraphKeys[i]), textW));
        }

        this.panelH = PAD + 14 + 14 + bodyLines.size() * LINE_H + 12 + BTN_H + PAD;
        this.left = (this.width - panelW) / 2;
        this.top = Math.max(4, (this.height - panelH) / 2);

        int btnY = top + panelH - PAD - BTN_H;
        int half = (panelW - PAD * 2 - 6) / 2;
        addDrawableChild(ButtonWidget.builder(Text.translatable("autofarm.danger.cancel"),
                        b -> close())
                .dimensions(left + PAD, btnY, half, BTN_H).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("autofarm.danger.confirm"),
                        b -> onConfirm.run())
                .dimensions(left + PAD + half + 6, btnY, panelW - PAD * 2 - half - 6, BTN_H).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        context.fill(left, top, left + panelW, top + panelH, COLOR_PANEL);
        drawRectOutline(context, left, top, left + panelW, top + panelH, COLOR_EDGE);

        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("autofarm.danger.title"),
                left + panelW / 2, top + PAD, COLOR_TITLE);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("autofarm.danger.subtitle"),
                left + panelW / 2, top + PAD + 14, COLOR_SUBTITLE);

        int y = top + PAD + 14 + 14;
        for (OrderedText line : bodyLines) {
            if (line == null) {
                y += LINE_H / 2;
                continue;
            }
            context.drawTextWithShadow(this.textRenderer, line, left + PAD, y, COLOR_TEXT);
            y += LINE_H;
        }

        super.render(context, mouseX, mouseY, delta);
    }

    /** 画一圈 1 像素的红边（DrawContext 没有现成的描边 API，用四条填充拼） */
    private static void drawRectOutline(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        context.fill(x1, y1, x2, y1 + 1, color);
        context.fill(x1, y2 - 1, x2, y2, color);
        context.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
        context.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
    }

    @Override
    public void close() {
        // 取消 / Esc：什么都不改，回上层面板
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }
}
