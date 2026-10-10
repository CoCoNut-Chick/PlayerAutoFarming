package com.autofarm.client.harvest;

import com.autofarm.client.config.AutoFarmConfig;
import com.autofarm.client.human.Humanizer;
import com.autofarm.client.interact.BlockInteractor;
import com.autofarm.client.inventory.InventoryScanner;
import com.autofarm.client.scan.CropRegistry;
import com.autofarm.client.stats.StatsManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

/**
 * 站在一棵作物前面，把它挖掉、再补种回去。
 *
 * <h2>它只负责「到了之后干什么」</h2>
 * 走过去的活是 {@code PathWalker} 干的。路线走完、人停在作物前，就把目标交到这里，
 * 由它做完「挖掉 → 补种」，做完再由调用方放行去下一个目标。
 *
 * <h2>主路径的两步</h2>
 * <ol>
 *   <li><b>MINE</b>：每 tick 调一次原版破坏进度（顺便自动换到最合适的工具），
 *       直到这一格不再是成熟作物为止。作物硬度是 0，通常一下就碎；</li>
 *   <li><b>REPLANT</b>：在刚腾出来的耕地上把手里的种子种下去。种子是从快捷栏里
 *       <b>反查</b>出来的 —— 见 {@link InventoryScanner#findSeedSlot}，
 *       不需要维护「小麦配小麦种子」这种对照表，模组作物一样能自动适配。</li>
 * </ol>
 *
 * <h2>有些东西不是挖的，是摘的</h2>
 * 甜浆果丛、发光浆果这类「植株留着、只摘果子」的作物走的是第三条路
 * <b>PICK</b>：右键一次把果实摘走，植株原地留着，所以也不用补种。
 * 到底走哪条路由 {@link CropRegistry#harvestMode} 决定 ——
 * 挖错了会把整丛铲掉，这不是小问题，所以它必须逐格判断而不是猜。
 *
 * <h2>还有一种目标根本不是「作物」</h2>
 * 打开「补种空耕地」之后，<b>空着的耕地</b>也会被排进路线（见 {@code CropScanner}）。
 * 那种目标上没有任何作物，走的是 <b>PLANT</b> 阶段：不知道要种什么，
 * 所以从手边挑一种种子种下去 —— 优先玩家指定的作物，否则数量最多的那种。
 * 判定入口在 {@link #start}：它先问这一格是「成熟作物」还是「空耕地」，再决定去哪条路。
 *
 * <h2>几个刻意的取舍</h2>
 * <ul>
 *   <li>进来时如果那格已经不是成熟作物（被别人挖走了、或者本来就是误判），
 *       直接收工，绝不动别人的庄稼；</li>
 *   <li>挖超时就放弃这一棵，免得卡在挖不动的方块上把整条路线堵死；</li>
 *   <li>快捷栏里没种子时<b>会先翻一遍整个背包</b>：找到就把那一组换到
 *       「默认种子槽位」继续种。真的整个背包都没有，才判「缺种子」，
 *       并把这件事留给任务大脑去补给点取；</li>
 *   <li>失败原因（没种子、挖不动、种不下去）会留在 {@link #getNotice()} 里，
 *       面板上能看到，不用去猜它为什么跳过这一棵。</li>
 * </ul>
 */
public final class CropHarvester {

    /** 一个目标要走的几步 */
    private enum Stage {
        /** 正在挖掉成熟作物 */
        MINE,
        /** 正在右键摘（甜浆果、发光浆果这类） */
        PICK,
        /** 正在补种（刚收掉的那一格，种什么已经知道） */
        REPLANT,
        /** 正在往空着的耕地上种（种什么还不知道，得现场挑） */
        PLANT,
        /** 正在对还没熟的作物撒骨粉（附加功能「骨粉催熟」） */
        BONE_MEAL,
        /** 这一棵处理完了 */
        DONE
    }

