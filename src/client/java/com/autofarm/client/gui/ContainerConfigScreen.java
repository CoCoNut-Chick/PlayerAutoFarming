package com.autofarm.client.gui;

import com.autofarm.client.config.AutoFarmConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * 「扩展存储与补给」次级面板：给四个专用容器各配一个坐标 + 一个启用开关。
 *
 * <h2>四个容器各干什么</h2>
 * <ol>
 *   <li><b>农作物输出容器</b> —— 存收获物（小麦、胡萝卜这类作物产物）；</li>
 *   <li><b>种子输入容器</b> —— 取种子（补种 / 补种空耕地用）；</li>
 *   <li><b>工具补给容器</b> —— 取工具、骨粉这类耗材；</li>
 *   <li><b>杂物临时存储容器</b> —— 存非农作物物品（石头、泥土这类），默认关闭。</li>
 * </ol>
 *
 * <p>每个容器都能单独<b>禁用</b>：禁用后该用途退回默认补给点（主面板那组坐标）；
 * 杂物容器特殊 —— 禁用就干脆不存杂物，免得把杂物和作物混进同一个箱子。
 *
 * <h2>和主面板的关系</h2>
 * 编辑的是主面板递进来的<b>同一个草稿对象</b>：点「完成」回主面板、
 * 最终由主面板的「保存并关闭」落盘；点「取消」/ Esc 恢复进来前的样子。
 */
public class ContainerConfigScreen extends ScrollPanelScreen {

    private static final int CONTAINERS = 4;
    private static final int AXIS_LABEL_W = 14;

    /** 每个容器分区：标题 + 启用行 + 坐标行 + 记录行 */
    private static final int TITLE_H = 18;
    private static final int ROW_GAP = 20;
    private static final int SEC_H = TITLE_H + 3 * ROW_GAP + 4;
    private static final int SEC_GAP = 4;

    /** 分区内相对 y */
    private static final int ROW_ENABLE = TITLE_H;
    private static final int ROW_COORD = TITLE_H + ROW_GAP;
    private static final int ROW_RECORD = TITLE_H + 2 * ROW_GAP;

    private static final String[] TITLE_KEYS = {
            "autofarm.container.crop",
            "autofarm.container.seed",
            "autofarm.container.tool",
            "autofarm.container.junk"
    };

    private final AutoFarmConfig draft;

    // 「取消」时恢复用的旧值
    private final boolean[] oldEnabled = new boolean[CONTAINERS];
    private final Integer[][] oldCoord = new Integer[CONTAINERS][3];

    private final ButtonWidget[] enabledBtn = new ButtonWidget[CONTAINERS];
    private final TextFieldWidget[][] coordField = new TextFieldWidget[CONTAINERS][3];
    private final int[][] axisLabelX = new int[CONTAINERS][3];
    private final ButtonWidget[] recordBtn = new ButtonWidget[CONTAINERS];
    private final ButtonWidget[] clearBtn = new ButtonWidget[CONTAINERS];

    public ContainerConfigScreen(Screen parent, AutoFarmConfig sharedDraft) {
        super(parent, Text.translatable("autofarm.container.title"));
        this.draft = sharedDraft;
        for (int c = 0; c < CONTAINERS; c++) {
            oldEnabled[c] = getEnabled(c);
            for (int a = 0; a < 3; a++) {
                oldCoord[c][a] = getCoord(c, a);
            }
        }
    }

    @Override
    protected int contentHeight() {
        return CONTAINERS * (SEC_H + SEC_GAP) + 8;
    }

    // ================= 建控件 =================

    @Override
    protected void buildContent() {
        int fullW = contentWidth;
        int halfW = (fullW - 4) / 2;
        int colW = Math.max(56, (fullW - 12) / 3);
        int fieldW = colW - AXIS_LABEL_W;

        for (int c = 0; c < CONTAINERS; c++) {
            // 启用开关
            int ci = c;
            enabledBtn[c] = ButtonWidget.builder(
                    toggleLabel("autofarm.option.container_enabled", getEnabled(c)), b -> {
                        setEnabled(ci, !getEnabled(ci));
                        b.setMessage(toggleLabel("autofarm.option.container_enabled", getEnabled(ci)));
                    }).dimensions(contentX, 0, fullW, ROW_H).build();
            addScrolling(enabledBtn[c]);

            // 三个坐标字段
            for (int a = 0; a < 3; a++) {
                axisLabelX[c][a] = contentX + a * (colW + 6);
                coordField[c][a] = coordField(axisLabelX[c][a] + AXIS_LABEL_W, fieldW, getCoord(c, a));
                addScrolling(coordField[c][a]);
            }

            // 记录 / 清除
            recordBtn[c] = ButtonWidget.builder(Text.translatable("autofarm.supply.record"), b -> {
                if (this.client != null && this.client.player != null) {
                    BlockPos p = this.client.player.getBlockPos();
                    coordField[ci][0].setText(Integer.toString(p.getX()));
                    coordField[ci][1].setText(Integer.toString(p.getY()));
                    coordField[ci][2].setText(Integer.toString(p.getZ()));
                }
            }).dimensions(contentX, 0, halfW, ROW_H).build();
            addScrolling(recordBtn[c]);

            clearBtn[c] = ButtonWidget.builder(Text.translatable("autofarm.supply.clear"), b -> {
                coordField[ci][0].setText("");
                coordField[ci][1].setText("");
                coordField[ci][2].setText("");
            }).dimensions(contentX + halfW + 4, 0, halfW, ROW_H).build();
            addScrolling(clearBtn[c]);
        }

        int btnW = (contentWidth - 4) / 2;
        addFooter(
                ButtonWidget.builder(Text.translatable("autofarm.button.cancel"), b -> onCancel())
                        .dimensions(contentX, 0, btnW, ROW_H).build(),
                ButtonWidget.builder(Text.translatable("autofarm.highlight.done"), b -> applyAndClose())
                        .dimensions(contentX + btnW + 4, 0, btnW, ROW_H).build()
        );
    }

