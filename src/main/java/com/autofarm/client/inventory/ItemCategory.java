package com.autofarm.client.inventory;

import com.autofarm.client.compat.FoodCompat;
import com.autofarm.client.scan.CropRegistry;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
//? if <1.21.2 {
import net.minecraft.world.item.ItemNameBlockItem;
//?}
import net.minecraft.world.item.ItemStack;

/**
 * 「往箱子里存东西的时候，这一类怎么处理」——把背包里的东西分成固定的七类。
 *
 * <h2>为什么按能力分，而不是按物品名</h2>
 * 因为物品名是列不完的。装一个农业模组就会多出十几种作物，靠白名单迟早会漏。
 * 所以这里判的全是<b>原版机制</b>：能不能种、能不能吃、放下去会不会变成方块、
 * 有没有耐久 —— 任何模组的东西都绕不过这四件事。
 *
 * <h2>判定顺序（先满足的先算）</h2>
 * <ol>
 *   <li>能种、不能吃、是「专门的种子物品」→ {@link #SEED}；</li>
 *   <li>能种，但不是专门的种子（收获物本身就是补种材料）→ {@link #SELF_SEED}；</li>
 *   <li>不能种、能吃，但名字对得上某种作物的种子 → {@link #HARVEST_FOOD}（甜菜根）；</li>
 *   <li>不能种、能吃 → {@link #FOOD}；</li>
 *   <li>不能种、不能吃、是方块 → {@link #BLOCK}；</li>
 *   <li>都不能，但有耐久 → {@link #TOOL}；</li>
 *   <li>剩下的 → {@link #PRODUCE}。</li>
 * </ol>
 *
 * <h2>「专门的种子」和「收获物本身就是种子」怎么分</h2>
 * 用 {@link ItemNameBlockItem}。原版里凡是「放下去长出来的不是它自己」的物品
 * —— 小麦种子 → 小麦、甜菜种子 → 甜菜、瓜种 → 瓜茎、地狱疣 —— 都是这个类；
 * 而甘蔗、竹子、仙人掌这种「放下去就是它自己」的，用的是普通的 {@link BlockItem}。
 *
 * <p>胡萝卜、土豆虽然也用了 {@link ItemNameBlockItem}，但它们能吃，
 * 所以按「收获物即种子」算 —— 这正是最需要「留几组」的那一类。
 *
 * <p>这一套判定同时还决定了补给点搬运时的<b>留几栈</b>：见
 * {@link com.autofarm.client.config.AutoFarmConfig#keepFor(ItemCategory)}。
 */
public enum ItemCategory {

    /**
     * 专门的种子：放下去长成作物，本身不能吃。
     * 补种全指着它，所以默认一栈都不存。
     */
    SEED("autofarm.category.seed",
            "autofarm.category.seed.tip",
            "autofarm.category.seed.examples",
            -1),

    /**
     * 收获物本身就是补种材料：既能种又能吃（胡萝卜、土豆），
     * 或者种下去就是它自己（甘蔗、竹子、仙人掌）。
     *
     * <p>这类最尴尬：全存了下次没得种，全留着背包又会被它塞满。默认留 1 组、其余存掉。
     */
    SELF_SEED("autofarm.category.self_seed",
            "autofarm.category.self_seed.tip",
            "autofarm.category.self_seed.examples",
            1),

    /** 能吃、种不下去、也看不出是哪种作物收下来的东西（面包、熟牛排……）。饿到跑不动时要靠它，默认不存 */
    FOOD("autofarm.category.food",
            "autofarm.category.food.tip",
            "autofarm.category.food.examples",
            -1),

    /** 放下去会变成方块、但那个方块不是作物（石头、木板……）。可能被拿去垫脚，默认不存 */
    BLOCK("autofarm.category.block",
            "autofarm.category.block.tip",
            "autofarm.category.block.examples",
            -1),

    /** 有耐久的工具、武器，默认不存 */
    TOOL("autofarm.category.tool",
            "autofarm.category.tool.tip",
            "autofarm.category.tool.examples",
            -1),

    /** 既不能吃、也不是方块、又没耐久的收获物。留着只占格子，默认全存 */
    PRODUCE("autofarm.category.produce",
            "autofarm.category.produce.tip",
            "autofarm.category.produce.examples",
            0),

    /**
     * 「能吃的收获物」：某种作物收下来、但<b>不能拿去补种</b>的食物。
     *
     * <p>甜菜根就是这个类型：种下去的是<b>甜菜种子</b>，收下来的是<b>甜菜根</b>，
     * 两者不是同一个物品，所以甜菜根只能吃、种不回去。这类东西留在身上纯粹占格子，
     * 默认<b>完全存储</b>。
     *
     * <p>怎么认出它（还是不写物品名单）：遍历注册表里所有「种子物品」
     * （放下去会变成作物方块的那种），看有没有哪一种的名字是
     * <code>&lt;本物品&gt;_seeds</code> / <code>&lt;本物品&gt;_seed</code>。
     * 甜菜根叫 {@code beetroot}、甜菜种子叫 {@code beetroot_seeds} —— 对得上；
     * 面包、牛排找不到对应的种子，自然落不进这一类。
     *
     * <p><b>为什么不干脆把「食物」整类都设成完全存储</b>：面包、熟牛排这类东西
     * 是饿到跑不动时的救命粮，自动进食只从快捷栏拿，全存进箱子就真没得吃了。
     * 「收获食物」才是「只能当粮食、留着没用」的那一档，两者得分开。
     */
    HARVEST_FOOD("autofarm.category.harvest_food",
            "autofarm.category.harvest_food.tip",
            "autofarm.category.harvest_food.examples",
            0);