    /** 挖一棵最多花多少 tick，超了就放弃（别堵在挖不动的方块上） */
    private static final int MINE_TIMEOUT_TICKS = 200;

    /** 摘一次最多花多少 tick */
    private static final int PICK_TIMEOUT_TICKS = 40;

    /** 右键每隔几 tick 试一次，失败也不至于每帧都发一次交互包 */
    private static final int PICK_RETRY_INTERVAL = 4;

    /** 补种每隔几 tick 试一次，失败也不至于每帧都发一次交互包 */
    private static final int REPLANT_RETRY_INTERVAL = 4;

    /** 补种最多试多少 tick */
    private static final int REPLANT_TIMEOUT_TICKS = 40;

    /**
     * 撒骨粉的两次间隔（tick）。
     *
     * <p>比「摘一次」的间隔大：骨粉推进一次之后要等一会儿才会结算生长，
     * 连着点既浪费骨粉（随机推进可能白推）也更像机器。
     */
    private static final int BONE_MEAL_RETRY_INTERVAL = 8;

    /** 撒骨粉最多花多少 tick，超了就放弃这一株 */
    private static final int BONE_MEAL_TIMEOUT_TICKS = 120;

    /**
     * 动手之前准星允许偏目标中心多少度。
     *
     * <p>取 8 度很保守：一格方块在 1 格外看过去的张角有 50 多度，
     * 所以只要过了这一关，准星一定落在方块上。它比
     * {@link BlockInteractor} 内部放置用的容差更严，这样「我们放行之后它一定肯发交互」，
     * 不会出现两边互相等的情况。
     */
    private static final float AIM_TOLERANCE = 8.0f;

    /** 交互距离上限。原版够得着是 4.5 格，取 4.0 留一点余量 */
    private static final double REACH = 4.0;

    private static BlockPos target;
    private static Stage stage = Stage.DONE;

    /** 被挖掉的是哪种作物（决定了补种要种什么）。空地补种时进来是 null，由 {@link #plant} 现挑 */
    private static Block cropBlock;

    /**
     * 这一格该怎么收：挖、摘、还是不碰。
     *
     * <p>进来的时候问一次 {@link CropRegistry#harvestMode}，之后整段动作都照它走 ——
     * 因为「摘」完（甜浆果丛）那格还是同一丛作物，「挖」完那格已经变成空气，
     * 中途再问一次得到的是不同的答案。
     */
    private static CropRegistry.HarvestMode mode = CropRegistry.HarvestMode.SKIP;

    /**
     * 这次目标是不是「额外挖掉」（瓜 / 自定义方块）—— 挖掉就完事，不补种。
     *
     * <p>瓜挖掉之后茎还在、会接着长瓜；自定义方块通常也不是「收完要种回去」的作物。
     * 所以这类目标走 {@link #startBreak}，只挖不种。普通作物仍是 {@code false}，
     * 挖完按开关决定要不要补种。
     */
    private static boolean extraBreak;

    /** 这一株已经撒了几次骨粉 —— 受配置的 {@code boneMealLimit} 约束 */
    private static int boneMealUsed;

    private static int ticks;

    /**
     * 下一次允许发交互的时刻（{@link #ticks} 口径）。
     *
     * <p>原来是「每 4 tick 试一次」这种等间距的重试；换成「上次动作 + 一个带抖动的间隔」
     * 之后，防踢模式就能把这份节拍感打散，而重试的密度基本不变。
     */
    private static int nextActionAt;

    /** 有没有真的把它收掉、有没有补上种，都只用于状态展示 */
    private static boolean harvested;
    private static boolean replanted;

    /** 这一次是不是「接上果实摘走的」（甜浆果、发光浆果），给状态栏用 */
    private static boolean picked;

    /** 空地补种到底种上了没有 */
    private static boolean planted;

    /** 这一次是不是「因为快捷栏里没种子才没补种」——任务大脑据此决定要不要去补给点 */
    private static boolean missingSeed;

    /** 上一次的异常说明（翻译键），null 表示一切正常 */
    private static String notice;

