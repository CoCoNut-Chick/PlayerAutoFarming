package com.autofarm.client.gui;

import com.autofarm.client.config.AutoFarmConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 高亮描边的次级面板：调颜色、粗细、不透明度。
 *
 * <p>它编辑的是主面板递进来的<b>同一个草稿对象</b>，所以：
 * <ul>
 *   <li>点「完成」→ 回到主面板，草稿里已经是新颜色了（主面板最终「保存并关闭」时才落盘）</li>
 *   <li>点「取消」或按 Esc → 把草稿恢复成进来之前的样子，等于什么都没改</li>
 * </ul>
 * 这样比「两次保存」顺手，也不会出现「主面板拿旧草稿把这里改的东西覆盖掉」的问题。
 */
public class HighlightStyleScreen extends ScrollPanelScreen {

    // ---------------- 内容区布局（相对内容区顶部） ----------------
    private static final int SEC_PREVIEW_Y = 0, SEC_PREVIEW_H = 58;
    private static final int SEC_PRESET_Y = 62, SEC_PRESET_H = 68;
    private static final int SEC_CUSTOM_Y = 134, SEC_CUSTOM_H = 114;
    private static final int SEC_STYLE_Y = 252, SEC_STYLE_H = 62;

    private static final int ROW_R = 158;
    private static final int ROW_G = 180;
    private static final int ROW_B = 202;
    private static final int ROW_HEX = 224;
    private static final int ROW_WIDTH = 268;
    private static final int ROW_ALPHA = 290;

    private static final int PRESET_ROW1 = 84;
    private static final int PRESET_ROW2 = 106;
    private static final int PRESET_COLS = 8;
    private static final int PRESET_GAP = 4;

    private static final int PREVIEW_X = 8;
    private static final int PREVIEW_Y = 20;
    private static final int PREVIEW_W = 44;
    private static final int PREVIEW_H = 34;

    private static final int HEX_LABEL_W = 48;
    private static final int HEX_FIELD_W = 88;

    /** 预设色板：白 / 红 / 橙 / 黄 / 亮绿 / 绿 / 青 / 蓝 · 紫 / 粉 / 金 / 薄荷 / 浅灰 / 灰 / 深灰 / 黑 */
    private static final int[] PRESETS = {
            0xFFFFFF, 0xFF5555, 0xFF8C26, 0xFFE14D, 0x6BFF6B, 0x2ECC71, 0x3FE0E0, 0x4DA6FF,
            0x8A5CFF, 0xFF6BC1, 0xFFD700, 0x00E5A0, 0xE0E0E0, 0x8C8C8C, 0x4D4D4D, 0x000000
    };

    private static final int DEFAULT_COLOR = 0xFF8C26;
    private static final int DEFAULT_WIDTH = 1;
    private static final int DEFAULT_ALPHA = 90;

    private final AutoFarmConfig draft;
    /** 进来时的原值，用于「取消」 */
    private final int oldColor;
    private final int oldWidth;
    private final int oldAlpha;

    private final List<SwatchButton> swatches = new ArrayList<>();

    private LabeledIntSlider redSlider;
    private LabeledIntSlider greenSlider;
    private LabeledIntSlider blueSlider;
    private LabeledIntSlider widthSlider;
    private LabeledIntSlider alphaSlider;
    private EditBox hexField;

    /** 防止「同步输入框 ↔ 同步滑块」互相递归触发 */
    private boolean syncing;

    public HighlightStyleScreen(Screen parent, AutoFarmConfig sharedDraft) {
        super(parent, Component.translatable("autofarm.highlight.title"));
        this.draft = sharedDraft;
        this.oldColor = sharedDraft.highlightColor;
        this.oldWidth = sharedDraft.highlightWidth;
        this.oldAlpha = sharedDraft.highlightAlpha;
    }

    @Override
    protected int contentHeight() {
        return SEC_STYLE_Y + SEC_STYLE_H + 4;
    }

    // ================= 建控件 =================

