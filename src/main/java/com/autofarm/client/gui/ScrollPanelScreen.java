package com.autofarm.client.gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.9 {
/*import net.minecraft.client.input.MouseButtonEvent;
*///?}
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

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
    /** 顶部 tab 栏高度（相对内容区顶部）。两个面板共用同一套排版 */
    protected static final int TAB_BAR_H = 24;

    private static final int MIN_PANEL_H = 96;
    /** 一格滚轮滚多少像素 */
    private static final int SCROLL_STEP = 18;
    /** 滚动条的可抓取宽度（画出来只有一半宽，但点得中） */
    private static final int SCROLLBAR_GRAB_W = 8;

    // ---------------- 配色 ----------------
    protected static final int COLOR_PANEL = 0xC8101010;
    protected static final int COLOR_SECTION = 0x30FFFFFF;
    protected static final int COLOR_TITLE = 0xFFFFFFFF;
    protected static final int COLOR_HEADER = 0xFFFFCC66;
    protected static final int COLOR_TEXT = 0xFFE0E0E0;
    protected static final int COLOR_HINT = 0xFF909090;
    /** 提示/警告用的暖色（比如「没种子，跳过了补种」） */
    protected static final int COLOR_NOTICE = 0xFFFFB020;
    /** 开关「已开启」用的绿 */
    protected static final int COLOR_ON = 0xFF55FF55;
    /** 高风险开关用的红 */
    protected static final int COLOR_DANGER = 0xFFFF5555;

    private static final int COLOR_DIVIDER = 0x30FFFFFF;
    private static final int COLOR_FADE = 0x50000000;
    private static final int COLOR_BAR_BG = 0x40000000;
    private static final int COLOR_BAR = 0x80FFFFFF;

    protected final Screen parent;

    private final List<AbstractWidget> scrollingWidgets = new ArrayList<>();
    private final List<AbstractWidget> footerWidgets = new ArrayList<>();

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

    // ---------------- 滚动条运行时几何（每帧算一次，拖拽时复用） ----------------
    private int sbTrackTop;
    private int sbTrackH;
    private int sbThumbTop;
    private int sbThumbH;
    /** 正按着滑块拖 */
    private boolean draggingScrollbar;
    /** 按下时鼠标相对滑块顶端的偏移，拖的时候保持不变 */
    private int dragGrabOffset;

    protected ScrollPanelScreen(Screen parent, Component title) {
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
    protected abstract void renderContent(GuiGraphics context, int originY,
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
        for (AbstractWidget w : footerWidgets) {
            w.setY(footerY);
        }
        relayout();
    }

    /** 注册一个「跟着内容一起滚」的控件 */
    protected void addScrolling(AbstractWidget widget) {
        scrollingWidgets.add(widget);
        addRenderableWidget(widget);
    }

    /** 注册一个「固定在面板底部」的控件 */
    protected void addFooter(AbstractWidget... widgets) {
        for (AbstractWidget w : widgets) {
            footerWidgets.add(w);
            addRenderableWidget(w);
        }
    }

    /**
     * 重新摆放所有滚动控件，并把完全滚出可视区的控件隐藏掉。
     * 隐藏这一步是必须的：否则它们虽然看不见，点击区域却还留在面板外面。
     */
    protected void relayout() {
        positionContent(contentTop - scroll);
        for (AbstractWidget w : scrollingWidgets) {
            // 1.20.1 的 ClickableWidget 只有 public 字段 visible，没有 setVisible()
            w.visible = w.getY() + w.getHeight() > contentTop && w.getY() < contentBottom;
        }
    }

    protected static void setRowY(AbstractWidget widget, int y) {
        if (widget != null) {
            widget.setY(y);
        }
    }

    // ================= 渲染 =================

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // 1.20.2 起 renderBackground 要多收鼠标坐标与帧时间（原版要在背景上做模糊遮罩）
        //? if <1.20.2 {
        this.renderBackground(context);
        //?} else {
        /*this.renderBackground(context, mouseX, mouseY, delta);
        *///?}

        // 面板底板
        context.fill(left, top, left + panelW, top + panelH, COLOR_PANEL);

        int originY = contentTop - scroll;

        // ---- 内容区：开裁剪，滚出可视区的部分不会被画出来 ----
        context.enableScissor(left + 1, contentTop, left + panelW - 1, contentBottom);

        renderContent(context, originY, mouseX, mouseY, delta);

        // 这里千万不能调 super.render()：它会遍历所有 drawable，
        // 把固定页脚的按钮也画进来，然后被上面的裁剪一起切掉。
        for (AbstractWidget w : scrollingWidgets) {
            if (w.visible) {
                w.render(context, mouseX, mouseY, delta);
            }
        }

        context.disableScissor();

        // ---- 固定部分 ----
        context.drawCenteredString(this.font, this.title,
                left + panelW / 2, top + 8, COLOR_TITLE);

        context.fill(left + 1, footerTop, left + panelW - 1, footerTop + 1, COLOR_DIVIDER);
        for (AbstractWidget w : footerWidgets) {
            w.render(context, mouseX, mouseY, delta);
        }

        // 可视区上下边缘压一条暗色，暗示「上面/下面还有内容」
        if (scroll > 0) {
            context.fill(left + 1, contentTop, left + panelW - 1, contentTop + 3, COLOR_FADE);
        }
        if (scroll < maxScroll) {
            context.fill(left + 1, contentBottom - 3, left + panelW - 1, contentBottom, COLOR_FADE);
        }

        renderScrollbar(context, overScrollbar(mouseX, mouseY) || draggingScrollbar);
    }

    /** 画一块分区底板。relY / relH 都是「相对内容区顶部」的坐标 */
    protected void drawSection(GuiGraphics context, int originY, int relY, int relH) {
        context.fill(contentX, originY + relY, contentX + contentWidth, originY + relY + relH, COLOR_SECTION);
    }

    /** 画分区小标题 */
    protected void drawHeader(GuiGraphics context, int originY, int relY, String key) {
        context.drawString(this.font, Component.translatable(key),
                contentX + 6, originY + relY + 4, COLOR_HEADER);
    }

    /** 画一个矩形线框（用 4 条 1 像素的填充拼出来，DrawContext 没有现成的描边 API） */
    protected static void drawRectOutline(GuiGraphics context, int x1, int y1, int x2, int y2, int color) {
        context.fill(x1, y1, x2, y1 + 1, color);
        context.fill(x1, y2 - 1, x2, y2, color);
        context.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
        context.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
    }

    /**
     * 滚动条：轨道 + 滑块。滑块可以<b>用鼠标按住拖</b>，也可以点轨道直接跳。
     *
     * <p>几何量顺便存进字段，鼠标事件里直接复用（不用再算一遍，也不会和画出来的位置错开）。
     */
    private void renderScrollbar(GuiGraphics context, boolean hovered) {
        if (maxScroll <= 0) {
            sbThumbH = 0;
            sbThumbTop = 0;
            sbTrackH = 0;
            sbTrackTop = 0;
            return;
        }
        int viewH = contentBottom - contentTop;
        sbTrackTop = contentTop + 2;
        sbTrackH = viewH - 4;
        sbThumbH = Math.max(18, (int) ((long) sbTrackH * viewH / contentHeight()));
        sbThumbTop = sbTrackTop + (int) ((long) (sbTrackH - sbThumbH) * scroll / maxScroll);

        int barX = left + panelW - 5;
        context.fill(barX, sbTrackTop, barX + 3, sbTrackTop + sbTrackH, COLOR_BAR_BG);
        // 悬停或拖拽时滑块加宽、变亮 —— 一眼看出「这东西能拖」
        boolean active = hovered || draggingScrollbar;
        int thumbColor = active ? 0xE0FFFFFF : COLOR_BAR;
        int thumbLeft = active ? barX - 1 : barX;
        int thumbRight = active ? barX + 5 : barX + 3;
        context.fill(thumbLeft, sbThumbTop, thumbRight, sbThumbTop + sbThumbH, thumbColor);
    }

    /** 鼠标是不是落在滚动条那条竖带上（放宽到 {@value #SCROLLBAR_GRAB_W} 像素，好抓） */
    private boolean overScrollbar(double mouseX, double mouseY) {
        return maxScroll > 0
                && mouseX >= left + panelW - SCROLLBAR_GRAB_W
                && mouseX <= left + panelW
                && mouseY >= sbTrackTop
                && mouseY <= sbTrackTop + sbTrackH;
    }

    /** 把滑块挪到某个 y（把「抓取点」对齐鼠标），并据此换算滚动量 */
    private void dragScrollbarTo(double mouseY) {
        int span = sbTrackH - sbThumbH;
        if (span <= 0) {
            return;
        }
        int thumbTop = clampInt((int) mouseY - dragGrabOffset, sbTrackTop, sbTrackTop + span);
        int next = clampInt((int) Math.round((double) (thumbTop - sbTrackTop) * maxScroll / span),
                0, maxScroll);
        if (next != scroll) {
            scroll = next;
            relayout();
        }
    }

    // ================= 输入 =================

    // 1.20.2 给滚轮事件加了「横向滚动量」，签名从 3 个参数变成 4 个。
    // 我们只关心纵向，所以新版多出来的那个参数就收着不用。
    //? if <1.20.2 {
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
    //?} else {
    /*@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double amount) {
    *///?}
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
        //? if <1.20.2 {
        return super.mouseScrolled(mouseX, mouseY, amount);
        //?} else {
        /*return super.mouseScrolled(mouseX, mouseY, horizontalAmount, amount);
        *///?}
    }

    // 1.21.9 把输入事件换成了记录类：鼠标事件统一收进 MouseButtonEvent，
    // 坐标和按键号从「三个裸参数」变成 event.x() / event.y() / event.button()。
    // 为了让下面那段逻辑一行都不用复制，这里在签名后面把三个值取成同名局部变量。
    //? if <1.21.9 {
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
    //?} else {
    /*@Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
    *///?}
        // 先看是不是按住滚动条拖
        if (button == 0 && overScrollbar(mouseX, mouseY)) {
            if (mouseY >= sbThumbTop && mouseY < sbThumbTop + sbThumbH) {
                // 按在滑块上：记住抓取点，拖的时候保持这个相对位置
                dragGrabOffset = (int) mouseY - sbThumbTop;
            } else {
                // 点轨道空白：滑块中心跳过来，然后接着拖
                dragGrabOffset = sbThumbH / 2;
                dragScrollbarTo(mouseY);
            }
            draggingScrollbar = true;
            return true;
        }
        // 标题条那条带子上可能压着「只露出一半」的控件，直接不让点，免得误触
        if (mouseY < contentTop) {
            return false;
        }
        //? if <1.21.9 {
        return super.mouseClicked(mouseX, mouseY, button);
        //?} else {
        /*return super.mouseClicked(event, doubleClick);
        *///?}
    }

    //? if <1.21.9 {
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
    //?} else {
    /*@Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        double mouseY = event.y();
    *///?}
        if (draggingScrollbar) {
            dragScrollbarTo(mouseY);
            return true;
        }
        //? if <1.21.9 {
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        //?} else {
        /*return super.mouseDragged(event, deltaX, deltaY);
        *///?}
    }

    //? if <1.21.9 {
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
    //?} else {
    /*@Override
    public boolean mouseReleased(MouseButtonEvent event) {
        int button = event.button();
    *///?}
        if (draggingScrollbar && button == 0) {
            draggingScrollbar = false;
            return true;
        }
        //? if <1.21.9 {
        return super.mouseReleased(mouseX, mouseY, button);
        //?} else {
        /*return super.mouseReleased(event);
        *///?}
    }

    // ================= 行为 =================

    /** 取消 / 按 Esc：回上层界面。子类可以覆写来丢弃草稿 */
    protected void onCancel() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    /** 「完成 / 保存」之后回上层界面 */
    protected void backToParent() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    @Override
    public void onClose() {
        onCancel();
    }

    /** 左上角弹一条动作栏提示 */
    protected void toast(String translationKey) {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(Component.translatable(translationKey), true);
        }
    }

    /**
     * 开关按钮的文案：<b>开着 → 绿色</b>，关着 → 保持默认色。
     *
     * <p>整块面板里一眼扫过去，绿色的就是「已经开了」的项 —— 不必逐个去读「开 / 关」两个字。
     */
    protected static Component toggleLabel(String optionKey, boolean value) {
        MutableComponent key = Component.translatable(optionKey);
        if (value) {
            key = key.withStyle(ChatFormatting.GREEN);
        }
        return Component.translatable("autofarm.toggle", key,
                Component.translatable(value ? "autofarm.on" : "autofarm.off"));
    }

    /**
     * 高风险开关的文案：<b>开着 → 绿色</b>（和其他开关一致，方便一眼看「开了没」），
     * <b>关着 → 红色</b> —— 红色在这里是个「别随手打开」的记号：
     * 这类开关（自动打怪、暴力模式…）出厂就<i>不推荐开启</i>，红色提醒你它是危险功能。
     */
    protected static Component dangerToggleLabel(String optionKey, boolean value) {
        MutableComponent key = Component.translatable(optionKey)
                .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED);
        return Component.translatable("autofarm.toggle", key,
                Component.translatable(value ? "autofarm.on" : "autofarm.off"));
    }

    /**
     * 画顶部 tab 栏的「选中」标记：底下一条高亮横线。
     *
     * <p>两个面板共用同一套观感 —— 当前所在的大类下面画一条金线，其余保持素净。
     */
    protected void drawTabMarker(GuiGraphics context, Button[] buttons, int currentTab) {
        for (int i = 0; i < buttons.length; i++) {
            Button btn = buttons[i];
            if (btn == null || i != currentTab) {
                continue;
            }
            context.fill(btn.getX(), btn.getY() + btn.getHeight() - 1,
                    btn.getX() + btn.getWidth(), btn.getY() + btn.getHeight(), COLOR_HEADER);
        }
    }

    protected static int clampInt(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