    private CropHarvester() {
    }

    // ================= 对外接口 =================

    /** 手上有没有还在处理的目标 */
    public static boolean isBusy() {
        return stage != Stage.DONE;
    }

    /** 现在是在补种，还是在挖 */
    public static boolean isReplanting() {
        return stage == Stage.REPLANT || stage == Stage.PLANT;
    }

    /** 上一次的异常说明（翻译键），null 表示一切正常 */
    public static String getNotice() {
        return notice;
    }

    /**
     * 「被挖掉的是哪种作物」。挖掉之后那一格就变空气了，所以这个信息只能趁热记下来，
     * 补给点取种子时也正是靠它知道「该从箱子里拿什么」。
     */
    public static Block getCropBlock() {
        return cropBlock;
    }

    /** 这一次是不是「没种子所以没补种」——任务大脑据此决定要不要跑一趟补给点 */
    public static boolean needsSeedRefill() {
        return missingSeed;
    }

    /** 这一次是不是「摘」下来的（甜浆果、发光浆果），而不是挖下来的 */
    public static boolean wasPicked() {
        return picked;
    }

    /** 空地补种有没有真的种上 */
    public static boolean wasPlanted() {
        return planted;
    }

    /**
     * 开始处理一个目标。
     *
     * <p>「一个目标」有两种来路，判定全在这里：
     * <ul>
     *   <li><b>成熟作物</b>（挖 / 摘）—— 走原来那两三条路；</li>
     *   <li><b>空着的耕地</b>（补种）—— 当 {@code config.fillEmptyFarmland} 打开、
     *       而且这一格确实是「上面空气、下面耕地」时才接手。种什么这里还不知道，
     *       留到 {@link #plant} 里现场挑。</li>
     * </ul>
     * 交给它一个别的坐标（早就被别人收走了、或者中途地被踩没了）就直接收工。
     */
    public static void start(ClientWorld world, BlockPos pos, AutoFarmConfig config) {
        begin(pos);
        if (world == null || target == null) {
            stage = Stage.DONE;
            return;
        }
        extraBreak = false;

        // 先问清楚「这一格该怎么收」，整段动作都照它走
        BlockState state = world.getBlockState(target);
        mode = CropRegistry.harvestMode(state);
        switch (mode) {
            case BREAK -> {
                // 把「要补种什么」趁热记下来：挖掉之后方块就变空气了，那时候再问就晚了
                cropBlock = state.getBlock();
                stage = Stage.MINE;
            }
            // 摘：植株留着，不补种
            case USE -> stage = Stage.PICK;
            case SKIP -> {
                // 不是成熟作物。开着「补种空耕地」、而且这一格确实是空着的耕地时，
                // 就当成补种目标；否则按老规矩跳过
                if (config != null && config.fillEmptyFarmland
                        && CropRegistry.isPlantingSpot(world, target)) {
                    stage = Stage.PLANT;
                } else {
                    stage = Stage.DONE;
                }
            }
        }
    }

    /**
     * 挖掉一个<b>非作物</b>方块（瓜 / 自定义方块），挖掉即收、不补种。
     *
     * <p>入口和 {@link #start} 不同：那边要先问「这是不是成熟作物」，这边不需要 ——
     * 瓜和自定义方块不是作物（没有 age、判不出成熟），直接按「挖掉」处理。
     * 走同一个 {@link Stage#MINE} 阶段，但 {@code extraBreak} 为真，
     * 挖到空气就算成功，且绝不再补种。
     */
    public static void startBreak(ClientWorld world, BlockPos pos) {
        begin(pos);
        if (world == null || target == null) {
            stage = Stage.DONE;
            return;
        }
        extraBreak = true;
        // 已经被挖走了（别人挖的、或者本来就认错了）→ 直接算完成
        if (world.getBlockState(target).isAir()) {
            stage = Stage.DONE;
            return;
        }
        mode = CropRegistry.HarvestMode.BREAK;
        stage = Stage.MINE;
    }

