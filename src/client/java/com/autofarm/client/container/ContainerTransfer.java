package com.autofarm.client.container;

import com.autofarm.client.config.AutoFarmConfig;
import com.autofarm.client.config.ConfigManager;
import com.autofarm.client.interact.BlockInteractor;
import com.autofarm.client.inventory.InventoryScanner;
import com.autofarm.client.inventory.ItemCategory;
import com.autofarm.client.scan.CropRegistry;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 在补给点的箱子里「存东西 / 取种子」。
 *
 * <h2>它管的是一小段流程，不是一次点击</h2>
 * <ol>
 *   <li><b>APPROACH</b>：把准星压到箱子上，右键把它打开；</li>
 *   <li><b>TRANSFERRING</b>：每几 tick 发一次「Shift + 左键」；
 *       存东西是点自己背包里那些不值钱的格子，取种子是点箱子里那些格；</li>
 *   <li><b>CLOSING</b>：把界面关掉，把控制权还回去。</li>
 * </ol>
 *
 * <h2>为什么是「Shift + 左键」</h2>
 * 点格子走的是 {@code ClientPlayerInteractionManager.clickSlot}，
 * 动作类型 {@code QUICK_MOVE} —— 这就是真人按住 Shift 点格子的那件事，
 * 服务端收到的是一个普通的「快速移动」槽位包，没有任何非常规的地方。
 *
 * <p>一次点一整栈（而不是一个个搬），所以一个箱子十几格也就十几下。
 *
 * <h2>取哪些、存哪些</h2>
 * <ul>
 *   <li><b>存</b>：判定见 {@link InventoryScanner#depositKeepBudget}。
 *       先把物品归到 {@link com.autofarm.client.inventory.ItemCategory} 的七类之一
 *       （补种种子 / 收获物即种子 / 食物 / 方块 / 工具 / 纯收获物），
 *       再看玩家在那张「存放策略」表里给这一类设了
 *       「不存储 / 完全存储 / 保留 n 组」中的哪一种。默认是：
 *       <ul>
 *         <li>补种种子、食物、方块、工具 —— 一栈都不存；</li>
 *         <li>胡萝卜、土豆、甘蔗这类「收获物本身就是补种材料」的 —— 留 1 组，多的存掉
 *             （以前它们被当成「方块」一栈不存，结果背包迟早被塞满；全存又会把
 *             补种材料也存走。留几组正好两头都不误）；</li>
 *         <li>小麦这类纯收获物 —— 全存。</li>
 *       </ul>
 *       顺序上先扫快捷栏再扫主背包，所以「留下的那几栈」会尽量留在手边。</li>
 *   <li><b>取</b>：箱子里那些「放下去会变成作物」的物品，也就是种子。
 *       如果这次是「因为缺某一种种子才来的」，只要那一种；
 *       否则箱子里的作物种子都拿。</li>
 * </ul>
 * 存排在前、取排在后 —— 先把背包腾出空间，再把种子装进来。
 *
 * <h2>背包一个空格都没有的时候</h2>
 * 取种子本来走的是「Shift + 左键一键挪」，它会自动找空格。可要是<b>一个空格都没有</b>，
 * 这一下就会石沉大海。所以这里准备了一条后路：直接把那一组种子<b>点起来、换到
 * 「默认种子槽位」</b>（面板上可设），原本占着那一格的东西再塞回箱子。
 * 三步都走原版的点击动作，和真人「拿起来 → 放到第 9 格 → 把换下来的东西放回箱子」
 * 一模一样。
 *
 * <h2>几个自我保护</h2>
 * <ul>
 *   <li>点几下没生效（同一格连续点还是原样）就收手，不会无脑循环；</li>
 *   <li>总点击数和总时长都有上限；</li>
 *   <li>每 tick 重新看一遍界面里的实际内容，而不是照着开箱那一刻的快照做
 *       —— 免得服务端和客户端一不同步就越做越错。</li>
 * </ul>
 */
public final class ContainerTransfer {

    /** 走到结果了没有 */
    public enum Result {
        /** 还在做 */
        RUNNING,
        /** 做完了，界面已关 */
        DONE,
        /** 出问题了（打不开、箱子满了……），界面已关，原因见 {@link #getFailure()} */
        FAILED
    }

    private enum Phase {
        APPROACH, TRANSFERRING, CLOSING
    }

    /** 右键箱子的重试间隔 */
    private static final int OPEN_RETRY_INTERVAL = 5;
    /** 多久还打不开就放弃 */
    private static final int OPEN_TIMEOUT_TICKS = 80;

    /** 两次点击之间隔几 tick，让服务端有时间处理 */
    private static final int CLICK_INTERVAL = 3;
    /** 单次拜访最多点多少下 */
    private static final int MAX_CLICKS = 64;
    /** 单次拜访最长持续多少 tick */
    private static final int TRANSFER_TIMEOUT_TICKS = 500;
    /** 同一格连着白点几次就收手 */
    private static final int STALL_LIMIT = 3;

    /** 一次最多取几栈种子，免得反过来把背包塞满 */
    private static final int REFILL_MAX_STACKS = 8;

    private static BlockPos target;
    private static Phase phase = Phase.CLOSING;
    private static boolean active;

    private static boolean wantDeposit;
    private static boolean wantRefill;
    /**
     * 这一趟「存」的是<b>杂物</b>（非农作物物品）还是<b>作物产物</b>。
     *
     * <p>{@code true} = 杂物临时存储（石头、泥土、骨头这类非作物物品单独存一个箱子，
     * 腾背包空间，和「存放策略」的 keepFor 无关、一律全存）；
     * {@code false} = 常规作物产物存储（按 keepFor 的额度存）。</p>
     */
    private static boolean depositJunk;
    /**
     * 这一趟要不要顺带取<b>耗材</b>（目前就是骨粉）。
     *
     * <p>它是「耗材自给」那一项的需求：骨粉催熟开着、而手边的骨粉快见底时，
     * 去补给点顺手补一栈，免得催熟开着却因为没骨粉而干等着。
     */
    private static boolean wantSupplies;
    /**
     * 这一趟要不要顺带取一件<b>有耐久的工具</b>。
     *
     * <p>对应「备用工具自给」：背包里一把还带余量的工具都没有时（挖路挖断了、镐子丢了……），
     * 去补给点补一把，否则只能站着等玩家回来。
     */
    private static boolean wantTool;
    private static final Set<Block> wantedSeeds = new HashSet<>();

    private static int ticks;
    private static int clickCooldown;
    private static int clicks;
    private static int refillStacks;

    private static int lastSlot = -1;
    private static int lastSignature;
    private static int stalls;

    private static int deposited;
    private static int takenStacks;
    private static String failure;

    /**
     * 「把种子强行换到默认种子槽位」这件事走到第几步。
     *
     * <p>0 = 没在做；1 = 已经把种子从箱子里点起来了（在光标上）；2 = 已经换到默认槽位了。
     * 一步一步来、每步之间隔 {@link #CLICK_INTERVAL} tick，是因为原版一次点击就是一个包，
     * 一口气连发三个包容易被服务端判成异常操作。
     */
    private static int forceStage;

    private ContainerTransfer() {
    }

    // ================= 对外接口 =================

    /**
     * 开始一轮存取。
     *
     * @param pos      补给点容器所在的坐标
     * @param deposit  这一趟要不要存东西
     * @param refill   这一趟要不要取种子
     * @param seeds    优先要取的种子（空集合表示「箱子里的作物种子都拿」）
     * @param supplies 这一趟要不要顺带取耗材（骨粉）
     * @param tool     这一趟要不要顺带取一件有耐久的工具
     * @param junk     这一趟存的是不是「杂物」（非农作物物品）——见 {@link #depositJunk}
     */
    public static void begin(BlockPos pos, boolean deposit, boolean refill, Collection<Block> seeds,
                             boolean supplies, boolean tool, boolean junk) {
        target = pos == null ? null : pos.toImmutable();
        wantDeposit = deposit;
        wantRefill = refill;
        wantSupplies = supplies;
        wantTool = tool;
        depositJunk = junk;
        wantedSeeds.clear();
        if (seeds != null) {
            wantedSeeds.addAll(seeds);
        }

        phase = Phase.APPROACH;
        active = target != null;
        ticks = 0;
        clickCooldown = 0;
        clicks = 0;
        refillStacks = 0;
        lastSlot = -1;
        lastSignature = 0;
        stalls = 0;
        deposited = 0;
        takenStacks = 0;
        failure = null;
        forceStage = 0;
    }

    /** 停手（退出世界、关掉开关时用） */
    public static void reset() {
        active = false;
        phase = Phase.CLOSING;
        target = null;
        wantedSeeds.clear();
        wantSupplies = false;
        wantTool = false;
        failure = null;
        forceStage = 0;
    }

    /** 这一次实际存进去几栈 */
    public static int getDepositedStacks() {
        return deposited;
    }

    /** 这一次实际取回来几栈 */
    public static int getTakenStacks() {
        return takenStacks;
    }

    /** 失败原因（翻译键），正常结束是 null */
    public static String getFailure() {
        return failure;
    }

    /**
     * 推进一步。
     *
     * @return {@link Result#RUNNING} 表示下一 tick 还要继续
     */
    public static Result tick(MinecraftClient client) {
        ClientPlayerEntity player = client == null ? null : client.player;
        if (!active || player == null || client.world == null || target == null) {
            active = false;
            return Result.FAILED;
        }
        ticks++;

        switch (phase) {
            case APPROACH -> {
                return approach(client, player);
            }
            case TRANSFERRING -> {
                return transfer(client, player);
            }
            case CLOSING -> {
                return closing(client);
            }
            default -> {
                return Result.DONE;
            }
        }
    }

    // ================= 三个阶段 =================

    private static Result approach(MinecraftClient client, ClientPlayerEntity player) {
        // 界面已经开了（可能上一 tick 发的包刚生效）→ 直接进存取
        if (client.currentScreen instanceof HandledScreen<?>) {
            phase = Phase.TRANSFERRING;
            ticks = 0;
            clickCooldown = CLICK_INTERVAL;
            return Result.RUNNING;
        }
        // 你自己开着别的界面（物品栏、聊天）→ 等着，别乱点
        if (client.currentScreen != null) {
            return Result.RUNNING;
        }

        // 够不着说明导航没把人送到位，交给上层去处理
        if (!BlockInteractor.isWithinReach(player, target, 4.0)) {
            return Result.RUNNING;
        }

        // 准星对准了、间隔到了，就右键一次
        if (ticks % OPEN_RETRY_INTERVAL == 1) {
            BlockInteractor.useBlock(client, target);
        } else {
            BlockInteractor.aimAt(player, target);
        }

        if (ticks > OPEN_TIMEOUT_TICKS) {
            failure = "autofarm.notice.supply_open_failed";
            phase = Phase.CLOSING;
        }
        return Result.RUNNING;
    }

    private static Result transfer(MinecraftClient client, ClientPlayerEntity player) {
        if (client.interactionManager == null) {
            finish();
            return Result.FAILED;
        }
        if (!(client.currentScreen instanceof HandledScreen<?> screen)) {
            // 界面没了（被关掉、被服务端踢掉）→ 当成结束，别在这儿干等
            failure = "autofarm.notice.supply_closed";
            finish();
            return Result.FAILED;
        }

        if (--clickCooldown > 0) {
            return Result.RUNNING;
        }
        clickCooldown = CLICK_INTERVAL;

        ScreenHandler handler = screen.getScreenHandler();

        // ---- 半路中的「换到默认种子槽位」动作：一次 tick 只推进一步 ----
        if (forceStage > 0) {
            forceStep(client, player, handler);
            return Result.RUNNING;
        }

        // ---- 先存后取 ----
        int slot = nextDepositSlot(handler);
        boolean depositing = slot >= 0;
        if (!depositing) {
            slot = nextTakeSlot(handler);
        }

        if (slot < 0) {
            // 没有要搬的了：这一趟结束。原因要说清楚是哪一样没拿到
            if (wantRefill && refillStacks == 0) {
                failure = "autofarm.notice.supply_no_seed";
            } else if ((wantSupplies || wantTool) && takenStacks == 0) {
                failure = "autofarm.notice.supply_no_supply";
            }
            phase = Phase.CLOSING;
            return Result.RUNNING;
        }

        // 同一格连着点还是原样 → 这一下没生效，别死循环
        ItemStack preview = handler.slots.get(slot).getStack();
        int signature = System.identityHashCode(preview.getItem()) * 31 + preview.getCount();
        if (slot == lastSlot && signature == lastSignature) {
            if (++stalls > STALL_LIMIT) {
                failure = "autofarm.notice.supply_stuck";
                phase = Phase.CLOSING;
                return Result.RUNNING;
            }
        } else {
            stalls = 0;
        }
        lastSlot = slot;
        lastSignature = signature;

        // ---- 取种子、但背包一个空格都没有：改走「强行换到默认种子槽位」 ----
        if (!depositing && InventoryScanner.countFreeSlots(player) == 0
                && beginForceIntoDefaultSlot(client, player, handler, slot)) {
            return Result.RUNNING;
        }

        clickMove(client, player, handler, slot);
        clicks++;
        if (depositing) {
            deposited++;
        } else {
            takenStacks++;
            refillStacks++;
        }

        if (clicks >= MAX_CLICKS || ticks > TRANSFER_TIMEOUT_TICKS) {
            phase = Phase.CLOSING;
        }
        return Result.RUNNING;
    }

    /** 一键搬运：Shift + 左键，让原版自己去目标容器里找位置 */
    private static void clickMove(MinecraftClient client, ClientPlayerEntity player,
                                  ScreenHandler handler, int slot) {
        client.interactionManager.clickSlot(handler.syncId, slot, 0, SlotActionType.QUICK_MOVE, player);
        player.swingHand(Hand.MAIN_HAND);
    }

    // ================= 「强行换到默认种子槽位」 =================

    /** 默认种子槽位对应的快捷栏下标（0~8） */
    private static int defaultSeedHotbar() {
        int slot = ConfigManager.get().defaultSeedSlot - 1; // 面板上是 1~9
        return Math.max(0, Math.min(PlayerInventory.getHotbarSize() - 1, slot));
    }

    /**
     * 第一步：把箱子里的那一栈种子点到光标上。
     *
     * @return 有没有真的开始（默认槽位在界面里找不到、或者不在容器界面时就放弃）
     */
    private static boolean beginForceIntoDefaultSlot(MinecraftClient client, ClientPlayerEntity player,
                                                     ScreenHandler handler, int chestSlotId) {
        if (client.interactionManager == null) {
            return false;
        }
        int dest = InventoryScanner.playerSlotId(handler, defaultSeedHotbar());
        if (dest < 0) {
            return false; // 这个界面里没有玩家的快捷栏（比如某些机器的自定义界面）
        }
        client.interactionManager.clickSlot(handler.syncId, chestSlotId, 0, SlotActionType.PICKUP, player);
        player.swingHand(Hand.MAIN_HAND);
        forceStage = 1;
        return true;
    }

    /**
     * 后面两步：换到默认槽位，再把换下来的东西塞回箱子。
     *
     * <p>每 tick 只做一步（调用方有 {@link #CLICK_INTERVAL} 的节流）。
     */
    private static void forceStep(MinecraftClient client, ClientPlayerEntity player, ScreenHandler handler) {
        if (client.interactionManager == null) {
            forceStage = 0;
            return;
        }
        int dest = InventoryScanner.playerSlotId(handler, defaultSeedHotbar());
        if (dest < 0) {
            forceStage = 0;
            return;
        }

        if (forceStage == 1) {
            // 把光标上的种子放进默认槽位；原本在那一格的东西会回到光标上
            client.interactionManager.clickSlot(handler.syncId, dest, 0, SlotActionType.PICKUP, player);
            player.swingHand(Hand.MAIN_HAND);
            forceStage = 2;
            return;
        }

        // forceStage == 2：光标上还捏着换下来的东西，找个箱子空位放回去
        if (!handler.getCursorStack().isEmpty()) {
            int empty = nextEmptyContainerSlot(handler);
            if (empty < 0) {
                // 箱子里也满了：换回去，宁可这次没取成，也不把东西丢在地上
                client.interactionManager.clickSlot(handler.syncId, dest, 0, SlotActionType.PICKUP, player);
                player.swingHand(Hand.MAIN_HAND);
                forceStage = 0;
                failure = "autofarm.notice.supply_stuck";
                phase = Phase.CLOSING;
                return;
            }
            client.interactionManager.clickSlot(handler.syncId, empty, 0, SlotActionType.PICKUP, player);
            player.swingHand(Hand.MAIN_HAND);
        }

        forceStage = 0;
        clicks++;
        takenStacks++;
        refillStacks++;
    }

    /** 箱子里第一个空格（只找非玩家背包的格） */
    private static int nextEmptyContainerSlot(ScreenHandler handler) {
        for (Slot slot : handler.slots) {
            if (!(slot.inventory instanceof PlayerInventory) && slot.getStack().isEmpty()) {
                return slot.id;
            }
        }
        return -1;
    }

    // ================= 找要搬的格子 =================

    /**
     * 下一个该存进箱子的格子。
     *
     * <p>判定交给 {@link InventoryScanner#depositKeepBudget}，这里只负责「数」：
     * 同一种物品按扫描顺序累计，<b>超过「留几栈」额度的那些</b>才是要搬走的。
     *
     * <p>扫两轮是刻意的 —— <b>先快捷栏、再主背包</b>。这样额度用掉的是主背包里那几栈，
     * 留在手边的恰好是快捷栏里的，取用最方便。
     *
     * @return -1 表示没有要存的
     */
    private static int nextDepositSlot(ScreenHandler handler) {
        if (!wantDeposit) {
            return -1;
        }
        AutoFarmConfig config = ConfigManager.get();
        Map<Item, Integer> kept = new HashMap<>();
        int hotbarSize = PlayerInventory.getHotbarSize();

        // 扫两轮是刻意的 —— 先快捷栏、再主背包
        for (int pass = 0; pass < 2; pass++) {
            for (Slot slot : handler.slots) {
                if (!(slot.inventory instanceof PlayerInventory)) {
                    continue;
                }
                boolean hotbar = slot.getIndex() < hotbarSize;
                if ((pass == 0) != hotbar) {
                    continue;
                }
                // 存杂物时<b>不碰快捷栏</b>：那里面是自动进食的食物、垫路的方块、
                // 手上的工具 —— 搬走它们会让这几个功能当场失效。作物产物不在此限
                // （收下来的庄稼就该腾出去）。
                if (depositJunk && hotbar) {
                    continue;
                }
                ItemStack stack = slot.getStack();
                if (stack.isEmpty()) {
                    continue;
                }
                // 作物 / 杂物分流：一趟只搬一类，另一类跳过
                boolean cropRelated = isCropRelated(ItemCategory.classify(stack));
                if (cropRelated == depositJunk) {
                    continue; // 存杂物时跳过作物，存作物时跳过杂物
                }
                // 两趟都按「存放策略」的额度存 —— 杂物也不再无脑全存：
                // 否则食物（默认不存）、垫脚方块（默认不存）会被一起搬走，
                // 自动进食断粮、跨沟垫路失效。想清空某类杂物，去「存放策略」把它设成「全存」。
                int budget = InventoryScanner.depositKeepBudget(stack, config);
                if (budget < 0) {
                    continue; // 不该存的，连数都不用数
                }
                int already = kept.getOrDefault(stack.getItem(), 0);
                if (already >= budget) {
                    return slot.id;
                }
                kept.merge(stack.getItem(), 1, Integer::sum);
            }
        }
        return -1;
    }

    /** 这一类算不算「作物相关」（该存到农作物输出容器，而不是杂物容器） */
    private static boolean isCropRelated(ItemCategory category) {
        return category != null && category.isCropRelated();
    }

    /**
     * 下一个该从箱子里取走的格子。
     *
     * <p>三样东西共用这一条「取」的通道，优先级从高到低：
     * <ol>
     *   <li><b>种子</b> —— 不取到就没法补种，这是最要紧的；</li>
     *   <li><b>骨粉</b> —— 「耗材自给」要的，给「骨粉催熟」续航；</li>
     *   <li><b>有耐久的工具</b> —— 「备用工具自给」要的。</li>
     * </ol>
     * 一次只搬一栈（Shift + 左键原版会一整栈搬走），总栈数受
     * {@link #REFILL_MAX_STACKS} 限制 —— 免得反过来把背包塞满。
     *
     * @return -1 表示没有要取的了
     */
    private static int nextTakeSlot(ScreenHandler handler) {
        if (takenStacks >= REFILL_MAX_STACKS) {
            return -1;
        }
        if (wantRefill) {
            int seed = findContainerSlot(handler, ContainerTransfer::isWantedSeed);
            if (seed >= 0) {
                return seed;
            }
        }
        if (wantSupplies) {
            int meal = findContainerSlot(handler, stack -> stack.isOf(Items.BONE_MEAL));
            if (meal >= 0) {
                return meal;
            }
        }
        if (wantTool) {
            int tool = findContainerSlot(handler, ContainerTransfer::isTool);
            if (tool >= 0) {
                return tool;
            }
        }
        return -1;
    }

    /** 箱子里（不含玩家背包）第一个满足条件的格子；没有返回 -1 */
    private static int findContainerSlot(ScreenHandler handler, Predicate<ItemStack> test) {
        for (Slot slot : handler.slots) {
            if (!(slot.inventory instanceof PlayerInventory) && test.test(slot.getStack())) {
                return slot.id;
            }
        }
        return -1;
    }

    /**
     * 这个物品算不算「有耐久的工具」—— 能被 {@code BoneMealItem} 之外的那套耐久系统管的东西。
     *
     * <p>判据就是「有耐久条」：镐子、斧、剑、弓、盾、鞘翅都算。刻意<b>不维护工具名单</b>，
     * 模组工具一样能被认出来（它只要带 {@code maxDamage}）。
     */
    private static boolean isTool(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getMaxDamage() > 0;
    }

    private static Result closing(MinecraftClient client) {
        Screen screen = client.currentScreen;
        if (screen != null) {
            // HandledScreen.close() 会先给服务端发关闭包，再把界面撤掉
            screen.close();
        }
        active = false;
        return failure == null ? Result.DONE : Result.FAILED;
    }

    private static void finish() {
        active = false;
        phase = Phase.CLOSING;
    }

    /**
     * 这个物品是我们想从箱子里取走的种子吗。
     *
     * <p>判定和补种用的是同一套反查：物品放下去会变成「作物方块」，
     * 那它就是种子。这样模组的种子也能自动认出来。
     */
    private static boolean isWantedSeed(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        Block block = blockItem.getBlock();
        if (!CropRegistry.isCropBlock(block)) {
            return false;
        }
        return wantedSeeds.isEmpty() || wantedSeeds.contains(block);
    }
}
