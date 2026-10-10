package com.autofarm.client.inventory;

import com.autofarm.client.config.AutoFarmConfig;
import com.autofarm.client.scan.CropRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EmptyBlockView;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 「我现在身上有什么」的快速体检。
 *
 * <h2>为什么只看快捷栏</h2>
 * Minecraft 里放置方块用的是<b>主手</b>，而主手拿的东西由快捷栏（前 9 格）决定。
 * 背包深处的方块必须先搬到快捷栏才用得上，中间要开背包、点格子，
 * 对一个「边走路边干活」的自动化流程来说太重了。
 *
 * <p>所以这里的约定是：<b>想让它拿来垫脚的方块，请放进快捷栏</b>。
 * 这样既能自动选中（改 {@code selectedSlot} 就行，不用开界面），也不会误动你背包里
 * 精心整理好的东西。挖掘工具同理 —— 见 {@link #bestTool}：
 * 挖之前会先在快捷栏里挑一把最合适的换到手上，挑不到比空手更好的就保持不动。
 */
public final class InventoryScanner {

    /** 玩家背包的总格数：快捷栏 9 + 主栏 27 */
    public static final int INVENTORY_SLOTS = 36;

    private InventoryScanner() {
    }

    /**
     * 一把候选工具：在第几格、对这个方块多快、是不是「正确工具」。
     *
     * @param slot     快捷栏槽位（0~8）
     * @param speed    对目标方块的挖掘速度倍数（原版 {@code getMiningSpeedMultiplier}）
     * @param suitable 是不是挖这种方块该用的工具（决定掉落物和 30 / 100 的除数）
     */
    public record ToolChoice(int slot, float speed, boolean suitable) {
    }

    /**
     * 在快捷栏里挑一把「对目标方块最合适」的工具。
     *
     * <p>打分用的是原版破坏速度公式的核心：{@code 速度 / (30 或 100)}。
     * 除数取 30 表示「这是挖这种方块该用的工具」（掉落物正常，而且快三倍多），
     * 否则取 100。硬度是同一个方块共有的属性，比较时约得掉，所以这里不用管。
     *
     * <p><b>基准是「现在手上这把」</b>，只有确实更快才换。所以快捷栏里放把镐子，
     * 遇到石头它会自己换过去；全是没用的东西时，它不会乱动你的手。
     *
     * @return 最优选择；玩家或方块状态为空时返回 null
     */
    public static ToolChoice bestTool(ClientPlayerEntity player, BlockState state) {
        if (player == null || state == null) {
            return null;
        }
        PlayerInventory inventory = player.getInventory();
        int hotbar = PlayerInventory.getHotbarSize();
        int selected = inventory.selectedSlot;

        // 基准：现在手上这把（空手就是速度 1、非正确工具）
        ItemStack held = inventory.getStack(selected);
        float heldSpeed = held.isEmpty() ? 1.0f : held.getMiningSpeedMultiplier(state);
        boolean heldSuitable = !held.isEmpty() && held.isSuitableFor(state);
        ToolChoice best = new ToolChoice(selected, heldSpeed, heldSuitable);
        float bestScore = score(heldSpeed, heldSuitable);

        for (int slot = 0; slot < hotbar; slot++) {
            if (slot == selected) {
                continue;
            }
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            float speed = stack.getMiningSpeedMultiplier(state);
            boolean suitable = stack.isSuitableFor(state);
            float current = score(speed, suitable);
            // 必须「确实更快」才换手，同分时保持不动，免得两把一样的镐子之间来回跳
            if (current > bestScore + 1.0e-4f) {
                bestScore = current;
                best = new ToolChoice(slot, speed, suitable);
            }
        }
        return best;
    }

    /**
     * 挖某个方块之前，该把快捷栏切到第几格。
     *
     * @return <b>-1</b> 表示不用换（现在手上这把就是最优的，或者没有更好的）
     */
    public static int findBestToolSlot(ClientPlayerEntity player, BlockState state) {
        ToolChoice best = bestTool(player, state);
        if (best == null) {
            return -1;
        }
        return best.slot() == player.getInventory().selectedSlot ? -1 : best.slot();
    }

    /**
     * 手里的工具快用坏了 → 从背包里换一把更好的（附加功能，默认关闭）。
     *
     * <p>判据只有两条，都很保守：
     * <ol>
     *   <li>手上拿的确实是<b>有耐久的东西</b>（不是方块、不是空手）；</li>
     *   <li>它的剩余耐久已经低于 {@code thresholdPercent}% —— 也就是快见底了。</li>
     * </ol>
     * 满足之后，去背包里找<b>同一种物品</b>、而且剩余耐久<b>更多</b>的那一把换成主手。
     * 找不到就什么也不做 —— 绝不会用一把破的换掉你手上好的。
     *
     * <p>换手走的是 {@link #swapToHotbar}，也就是原版「按数字键换位」那条路，
     * 全程不打开任何界面。和新工具之间不留空窗：调用方应当在它返回 {@code true} 时
     * 跳过本 tick 的动作，下一 tick 再用新手继续。
     *
     * @param thresholdPercent 剩余耐久低于百分之几就该换（1~50）
     * @return 有没有真的换了手
     */
    public static boolean replaceWornTool(MinecraftClient client, ClientPlayerEntity player,
                                          int thresholdPercent) {
        if (client == null || player == null) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        int selected = inventory.selectedSlot;
        ItemStack held = inventory.getStack(selected);
        if (held.isEmpty() || held.getMaxDamage() <= 0) {
            return false; // 手上不是工具，不掺和
        }
        int maxDamage = held.getMaxDamage();
        int remaining = maxDamage - held.getDamage();
        int threshold = Math.max(1, maxDamage * thresholdPercent / 100);
        if (remaining > threshold) {
            return false; // 还够用
        }

        // 找同种物品里剩余耐久最多的那一把。基准是「手上这把」，所以只有确实更好才换
        int bestSlot = -1;
        int bestRemaining = remaining;
        for (int slot = 0; slot < INVENTORY_SLOTS; slot++) {
            if (slot == selected) {
                continue;
            }
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty() || stack.getMaxDamage() <= 0 || !stack.isOf(held.getItem())) {
                continue;
            }
            int rest = stack.getMaxDamage() - stack.getDamage();
            if (rest > bestRemaining) {
                bestRemaining = rest;
                bestSlot = slot;
            }
        }
        if (bestSlot < 0) {
            return false;
        }
        return swapToHotbar(client, player, bestSlot, selected);
    }

    /**
     * 快捷栏里「回饱食度最多」的那一格食物。
     *
     * <p>比的是 {@link FoodComponent#getHunger()}，也就是物品栏里显示的那半个鸡腿数，
     * <b>不是</b>饱和度。理由很实际：决定「能不能疾跑」的是饥饿值本身，
     * 所以一口气回最多的那个，才是最短时间内恢复赶路效率的选择。
     *
     * <p>并列时取靠前的槽位（第一个遇到的就是最好的），不会在两个一样的食物之间乱跳。
     *
     * @return 没有食物时返回 -1
     */
    public static int findBestFoodSlot(ClientPlayerEntity player) {
        if (player == null) {
            return -1;
        }
        PlayerInventory inventory = player.getInventory();
        int hotbar = PlayerInventory.getHotbarSize();
        int best = -1;
        int bestHunger = 0;

        for (int slot = 0; slot < hotbar; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty() || !stack.isFood()) {
                continue;
            }
            FoodComponent food = stack.getItem().getFoodComponent();
            if (food == null) {
                continue;
            }
            if (food.getHunger() > bestHunger) {
                bestHunger = food.getHunger();
                best = slot;
            }
        }
        return best;
    }

    /**
     * 快捷栏里能种出 {@code cropBlock} 这种作物的那一格。
     *
     * <p>做法是<b>反查</b>：找一个「放下去正好变成这种作物方块」的物品，
     * 也就是 {@code BlockItem.getBlock() == cropBlock}。不维护「小麦配小麦种子」
     * 这种对照表，好处是模组作物也能自动适配。
     *
     * <p>几个原版的例子可以印证这个判定是对的：
     * 小麦种子的 {@code getBlock()} 是小麦方块、胡萝卜物品对应胡萝卜方块、
     * 西瓜种子对应西瓜茎 —— 全是同一个方块实例，所以 {@code ==} 比得中。
     *
     * @return 没有种子时返回 -1
     */
    public static int findSeedSlot(ClientPlayerEntity player, Block cropBlock) {
        return findSeedSlot(player, cropBlock, -1);
    }

    /**
     * 和上一个一样，但会<b>优先看某个快捷栏格</b>。
     *
     * <p>优先的那一格就是面板上的「默认种子槽位」：它只是一个偏好，不是强制 ——
     * 那一格没有该作物种子时，照样会把整个快捷栏扫一遍。
     *
     * @param preferredHotbar 想优先用的快捷栏格（0~8）；不关心就传 -1
     */
    public static int findSeedSlot(ClientPlayerEntity player, Block cropBlock, int preferredHotbar) {
        if (player == null || cropBlock == null) {
            return -1;
        }
        PlayerInventory inventory = player.getInventory();
        int hotbar = PlayerInventory.getHotbarSize();

        if (preferredHotbar >= 0 && preferredHotbar < hotbar
                && matchesSeed(inventory.getStack(preferredHotbar), cropBlock)) {
            return preferredHotbar;
        }
        for (int slot = 0; slot < hotbar; slot++) {
            if (matchesSeed(inventory.getStack(slot), cropBlock)) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * <b>深搜整个背包</b>（快捷栏 9 格 + 主背包 27 格）找这种作物的种子。
     *
     * <p>它是「快捷栏里没有种子」之后的第二道防线。顺序上先快捷栏、再主背包，
     * 所以顺手也能当「手边有没有」用。
     *
     * <p>为什么要单独有一个：原版放置只用主手，所以种子必须先进快捷栏。
     * 以前这里只找快捷栏，快捷栏用完就判「没种子」——哪怕背包里还躺着好几组，
     * 于是白白跑一趟补给点。现在先深搜一遍，找得到就把它换到手上继续种。
     *
     * @return 整个背包里都没有时返回 -1
     */
    public static int findSeedSlotDeep(ClientPlayerEntity player, Block cropBlock) {
        if (player == null || cropBlock == null) {
            return -1;
        }
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < INVENTORY_SLOTS; slot++) {
            if (matchesSeed(inventory.getStack(slot), cropBlock)) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * 把背包第 {@code invIndex} 格的物品<b>换到</b>快捷栏第 {@code hotbarIndex} 格。
     *
     * <p>走的是原版那条路：{@code clickSlot} + {@code SlotActionType.SWAP}，
     * 也就是真人「鼠标悬停在某格、按一下数字键 1~9」做的那件事。服务端收到的
     * 是一个普通的换位包，没有任何非常规之处。
     *
     * <p>用的是<b>玩家自己的物品栏</b>（{@code playerScreenHandler}），
     * 所以不需要打开任何界面 —— 收割途中正好也没有界面开着。如果这时候你
     * 已经打开了别的界面（背包、箱子），就<b>不动手</b>，免得点和你想的不是同一个容器。
     *
     * <p>注意它会「交换」：目标快捷栏格里原本的东西会被挪到 {@code invIndex} 那一格，
     * 不会凭空消失。
     *
     * @return 有没有真的发出这次交换
     */
    public static boolean swapToHotbar(MinecraftClient client, ClientPlayerEntity player,
                                       int invIndex, int hotbarIndex) {
        if (client == null || client.interactionManager == null || player == null) {
            return false;
        }
        int hotbar = PlayerInventory.getHotbarSize();
        if (invIndex < 0 || invIndex >= INVENTORY_SLOTS
                || hotbarIndex < 0 || hotbarIndex >= hotbar) {
            return false;
        }
        if (invIndex == hotbarIndex) {
            return true;
        }
        // 只认「玩家自己的物品栏」：这时候 currentScreenHandler 必须是 PlayerScreenHandler
        ScreenHandler handler = player.currentScreenHandler;
        if (!(handler instanceof PlayerScreenHandler)) {
            return false;
        }
        int source = playerSlotId(handler, invIndex);
        if (source < 0) {
            return false;
        }
        client.interactionManager.clickSlot(handler.syncId, source, hotbarIndex,
                SlotActionType.SWAP, player);
        return true;
    }

    /**
     * 背包里一共多少个<b>骨粉</b>（快捷栏 + 主背包）。
     *
     * <p>「耗材自给」用它判断该不该去补给点补一趟（见
     * {@link AutoFarmConfig#boneMealThreshold}），「骨粉催熟」则用它决定这一趟值不值得跑。
     */
    public static int countBoneMeal(ClientPlayerEntity player) {
        if (player == null) {
            return 0;
        }
        PlayerInventory inventory = player.getInventory();
        int total = 0;
        for (int slot = 0; slot < INVENTORY_SLOTS; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isOf(Items.BONE_MEAL)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * 快捷栏里第一个有骨粉的格号；没有就返回 -1。
     *
     * <p>骨粉必须先在<b>快捷栏</b>里才能用（原版只用主手，而主手拿的是快捷栏
     * 选中的那一格）。返回 -1 就说明没法就地撒 —— 这时该走「耗材自给」去补给点取，
     * 而不是翻背包深处（挖背包同样要占着快捷栏一格，反而更绕）。
     */
    public static int findBoneMealSlot(ClientPlayerEntity player) {
        if (player == null) {
            return -1;
        }
        PlayerInventory inventory = player.getInventory();
        int hotbar = PlayerInventory.getHotbarSize();
        for (int slot = 0; slot < hotbar; slot++) {
            if (inventory.getStack(slot).isOf(Items.BONE_MEAL)) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * <b>深搜整个背包</b>（快捷栏 9 格 + 主背包 27 格）找骨粉。
     *
     * <p>骨粉要在快捷栏里才能用（原版只用主手），所以这是「快捷栏没有骨粉」之后的
     * 第二道防线：主背包深处还有骨粉时，把它找出来交给调用方换到快捷栏，
     * 而不是像以前那样直接判「没骨粉」。
     *
     * @return 整个背包里都没有时返回 -1
     */
    public static int findBoneMealSlotDeep(ClientPlayerEntity player) {
        if (player == null) {
            return -1;
        }
        PlayerInventory inventory = player.getInventory();
        for (int slot = 0; slot < INVENTORY_SLOTS; slot++) {
            if (inventory.getStack(slot).isOf(Items.BONE_MEAL)) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * 背包里还有几件「<b>还带一半以上余量</b>」的有耐久物品（工具、武器、鞘翅）。
     *
     * <p>给「备用工具自给」当判据用：只要还剩一把能用的，就不用去补给点跑这一趟。
     * 为什么按「一半」而不是「没断」：一把只剩 3 点耐久的镐子对收割来说和没有差不多 ——
     * 与其现在跑一趟，不如等它真断了再说，免得来回折腾。
     */
    public static int countUsableTools(ClientPlayerEntity player) {
        if (player == null) {
            return 0;
        }
        PlayerInventory inventory = player.getInventory();
        int total = 0;
        for (int slot = 0; slot < INVENTORY_SLOTS; slot++) {
            ItemStack stack = inventory.getStack(slot);
            int max = stack.isEmpty() ? 0 : stack.getMaxDamage();
            if (max <= 0) {
                continue; // 方块、种子这些没有耐久的
            }
            if (max - stack.getDamage() >= max / 2) {
                total++;
            }
        }
        return total;
    }

    /**
     * 把快捷栏选中格切到 {@code hotbarIndex}。
     *
     * <p>走的是原版「按数字键 1~9」那条路（{@code clickSlot} + {@code SlotActionType.SWAP}，
     * button 位传目标格号），也就是真人伸手去按数字键做的那件事。服务端收到的是一个
     * 普通的换位包 —— 直接改客户端的 {@code selectedSlot} 是不行的，那样本地显示换了、
     * 服务端还以为你拿着旧东西。
     *
     * <p>和 {@link #swapToHotbar} 的区别：那个是<b>交换两格物品</b>，
     * 这个只是<b>切换选中哪一格</b>，不搬动任何东西。
     *
     * @return 有没有真的发出了这次换位（本来就在这一格上也返回 true）
     */
    public static boolean selectHotbarSlot(MinecraftClient client, ClientPlayerEntity player,
                                           int hotbarIndex) {
        if (client == null || client.interactionManager == null || player == null) {
            return false;
        }
        int hotbar = PlayerInventory.getHotbarSize();
        if (hotbarIndex < 0 || hotbarIndex >= hotbar) {
            return false;
        }
        if (player.getInventory().selectedSlot == hotbarIndex) {
            return true; // 本来就在这一格
        }
        ScreenHandler handler = player.currentScreenHandler;
        if (!(handler instanceof PlayerScreenHandler)) {
            return false;
        }
        int source = playerSlotId(handler, hotbarIndex);
        if (source < 0) {
            return false;
        }
        client.interactionManager.clickSlot(handler.syncId, source, hotbarIndex,
                SlotActionType.SWAP, player);
        return true;
    }

    /** 物品栏里第 {@code invIndex} 格，在某个界面处理器里对应的槽位号；找不到返回 -1 */
    public static int playerSlotId(ScreenHandler handler, int invIndex) {
        if (handler == null) {
            return -1;
        }
        for (Slot slot : handler.slots) {
            if (slot.inventory instanceof PlayerInventory && slot.getIndex() == invIndex) {
                return slot.id;
            }
        }
        return -1;
    }

    /** 这一栈是不是「放下去能长出 {@code cropBlock}」的东西 */
    private static boolean matchesSeed(ItemStack stack, Block cropBlock) {
        return stack != null && !stack.isEmpty()
                && stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() == cropBlock;
    }

    /**
     * 「手边最值得拿去补种空耕地」的那一种作物。
     *
     * <p>和 {@link #findSeedSlot} 是配套的两个方向：那个是「已知要种小麦 → 找小麦种子」，
     * 这个是「不知道要种什么 → 挑一种出来」。用在空地补种上：空着的耕地没告诉你
     * 该长什么，只能从手边有的种子里选。
     *
     * <p>挑选顺序：
     * <ol>
     *   <li><b>玩家指定的作物</b>（面板上的「空地补种作物」）—— 手边正好有就用它；</li>
     *   <li>否则挑<b>数量最多</b>的那种 —— 最不容易种两下就断货。</li>
     * </ol>
     * 统计用的是<b>快捷栏 + 主背包</b>的总数（反正后面 {@code findSeed} 会负责把它
     * 换到快捷栏），但并列时按扫描顺序取先遇到的那个（走 {@link java.util.LinkedHashMap}），
     * 所以结果稳定，不会每 tick 换一种作物种。
     *
     * @param preferred 玩家指定的作物方块；没指定传 null
     * @return 该种的作物方块；手边一个能种的都没有时返回 null
     */
    public static Block choosePlantCrop(ClientPlayerEntity player, Block preferred) {
        if (player == null) {
            return null;
        }
        PlayerInventory inventory = player.getInventory();
        // LinkedHashMap：保留「背包里先遇到」的顺序，并列时结果才是确定的
        Map<Block, Integer> counts = new LinkedHashMap<>();
        for (int slot = 0; slot < INVENTORY_SLOTS; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!isPlantable(stack)) {
                continue;
            }
            Block crop = ((BlockItem) stack.getItem()).getBlock();
            counts.merge(crop, stack.getCount(), Integer::sum);
        }
        if (counts.isEmpty()) {
            return null;
        }
        if (preferred != null && counts.containsKey(preferred)) {
            return preferred;
        }
        Block best = null;
        int bestCount = 0;
        for (Map.Entry<Block, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    /**
     * 这个物品放下去会不会长成作物 —— 也就是<b>它算不算补种用的东西</b>。
     *
     * <p>两种作物都算：
     * <ul>
     *   <li><b>真正的种子</b>（小麦种子、甜菜种子……）—— 只能种，不能吃；</li>
     *   <li><b>「非产生种子类农作物」</b>（胡萝卜、土豆）—— 收获物<b>本身就是</b>补种材料，
     *       既能吃又能种，没有单独的种子物品。</li>
     * </ul>
     * 这个区分在储存时很关键：交给 {@link ItemCategory} 去分档。
     */
    public static boolean isPlantable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.getItem() instanceof BlockItem blockItem
                && CropRegistry.isCropBlock(blockItem.getBlock());
    }

    /**
     * 往箱子里存东西时，这种物品<b>最多留几栈</b>。
     *
     * <p>返回值分三种：
     * <ul>
     *   <li><b>-1</b> —— 一栈都不存，全留身上；</li>
     *   <li><b>0</b> —— 全存；</li>
     *   <li><b>n</b> —— 留 n 栈，多的存。</li>
     * </ul>
     *
     * <p>具体哪一类是哪一种，由玩家在「存放策略」子面板里逐类设置
     * （{@link com.autofarm.client.config.AutoFarmConfig#keepFor}）；
     * 归类本身交给 {@link ItemCategory#classify}，判的是「能不能种 / 能不能吃 /
     * 是不是方块 / 有没有耐久」这些原版机制，所以模组作物也照样能落进对应的档。
     *
     * @param config 当前配置，里面存着七类各自的策略
     */
    public static int depositKeepBudget(ItemStack stack, AutoFarmConfig config) {
        ItemCategory category = ItemCategory.classify(stack);
        if (category == null || config == null) {
            return ItemCategory.KEEP_ALL;
        }
        return config.keepFor(category);
    }

    /**
     * 背包里还有没有「能存进箱子的东西」——一个都没有就别白跑一趟补给点了。
     *
     * <p>判定和真正的搬运用的是同一套（{@link #depositKeepBudget}）：
     * 胡萝卜、土豆这种要在「超过要留的组数」之后才算能存，所以只带着一组胡萝卜时
     * 这里会老老实实回答「没东西可存」，不会让它空跑。
     */
    public static boolean hasDepositableItems(ClientPlayerEntity player, AutoFarmConfig config) {
        if (player == null) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        Map<Item, Integer> seen = new HashMap<>();
        // 先快捷栏后主背包：和实际搬运时的顺序一致，「留下的那几栈」才会留在手边
        for (int slot = 0; slot < INVENTORY_SLOTS; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            int budget = depositKeepBudget(stack, config);
            if (budget < 0) {
                continue;
            }
            int already = seen.getOrDefault(stack.getItem(), 0);
            if (already >= budget) {
                return true;
            }
            seen.merge(stack.getItem(), 1, Integer::sum);
        }
        return false;
    }

    /**
     * 背包里（快捷栏 + 主背包）一共有多少「能补种的东西」。
     *
     * <p>两个用法：
     * <ul>
     *   <li>指定 {@code cropBlock} 时只数这一种作物的种子 —— 也就是「这种菜还够种几次」，
     *       HUD 上显示的就是它，快见底时你会先看出来；</li>
     *   <li>{@code cropBlock} 为 null 时数所有能种的东西（手上没有具体作物目标时用）。</li>
     * </ul>
     *
     * <p>数的是<b>物品个数</b>而不是栈数：还够种 12 次比「还剩 1 栈」直观得多。
     */
    public static int countSeeds(ClientPlayerEntity player, Block cropBlock) {
        if (player == null) {
            return 0;
        }
        PlayerInventory inventory = player.getInventory();
        int total = 0;
        for (int slot = 0; slot < INVENTORY_SLOTS; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (cropBlock != null ? matchesSeed(stack, cropBlock) : isPlantable(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** 背包里还剩几个空格 */
    public static int countFreeSlots(ClientPlayerEntity player) {        if (player == null) {
            return 0;
        }
        PlayerInventory inventory = player.getInventory();
        int free = 0;
        for (int slot = 0; slot < INVENTORY_SLOTS; slot++) {
            if (inventory.getStack(slot).isEmpty()) {
                free++;
            }
        }
        return free;
    }

    /** 背包快满了（空格 ≤ {@code reserve}）——该去补给点存东西了 */
    public static boolean isNearlyFull(ClientPlayerEntity player, int reserve) {
        return countFreeSlots(player) <= reserve;
    }

    /**
     * 单个候选的评分：正确工具除数 30，否则 100
     */
    private static float score(float speed, boolean suitable) {
        return speed / (suitable ? 30.0f : 100.0f);
    }

    /**
     * 快捷栏里一共有多少个「能拿来垫脚的方块」。
     *
     * <p>寻路时用它当作「一路上最多能垫几块」的上限。
     */
    public static int countPlaceableInHotbar(ClientPlayerEntity player) {
        if (player == null) {
            return 0;
        }
        PlayerInventory inventory = player.getInventory();
        int hotbar = PlayerInventory.getHotbarSize();
        int total = 0;
        for (int slot = 0; slot < hotbar; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (isUsableAsFloor(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /**
     * 找快捷栏里第一个能用的方块槽位。
     *
     * <p>优先「手上正拿着的那一格」，这样如果玩家已经拿着石头，
     * 就不会莫名其妙把手里的东西换掉。
     *
     * @return 没有可用方块时返回 -1
     */
    public static int findPlaceableHotbarSlot(ClientPlayerEntity player) {
        if (player == null) {
            return -1;
        }
        PlayerInventory inventory = player.getInventory();
        int hotbar = PlayerInventory.getHotbarSize();

        int selected = inventory.selectedSlot;
        if (selected >= 0 && selected < hotbar && isUsableAsFloor(inventory.getStack(selected))) {
            return selected;
        }
        for (int slot = 0; slot < hotbar; slot++) {
            if (isUsableAsFloor(inventory.getStack(slot))) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * 这个物品能不能拿来垫脚。
     *
     * <p>两个条件：得是个「放下去会变成方块」的物品，且那个方块要能踩得住
     * （有碰撞箱、不是水或岩浆）。用默认状态查碰撞箱，不维护白名单，
     * 模组方块也能自动适配。
     */
    private static boolean isUsableAsFloor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        BlockState state = blockItem.getBlock().getDefaultState();
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        Block block = state.getBlock();
        if (block == net.minecraft.block.Blocks.AIR) {
            return false;
        }
        // 空世界上下文足够判断「这个方块本身是不是实心的」，和放在哪里无关
        return state.isSolidBlock(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
    }
}