    /** 两个入口（普通收割 / 额外挖掉）共用的字段初始化 */
    private static void begin(BlockPos pos) {
        target = pos == null ? null : pos.toImmutable();
        cropBlock = null;
        mode = CropRegistry.HarvestMode.SKIP;
        extraBreak = false;
        boneMealUsed = 0;
        ticks = 0;
        nextActionAt = 0;
        harvested = false;
        replanted = false;
        picked = false;
        planted = false;
        missingSeed = false;
        notice = null;
    }

    /**
     * 对一株<b>还没熟</b>的作物撒骨粉（附加功能「骨粉催熟」，默认关闭）。
     *
     * <p>和另外两个入口的区别：这一条<b>不挖、不摘、不种</b> —— 它只是站在那儿
     * 把骨粉丢在作物上。走完之后那一格仍然是一株作物（可能熟了、可能还要接着催），
     * 所以绝不能顺手把它当成「收完了」给补种。
     *
     * <p>「熟没熟」每 tick 都重新问一次 {@link CropRegistry#isGrowing}：
     * 熟了、或者被人挖了、或者压根不是作物了，立刻收工 —— 这样就算中途出了意外，
     * 也不会在原地反复对着一格不该动的东西撒骨粉。
     */
    public static void startBoneMeal(ClientWorld world, BlockPos pos, AutoFarmConfig config) {
        begin(pos);
        if (world == null || target == null) {
            stage = Stage.DONE;
            return;
        }
        boneMealUsed = 0;
        stage = CropRegistry.isGrowing(world.getBlockState(target))
                ? Stage.BONE_MEAL
                : Stage.DONE;
    }

    /** 停手（退出世界、关掉开关时用） */
    public static void reset() {
        target = null;
        stage = Stage.DONE;
        cropBlock = null;
        mode = CropRegistry.HarvestMode.SKIP;
        extraBreak = false;
        boneMealUsed = 0;
        ticks = 0;
        nextActionAt = 0;
        harvested = false;
        replanted = false;
        picked = false;
        planted = false;
        missingSeed = false;
        notice = null;
    }

    /**
     * 推进一步。
     *
     * @return true 表示这一棵处理完了（挖掉了、或者判定不处理了），可以去下一个目标
     */
    public static boolean tick(MinecraftClient client, AutoFarmConfig config) {
        ClientPlayerEntity player = client == null ? null : client.player;
        if (player == null || client.world == null || target == null || stage == Stage.DONE) {
            stage = Stage.DONE;
            return true;
        }
        // 旁观模式没法挖也没法种，直接交差
        if (player.isSpectator()) {
            stage = Stage.DONE;
            return true;
        }

        Stage next = switch (stage) {
            case MINE -> mine(client, player, config);
            case PICK -> pick(client, player);
            case REPLANT -> replant(client, player, config);
            case PLANT -> plant(client, player, config);
            case BONE_MEAL -> boneMealTick(client, player, config);
            case DONE -> Stage.DONE;
        };
        stage = next;
        return stage == Stage.DONE;
    }

    /** 这一棵是不是真的收到了、并且补种上了（给状态栏用） */
    public static boolean wasHarvested() {
        return harvested;
    }

    public static boolean wasReplanted() {
        return replanted;
    }

    // ================= MINE / PICK / REPLANT =================