    @Override
    protected void buildContent() {
        int fullW = contentWidth;

        // 窗口缩放会重新走一遍 init()，这个列表必须自己清，
        // 否则旧控件会一直累加，positionContent 会去摆一堆已经作废的对象
        swatches.clear();

        // ---- 预设色板 ----
        int swatchW = swatchWidth();
        for (int i = 0; i < PRESETS.length; i++) {
            int row = i / PRESET_COLS;
            int col = i % PRESET_COLS;
            SwatchButton swatch = new SwatchButton(
                    contentX + col * (swatchW + PRESET_GAP),
                    row == 0 ? PRESET_ROW1 : PRESET_ROW2,
                    swatchW, ROW_H, PRESETS[i]);
            swatches.add(swatch);
            addScrolling(swatch);
        }

        // ---- R / G / B ----
        this.redSlider = new LabeledIntSlider(contentX, 0, fullW, ROW_H,
                "autofarm.highlight.red", 0, 255, red(), v -> setChannel(16, v));
        this.greenSlider = new LabeledIntSlider(contentX, 0, fullW, ROW_H,
                "autofarm.highlight.green", 0, 255, green(), v -> setChannel(8, v));
        this.blueSlider = new LabeledIntSlider(contentX, 0, fullW, ROW_H,
                "autofarm.highlight.blue", 0, 255, blue(), v -> setChannel(0, v));
        addScrolling(redSlider);
        addScrolling(greenSlider);
        addScrolling(blueSlider);

        // ---- 十六进制输入框 ----
        // 注意：TextFieldWidget.setText() 会触发 setChangedListener 的回调，
        // 所以初始值必须在挂监听器之前设好，免得建界面时就白跑一轮。
        this.hexField = new EditBox(this.font,
                contentX + HEX_LABEL_W, 0, HEX_FIELD_W, ROW_H, Component.empty());
        hexField.setMaxLength(7);
        hexField.setValue(hex());
        hexField.setResponder(this::onHexTyped);
        addScrolling(hexField);

        // ---- 粗细 / 不透明度 ----
        this.widthSlider = new LabeledIntSlider(contentX, 0, fullW, ROW_H,
                "autofarm.highlight.width", 1, 8, draft.highlightWidth, " px",
                v -> draft.highlightWidth = v);
        this.alphaSlider = new LabeledIntSlider(contentX, 0, fullW, ROW_H,
                "autofarm.highlight.alpha", 10, 100, draft.highlightAlpha, "%",
                v -> draft.highlightAlpha = v);
        addScrolling(widthSlider);
        addScrolling(alphaSlider);

        // ---- 页脚：恢复默认 / 取消 / 完成 ----
        int btnW = (fullW - 8) / 3;
        addFooter(
                Button.builder(Component.translatable("autofarm.highlight.reset"), b -> resetToDefault())
                        .bounds(contentX, 0, btnW, ROW_H).build(),
                Button.builder(Component.translatable("autofarm.button.cancel"), b -> onCancel())
                        .bounds(contentX + btnW + 4, 0, btnW, ROW_H).build(),
                Button.builder(Component.translatable("autofarm.highlight.done"), b -> backToParent())
                        .bounds(contentX + 2 * (btnW + 4), 0, btnW, ROW_H).build()
        );
    }

    private int swatchWidth() {
        return Math.max(12, (contentWidth - (PRESET_COLS - 1) * PRESET_GAP) / PRESET_COLS);
    }

    @Override
    protected void positionContent(int originY) {
        int swatchW = swatchWidth();
        for (int i = 0; i < swatches.size(); i++) {
            int row = i / PRESET_COLS;
            int col = i % PRESET_COLS;
            SwatchButton w = swatches.get(i);
            w.setX(contentX + col * (swatchW + PRESET_GAP));
            w.setY(originY + (row == 0 ? PRESET_ROW1 : PRESET_ROW2));
        }

        setRowY(redSlider, originY + ROW_R);
        setRowY(greenSlider, originY + ROW_G);
        setRowY(blueSlider, originY + ROW_B);
        setRowY(hexField, originY + ROW_HEX);
        setRowY(widthSlider, originY + ROW_WIDTH);
        setRowY(alphaSlider, originY + ROW_ALPHA);
    }

    // ================= 自绘 =================

    @Override
    protected void renderContent(GuiGraphics context, int originY, int mouseX, int mouseY, float delta) {
        // ---- 预览 ----
        drawSection(context, originY, SEC_PREVIEW_Y, SEC_PREVIEW_H);
        drawHeader(context, originY, SEC_PREVIEW_Y, "autofarm.highlight.section.preview");

        int px = contentX + PREVIEW_X;
        int py = originY + SEC_PREVIEW_Y + PREVIEW_Y;

        context.fill(px, py, px + PREVIEW_W, py + PREVIEW_H, 0x60000000);
        // 用真实的颜色 / 粗细 / 不透明度套一圈，所见即所得
        int argb = draft.highlightArgb();
        for (int layer = 0; layer < draft.highlightWidth; layer++) {
            drawRectOutline(context,
                    px - layer, py - layer,
                    px + PREVIEW_W + layer, py + PREVIEW_H + layer,
                    argb);
        }

        int textX = px + PREVIEW_W + 14;
        context.drawString(this.font, Component.literal(hex()), textX, py + 2, COLOR_TITLE);
        context.drawString(this.font,
                Component.literal(draft.highlightWidth + " px   " + draft.highlightAlpha + "%"),
                textX, py + 15, COLOR_HINT);
        context.drawString(this.font,
                Component.translatable("autofarm.highlight.preview.hint"), textX, py + 25, COLOR_HINT);

        // ---- 预设颜色 ----
        drawSection(context, originY, SEC_PRESET_Y, SEC_PRESET_H);
        drawHeader(context, originY, SEC_PRESET_Y, "autofarm.highlight.section.preset");

        // ---- 自定义颜色 ----
        drawSection(context, originY, SEC_CUSTOM_Y, SEC_CUSTOM_H);
        drawHeader(context, originY, SEC_CUSTOM_Y, "autofarm.highlight.section.custom");
        context.drawString(this.font,
                Component.translatable("autofarm.highlight.hex"),
                contentX + 6, originY + ROW_HEX + 5, COLOR_HINT);

        // 输入框右边再放一块当前色，改色时一眼能对上
        int chipX = contentX + HEX_LABEL_W + HEX_FIELD_W + 10;
        context.fill(chipX, originY + ROW_HEX + 1, chipX + 18, originY + ROW_HEX + 17, 0xFF000000);
        context.fill(chipX + 1, originY + ROW_HEX + 2, chipX + 17, originY + ROW_HEX + 16,
                0xFF000000 | (draft.highlightColor & 0xFFFFFF));

        // ---- 描边样式 ----
        drawSection(context, originY, SEC_STYLE_Y, SEC_STYLE_H);
        drawHeader(context, originY, SEC_STYLE_Y, "autofarm.highlight.section.style");
    }