    /** 一栈都不存（全留身上） */
    public static final int KEEP_ALL = -1;
    /** 全部存进箱子 */
    public static final int STORE_ALL = 0;
    /** 「保留 n 组」的上限，和面板上循环的档位一致 */
    public static final int MAX_KEEP = 5;

    /** 种子物品名字的后缀。原版一律用 {@code _seeds}，个别模组用短一点的 {@code _seed} */
    private static final String[] SEED_SUFFIXES = {"_seeds", "_seed"};

    /**
     * 缓存：所有「收获食物」的物品名（去掉命名空间的部分）。
     * 物品注册表在游戏跑起来之后就不会再变了，所以算一次就够了。
     */
    private static Set<String> harvestFoodNames;

    private final String nameKey;
    private final String tipKey;
    private final String examplesKey;
    private final int defaultKeep;

    ItemCategory(String nameKey, String tipKey, String examplesKey, int defaultKeep) {
        this.nameKey = nameKey;
        this.tipKey = tipKey;
        this.examplesKey = examplesKey;
        this.defaultKeep = defaultKeep;
    }

    /** 类别总数的简写，给配置数组定长用 */
    public static int count() {
        return values().length;
    }

    /**
     * 面板里的显示顺序（和 {@link #ordinal()} 无关）。
     *
     * <p>枚举声明的顺序决定了配置数组的下标，所以新类别只能<b>追加在末尾</b>，
     * 否则老配置里逐类存的值会全部错位。但在界面上「收获食物」就该紧挨着「食物」，
     * 于是单独给一份展示用的顺序。
     */
    public static ItemCategory[] displayOrder() {
        return new ItemCategory[]{SEED, SELF_SEED, FOOD, HARVEST_FOOD, BLOCK, TOOL, PRODUCE};
    }

    /** 显示名，例如「补种种子」 */
    public String nameKey() {
        return nameKey;
    }

    /** 一句话说明这类东西是什么、为什么这么存 */
    public String tipKey() {
        return tipKey;
    }

    /** 原版里属于这一类的例子，用来给玩家一个直观对照 */
    public String examplesKey() {
        return examplesKey;
    }

    /** 出厂默认的存放策略：-1 不存、0 全存、n 留 n 组 */
    public int defaultKeep() {
        return defaultKeep;
    }

    /**
     * 这一类算不算「作物相关」（该进农作物输出容器，而不是杂物容器）。
     *
     * <p>种子、收获物即种子、纯收获物、收获食物都算作物相关；
     * 食物、方块、工具则算「杂物」，走杂物临时存储容器。
     */
    public boolean isCropRelated() {
        return this == SEED || this == SELF_SEED || this == PRODUCE || this == HARVEST_FOOD;
    }

    /**
     * 这个物品属于哪一类。<b>空栈返回 null</b>。
     *
     * @see ItemCategory 顶部的判定顺序
     */
    public static ItemCategory classify(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        boolean plantable = InventoryScanner.isPlantable(stack);
        boolean food = FoodCompat.isEdible(stack);

        if (plantable) {
            // 专门的种子：原版给它们用的都是 AliasedBlockItem ——
            // 「放下会长出别的方块」这件事，只有这个类表达得出来
            //? if <1.21.2 {
            if (!food && stack.getItem() instanceof ItemNameBlockItem) {
                return SEED;
            }
            //?} else {
            /*if (!food && stack.getItem() instanceof BlockItem) {
                return SEED;
            }
            *///?}
            return SELF_SEED;
        }
        if (food) {
            // 能吃、种不下去，但名字对得上某种作物的种子（甜菜根 ↔ 甜菜种子）→
            // 这是「收获食物」，和面包、熟牛排那种成品食物分开
            return isHarvestFood(stack) ? HARVEST_FOOD : FOOD;
        }
        if (stack.getItem() instanceof BlockItem) {
            return BLOCK;
        }
        if (stack.getMaxDamage() > 0) {
            return TOOL;
        }
        return PRODUCE;
    }

    /**
     * 这个物品是不是「某种作物收下来、但又种不回去」的东西。
     *
     * <p>判法见 {@link #HARVEST_FOOD}：拿它的名字去对所有种子物品的名字，
     * 看有没有 <code>名字 + _seeds</code> / <code>名字 + _seed</code>。
     * 走的是注册表里的实际名字而不是物品白名单，所以模组作物只要按同样习惯命名，
     * 一样能自动认出来。
     */
    private static boolean isHarvestFood(ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        return harvestFoodNames().contains(path);
    }

    /** 惰性构建上面那张名字表：第一次用到的时候才算，算完缓存住 */
    private static Set<String> harvestFoodNames() {
        if (harvestFoodNames == null) {
            Set<String> names = new HashSet<>();
            for (Item item : BuiltInRegistries.ITEM) {
                // 只管真正的种子：放下去会变成作物方块的那种
                if (!(item instanceof BlockItem blockItem)
                        || !CropRegistry.isCropBlock(blockItem.getBlock())) {
                    continue;
                }
                String seedPath = BuiltInRegistries.ITEM.getKey(item).getPath();
                for (String suffix : SEED_SUFFIXES) {
                    if (seedPath.endsWith(suffix) && seedPath.length() > suffix.length()) {
                        names.add(seedPath.substring(0, seedPath.length() - suffix.length()));
                    }
                }
            }
            harvestFoodNames = names;
        }
        return harvestFoodNames;
    }
}