    /**
     * 右键把果实摘走（甜浆果丛、发光浆果这类）。
     *
     * <p>和挖的区别只有一个：<b>植株留着</b>。所以摘完既不用补种，也不用担心
     * 把别人的田毁了 —— 这正是必须区分收获方式的原因：照着挖下去，
     * 甜浆果丛会连根铲掉（掉落物还是浆果，但那一丛没了）。
     *
     * <p>判定「摘成功了没有」的办法很省事：<b>这一格不再是可收获状态了</b>
     * 就算成功。原版摘完会把 age 退一格（甜浆果），或者把浆果标志清掉（发光浆果），
     * 两种都会让它从 {@code USE} 变成 {@code SKIP}。
     */
    private static Stage pick(MinecraftClient client, ClientPlayerEntity player) {
        // 已经摘掉了、或者被别的玩家摘走了 → 完事
        if (CropRegistry.harvestMode(client.world.getBlockState(target))
                != CropRegistry.HarvestMode.USE) {
            harvested = true;
            return Stage.DONE;
        }

        BlockInteractor.aimAt(player, target);
        if (!BlockInteractor.isAimedAt(player, target, AIM_TOLERANCE)) {
            return Stage.PICK; // 转视角很快，不计入超时
        }

        // 够得着才发交互：隔着两格按右键，服务端会当成作弊
        // 重试的密度和以前一样，只是「隔多久试一次」带上了随机抖动 ——
        // 等间距的重试本身就是一个很明显的机器特征
        if (BlockInteractor.isWithinReach(player, target, REACH) && ticks >= nextActionAt) {
            BlockInteractor.useBlock(client, target);
            nextActionAt = ticks + Humanizer.gap(PICK_RETRY_INTERVAL);
        }
        ticks++;

        if (CropRegistry.harvestMode(client.world.getBlockState(target))
                != CropRegistry.HarvestMode.USE) {
            harvested = true;
            picked = true;
            return Stage.DONE;
        }
        if (ticks > PICK_TIMEOUT_TICKS) {
            notice = "autofarm.notice.pick_failed";
            return Stage.DONE;
        }
        return Stage.PICK;
    }

    private static Stage mine(MinecraftClient client, ClientPlayerEntity player, AutoFarmConfig config) {
        BlockState before = client.world.getBlockState(target);

        if (extraBreak) {
            // 额外目标（瓜 / 自定义方块）：挖到空气就算成功，中途被挖走也直接算完成。
            // 它没有「成熟」概念，所以判断标准就是「这一格还是不是那个方块」。
            if (before.isAir()) {
                harvested = true;
                return Stage.DONE;
            }
        } else {
            // 进来（或者上一 tick 之后）就已经不是成熟作物了：被别的玩家收走了，
            // 或者本来就认错了 —— 两种情况都不该动它
            if (CropRegistry.harvestMode(before) != CropRegistry.HarvestMode.BREAK) {
                return Stage.DONE;
            }
            if (cropBlock == null) {
                cropBlock = before.getBlock();
            }
        }

        // 一边挖一边把准星压到作物上。三档瞄准模式，从稳到快：
        // 精准（转正中心 8 度内）/ 效率（转到视线命中方块）/ 极效率（不转头直接挖）。
        switch (config.aimMode) {
            case AutoFarmConfig.AIM_EXTREME -> {
                // 极效率：完全不转头、不管准星，只要在挖掘范围内就直接挖。
                // 这是最容易被服务器判成作弊的一档，选它之前主面板已经弹过风险确认屏。
                if (!BlockInteractor.isWithinReach(player, target, REACH)) {
                    return Stage.MINE;
                }
            }
            case AutoFarmConfig.AIM_EFFICIENT -> {
                // 效率：走路时 PathWalker 已经把头转向目标（落脚点就选在能看见目标的那一格），
                // 所以到站后先<b>沿用这个朝向</b>，视线命中目标就直接挖，省掉 aimAt 那一小段微调；
                // 没命中 —— 多半是目标前面隔着一株未成熟作物（或别的方块）挡住了视线 ——
                // 才转正过去，绝不在视线被挡时盲挖。
                if (!BlockInteractor.isWithinReach(player, target, REACH)) {
                    return Stage.MINE;
                }
                if (!isCrosshairOn(player, target)) {
                    BlockInteractor.aimAt(player, target);
                    if (!isCrosshairOn(player, target)) {
                        return Stage.MINE; // 还在转，转的这几 tick 不计入超时
                    }
                }
            }
            default -> {
                // 精准：先把准星精确压到作物中心（8 度以内）才动手，
                // 否则会出现「头还没转过去，方块先碎了」。
                BlockInteractor.aimAt(player, target);
                if (!BlockInteractor.isAimedAt(player, target, AIM_TOLERANCE)) {
                    return Stage.MINE; // 转视角很快（几 tick），所以这段时间不计入超时
                }
            }
        }

        BlockInteractor.mineTick(client, target);

        if (++ticks > MINE_TIMEOUT_TICKS) {
            notice = "autofarm.notice.mine_timeout";
            return Stage.DONE;
        }

        if (extraBreak) {
            // 挖掉了没有：这一格变空气了就算成功（瓜 / 自定义方块挖掉后不补种）
            if (client.world.getBlockState(target).isAir()) {
                harvested = true;
                return Stage.DONE;
            }
            return Stage.MINE;
        }

        // 刚刚这一下把它挖掉了吗
        if (CropRegistry.harvestMode(client.world.getBlockState(target))
                != CropRegistry.HarvestMode.BREAK) {
            harvested = true;
            ticks = 0;
            nextActionAt = 0;
            return config.replant ? Stage.REPLANT : Stage.DONE;
        }
        return Stage.MINE;
    }

