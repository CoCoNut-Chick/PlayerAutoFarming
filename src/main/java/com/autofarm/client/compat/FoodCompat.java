package com.autofarm.client.compat;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
//? if >=1.20.5 {
/*import net.minecraft.core.component.DataComponents;
*///?}

/**
 * 食物信息的跨版本读法。
 *
 * <h2>为什么要单独一层</h2>
 * 1.20.5 把「物品的属性」整体搬进了<b>数据组件（DataComponents）</b>：
 * <ul>
 *   <li>{@code ItemStack#isEdible()} → 没有了，改问「有没有 FOOD 组件」；</li>
 *   <li>{@code Item#getFoodProperties()} → 没有了，食物属性挂在<b>实例</b>上而不是物品类型上；</li>
 *   <li>{@code FoodProperties} 变成 record，取值方法从 {@code getNutrition()} 变成 {@code nutrition()}。</li>
 * </ul>
 *
 * <p>这三处差异散落在 {@code ItemCategory}、{@code AutoEater}、{@code InventoryScanner}
 * 好几个文件里。与其在每个调用点都写一遍版本条件，不如全部收进这一个类 ——
 * 以后再有版本改动，只需要动这里。
 *
 * <p>注意所有方法都做了空值保护：调用点就不用各自判断了。
 */
public final class FoodCompat {

    private FoodCompat() {
    }

    /** 这格东西能不能吃 */
    public static boolean isEdible(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        //? if <1.20.5 {
        return stack.isEdible();
        //?} else {
        /*return stack.has(DataComponents.FOOD);
        *///?}
    }

    /** 这格东西的食物属性；不是食物则返回 null */
    public static FoodProperties food(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        //? if <1.20.5 {
        return stack.getItem().getFoodProperties();
        //?} else {
        /*return stack.get(DataComponents.FOOD);
        *///?}
    }

    /** 这份食物能回多少饥饿值 */
    public static int nutrition(FoodProperties food) {
        if (food == null) {
            return 0;
        }
        //? if <1.20.5 {
        return food.getNutrition();
        //?} else {
        /*return food.nutrition();
        *///?}
    }
}