    // ================= 颜色读写 =================

    private int red() {
        return (draft.highlightColor >> 16) & 0xFF;
    }

    private int green() {
        return (draft.highlightColor >> 8) & 0xFF;
    }

    private int blue() {
        return draft.highlightColor & 0xFF;
    }

    private String hex() {
        return String.format(Locale.ROOT, "#%06X", draft.highlightColor & 0xFFFFFF);
    }

    /** 改 R / G / B 里的某一个通道 */
    private void setChannel(int shift, int value) {
        int mask = 0xFF << shift;
        int c = draft.highlightColor & 0xFFFFFF;
        draft.highlightColor = (c & ~mask) | ((value & 0xFF) << shift);
        syncHexField();
    }

    /** 点预设色 */
    private void applyColor(int rgb) {
        draft.highlightColor = rgb & 0xFFFFFF;
        syncSliders();
        syncHexField();
    }

    private void syncSliders() {
        redSlider.setIntValue(red());
        greenSlider.setIntValue(green());
        blueSlider.setIntValue(blue());
    }

    private void syncHexField() {
        if (hexField == null || syncing) {
            return;
        }
        syncing = true;
        hexField.setValue(hex());
        syncing = false;
    }

    /** 用户在十六进制框里敲字：能解析才应用，敲到一半（比如 "#FF8"）就安静地忽略 */
    private void onHexTyped(String raw) {
        if (syncing) {
            return;
        }
        Integer parsed = parseHex(raw);
        if (parsed == null) {
            return;
        }
        syncing = true;
        draft.highlightColor = parsed;
        syncSliders();
        syncing = false;
    }

    private static Integer parseHex(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.length() != 6) {
            return null;
        }
        try {
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void resetToDefault() {
        draft.highlightColor = DEFAULT_COLOR;
        draft.highlightWidth = DEFAULT_WIDTH;
        draft.highlightAlpha = DEFAULT_ALPHA;
        syncSliders();
        syncHexField();
        widthSlider.setIntValue(DEFAULT_WIDTH);
        alphaSlider.setIntValue(DEFAULT_ALPHA);
    }

    // ================= 行为 =================

    @Override
    protected void onCancel() {
        // 把草稿恢复成进来之前的样子
        draft.highlightColor = oldColor;
        draft.highlightWidth = oldWidth;
        draft.highlightAlpha = oldAlpha;
        backToParent();
    }

    // ================= 内部控件 =================

    /** 一个纯色小方块，点一下就把描边颜色设成它 */
    private class SwatchButton extends Button {

        private final int color;

        SwatchButton(int x, int y, int width, int height, int color) {
            super(x, y, width, height, Component.empty(),
                    b -> applyColor(color), DEFAULT_NARRATION);
            this.color = color;
        }

        @Override
        protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
            super.renderWidget(context, mouseX, mouseY, delta);

            int x1 = getX() + 2;
            int y1 = getY() + 2;
            int x2 = getX() + getWidth() - 2;
            int y2 = getY() + getHeight() - 2;
            context.fill(x1, y1, x2, y2, 0xFF000000 | color);

            if ((draft.highlightColor & 0xFFFFFF) == color) {
                // 选中态：套一圈黑白双框，无论底色是黑还是白都看得清
                drawRectOutline(context, x1 - 2, y1 - 2, x2 + 2, y2 + 2, 0xFF000000);
                drawRectOutline(context, x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xFFFFFFFF);
            }
        }
    }
}