    /**
     * 准星（视线）现在是不是已经<b>落在这个方块上</b>。
     *
     * <p>效率模式用它代替「对准中心」：它只看视线有没有穿过这格方块，不要求准星
     * 压在方块几何中心上 —— 所以只要角色大致面朝这格，就能跳过转头直接动手。
     *
     * <p>这里<b>不读</b> {@code client.crosshairTarget}：那个值是渲染阶段算的，
     * 滞后一帧，而且 {@code aimAt} 刚转完视角它也不会立刻刷新，用它判断会让效率档
     * 「明明朝好了却一直不挖」。改走原版 {@code raycast}，每 tick 实时求一次视线命中。
     */
    private static boolean isCrosshairOn(ClientPlayerEntity player, BlockPos pos) {
        if (player == null || pos == null) {
            return false;
        }
        HitResult hit = player.raycast(REACH, 0.0f, false);
        return hit != null && hit.getType() == HitResult.Type.BLOCK
                && ((BlockHitResult) hit).getBlockPos().equals(pos);
    }

    private static Stage replant(MinecraftClient client, ClientPlayerEntity player, AutoFarmConfig config) {
        Block present = client.world.getBlockState(target).getBlock();
        // 已经长着同一种作物了：可能是我们种上了，也可能是别人种的，都别再动手
        if (cropBlock == null || present == cropBlock) {
            replanted = cropBlock != null;
            return Stage.DONE;
        }

        int slot = findSeed(client, player, config);
        if (slot < 0) {
            notice = "autofarm.notice.no_seed";
            missingSeed = true;
            return Stage.DONE;
        }

        // 同样先对准：种子是种在脚下那块耕地上的，准星要压在那一格上
        BlockInteractor.aimAt(player, target);
        if (!BlockInteractor.isAimedAt(player, target, AIM_TOLERANCE)) {
            return Stage.REPLANT;
        }

        // 种子是「种在下面那块耕地上的」，所以要贴着耕地往上一格放
        if (ticks >= nextActionAt) {
            BlockInteractor.placeWith(client, target, slot);
            nextActionAt = ticks + Humanizer.gap(REPLANT_RETRY_INTERVAL);
        }
        ticks++;

        if (client.world.getBlockState(target).getBlock() == cropBlock) {
            replanted = true;
            return Stage.DONE;
        }
        if (ticks > REPLANT_TIMEOUT_TICKS) {
            notice = "autofarm.notice.replant_failed";
            return Stage.DONE;
        }
        return Stage.REPLANT;
    }

