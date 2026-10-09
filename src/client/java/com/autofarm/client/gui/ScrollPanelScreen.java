package com.autofarm.client.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * 「固定标题 + 可滚动内容 + 固定页脚」三段式面板的公共骨架。
 *
 * <p>为什么需要它：Minecraft 的「界面尺寸（GUI Scale）」调高之后，逻辑分辨率会变小
 * （1080p 在 scale 4 下只有 270 像素高），任何写死高度的面板都会被切掉底部的按钮。
 * 让面板高度自适应 + 内容可滚动，用户就不必再去拉窗口大小了。
 *
 * <p>子类只需要回答三个问题：
 * <ol>
 *   <li>{@link #contentHeight()} 内容总共多高</li>
 *   <li>{@link #buildContent()} 有哪些控件（用 {@link #addScrolling} / {@link #addFooter} 注册）</li>
 *   <li>{@link #positionContent(int)} 每个控件摆在内容的第几行</li>
 * </ol>
 * 再加一个 {@link #renderContent} 画自绘部分（分区底板、标题、说明文字）。
 */
public abstract class ScrollPanelScreen extends Screen {

    // ---------------- 几何常量 ----------------
    /** 理想宽度；屏幕不够宽时自动收窄 */
    protected static final int PREF_PANEL_W = 320;
    /** 标题区高度（固定不动） */
    protected static final int TITLE_H = 26;
    /** 底部按钮区高度（固定不动） */
    protected static final int FOOTER_H = 30;
    /** 面板左右内边距 */
    protected static final int PAD = 12;
    /** 标准行高 */
    protected static final int ROW_H = 18;

    private static final int MIN_PANEL_H = 96;
    /** 一格滚轮滚多少像素 */
    private static final int SCROLL_STEP = 18;

    // ---------------- 配色 ----------------
    protected static final int COLOR_PANEL = 0xC8101010;
    protected static final int COLOR_SECTION = 0x30FFFFFF;
    protected static final int COLOR_TITLE = 0xFFFFFFFF;
    protected static final int COLOR_HEADER = 0xFFFFCC66;
    protected static final int COLOR_TEXT = 0xFFE0E0E0;
    protected static final int COLOR_HINT = 0xFF909090;
    /** 提示/警告用的暖色（比如「没种子，跳过了补种」） */
    protected static final int COLOR_NOTICE = 0xFFFFB020;

    private static final int COLOR_DIVIDER = 0x30FFFFFF;
    private static final int COLOR_FADE = 0x50000000;
    private static final int COLOR_BAR_BG = 0x40000000;
    private static final int COLOR_BAR = 0x80FFFFFF;

    protected final Screen parent;

    private final List<ClickableWidget> scrollingWidgets = new ArrayList<>();
    private final List<ClickableWidget> footerWidgets = new ArrayList<>();

    // ---------------- 运行时几何（init 时算好，子类可直接用） ----------------
    protected int left;
    protected int top;
    protected int panelW;
    protected int panelH;
    /** 内容区左边界（= left + PAD） */
    protected int contentX;
    /** 内容区宽度（= panelW - 2 * PAD） */
    protected int contentWidth;
    /** 可视内容区的上边界（屏幕坐标） */
    protected int contentTop;
    /** 可视内容区的下边界（屏幕坐标） */
    protected int contentBottom;
    /** 固定页脚的上边界 */
    protected int footerTop;

    protected int scroll;
    protected int maxScroll;

    protected ScrollPanelScreen(Screen parent, Text title) {
        super(title);
        this.parent = parent;
    }

    /** 可滚动内容的总高度 */
    protected abstract int contentHeight();

    /** 建控件。几何量（contentX / contentWidth）在这里已经算好了 */
    protected abstract void buildContent();

    /**
     * 把控件摆到该摆的位置。
     *
     * @param originY 内容区顶部在屏幕上的实际 y（已经扣掉了滚动偏移），
     *                所以「内容相对第 N 行」的位置就是 {@code originY + N}
     */
    protected abstract void positionContent(int originY);

    /** 画跟着内容一起滚动的自绘部分 */
    protected abstract void renderContent(DrawContext context, int originY,
                                          int mouseX, int mouseY, float delta);

    @Override
    protected final void init() {
        // ---- 自适应尺寸：装不下就收窄、变矮，剩下的交给滚动 ----
        int availW = Math.max(150, this.width - PAD);
        int availH = Math.max(MIN_PANEL_H, this.height - 6);
        this.panelW = Math.min(PREF_PANEL_W, availW);
        this.panelH = Math.min(TITLE_H + contentHeight() + FOOTER_H, availH);
        this.left = (this.width - panelW) / 2;
        this.top = Math.max(2, (this.height - panelH) / 2);

        this.contentTop = top + TITLE_H;
        this.contentBottom = top + panelH - FOOTER_H;
        this.footerTop = contentBottom;
        this.contentX = left + PAD;
        this.contentWidth = panelW - PAD * 2;
        this.maxScroll = Math.max(0, contentHeight() - (contentBottom - contentTop));
        this.scroll = clampInt(scroll, 0, maxScroll);

        this.scrollingWidgets.clear();
        this.footerWidgets.clear();

        buildContent();

        int footerY = footerTop + 6;
        for (ClickableWidget w : footerWidgets) {
            w.setY(footerY);
        }
        relayout();
    }

    /** 注册一个「跟着内容一起滚」的控件 */
    protected void addScrolling(ClickableWidget widget) {
        scrollingWidgets.add(widget);
        addDrawableChild(widget);
    }

    /** 注册一个「固定在面板底部」的控件 */
    protected void addFooter(ClickableWidget... widgets) {
        for (ClickableWidget w : widgets) {
            footerWidgets.add(w);
            addDrawableChild(w);
        }
    }

    /**
     * 重新摆放所有滚动控件，并把完全滚出可视区的控件隐藏掉。
     * 隐藏这一步是必须的：否则它们虽然看不见，点击区域却还留在面板外面。
     */
    protected void relayout() {
        positionContent(contentTop - scroll);
        for (ClickableWidget w : scrollingWidgets) {
            // 1.20.1 的 ClickableWidget 只有 public 字段 visible，没有 setVisible()
            w.visible = w.getY() + w.getHeight() > contentTop && w.getY() < contentBottom;
        }
    }

    protected static void setRowY(ClickableWidget widget, int y) {
        if (widget != null) {
            widget.setY(y);
        }
    }

    // ================= 渲染 =================

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        // 面板底板
        context.fill(left, top, left + panelW, top + panelH, COLOR_PANEL);

        int originY = contentTop - scroll;

        // ---- 内容区：开裁剪，滚出可视区的部分不会被画出来 ----
        context.enableScissor(left + 1, contentTop, left + panelW - 1, contentBottom);

        renderContent(context, originY, mouseX, mouseY, delta);

        // 这里千万不能调 super.render()：它会遍历所有 drawable，
        // 把固定页脚的按钮也画进来，然后被上面的裁剪一起切掉。
        for (ClickableWidget w : scrollingWidgets) {
            if (w.visible) {
                w.render(context, mouseX, mouseY, delta);
            }
        }

        context.disableScissor();

        // ---- 固定部分 ----
        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
                left + panelW / 2, top + 8, COLOR_TITLE);

        context.fill(left + 1, footerTop, left + panelW - 1, footerTop + 1, COLOR_DIVIDER);
        for (ClickableWidget w : footerWidgets) {
            w.render(context, mouseX, mouseY, delta);
        }

        // 可视区上下边缘压一条暗色，暗示「上面/下面还有内容」
        if (scroll > 0) {
            context.fill(left + 1, contentTop, left + panelW - 1, contentTop + 3, COLOR_FADE);
        }
        if (scroll < maxScroll) {
            context.fill(left + 1, contentBottom - 3, left + panelW - 1, contentBottom, COLOR_FADE);
        }

        renderScrollbar(context);
    }

    /** 画一块分区底板。relY / relH 都是「相对内容区顶部」的坐标 */
    protected void drawSection(DrawContext context, int originY, int relY, int relH) {
        context.fill(contentX, originY + relY, contentX + contentWidth, originY + relY + relH, COLOR_SECTION);
    }

    /** 画分区小标题 */
    protected void drawHeader(DrawContext context, int originY, int relY, String key) {
        context.drawTextWithShadow(this.textRenderer, Text.translatable(key),
                contentX + 6, originY + relY + 4, COLOR_HEADER);
    }

    /** 画一个矩形线框（用 4 条 1 像素的填充拼出来，DrawContext 没有现成的描边 API） */
    protected static void drawRectOutline(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        context.fill(x1, y1, x2, y1 + 1, color);
        context.fill(x1, y2 - 1, x2, y2, color);
        context.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
        context.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
    }

    private void renderScrollbar(DrawContext context) {
        if (maxScroll <= 0) {
            return;
        }
        int viewH = contentBottom - contentTop;
        int trackTop = contentTop + 2;
        int trackH = viewH - 4;
        int barX = left + panelW - 5;

        int thumbH = Math.max(18, (int) ((long) trackH * viewH / contentHeight()));
        int thumbY = trackTop + (int) ((long) (trackH - thumbH) * scroll / maxScroll);

        context.fill(barX, trackTop, barX + 3, trackTop + trackH, COLOR_BAR_BG);
        context.fill(barX, thumbY, barX + 3, thumbY + thumbH, COLOR_BAR);
    }

    // ================= 输入 =================

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (maxScroll > 0
                && mouseX >= left && mouseX <= left + panelW
                && mouseY >= contentTop && mouseY <= contentBottom) {
            int next = clampInt(scroll - (int) Math.round(amount * SCROLL_STEP), 0, maxScroll);
            if (next != scroll) {
                scroll = next;
                relayout();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 标题条那条带子上可能压着「只露出一半」的控件，直接不让点，免得误触
        if (mouseY < contentTop) {
            return false;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ================= 行为 =================

    /** 取消 / 按 Esc：回上层界面。子类可以覆写来丢弃草稿 */
    protected void onCancel() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    /** 「完成 / 保存」之后回上层界面 */
    protected void backToParent() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }

    @Override
    public void close() {
        onCancel();
    }

    /** 左上角弹一条动作栏提示 */
    protected void toast(String translationKey) {
        if (this.client != null && this.client.player != null) {
            this.client.player.sendMessage(Text.translatable(translationKey), true);
        }
    }

    protected static Text toggleLabel(String optionKey, boolean value) {
        return Text.translatable("autofarm.toggle",
                Text.translatable(optionKey),
                Text.translatable(value ? "autofarm.on" : "autofarm.off"));
    }

    protected static int clampInt(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
