package com.autofarm.client.gui;

import com.autofarm.client.config.AutoFarmConfig;
import com.autofarm.client.inventory.InventoryScanner;
import com.autofarm.client.inventory.ItemCategory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.9 {
/*import net.minecraft.client.input.MouseButtonEvent;
*///?}
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * 「存放策略」次级面板：一张表，七类物品各一行，逐类选「不存储 / 完全存储 / 保留 n 组」。
 *
 * <h2>为什么要做成表</h2>
 * 存放这件事本来就是<b>分类</b>的：种子不能存、小麦必须存、胡萝卜要留一半。
 * 以前只有一个「备用保留组数」能调，别的都写死在代码里 —— 碰上甜菜根这种
 * 「是食物可又不是种子」的东西就没辙了。现在把归类结果整张摆出来，
 * 每一行自己说了算。
 *
 * <h2>怎么操作</h2>
 * <ul>
 *   <li><b>左键</b>点一行 → 向后循环；<b>右键</b> → 向前循环。
 *       顺序是「不存储 → 完全存储 → 保留 1 组 → … → 保留 5 组 → 回到不存储」，
 *       两个极端挨着，来回切换一下就够了；</li>
 *   <li><b>鼠标悬停</b>在某一行上，会弹一块说明：这一类的判定规则、
 *       原版里的例子，以及<b>你背包里此刻属于这一类的物品</b>。
 *       归类是按能力判的（能不能种 / 能不能吃 / 是不是方块 / 有没有耐久），
 *       所以装了模组也能当场看出某个东西被分到了哪一类。</li>
 * </ul>
 *
 * <h2>和主面板的关系</h2>
 * 它编辑的是主面板递进来的<b>同一个草稿对象</b>（和「描边样式」面板一个套路）：
 * 点「完成」回主面板、最终再由主面板的「保存并关闭」落盘；
 * 点「取消」或按 Esc 就把草稿里的策略恢复成进来之前的样子。
 */
public class StoragePolicyScreen extends ScrollPanelScreen {

    // ---------------- 内容区布局（相对内容区顶部） ----------------
    private static final int TABLE_HEADER_H = 15;
    private static final int TABLE_ROW_H = 20;
    private static final int TABLE_PAD = 4;
    private static final int ROW_FIRST = TABLE_HEADER_H + TABLE_PAD;
    private static final int SEC_TABLE_Y = 0;
    private static final int SEC_TABLE_H = ROW_FIRST + ItemCategory.count() * TABLE_ROW_H + TABLE_PAD;

    private static final int SEC_HINT_Y = SEC_TABLE_H + 4;
    private static final int SEC_HINT_H = 60;

    /** 说明区每一行的 y（相对分区顶部） */
    private static final int HINT_LINE_1 = 15;
    private static final int HINT_LINE_GAP = 11;

    /** 悬停说明框里文字自动折行的宽度 */
    private static final int TIP_WRAP = 190;
    private static final int TIP_PAD = 5;

    /** 点一下循环的档位。两个极端挨着，来回切换只要一下 */
    private static final int[] CYCLE = {-1, 0, 1, 2, 3, 4, 5};

    private static final int COLOR_KEEP_ALL = 0xFFE8E8E8;
    private static final int COLOR_STORE_ALL = 0xFFFFC46B;
    private static final int COLOR_KEEP_N = 0xFF9FE0FF;

    private final AutoFarmConfig draft;
    /** 进来时的那一份，用于「取消」 */
    private final int[] oldKeep;

    private final List<PolicyRow> rows = new ArrayList<>();

    public StoragePolicyScreen(Screen parent, AutoFarmConfig sharedDraft) {
        super(parent, Component.translatable("autofarm.storage.title"));
        this.draft = sharedDraft;
        this.oldKeep = sharedDraft.storageKeep == null ? null : sharedDraft.storageKeep.clone();
    }

    @Override
    protected int contentHeight() {
        return SEC_HINT_Y + SEC_HINT_H + 4;
    }

    // ================= 建控件 =================

    @Override
    protected void buildContent() {
        // 窗口缩放会重新走一遍 init()，这个列表必须自己清
        rows.clear();

        int rowX = contentX + TABLE_PAD;
        int rowW = contentWidth - TABLE_PAD * 2;
        // 用 displayOrder 而不是 values()：枚举顺序要留给配置数组的下标（新类别只能追加），
        // 界面上「收获食物」得紧挨着「食物」
        for (ItemCategory category : ItemCategory.displayOrder()) {
            PolicyRow row = new PolicyRow(rowX, 0, rowW, ROW_H, category);
            rows.add(row);
            addScrolling(row);
        }

        int btnW = (contentWidth - 8) / 3;
        addFooter(
                Button.builder(Component.translatable("autofarm.highlight.reset"), b -> resetToFactory())
                        .bounds(contentX, 0, btnW, ROW_H).build(),
                Button.builder(Component.translatable("autofarm.button.cancel"), b -> onCancel())
                        .bounds(contentX + btnW + 4, 0, btnW, ROW_H).build(),
                Button.builder(Component.translatable("autofarm.highlight.done"), b -> backToParent())
                        .bounds(contentX + 2 * (btnW + 4), 0, btnW, ROW_H).build()
        );
    }

    @Override
    protected void positionContent(int originY) {
        for (int i = 0; i < rows.size(); i++) {
            setRowY(rows.get(i), originY + ROW_FIRST + i * TABLE_ROW_H);
        }
    }

    // ================= 自绘 =================

    @Override
    protected void renderContent(GuiGraphics context, int originY, int mouseX, int mouseY, float delta) {
        // 表格分区不另画标题：下面这两栏表头就是它的标题
        drawSection(context, originY, SEC_TABLE_Y, SEC_TABLE_H);
        context.drawString(this.font,
                Component.translatable("autofarm.storage.col.category"),
                contentX + TABLE_PAD + 6, originY + 4, COLOR_HINT);
        Component policyHead = Component.translatable("autofarm.storage.col.policy");
        context.drawString(this.font, policyHead,
                contentX + contentWidth - TABLE_PAD - 6 - this.font.width(policyHead),
                originY + 4, COLOR_HINT);

        drawSection(context, originY, SEC_HINT_Y, SEC_HINT_H);
        drawHeader(context, originY, SEC_HINT_Y, "autofarm.storage.section.hint");
        for (int i = 0; i < 4; i++) {
            context.drawString(this.font,
                    Component.translatable("autofarm.storage.hint." + (i + 1)),
                    contentX + 6, originY + SEC_HINT_Y + HINT_LINE_1 + i * HINT_LINE_GAP,
                    COLOR_HINT);
        }
    }

    /**
     * 悬停说明框。
     *
     * <p>自己画、不走 {@code setTooltip}，是因为要保证多行 ——
     * 而且这块必须画在 {@link ScrollPanelScreen#render} 之后：
     * 内容区开着裁剪，画在里面的话贴边就会被切掉。
     */
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        for (PolicyRow row : rows) {
            if (row.isHovering(mouseX, mouseY)) {
                drawInfoBox(context, mouseX, mouseY, buildTip(row.category));
                break;
            }
        }
    }

    private void drawInfoBox(GuiGraphics context, int mouseX, int mouseY, List<TipLine> lines) {
        int lineH = this.font.lineHeight + 1;
        int maxW = 0;
        for (TipLine line : lines) {
            maxW = Math.max(maxW, this.font.width(line.text));
        }
        int boxW = maxW + TIP_PAD * 2;
        int boxH = lines.size() * lineH + TIP_PAD * 2;

        int x = mouseX + 10;
        int y = mouseY + 8;
        if (x + boxW > this.width - 4) {
            x = Math.max(4, mouseX - boxW - 10);
        }
        if (y + boxH > this.height - 4) {
            y = Math.max(4, this.height - 4 - boxH);
        }

        context.fill(x, y, x + boxW, y + boxH, 0xF0100C0C);
        drawRectOutline(context, x, y, x + boxW, y + boxH, 0x60FFFFFF);

        int ty = y + TIP_PAD;
        for (TipLine line : lines) {
            context.drawString(this.font, line.text, x + TIP_PAD, ty, line.color);
            ty += lineH;
        }
    }

    // ================= 说明框的内容 =================

    /** 一块说明里的一行：折行后的文本 + 颜色 */
    private static final class TipLine {
        private final FormattedCharSequence text;
        private final int color;

        private TipLine(FormattedCharSequence text, int color) {
            this.text = text;
            this.color = color;
        }
    }

    private List<TipLine> buildTip(ItemCategory category) {
        List<TipLine> lines = new ArrayList<>();
        wrap(lines, Component.translatable(category.nameKey()), COLOR_HEADER);
        wrap(lines, Component.translatable(category.tipKey()), COLOR_TEXT);
        wrap(lines, Component.translatable("autofarm.storage.examples",
                Component.translatable(category.examplesKey())), COLOR_TEXT);

        Map<String, Integer> found = inventoryOf(category);
        if (found.isEmpty()) {
            wrap(lines, Component.translatable("autofarm.storage.current.none"), COLOR_HINT);
        } else {
            wrap(lines, Component.translatable("autofarm.storage.current", join(found)), COLOR_NOTICE);
        }
        return lines;
    }

    private void wrap(List<TipLine> out, Component text, int color) {
        for (FormattedCharSequence line : this.font.split(text, TIP_WRAP)) {
            out.add(new TipLine(line, color));
        }
    }

    /** 背包里属于这一类的物品 → 名字 : 总数。最多列 5 种，多了用省略号收尾 */
    private Map<String, Integer> inventoryOf(ItemCategory category) {
        Map<String, Integer> found = new LinkedHashMap<>();
        if (this.minecraft == null || this.minecraft.player == null) {
            return found;
        }
        Inventory inventory = this.minecraft.player.getInventory();
        for (int slot = 0; slot < InventoryScanner.INVENTORY_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || ItemCategory.classify(stack) != category) {
                continue;
            }
            found.merge(stack.getHoverName().getString(), stack.getCount(), Integer::sum);
        }
        return found;
    }

    private static String join(Map<String, Integer> found) {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (Map.Entry<String, Integer> entry : found.entrySet()) {
            if (shown == 5) {
                sb.append("…");
                break;
            }
            if (shown > 0) {
                sb.append("、");
            }
            sb.append(entry.getKey()).append(" ×").append(entry.getValue());
            shown++;
        }
        return sb.toString();
    }

    // ================= 行为 =================

    /** 点一下循环。{@code step} 为 +1 是左键、-1 是右键 */
    private void cycle(ItemCategory category, int step) {
        int current = draft.keepFor(category);
        int index = 0;
        for (int i = 0; i < CYCLE.length; i++) {
            if (CYCLE[i] == current) {
                index = i;
                break;
            }
        }
        draft.setKeepFor(category, CYCLE[Math.floorMod(index + step, CYCLE.length)]);
    }

    private void resetToFactory() {
        draft.storageKeep = AutoFarmConfig.factoryStorageKeep();
    }

    @Override
    protected void onCancel() {
        // 直接换回进来之前那一份（因为草稿里存的是数组引用）
        draft.storageKeep = oldKeep;
        backToParent();
    }

    /** 界面上的一行：左边是类别名，右边是当前的存放方式 —— 一整行都是点击热区 */
    private final class PolicyRow extends Button {

        private final ItemCategory category;

        private PolicyRow(int x, int y, int width, int height, ItemCategory category) {
            // message 留空：父类只会画底板，文字由 renderButton 自己排版成
            // 「左类别、右策略」的表格样式
            super(x, y, width, height, Component.empty(),
                    b -> cycle(category, 1), DEFAULT_NARRATION);
            this.category = category;
        }

        /** 右键就是反向循环。这里自己判边界，不去碰 ButtonWidget 的鼠标逻辑 */
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
            if (button == 1 && isHovering(mouseX, mouseY)) {
                cycle(category, -1);
                return true;
            }
            //? if <1.21.9 {
            return super.mouseClicked(mouseX, mouseY, button);
            //?} else {
            /*return super.mouseClicked(event, doubleClick);
            *///?}
        }

        private boolean isHovering(double mouseX, double mouseY) {
            return this.visible
                    && mouseX >= getX() && mouseX < getX() + getWidth()
                    && mouseY >= getY() && mouseY < getY() + getHeight();
        }

        @Override
        protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
            // message 是空串，所以父类只会画底板和边框，不会写字；
            // 文字由下面自己排版成「左类别、右策略」的样子
            super.renderWidget(context, mouseX, mouseY, delta);

            int textY = getY() + (getHeight() - 8) / 2;
            context.drawString(StoragePolicyScreen.this.font,
                    Component.translatable(category.nameKey()), getX() + 6, textY, COLOR_TEXT);

            int keep = draft.keepFor(category);
            Component value = policyText(keep);
            int valueW = StoragePolicyScreen.this.font.width(value);
            context.drawString(StoragePolicyScreen.this.font, value,
                    getX() + getWidth() - 6 - valueW, textY, policyColor(keep));
        }
    }

    private static Component policyText(int keep) {
        if (keep < 0) {
            return Component.translatable("autofarm.storage.keep_all");
        }
        if (keep == 0) {
            return Component.translatable("autofarm.storage.store_all");
        }
        return Component.translatable("autofarm.storage.keep_n", keep);
    }

    private static int policyColor(int keep) {
        if (keep < 0) {
            return COLOR_KEEP_ALL;
        }
        return keep == 0 ? COLOR_STORE_ALL : COLOR_KEEP_N;
    }
}
