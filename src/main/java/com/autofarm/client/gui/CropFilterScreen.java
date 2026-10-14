package com.autofarm.client.gui;

import com.autofarm.client.config.AutoFarmConfig;
import com.autofarm.client.config.ConfigManager;
import com.autofarm.client.inventory.InventoryScanner;
import com.autofarm.client.scan.CropFilter;
import com.autofarm.client.scan.CropRegistry;
import com.autofarm.client.scan.CropScanner;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * 「作物」次级面板：挑哪些作物要收，以及空着的耕地补种什么。
 *
 * <h2>为什么单独开一个面板</h2>
 * 主面板上那些开关都是「要不要这么做」，而这里问的是「对<b>哪些东西</b>这么做」——
 * 数量随玩家种什么而变，塞进主面板会变成一张没有固定行数的表。所以单开一页。
 *
 * <h2>三段</h2>
 * <ol>
 *   <li><b>筛选模式</b> —— 全收 / 只收勾选的 / 不收勾选的 / 只收能补种的，点一下换一档；</li>
 *   <li><b>作物列表</b> —— 打开面板时会现场扫一遍周围，把你实际种着的作物列出来，
 *       左键点一行切换「收割 / 不收」。用的是 {@code CropScanner.findCropTypes}，
 *       所以列表里出现的都是你地里真有的东西，不是一张写死的原版清单；</li>
 *   <li><b>空地补种</b> —— 开关 + 「补种哪一种作物」。候选来自<b>你手边的种子</b>，
 *       所以不会出现「指定了小麦却一粒小麦种子都没有」这种白设的情况。</li>
 * </ol>
 *
 * <h2>和主面板的关系</h2>
 * 和「存放策略」「描边样式」两个次级面板一个套路：编辑的是主面板递进来的<b>同一个草稿</b>，
 * 点「完成」回主面板、最终由主面板的「保存并关闭」落盘；点「取消」或 Esc
 * 会把这三样设置恢复成进来之前的样子。
 */
public class CropFilterScreen extends ScrollPanelScreen {

    // ---------------- 内容区布局（相对内容区顶部） ----------------
    private static final int SEC_MODE_Y = 0;
    private static final int SEC_MODE_H = 64;
    private static final int ROW_MODE = 18;
    private static final int MODE_HINT_1 = 40;
    private static final int MODE_HINT_GAP = 11;

    private static final int SEC_LIST_Y = 68;
    /** 列表里第一行的位置（相对分区顶部）：让开标题那一行 */
    private static final int LIST_FIRST = 18;
    private static final int LIST_ROW_H = 20;
    private static final int LIST_PAD = 6;

    private static final int SEC_PLANT_H = 82;
    private static final int ROW_FILL_EMPTY = 16;
    private static final int ROW_PLANT_CROP = 36;
    private static final int PLANT_HINT_1 = 58;
    private static final int PLANT_HINT_GAP = 11;

    /** 悬停说明框 */
    private static final int TIP_WRAP = 200;
    private static final int TIP_PAD = 5;

    /** 面板宽度有限，模式那句说明最多排这么多行 */
    private static final int MODE_HINT_LINES = 2;

    private final AutoFarmConfig draft;

    // ---- 进来时的快照，供「取消」还原 ----
    private final int oldMode;
    private final List<String> oldFilter;
    private final boolean oldFillEmpty;
    private final String oldPlantCrop;

    /** 作物清单（每次重建界面时现场扫一次） */
    private List<String> candidates = new ArrayList<>();
    /** 「空地补种作物」的候选：手边真有种子的那几种 */
    private List<String> plantChoices = new ArrayList<>();

    private final List<CropRow> rows = new ArrayList<>();

    private Button modeButton;
    private Button fillEmptyButton;
    private Button plantCropButton;

    public CropFilterScreen(Screen parent, AutoFarmConfig sharedDraft) {
        super(parent, Component.translatable("autofarm.crop.title"));
        this.draft = sharedDraft;
        this.oldMode = sharedDraft.cropFilterMode;
        this.oldFilter = sharedDraft.cropFilter == null
                ? null : new ArrayList<>(sharedDraft.cropFilter);
        this.oldFillEmpty = sharedDraft.fillEmptyFarmland;
        this.oldPlantCrop = sharedDraft.plantCrop;
    }