    /**
     * 往一块空着的耕地上种东西。
     *
     * <p>和 {@link #replant} 的区别只有一个：<b>这里不知道该种什么</b>。
     * 补种是「刚挖掉了小麦 → 种回小麦」，而空着的耕地没告诉你它本来种的是什么，
     * 所以只能从手边有的种子里挑一种（见 {@link InventoryScanner#choosePlantCrop}）——
     * 优先玩家在面板上指定的作物，没指定就挑数量最多的那种。
     *
     * <p>「种上了没有」的判定很省事：<b>这一格不再是空气</b>就算成功。
     * 不管长出来的是不是我们想要的那种，反正地已经不空着了，
     * 再往同一格放第二次只会变成「对着作物右键」，反而容易出事。
     */
    private static Stage plant(MinecraftClient client, ClientPlayerEntity player, AutoFarmConfig config) {
        // 已经种上了（自己种的，或者别人顺手种了）→ 完事
        if (!client.world.getBlockState(target).isAir()) {
            planted = true;
            return Stage.DONE;
        }
        // 地没了（耕地被踩坏、被换成别的方块）→ 别硬往里塞
        if (!CropRegistry.isPlantingSpot(client.world, target)) {
            return Stage.DONE;
        }

        // 种什么：优先面板上指定的作物，否则挑手边最多的那种
        if (cropBlock == null) {
            cropBlock = InventoryScanner.choosePlantCrop(player, plantCropBlock(config));
        }
        if (cropBlock == null) {
            notice = "autofarm.notice.plant_no_seed";
            missingSeed = true;
            return Stage.DONE;
        }

        int slot = findSeed(client, player, config);
        if (slot < 0) {
            notice = "autofarm.notice.plant_no_seed";
            missingSeed = true;
            return Stage.DONE;
        }

        // 和补种一样：准星先压到那一格上，再放
        BlockInteractor.aimAt(player, target);
        if (!BlockInteractor.isAimedAt(player, target, AIM_TOLERANCE)) {
            return Stage.PLANT;
        }

        if (ticks >= nextActionAt) {
            BlockInteractor.placeWith(client, target, slot);
            nextActionAt = ticks + Humanizer.gap(REPLANT_RETRY_INTERVAL);
        }
        ticks++;

        if (!client.world.getBlockState(target).isAir()) {
            planted = true;
            return Stage.DONE;
        }
        if (ticks > REPLANT_TIMEOUT_TICKS) {
            notice = "autofarm.notice.plant_failed";
            return Stage.DONE;
        }
        return Stage.PLANT;
    }

    /**
     * 对着一株还没熟的作物撒骨粉（附加功能「骨粉催熟」）。
     *
     * <p>四个动作按顺序来，每一步都可能被打断：
     * <ol>
     *   <li>这一格还是不是「还没熟的作物」？不是 → 收工（熟了就该去收割了）；</li>
     *   <li>撒够次数了吗？撒满了 → 收手，别在一株上耗掉整袋骨粉；</li>
     *   <li>把骨粉换到手上 —— <b>换手那一 tick 不撒</b>，等下一 tick 确认手上真拿着它；</li>
     *   <li>对准、够得着，才撒。每撒一次记一次数，中间隔
     *       {@link #BONE_MEAL_RETRY_INTERVAL} tick 再撒下一次。</li>
     * </ol>
     *
     * <p>它<b>不挖、不摘、也不补种</b>：撒完骨粉这一格仍然是一株作物 ——
     * 熟了就交给收割流程，没熟就下一轮扫描再来。真正的「收完了」永远只有
     * {@link #mine} 和 {@link #pick} 两条路会置位。
     */
    private static Stage boneMealTick(MinecraftClient client, ClientPlayerEntity player,
                                      AutoFarmConfig config) {
        if (!CropRegistry.isGrowing(client.world.getBlockState(target))) {
            return Stage.DONE;
        }

        int limit = config == null ? 1 : Math.max(1, config.boneMealLimit);
        if (boneMealUsed >= limit) {
            notice = "autofarm.notice.bone_meal_limit";
            return Stage.DONE;
        }

        // 快捷栏里得有骨粉。背包深处的不管 —— 那要占着一格快捷栏才能用，
        // 不如把「没骨粉了」这件事交给「耗材自给」去补给点取一趟。
        int slot = InventoryScanner.findBoneMealSlot(player);
        if (slot < 0) {
            notice = "autofarm.notice.no_bone_meal";
            return Stage.DONE;
        }
        if (player.getInventory().selectedSlot != slot) {
            if (InventoryScanner.selectHotbarSlot(client, player, slot)) {
                ticks = 0;
                nextActionAt = 0;
            }
            return Stage.BONE_MEAL;
        }

        BlockInteractor.aimAt(player, target);
        if (!BlockInteractor.isAimedAt(player, target, AIM_TOLERANCE)) {
            return Stage.BONE_MEAL; // 转视角很快（几 tick），所以这段时间不计入超时
        }

        // 够得着才撒：隔着两格按右键，服务端会当成作弊
        if (BlockInteractor.isWithinReach(player, target, REACH) && ticks >= nextActionAt) {
            BlockInteractor.useBlock(client, target);
            boneMealUsed++;
            StatsManager.get().recordBoneMeal();
            nextActionAt = ticks + Humanizer.gap(BONE_MEAL_RETRY_INTERVAL);
        }
        ticks++;

        if (ticks > BONE_MEAL_TIMEOUT_TICKS) {
            notice = "autofarm.notice.bone_meal_stuck";
            return Stage.DONE;
        }
        return Stage.BONE_MEAL;
    }

