package com.autofarm.client.gui;

import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * 一个画着「调色板」的小方块按钮，点它进入高亮样式的次级面板。
 *
 * <p>图标可以用代码画出来，不需要额外的贴图资源。
 * 板身用<b>当前的描边颜色</b>填充，所以不用打开面板也能一眼看出现在选的是什么色。
 */
public class PaletteIconButton extends Button {

    /** 画几像素的方块。设计上是 10×10，外面留 4 像素的边距 */
    private static final int ICON = 10;

    /** 每次画的时候现取，这样草稿一改图标就跟着变 */
    private final IntSupplier colorSupplier;

    public PaletteIconButton(int x, int y, int size, IntSupplier colorSupplier, OnPress onPress) {
        super(x, y, size, size, Component.empty(), onPress, DEFAULT_NARRATION);
        this.colorSupplier = colorSupplier;
        setTooltip(Tooltip.create(Component.translatable("autofarm.highlight.customize")));
    }

    // 1.21.11 把「画底板」和「画内容」拆成了两步：
    // 底板归框架调用的 renderWidget，子类改为只填一个新增的抽象方法 renderContents。
    //? if <1.21.11 {
    @Override
    protected void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // 先让超类把原版按钮底板画出来，保证和旁边按钮风格一致
        super.renderWidget(context, mouseX, mouseY, delta);
    //?} else {
    /*@Override
    protected void renderContents(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // 新版底板由框架负责，这里只填内容，所以不再调 super
    *///?}

        int ix = getX() + (getWidth() - ICON) / 2;
        int iy = getY() + (getHeight() - ICON) / 2;

        // 板身 = 当前描边色
        context.fill(ix, iy, ix + ICON, iy + ICON, 0xFF101010);
        context.fill(ix + 1, iy + 1, ix + ICON - 1, iy + ICON - 1, 0xFF000000 | (colorSupplier.getAsInt() & 0xFFFFFF));

        // 四个颜料点，让人一眼认出这是调色板而不是普通色块
        context.fill(ix + 2, iy + 2, ix + 4, iy + 4, 0xFFFFFFFF);
        context.fill(ix + 6, iy + 2, ix + 8, iy + 4, 0xFF303030);
        context.fill(ix + 2, iy + 6, ix + 4, iy + 8, 0xFF303030);
        context.fill(ix + 6, iy + 6, ix + 8, iy + 8, 0xFFFFFFFF);
    }
}