    /**
     * 内容高度取决于作物清单有多长，所以清单必须在这里先算出来。
     *
     * <p>{@code ScrollPanelScreen.init()} 的顺序是「先问高度、再建控件」，
     * 于是这一趟顺便把清单刷新了，{@link #buildContent()} 直接用现成的结果 ——
     * 每次开面板（含缩放窗口重新 init）都会重扫，不会拿着一份过期清单。
     */
    @Override
    protected int contentHeight() {
        candidates = computeCandidates();
        plantChoices = computePlantChoices();
        int rows = Math.max(1, candidates.size());
        int listH = LIST_FIRST + rows * LIST_ROW_H + LIST_PAD;
        return SEC_LIST_Y + listH + 4 + SEC_PLANT_H + 4;
    }

    /** 作物列表分区的顶部（相对内容区顶部） */
    private int listSecY() {
        return SEC_LIST_Y;
    }

    /** 「空地补种」分区的顶部（相对内容区顶部） */
    private int plantSecY() {
        int rows = Math.max(1, candidates.size());
        return SEC_LIST_Y + LIST_FIRST + rows * LIST_ROW_H + LIST_PAD + 4;
    }

    // ================= 建控件 =================

    @Override
    protected void buildContent() {
        rows.clear();

        this.modeButton = Button.builder(modeLabel(draft.cropFilterMode), b -> cycleMode())
                .bounds(contentX, 0, contentWidth, ROW_H).build();
        addScrolling(modeButton);

        int rowW = contentWidth;
        for (String id : candidates) {
            CropRow row = new CropRow(contentX, 0, rowW, ROW_H, id);
            rows.add(row);
            addScrolling(row);
        }

        this.fillEmptyButton = Button.builder(
                toggleLabel("autofarm.crop.option.fill_empty", draft.fillEmptyFarmland), b -> {
                    draft.fillEmptyFarmland = !draft.fillEmptyFarmland;
                    b.setMessage(toggleLabel("autofarm.crop.option.fill_empty", draft.fillEmptyFarmland));
                }).bounds(contentX, 0, contentWidth, ROW_H).build();
        fillEmptyButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("autofarm.crop.fill_empty.tip")));
        addScrolling(fillEmptyButton);

        this.plantCropButton = Button.builder(plantLabel(), b -> cyclePlantCrop())
                .bounds(contentX, 0, contentWidth, ROW_H).build();
        plantCropButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("autofarm.crop.plant.tip")));
        addScrolling(plantCropButton);

        int btnW = (contentWidth - 8) / 3;
        addFooter(
                Button.builder(Component.translatable("autofarm.highlight.reset"), b -> resetToDefaults())
                        .bounds(contentX, 0, btnW, ROW_H).build(),
                Button.builder(Component.translatable("autofarm.button.cancel"), b -> onCancel())
                        .bounds(contentX + btnW + 4, 0, btnW, ROW_H).build(),
                Button.builder(Component.translatable("autofarm.highlight.done"), b -> backToParent())
                        .bounds(contentX + 2 * (btnW + 4), 0, btnW, ROW_H).build()
        );
    }

    @Override
    protected void positionContent(int originY) {
        setRowY(modeButton, originY + SEC_MODE_Y + ROW_MODE);
        int listTop = originY + listSecY() + LIST_FIRST;
        for (int i = 0; i < rows.size(); i++) {
            setRowY(rows.get(i), listTop + i * LIST_ROW_H);
        }
        int plantTop = originY + plantSecY();
        setRowY(fillEmptyButton, plantTop + ROW_FILL_EMPTY);
        setRowY(plantCropButton, plantTop + ROW_PLANT_CROP);
    }

    // ================= 自绘 =================

    @Override
    protected void renderContent(GuiGraphics context, int originY, int mouseX, int mouseY, float delta) {
        // ---- 筛选模式 ----
        drawSection(context, originY, SEC_MODE_Y, SEC_MODE_H);
        drawHeader(context, originY, SEC_MODE_Y, "autofarm.crop.section.mode");
        int hy = SEC_MODE_Y + MODE_HINT_1;
        int shown = 0;
        for (FormattedCharSequence line : this.font.split(
                Component.translatable(modeTipKey(draft.cropFilterMode)), contentWidth - 12)) {
            if (shown >= MODE_HINT_LINES) {
                break;
            }
            context.drawString(this.font, line,
                    contentX + 6, originY + hy + shown * MODE_HINT_GAP, COLOR_HINT);
            shown++;
        }

        // ---- 作物列表 ----
        int listY = listSecY();
        int rows = Math.max(1, candidates.size());
        drawSection(context, originY, listY, LIST_FIRST + rows * LIST_ROW_H + LIST_PAD);
        drawHeader(context, originY, listY, "autofarm.crop.section.list");
        // 当前模式用不到勾选时说一句，免得玩家点了半天发现没反应
        if (!listAffectsMode()) {
            Component note = Component.translatable("autofarm.crop.list.ignored");
            context.drawString(this.font, note,
                    contentX + contentWidth - 6 - this.font.width(note),
                    originY + listY + 4, COLOR_NOTICE);
        } else {
            Component note = Component.translatable("autofarm.crop.list.count", candidates.size());
            context.drawString(this.font, note,
                    contentX + contentWidth - 6 - this.font.width(note),
                    originY + listY + 4, COLOR_HINT);
        }
        if (candidates.isEmpty()) {
            context.drawString(this.font,
                    Component.translatable("autofarm.crop.list.empty"),
                    contentX + 6, originY + listY + LIST_FIRST + 5, COLOR_HINT);
        }

        // ---- 空地补种 ----
        int plantY = plantSecY();
        drawSection(context, originY, plantY, SEC_PLANT_H);
        drawHeader(context, originY, plantY, "autofarm.crop.section.plant");
        context.drawString(this.font,
                Component.translatable("autofarm.crop.plant.hint.1"),
                contentX + 6, originY + plantY + PLANT_HINT_1, COLOR_HINT);
        context.drawString(this.font,
                Component.translatable("autofarm.crop.plant.hint.2"),
                contentX + 6, originY + plantY + PLANT_HINT_1 + PLANT_HINT_GAP, COLOR_HINT);
    }

    /**
     * 悬停说明框。
     *
     * <p>和「存放策略」一样自己画、不走 {@code setTooltip}：要保证多行，
     * 而且要画在 {@link ScrollPanelScreen#render} 之后 —— 内容区开着裁剪，
     * 画在里面贴着边就会被切掉。
     */
    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        for (CropRow row : rows) {
            if (row.isHovering(mouseX, mouseY)) {
                drawInfoBox(context, mouseX, mouseY, buildTip(row.cropId));
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

    /** 一块说明里的一行：折行后的文本 + 颜色 */
    private static final class TipLine {
        private final FormattedCharSequence text;
        private final int color;

        private TipLine(FormattedCharSequence text, int color) {
            this.text = text;
            this.color = color;
        }
    }

    private List<TipLine> buildTip(String id) {
        List<TipLine> lines = new ArrayList<>();
        wrap(lines, CropRegistry.nameOf(id), COLOR_HEADER);
        wrap(lines, Component.translatable("autofarm.crop.tip.id", id), COLOR_HINT);
        wrap(lines, Component.translatable(inFilter(id)
                        ? "autofarm.crop.tip.checked" : "autofarm.crop.tip.unchecked"),
                inFilter(id) ? COLOR_NOTICE : COLOR_TEXT);
        // 「只收能补种的」模式下，每棵到底会不会被收，取决于手边还有没有它的种子 ——
        // 直接把数报出来，省得玩家对着一个灰框猜
        Block block = CropRegistry.blockOf(id);
        if (block != null) {
            int seeds = this.minecraft == null || this.minecraft.player == null
                    ? 0 : InventoryScanner.countSeeds(this.minecraft.player, block);
            wrap(lines, Component.translatable("autofarm.crop.tip.seeds", seeds),
                    seeds > 0 ? COLOR_TEXT : COLOR_NOTICE);
        }
        return lines;
    }

    private void wrap(List<TipLine> out, Component text, int color) {
        for (FormattedCharSequence line : this.font.split(text, TIP_WRAP)) {
            out.add(new TipLine(line, color));
        }
    }

    // ================= 候选清单 =================

    /**
     * 作物清单：<b>现场扫一遍周围</b>，把你地里真种着的作物列出来；
     * 再加上名单里已有的（可能这会儿不在视野里）和指定的补种作物。
     * 按显示名排序，位置稳定，不会每开一次面板就换个顺序。
     */
    private List<String> computeCandidates() {
        Set<String> ids = new LinkedHashSet<>();
        if (this.minecraft != null && this.minecraft.level != null && this.minecraft.player != null) {
            // 面板自己的扫描半径：至少 8 格、最多 32 格，免得开个面板扫出一堆东西
            int radius = Math.max(8, Math.min(32, ConfigManager.get().scanRadius));
            for (Block block : CropScanner.findCropTypes(
                    this.minecraft.level,
                    this.minecraft.player.blockPosition(),
                    radius,
                    CropScanner.DEFAULT_DOWN,
                    CropScanner.DEFAULT_UP)) {
                String id = CropRegistry.idOf(block);
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        if (draft.cropFilter != null) {
            ids.addAll(draft.cropFilter);
        }
        if (draft.plantCrop != null) {
            ids.add(draft.plantCrop);
        }
        List<String> list = new ArrayList<>(ids);
        list.sort(Comparator.comparing(id -> CropRegistry.nameOf(id).getString()));
        return list;
    }

    /**
     * 「空地补种作物」的候选：<b>手边真有种子</b>的那几种。
     *
     * <p>刻意不从注册表里把所有种子捞出来 —— 那样会列出几百种你根本没有的作物，
     * 而且选了也种不了。从背包反推，列表短且每一项都能真的用上。
     */
    private List<String> computePlantChoices() {
        List<String> out = new ArrayList<>();
        if (this.minecraft == null || this.minecraft.player == null) {
            return out;
        }
        Inventory inventory = this.minecraft.player.getInventory();
        for (int slot = 0; slot < InventoryScanner.INVENTORY_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!InventoryScanner.isPlantable(stack)) {
                continue;
            }
            String id = CropRegistry.idOf(((BlockItem) stack.getItem()).getBlock());
            if (id != null && !out.contains(id)) {
                out.add(id);
            }
        }
        out.sort(Comparator.comparing(id -> CropRegistry.nameOf(id).getString()));
        return out;
    }

    // ================= 行为 =================

    private boolean inFilter(String id) {
        return draft.cropFilter != null && draft.cropFilter.contains(id);
    }

    /** 当前模式看不看这份「勾选名单」 */
    private boolean listAffectsMode() {
        return draft.cropFilterMode == AutoFarmConfig.FILTER_WHITELIST
                || draft.cropFilterMode == AutoFarmConfig.FILTER_BLACKLIST;
    }

    private void toggleCrop(String id) {
        if (draft.cropFilter == null) {
            draft.cropFilter = new ArrayList<>();
        }
        if (!draft.cropFilter.remove(id)) {
            draft.cropFilter.add(id);
        }
    }

    private void cycleMode() {
        int next = (draft.cropFilterMode + 1) % AutoFarmConfig.FILTER_MODE_COUNT;
        // 切进「只收勾选的」而名单还是空的 → 先把现有的作物全勾上。
        // 效果是「切过去什么都不会变」，玩家再从里面取消掉不想要的，
        // 比一进去就什么都不收要好得多
        if (next == AutoFarmConfig.FILTER_WHITELIST
                && (draft.cropFilter == null || draft.cropFilter.isEmpty())) {
            draft.cropFilter = new ArrayList<>(candidates);
        }
        draft.cropFilterMode = next;
        if (modeButton != null) {
            modeButton.setMessage(modeLabel(next));
        }
    }

    private void cyclePlantCrop() {
        // 档位：自动 → 每种手边的种子 → 回到自动
        if (plantChoices.isEmpty()) {
            draft.plantCrop = null;
        } else {
            int current = draft.plantCrop == null ? -1 : plantChoices.indexOf(draft.plantCrop);
            int next = current + 1;
            draft.plantCrop = next >= plantChoices.size() ? null : plantChoices.get(next);
        }
        if (plantCropButton != null) {
            plantCropButton.setMessage(plantLabel());
        }
    }

    private void resetToDefaults() {
        draft.cropFilterMode = AutoFarmConfig.FILTER_OFF;
        draft.cropFilter = new ArrayList<>();
        draft.fillEmptyFarmland = false;
        draft.plantCrop = null;
        // 控件上的文字要跟着变，直接重建整页最省事
        if (this.minecraft != null) {
            this.minecraft.setScreen(new CropFilterScreen(this.parent, draft));
        }
    }

    @Override
    protected void onCancel() {
        // 直接换回进来之前那一份
        draft.cropFilterMode = oldMode;
        draft.cropFilter = oldFilter;
        draft.fillEmptyFarmland = oldFillEmpty;
        draft.plantCrop = oldPlantCrop;
        backToParent();
    }

    // ================= 文案 =================

    private static Component modeLabel(int mode) {
        return Component.translatable("autofarm.crop.mode.label",
                Component.translatable(modeNameKey(mode)));
    }

    private static String modeNameKey(int mode) {
        return switch (mode) {
            case AutoFarmConfig.FILTER_WHITELIST -> "autofarm.crop.mode.whitelist";
            case AutoFarmConfig.FILTER_BLACKLIST -> "autofarm.crop.mode.blacklist";
            case AutoFarmConfig.FILTER_ONLY_REPLANTABLE -> "autofarm.crop.mode.only_replantable";
            default -> "autofarm.crop.mode.off";
        };
    }

    private static String modeTipKey(int mode) {
        return switch (mode) {
            case AutoFarmConfig.FILTER_WHITELIST -> "autofarm.crop.mode.tip.whitelist";
            case AutoFarmConfig.FILTER_BLACKLIST -> "autofarm.crop.mode.tip.blacklist";
            case AutoFarmConfig.FILTER_ONLY_REPLANTABLE -> "autofarm.crop.mode.tip.only_replantable";
            default -> "autofarm.crop.mode.tip.off";
        };
    }

    private Component plantLabel() {
        Component value;
        if (draft.plantCrop != null && CropRegistry.blockOf(draft.plantCrop) != null) {
            value = CropRegistry.nameOf(draft.plantCrop);
        } else if (plantChoices.isEmpty()) {
            value = Component.translatable("autofarm.crop.plant.none");
        } else {
            value = Component.translatable("autofarm.crop.plant.auto");
        }
        return Component.translatable("autofarm.toggle",
                Component.translatable("autofarm.crop.option.plant_crop"), value);
    }

    /** 界面上的一行：左边是作物名，右边是「收割 / 不收」—— 一整行都是点击热区 */
    private final class CropRow extends Button {

        private final String cropId;

        private CropRow(int x, int y, int width, int height, String cropId) {
            // message 留空：父类只画底板，文字由 renderButton 自己排成表格样式
            super(x, y, width, height, Component.empty(),
                    b -> toggleCrop(cropId), DEFAULT_NARRATION);
            this.cropId = cropId;
        }

        private boolean isHovering(double mouseX, double mouseY) {
            return this.visible
                    && mouseX >= getX() && mouseX < getX() + getWidth()
                    && mouseY >= getY() && mouseY < getY() + getHeight();
        }

        //? if <1.21.11 {
        @Override
        protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
            super.renderWidget(context, mouseX, mouseY, delta);
        //?} else {
        /*@Override
        protected void renderContents(GuiGraphics context, int mouseX, int mouseY, float delta) {
        *///?}

            // 右边那半：在名单里就写「收割」（白名单）/「不收」（黑名单），
            // 不在名单里则反过来。这样不用去理解「正向还是反向」，看字就行
            boolean checked = inFilter(cropId);
            boolean positive = CropFilter.isListPositive(draft);
            boolean willHarvest = checked == positive;

            int textY = getY() + (getHeight() - 8) / 2;
            // 会收的作物名字用亮色、不会收的压暗 —— 不靠任何特殊符号，
            // 默认字体一定有这两个颜色，扫描一眼就知道哪些在名单里
            context.drawString(CropFilterScreen.this.font,
                    CropRegistry.nameOf(cropId), getX() + 6, textY,
                    !listAffectsMode() || willHarvest ? COLOR_TEXT : COLOR_HINT);

            Component value = Component.translatable(willHarvest
                    ? "autofarm.crop.harvest.yes" : "autofarm.crop.harvest.no");
            int color = !listAffectsMode() ? COLOR_HINT
                    : (willHarvest ? COLOR_TEXT : COLOR_NOTICE);
            int valueW = CropFilterScreen.this.font.width(value);
            context.drawString(CropFilterScreen.this.font, value,
                    getX() + getWidth() - 6 - valueW, textY, color);
        }
    }
}
