package com.autofarm.client.scan;

import java.util.Collection;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * 作物识别。刻意不维护「作物白名单」，
 * 这样原版作物和装了农业模组之后的作物都能被自动认出来。
 *
 * <h2>为什么要分「怎么收」</h2>
 * 不是所有成熟作物都是<b>挖掉</b>了收的。原版就有两种：
 * <ul>
 *   <li><b>挖</b>（{@link HarvestMode#BREAK}）—— 小麦、胡萝卜、土豆、甜菜根、地狱疣、可可果。
 *       挖掉之后再补种；</li>
 *   <li><b>右键摘</b>（{@link HarvestMode#USE}）—— <b>甜浆果丛</b>和<b>发光浆果</b>（洞穴藤蔓）。
 *       植株要留着，摘完还会再结；照着挖的话会把整丛铲掉。</li>
 * </ul>
 * 还有第三种要主动<b>绕开</b>（{@link HarvestMode#SKIP}）：<b>瓜茎</b>（西瓜/南瓜）。
 * 瓜茎带 age 属性、长满之后 age 顶到 7，看上去和「成熟作物」一模一样，
 * 但它上面结的瓜才是要收的东西 —— 之前拿 age 一刀切，会把瓜茎当成熟作物挖掉。
 *
 * <h2>本类还要回答的三个问题</h2>
 * <ul>
 *   <li>{@link #isCropBlock}：「这东西算不算作物」—— 补给点取种子时用；</li>
 *   <li>{@link #isHarvestCrop}：「算不算能收的作物」—— 列作物清单、做筛选时用（排除瓜茎）；</li>
 *   <li>{@link #isPlantingSpot}：「这一格能不能补种」—— 空地补种时用
 *       （上面是空气、下面是耕地）。</li>
 * </ul>
 * 再加上 {@link #idOf} / {@link #blockOf} / {@link #nameOf} 三个「方块 ↔ 注册名」的
 * 转换 —— 配置里没法存方块对象，只能存名字。
 */
public final class CropRegistry {

    /** 这一格该怎么收 */
    public enum HarvestMode {
        /** 不碰：不是作物、还没熟、或者压根不该动（比如瓜茎） */
        SKIP,
        /** 挖掉收：作物本体就是要收的东西，挖完可以补种 */
        BREAK,
        /** 右键摘：植株留着，只把果实摘走 */
        USE
    }

    private CropRegistry() {
    }

    /**
     * 判断一个方块状态是不是「现在已经可以收」的东西。
     *
     * <p>等价于 {@code harvestMode(state) != SKIP}，保留这个名字是因为
     * 扫描器和高亮用的都是它：它们只关心「要不要把这一格画上框」。
     */
    public static boolean isMature(BlockState state) {
        return harvestMode(state) != HarvestMode.SKIP;
    }

    /**
     * 这一格该怎么收 —— 见 {@link HarvestMode}。
     *
     * <p>判定的顺序即优先级，从「绝对不能碰」到「兜底」：
     * <ol>
     *   <li><b>瓜茎</b>（{@link StemBlock} / {@link AttachedStemBlock}）→ {@code SKIP}。
     *       带 age、长满后 age 顶格，是最容易被误认成成熟作物的一类；</li>
     *   <li><b>洞穴藤蔓</b>（{@link CaveVines}，发光浆果）→ 熟没熟看的是
     *       「有没有结浆果」这个<b>布尔</b>属性，不是 age，所以得单独判；</li>
     *   <li><b>甜浆果丛</b>（{@link SweetBerryBushBlock}）→ 长满才收，
     *       收法是右键，别挖；</li>
     *   <li><b>标准作物</b>（{@link CropBlock}）→ 用它自己的 {@code isMature}；</li>
     *   <li>兜底：带 {@code age} 属性且已顶到最大值 → 挖掉收。</li>
     * </ol>
     */
    public static HarvestMode harvestMode(BlockState state) {
        if (state == null) {
            return HarvestMode.SKIP;
        }
        Block block = state.getBlock();

        // 瓜茎：绝对不能挖。挖了瓜田就得重等一轮，而且它本来也不算「作物熟了」
        if (block instanceof StemBlock || block instanceof AttachedStemBlock) {
            return HarvestMode.SKIP;
        }

        // 发光浆果：结实了就要右键摘。注意这个属性只有「藤头」有，
        // 垂下来的那几节没有 BERRIES —— 所以先 contains 再 get，免得抛异常
        if (block instanceof CaveVines) {
            boolean berries = state.hasProperty(CaveVines.BERRIES) && state.getValue(CaveVines.BERRIES);
            return berries ? HarvestMode.USE : HarvestMode.SKIP;
        }

        // 甜浆果丛：长满才摘。它和普通作物不一样 —— 挖下去整丛就没了
        if (block instanceof SweetBerryBushBlock) {
            return reachedMaxAge(state) ? HarvestMode.USE : HarvestMode.SKIP;
        }

        // 判定：原版及绝大多数模组作物的标准写法 —— 继承 CropBlock
        if (block instanceof CropBlock crop) {
            return crop.isMaxAge(state) ? HarvestMode.BREAK : HarvestMode.SKIP;
        }

        // 兜底：只要方块身上带一个叫 age 的整数属性，且当前值已经顶到最大值，
        // 就当作成熟（覆盖不走 CropBlock 的模组作物）
        return reachedMaxAge(state) ? HarvestMode.BREAK : HarvestMode.SKIP;
    }

    /**
     * 这个方块种类本身是不是「会长大的作物」。
     *
     * <p>和 {@link #harvestMode} 问的是不同的问题：那边问「现在能不能收」，
     * 这边问「这个方块算不算作物」。用处在补给点取种子 ——
     * 一个物品放下去正好变成作物方块，那它就是种子，不需要维护种子对照表。
     *
     * <p>注意这里<b>故意把瓜茎也算成作物</b>：西瓜种子放下去变的就是瓜茎，
     * 把它排除出去的话，西瓜种子就不算种子了，补给点取种子会漏掉它。
     * 「瓜茎不该挖」是收获阶段的事，由 {@link HarvestMode#SKIP} 负责。
     */
    public static boolean isCropBlock(Block block) {
        if (block == null) {
            return false;
        }
        if (block instanceof CropBlock) {
            return true;
        }
        // 兜底覆盖不走 CropBlock 的作物：甘蔗、甜菜、瓜茎、地狱疣……都带 age
        return findAgeProperty(block.defaultBlockState()) != null;
    }

    /**
     * 这个方块算不算「能正常收割 / 补种的作物」——用来<b>列作物清单、做筛选</b>。
     *
     * <p>和 {@link #isCropBlock} 只差一点：把<b>瓜茎</b>排除掉。瓜茎是「种子的落点」
     * （西瓜种子放下去变的就是瓜茎），认种子时必须有它；但它本身不该被收
     * （见 {@link HarvestMode#SKIP}），所以也不该出现在「附近有哪些作物」这张表里。
     */
    public static boolean isHarvestCrop(Block block) {
        return isCropBlock(block)
                && !(block instanceof StemBlock)
                && !(block instanceof AttachedStemBlock);
    }

    /**
     * 这一格是不是「<b>还没熟、但已经种下了</b>」的作物 —— 附加功能「骨粉催熟」的目标。
     *
     * <p>它是「还没到收割的时候」那半张表：{@link #harvestMode} 判出 {@code SKIP}
     * 的作物里，有一部分是刚种下的幼苗（该催），另一部分是压根不该碰的东西
     * （瓜茎、发光浆果……不该催）。这里把后者剔掉。
     *
     * <p>两类要排除的，各有各的道理：
     * <ul>
     *   <li><b>瓜茎</b>（西瓜 / 南瓜茎）—— 它带 age，所以 {@link #isCropBlock} 会认它；
     *       但骨粉对瓜茎没有用，真正要等的是<b>旁边那格瓜</b>（见 {@link #isGourdBlock}），
     *       而且那也根本不是「催熟」能解决的 —— 所以整个排除；</li>
     *   <li><b>发光浆果 / 洞穴藤蔓</b>（{@link CaveVines}）—— 它没有 age，熟没熟看的是
     *       「有没有结浆果」，而骨粉对它无效，催了也白催。</li>
     * </ul>
     *
     * <p>剩下的就是真正能被骨粉推进的作物：小麦、胡萝卜、土豆、甜菜、甜浆果丛……
     * 以及各类模组作物 —— 只要它遵循原版「带 age」这套写法就自动适配。
     */
    public static boolean isGrowing(BlockState state) {
        if (state == null) {
            return false;
        }
        Block block = state.getBlock();
        if (block instanceof StemBlock || block instanceof AttachedStemBlock) {
            return false; // 瓜茎：骨粉没用，收成在旁边那格瓜上
        }
        if (block instanceof CaveVines) {
            return false; // 发光浆果：没有 age，骨粉也不吃它
        }
        // 得先是「作物」，然后才是「还没熟」。已成熟的那些归收割管，不归催熟管
        return isCropBlock(block) && harvestMode(state) == HarvestMode.SKIP;
    }

    /**
     * 这一格是不是「空着的耕地」——也就是可以补种的地方。
     *
     * <p>判据两条：<b>这一格是空气</b>，而且<b>下面那一格是耕地</b>。
     * 为什么返回的是「空气那一格」而不是耕地那一格：作物长在耕地上方，
     * 所以「要种的地方」是空气那格 —— 这和收割目标用的是同一套坐标语义
     * （收割目标也是作物所在的那一格），路线规划才能把两者一视同仁地排队。
     */
    public static boolean isPlantingSpot(ClientLevel world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        if (!world.getBlockState(pos).isAir()) {
            return false;
        }
        // 这里刻意用另一份 Mutable：调用方传进来的可能正是扫描用的那个游标对象，
        // 在它身上 down() 会把游标本身挪走，后面的循环就全乱了
        BELOW.set(pos.getX(), pos.getY() - 1, pos.getZ());
        return world.getBlockState(BELOW).getBlock() instanceof FarmBlock;
    }

    /** {@link #isPlantingSpot} 内部复用的一份坐标，避免每格都新建对象 */
    private static final BlockPos.MutableBlockPos BELOW = new BlockPos.MutableBlockPos();

    /** 方块 → 注册名（{@code minecraft:wheat}）。给配置里存作物名单用 */
    public static String idOf(Block block) {
        return block == null ? null : BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    /** 注册名 → 方块。认不出来（手改坏了配置、模组被卸了）返回 null */
    public static Block blockOf(String id) {
        if (id == null) {
            return null;
        }
        ResourceLocation identifier = ResourceLocation.tryParse(id);
        return identifier == null ? null : BuiltInRegistries.BLOCK.get(identifier);
    }

    /** 注册名 → 显示名（界面里列作物清单用）。认不出来就把原字符串显示出来 */
    public static Component nameOf(String id) {
        Block block = blockOf(id);
        if (block == null || block == Blocks.AIR) {
            return Component.literal(id == null ? "?" : id);
        }
        return block.getName();
    }

    /**
     * 这一格是不是「瓜类方块」—— 西瓜块（{@link Blocks#MELON}）或南瓜块
     * （{@link Blocks#PUMPKIN}）。瓜茎识别的收割目标。
     *
     * <p>注意它和 {@link #isHarvestCrop} 不是一回事：瓜茎是「作物」（茎会生长），
     * 但瓜方块本身不带 age、不是作物 —— 它是<b>收成</b>，是瓜茎旁边该挖掉的那一格。
     */
    public static boolean isGourdBlock(Block block) {
        return block == Blocks.MELON || block == Blocks.PUMPKIN;
    }

    /**
     * 这个掉落物算不算「农作物的产物」——附加功能「优先拾取掉落物」用它挑目标。
     *
     * <p>判据就两条，都不需要维护对照表：
     * <ol>
     *   <li><b>放下去能长出作物</b>：种子，以及胡萝卜 / 土豆这种
     *       「收下来的东西本身就是种子」的物品 —— 它们都实现了
     *       {@link ItemNameBlockItem}，且指向一个作物方块；</li>
     *   <li><b>名字和某个作物方块对得上</b>：小麦、甜菜根这类收下来的产物本身不是种子，
     *       但物品名和方块名有稳定的对应关系，见 {@link #isCropProduce}。</li>
     * </ol>
     *
     * <p>它刻意<b>不</b>认「可种植的东西」以外的物品，所以不会把玩家自己丢的杂物
     * 一股脑捡回来 —— 它只负责把从地里飞出去的东西收回来。
     */
    public static boolean isCropDrop(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        // 1. 放下去变作物方块 = 种子（胡萝卜、土豆这种「作物本体」也算）
        if (item instanceof ItemNameBlockItem aliased && isCropBlock(aliased.getBlock())) {
            return true;
        }
        return isCropProduce(item);
    }

    /**
     * 收下来的产物本身（不是种子）——比如小麦、甜菜根、土豆。
     *
     * <p>物品名和作物方块名的关系并不统一：小麦方块就叫 {@code wheat}，
     * 而胡萝卜 / 土豆 / 甜菜根这些方块是<b>复数</b>（{@code carrots} /
     * {@code potatoes} / {@code beetroots}），收下来的东西却是单数。
     * 所以这里把「原样、加 s、加 es」三种写法都试一遍，
     * 再用 {@code block.asItem() == item} 确认它们确实指同一件东西。
     */
    private static boolean isCropProduce(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        String path = id.getPath();
        for (String candidate : new String[]{path, path + "s", path + "es"}) {
            //? if <1.21 {
            Block block = BuiltInRegistries.BLOCK.get(new ResourceLocation(id.getNamespace(), candidate));
            //?} else {
            /*Block block = BuiltInRegistries.BLOCK.get(
                    ResourceLocation.fromNamespaceAndPath(id.getNamespace(), candidate));
            *///?}
            if (block != Blocks.AIR && isCropBlock(block) && block.asItem() == item) {
                return true;
            }
        }
        return false;
    }

    /** 带 {@code age} 属性、而且已经顶到最大值（长满了） */
    private static boolean reachedMaxAge(BlockState state) {
        IntegerProperty age = findAgeProperty(state);
        if (age == null) {
            return false;
        }
        Collection<Integer> values = age.getPossibleValues();
        int max = values.stream().mapToInt(Integer::intValue).max().orElse(-1);
        return max >= 0 && state.getValue(age) >= max;
    }

    /** 找出「名为 age 的整数属性」，没有就返回 null */
    private static IntegerProperty findAgeProperty(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty age && "age".equals(property.getName())) {
                return age;
            }
        }
        return null;
    }
}