    @Override
    protected void positionContent(int originY) {
        for (int c = 0; c < CONTAINERS; c++) {
            int y = originY + c * (SEC_H + SEC_GAP);
            setRowY(enabledBtn[c], y + ROW_ENABLE);
            for (int a = 0; a < 3; a++) {
                setRowY(coordField[c][a], y + ROW_COORD);
            }
            setRowY(recordBtn[c], y + ROW_RECORD);
            setRowY(clearBtn[c], y + ROW_RECORD);
        }
    }

    @Override
    protected void renderContent(DrawContext context, int originY, int mouseX, int mouseY, float delta) {
        for (int c = 0; c < CONTAINERS; c++) {
            int y = originY + c * (SEC_H + SEC_GAP);
            drawSection(context, originY, c * (SEC_H + SEC_GAP), SEC_H);
            drawHeader(context, originY, c * (SEC_H + SEC_GAP), TITLE_KEYS[c]);

            // 坐标轴的 X / Y / Z 标签
            for (int a = 0; a < 3; a++) {
                context.drawTextWithShadow(this.textRenderer,
                        Text.literal(AXIS_NAMES[a]),
                        axisLabelX[c][a], y + ROW_COORD + 5, COLOR_HINT);
            }
        }
    }

    private static final String[] AXIS_NAMES = {"X", "Y", "Z"};

    // ================= 行为 =================

    private void applyAndClose() {
        for (int c = 0; c < CONTAINERS; c++) {
            for (int a = 0; a < 3; a++) {
                setCoord(c, a, parseOrNull(coordField[c][a]));
            }
        }
        backToParent();
    }

    @Override
    protected void onCancel() {
        // 恢复进来前的样子
        for (int c = 0; c < CONTAINERS; c++) {
            setEnabled(c, oldEnabled[c]);
            for (int a = 0; a < 3; a++) {
                setCoord(c, a, oldCoord[c][a]);
            }
        }
        super.onCancel();
    }

    private TextFieldWidget coordField(int x, int width, Integer value) {
        TextFieldWidget field = new TextFieldWidget(this.textRenderer, x, 0, width, ROW_H, Text.empty());
        field.setMaxLength(7);
        field.setText(value == null ? "" : Integer.toString(value));
        return field;
    }

    private static Integer parseOrNull(TextFieldWidget field) {
        if (field == null) {
            return null;
        }
        String raw = field.getText();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ================= draft 字段读写（按容器索引） =================

    private boolean getEnabled(int c) {
        return switch (c) {
            case 0 -> draft.outCropEnabled;
            case 1 -> draft.inSeedEnabled;
            case 2 -> draft.inToolEnabled;
            default -> draft.outJunkEnabled;
        };
    }

    private void setEnabled(int c, boolean v) {
        switch (c) {
            case 0 -> draft.outCropEnabled = v;
            case 1 -> draft.inSeedEnabled = v;
            case 2 -> draft.inToolEnabled = v;
            default -> draft.outJunkEnabled = v;
        }
    }

    private Integer getCoord(int c, int axis) {
        return switch (c) {
            case 0 -> switch (axis) {
                case 0 -> draft.outCropX;
                case 1 -> draft.outCropY;
                default -> draft.outCropZ;
            };
            case 1 -> switch (axis) {
                case 0 -> draft.inSeedX;
                case 1 -> draft.inSeedY;
                default -> draft.inSeedZ;
            };
            case 2 -> switch (axis) {
                case 0 -> draft.inToolX;
                case 1 -> draft.inToolY;
                default -> draft.inToolZ;
            };
            default -> switch (axis) {
                case 0 -> draft.outJunkX;
                case 1 -> draft.outJunkY;
                default -> draft.outJunkZ;
            };
        };
    }

    private void setCoord(int c, int axis, Integer v) {
        switch (c) {
            case 0 -> {
                if (axis == 0) draft.outCropX = v;
                else if (axis == 1) draft.outCropY = v;
                else draft.outCropZ = v;
            }
            case 1 -> {
                if (axis == 0) draft.inSeedX = v;
                else if (axis == 1) draft.inSeedY = v;
                else draft.inSeedZ = v;
            }
            case 2 -> {
                if (axis == 0) draft.inToolX = v;
                else if (axis == 1) draft.inToolY = v;
                else draft.inToolZ = v;
            }
            default -> {
                if (axis == 0) draft.outJunkX = v;
                else if (axis == 1) draft.outJunkY = v;
                else draft.outJunkZ = v;
            }
        }
    }
}