    /** 面板上指定的「空地补种作物」；没设置、或者那个名字认不出来时返回 null（= 自动挑） */
    private static Block plantCropBlock(AutoFarmConfig config) {
        return config == null ? null : CropRegistry.blockOf(config.plantCrop);
    }

    /**
     * 找到「这一手该拿着的那组种子」在第几格。
     *
     * <p>顺序是三层，一层比一层退让：
     * <ol>
     *   <li><b>默认种子槽位</b> —— 玩家指定的那一格优先，符合直觉；</li>
     *   <li><b>快捷栏别处</b> —— 手边就有，直接拿来用；</li>
     *   <li><b>整个背包深搜</b> —— 找到之后把它<b>换</b>到默认种子槽位（原版放置只用主手，
     *       所以必须换到快捷栏）。这一步是这版新加的：以前只会翻快捷栏，
     *       快捷栏用光就白跑一趟补给点，哪怕背包深处还躺着好几组。</li>
     * </ol>
     *
     * <p>换位那一 tick 会直接返回，不顺手去种 —— 等下一轮重试时种子已经稳稳在快捷栏里了，
     * 这样「换手」和「放置」不会挤在同一个 tick 里，看着更像真人。
     *
     * @return 该用的快捷栏格号；整个背包都没有就返回 -1
     */
    private static int findSeed(MinecraftClient client, ClientPlayerEntity player, AutoFarmConfig config) {
        int preferred = preferredHotbar(config);
        int slot = InventoryScanner.findSeedSlot(player, cropBlock, preferred);
        if (slot >= 0) {
            return slot;
        }

        // 快捷栏没有 → 先在整个背包里深搜一遍
        int deep = InventoryScanner.findSeedSlotDeep(player, cropBlock);
        if (deep >= 0) {
            int targetHotbar = preferred >= 0 ? preferred : 0;
            if (InventoryScanner.swapToHotbar(client, player, deep, targetHotbar)) {
                notice = "autofarm.notice.seed_from_backpack";
                return targetHotbar;
            }
        }
        return -1;
    }

    /** 面板上设的「默认种子槽位」（1~9）换算成快捷栏下标（0~8） */
    private static int preferredHotbar(AutoFarmConfig config) {
        int slot = config.defaultSeedSlot - 1;
        int hotbar = PlayerInventory.getHotbarSize();
        return Math.max(0, Math.min(hotbar - 1, slot));
    }
}
