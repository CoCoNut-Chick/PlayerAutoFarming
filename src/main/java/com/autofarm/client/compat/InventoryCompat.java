package com.autofarm.client.compat;

import net.minecraft.world.entity.player.Inventory;

/**
 * 快捷栏「当前选中格」的跨版本读写。
 *
 * <h2>为什么要包一层</h2>
 * 1.21.5 把 {@code Inventory#selected} 这个一直公开的字段收成了私有，
 * 改成一对访问器 {@code getSelectedSlot()} / {@code setSelectedSlot(int)}。
 *
 * <p>这个字段在本模组里是**既读又写**的（换手、判断"当前手边是不是这一格"、
 * 找食物槽位……散在 5 个文件里），所以两个方向都得包一层。
 * 收进来的好处和 {@link FoodCompat} 一样：以后版本再动，只需要改这一个文件。
 */
public final class InventoryCompat {

    private InventoryCompat() {
    }

    /** 当前选中的快捷栏槽位（0~8） */
    public static int selected(Inventory inventory) {
        //? if <1.21.5 {
        return inventory.selected;
        //?} else {
        /*return inventory.getSelectedSlot();
        *///?}
    }

    /** 把选中的快捷栏槽位切到 slot（0~8） */
    public static void select(Inventory inventory, int slot) {
        //? if <1.21.5 {
        inventory.selected = slot;
        //?} else {
        /*inventory.setSelectedSlot(slot);
        *///?}
    }
}
