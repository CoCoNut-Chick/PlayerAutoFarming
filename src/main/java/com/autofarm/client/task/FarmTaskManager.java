package com.autofarm.client.task;

import com.autofarm.client.combat.KillAura;
import com.autofarm.client.compat.CameraCompat;
import com.autofarm.client.config.AutoFarmConfig;
import com.autofarm.client.config.ConfigManager;
import com.autofarm.client.container.ContainerCompat;
import com.autofarm.client.container.ContainerTransfer;
import com.autofarm.client.harvest.CropHarvester;
import com.autofarm.client.human.Humanizer;
import com.autofarm.client.human.IdleLook;
import com.autofarm.client.human.RescueHelper;
import com.autofarm.client.interact.BlockInteractor;
import com.autofarm.client.inventory.AutoEater;
import com.autofarm.client.inventory.InventoryScanner;
import com.autofarm.client.inventory.ItemCategory;
import com.autofarm.client.path.BlockNavigator;
import com.autofarm.client.path.PathPlanner;
import com.autofarm.client.path.PathStep;
import com.autofarm.client.path.PathWalker;
import com.autofarm.client.scan.CropFilter;
import com.autofarm.client.scan.CropRegistry;
import com.autofarm.client.scan.CropScanner;
import com.autofarm.client.stats.StatsManager;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 自动收割任务的大脑。
 *
 * <h2>它管两条完全不同的节奏</h2>
 * <ul>
 *   <li><b>每 tick</b>：驱动角色往前走、走到作物跟前就挖掉再补种；</li>
 *   <li><b>每隔一段时间</b>：重新扫描周围，看还有哪些成熟作物没去过；
 *       手上没有路线在跑、而又有新目标时，规划一条新路线（{@link PathPlanner}）。</li>
 * </ul>
 *
 * <p>为什么非要等「手上路线走完」再重新规划：边走边重排的话，
 * 角色会因为每秒钟都在改主意而走成锯齿。走完一轮再重新看，路线才稳定。
 *
 * <h2>优先级链（tick 级，高优先级可打断低优先级）</h2>
 * <p>同一 tick 里可能同时有好几件事想做，所以按下面的顺序从高到低判断，
 * <b>谁先成立就做谁、其余让路</b>——这就是「interrupt」：高优先级随时插入，
 * 打断低优先级的运行，做完再回来：
 * <ol>
 *   <li><b>吃</b>（{@link AutoEater}）：饿到跑不动先吃，走路收割都让路；</li>
 *   <li><b>环境自救</b>（{@link RescueHelper}）：溺水 / 着火，保命最优先；</li>
 *   <li><b>受击反制</b>：挨打停手、回头看向攻击者（开着自动打怪则由它接管）；</li>
 *   <li><b>停机类</b>：背包满、低血量、附近有怪/玩家、疲劳、时段外、环境不合适；</li>
 *   <li><b>危险撤退</b>：血少 + 附近有威胁，主动朝反方向撤离；</li>
 *   <li><b>工作区域返回</b>：人越界就停手走回自己那块地；</li>
 *   <li><b>自动打怪</b>（{@link KillAura}）：高风险，替玩家出手攻击；</li>
 *   <li><b>补给</b>（{@link ContainerTransfer}）：存作物/杂物、取种子/工具/骨粉；</li>
 *   <li><b>干活</b>（{@link PathWalker} + {@link CropHarvester}）：沿路线走，
 *       到站收割 / 催熟 / 补种；</li>
 *   <li><b>扫描 / 规划</b>：前几件事都不占着人时才重新排路线。</li>
 * </ol>
 *
 * <h2>路线内的目标优先级（一条路线里先做谁）</h2>
 * <ol>
 *   <li><b>掉落物</b>：会消失、会被水冲走，最紧急，先捡；</li>
 *   <li><b>骨粉催熟</b>：有未成熟作物 + 有骨粉时优先连续催熟，催完再收；</li>
 *   <li><b>收集</b>：成熟作物；</li>
 *   <li><b>瓜 / 自定义方块</b>；</li>
 *   <li><b>补种空耕地</b>：优先级最低，有富余才做。</li>
 * </ol>
 * 一句话：<b>保命 &gt; 战斗 &gt; 补给 &gt; 催熟 &gt; 收集 &gt; 补种</b>。
 *
 * <h2>「执行完上一步再进行下一步」是怎么保证的</h2>
 * <p>上表是<b>每一 tick 重新从高到低判断</b>：谁先成立就做谁、其余让路。所以
 * <b>高优先级随时能插入、打断低优先级</b>（interrupt）。
 *
 * <p>反过来，<b>低优先级不会打断正在进行的那一步</b>：
 * <ul>
 *   <li>走到半路时，只要当前目标还有效，扫描就<b>不重新排路线</b>
 *       （见 {@code pending} 前的「严格按步执行」判断）—— 不会「走到一半改道」；</li>
 *   <li>已经站到目标跟前、正握着它收割 / 种植时，任何重新规划都先让开
 *       （{@link PathWalker#isHolding()}）；</li>
 *   <li>连「骨粉告罄去补给」这种中断，也要求手上没在干半步活才触发。</li>
 * </ul>
 * 于是整体节奏是：<b>走一格 → 处理掉 → 再走一格</b>，只有更高优先级的突发事件会插进来。
 */
public final class FarmTaskManager {

    /**
     * 开着「穿透显示」时的扫描间隔下限（tick）。
     *
     * <p>透视模式下每一帧要额外画一堆穿过方块的描边，绘制压力明显更大，
     * 所以这里自动把扫描往后压一压，等于给它配了个更长的冷却时间。
     * 玩家自己设的间隔比这个还大时，以玩家设的为准。
     */
    private static final int THROUGH_WALLS_MIN_INTERVAL = 40;

    /** 背包空格少于等于这个数就算「快满了」，该去补给点存一趟 */
    private static final int INVENTORY_RESERVE = 1;

    /**
     * 走到掉落物所在格之后，原地停留几 tick 再放行。
     *
     * <p>「站上去」和「东西进背包」之间隔着原版的拾取判定，不是同一 tick 完成的。
     * 停这一小会儿，等它真的被吸走了再走开，否则会出现「明明去过、东西还在地上」。
     */
    private static final int PICKUP_WAIT_TICKS = 6;

    /** 同一格掉落物「站够了却还没进包」最多再等几轮（每轮 PICKUP_WAIT_TICKS），超了就放行 */
    private static final int PICKUP_MAX_RETRIES = 5;

    /** 掉落物离玩家近到这种程度（格）就当「抬脚就捡到了」，不再专门排进路线 */
    private static final double PICKUP_IGNORE_DISTANCE = 1.6;

    /**
     * 一件掉落物「插队去捡了却没捡到手」之后的冷却（tick）。
     *
     * <p>防的是死循环：某件东西掉进了够不着的角落（卡在栅栏里、泡在水里被推来推去），
     * 若它每轮都算「值得插队」，角色就会永远围着它转、田里的活全停下。
     * 试过一次没成，就先把它从「紧急」名单里摘掉 20 秒，让正常作业先跑起来。
     */
    private static final int PICKUP_URGENT_COOLDOWN_TICKS = 400;

    /**
     * 一趟补给「没办成」之后的冷却（tick）。
     *
     * <p>没办成包括：走不到、箱子打不开、箱子已经满了搬不动、箱子里没有要的种子。
     * 这几种情况如果不冷静一下，下一 tick 的条件依然成立，
     * 它就会「立刻再跑一趟」——变成在两个点之间来回横跳。
     */
    private static final int SUPPLY_RETRY_COOLDOWN_TICKS = 400;

    private static FarmState state = FarmState.IDLE;
    private static List<BlockPos> matureCrops = Collections.emptyList();

    /**
     * 附近「空着的耕地」——上面是空气、下面是耕地的那种格子。
     *
     * <p>只有「补种空耕地」开关开着、而且自动收割也开着的时候才有内容
     * （关着时是空表，连扫都不扫）。它们和成熟作物走同一条路线、同一个执行器，
     * 区别只在到了之后干什么：那边是挖，这边是种（见 {@code CropHarvester.start}）。
     */
    private static List<BlockPos> emptyFarmland = Collections.emptyList();

    /**
     * 额外收割目标：瓜茎旁的瓜（{@code melonHarvest}）+ 玩家自定义方块
     * （{@code customHarvestBlocks}）。它们不是作物（没有 age、判不出成熟），
     * 所以单独一个集合，收割时走「挖掉不补种」的 {@code CropHarvester.startBreak}。
     */
    private static final Set<BlockPos> extraTargets = new HashSet<>();

    /**
     * {@link #extraTargets} 的只读列表镜像 —— 渲染层每帧都会要它，
     * 直接在 {@link #getExtraTargets()} 里 new 一份等于每帧都复制，浪费。
     * 每次扫描填完 extraTargets 后刷新一次即可。
     */
    private static List<BlockPos> extraTargetsList = Collections.emptyList();

    /**
     * {@link #pickupTargets} 的只读列表镜像 —— 和 {@link #extraTargetsList} 同一个理由：
     * 渲染层每帧都要拿它画框，直接在 getter 里复制太浪费。一次扫描刷新一遍即可。
     */
    private static List<BlockPos> pickupTargetsList = Collections.emptyList();

    /**
     * 骨粉催熟目标：田里那些「<b>还没熟、但已经种下了</b>」的作物。
     *
     * <p>它和成熟作物分开的理由很实际：催熟是「有富余才做」的事，收成是「熟了就该收」的事，
     * 所以它排在路线最后（见下面 {@code pending} 的拼接顺序）。而且开关一关，
     * 这块连扫都不扫 —— 成熟作物列表里不会有它，世界里也不会给它画框。
     */
    private static final Set<BlockPos> boneMealTargets = new HashSet<>();

    private static long lastScanTick = Long.MIN_VALUE;

    /**
     * 上一 tick 看到的「自动收割」开关状态。
     *
     * <p>用它来抓「关 → 开」这个边沿：只要玩家把开关重新打开（不管是按快捷键，
     * 还是在面板里点），就清空「已处理」记忆并强制立刻重扫一次。
     * 这正是快捷键存在的意义 —— <b>让那些还没被挖掉的成熟作物重新被认出来</b>。
     */
    private static boolean lastEnabled = false;

    /**
     * 本轮已经处理过的目标（走到了、或者判定过不去放弃了）。
     *
     * <p>这个集合每轮扫描会跟当前扫描结果求交集自动瘦身：
     * 真被挖掉的、或者玩家走远后扫不到的，都会自己离开集合。
     */
    private static final Set<BlockPos> handled = new HashSet<>();

    // ================= 补给点往返 =================

    /** 补给点往返走到哪一步了 */
    private enum SupplyPhase {
        /** 没在往返 */
        NONE,
        /** 正在走去补给点 */
        GOING,
        /** 人到了，正在开箱存取 */
        TRANSFERRING
    }

    /** 补给的目的 —— 决定用哪个专用容器（没设/禁用时退回默认补给点） */
    private enum SupplyPurpose {
        /** 存作物产物 → 农作物输出容器 */
        DEPOSIT_CROP,
        /** 存杂物（非农作物物品）→ 杂物临时存储容器 */
        DEPOSIT_JUNK,
        /** 取种子 → 种子输入容器 */
        REFILL_SEED,
        /** 取工具 / 骨粉 → 工具补给容器 */
        FETCH_TOOL
    }

    private static SupplyPhase supplyPhase = SupplyPhase.NONE;
    /** 这一趟要不要存东西 */
    private static boolean supplyDeposit;
    /** 这一趟要不要取种子 */
    private static boolean supplyRefill;
    /** 这一趟存的是不是「杂物」（非农作物物品，见 {@code ContainerTransfer.depositJunk}） */
    private static boolean supplyDepositJunk;
    /** 这一趟的补给目的 —— 决定用哪个专用容器（输出 / 种子 / 工具 / 杂物） */
    private static SupplyPurpose supplyPurpose = SupplyPurpose.DEPOSIT_CROP;

    /**
     * 这一趟要顺带取<b>骨粉</b>（附加功能「耗材自给」）。
     *
     * <p>它在每次规划路线前重新算一次：开了「骨粉催熟」、又开了「耗材自给」、
     * 而手边骨粉已经掉到阈值以下 —— 那就记一笔，下面的补给流程会顺路去取一栈。
     * 不开催熟就不需要骨粉，所以那时它恒为 false，白跑一趟的可能都没有。
     */
    private static boolean suppliesWanted;

    /**
     * 这一趟要顺带取<b>一件有耐久的工具</b>（附加功能「备用工具自给」）。
     *
     * <p>判据是「背包里一把还带一半以上余量的都没有」。不挖路的时候工具本来就不会坏，
     * 所以平时它恒为 false，只有真断了一把时才成立。
     */
    private static boolean toolWanted;

    /**
     * 这一趟实际要去的容器方块。
     *
     * <p>为什么不直接用配置里的补给点坐标：面板上「记录当前位置」记的是<b>玩家脚下那一格</b>，
     * 站着记录、蹲在箱子上面记录都会偏一格。所以出发时会在补给点附近认一下
     * （见 {@link ContainerCompat#findContainer}），把真正的那口箱子记在这儿，
     * 后面开箱、走路、够不够得着全都用它。
     */
    private static BlockPos supplyContainer;

    /**
     * 「缺种子」这件事还悬着 —— 走完手上这条路线就去补给点。
     *
     * <p>不在收割当场掉头，是为了不把一条已经规划好的路线打断：
     * 剩下的几棵作物照收，收完再去补货，然后重新扫一遍继续。
     */
    private static boolean refillWanted;

    /** 具体缺哪些作物的种子（补给点只取这些，不乱搬别的东西） */
    private static final Set<Block> missingSeeds = new HashSet<>();

    /** 补给点相关的一句话结果，显示在面板状态区 */
    private static String supplyNotice;

    /** 上一趟补给没办成时留下的冷静期，倒计时到 0 之前不再去 */
    private static int supplyCooldown;

    // ================= 统计与播报 =================

    /**
     * 这一轮（从上次打开开关算起）收了几个。
     *
     * <p>只给 HUD 和面板看：没有它你根本不知道它到底干了多少活，
     * 只能靠盯着世界里的方块变化猜。
     */
    private static int harvestedCount;

    /** 累计统计的低频落盘计时器（tick）：数满就写一次盘，不用等退出世界才落盘 */
    private static int statsSaveTicks;

    /** 这一轮往空耕地上种了几格（只有开了「补种空耕地」才可能不为 0） */
    private static int plantedCount;

    /** 上一次用 actionbar 播报过的「停机原因」，用来防止同一个原因反复刷屏 */
    private static String lastPauseAnnounce;

    /** 上一次播报过的「事件类」提示（补给结果、种子用完） */
    private static String lastEventAnnounce;

    // ================= 附加功能运行时状态 =================
    // 下面这几项只在「附加功能」面板里对应的开关打开时才会被读写；
    // 全关的时候它们保持不动，等于不存在。

    /**
     * 「失败目标暂时拉黑」：位置 → 解禁时刻（{@code world.getTime()}）。
     *
     * <p>失败的目标本来会被塞进 {@link #handled} 永久跳过；开了这个功能之后，
     * 它改记在定时器里，到点自动重新排进路线。
     */
    private static final Map<BlockPos, Long> failedUntil = new HashMap<>();

    /**
     * 「优先拾取掉落物」：这一轮扫描认出来的、掉在地上还没进包的作物掉落物（记的是所在格）。
     *
     * <p>它和 {@link #matureCrops} 一样是「本轮的目标」，但走的是完全不同的处理：
     * 到了那一格不用挖也不用种，站上去等原版把东西吸进背包就行。
     * 关掉这个附加功能时它恒为空表，等于不存在。
     */
    private static final Set<BlockPos> pickupTargets = new HashSet<>();

    /** 正在掉落物格上等拾取时的倒计时；换目标时重新计时 */
    private static int pickupWaitTicks;

    /** 正在等待拾取的那一格（换目标时用来重置上面的倒计时） */
    private static BlockPos pickupWaitPos;

    /** 同一格掉落物「站够了却还没进包」时已经重试了几轮（避免无限耗在同一格） */
    private static int pickupRetryTicks;

    /**
     * 这一轮是不是「专门为掉落物跑一趟」。
     *
     * <p>为真时下面的规划只排掉落物、不排作物 / 空地 / 瓜 / 骨粉目标 ——
     * 这正是「掉落物优先」插队的效果：手上的活全放一放，先跑这一趟把东西捡回来。
     */
    private static boolean urgentPickupRun;

    /** 这一趟专门去捡的那些掉落物（{@link #urgentPickupRun} 为真时才有意义） */
    private static final Set<BlockPos> urgentPickupTargets = new HashSet<>();

    /** 每格掉落物的「存在时长」快照（tick），扫描时顺手记下，插队判定直接读它 */
    private static final Map<BlockPos, Long> pickupAges = new HashMap<>();

    /** 「插队去捡过、但没捡到」的位置 → 解禁时刻，冷却期内不再算紧急 */
    private static final Map<BlockPos, Long> pickupUrgentBlocked = new HashMap<>();

    /**
     * 防踢模式：已经「打量过」的目标。
     *
     * <p>犹豫只在<b>第一次</b>站到某一棵跟前时发生一次 —— 靠它把「同一个目标反复犹豫」
     * 挡掉，否则这个目标会被无限期地拖下去，永远轮不到动手。
     */
    private static BlockPos hesitatedAt;

    /** 拟人化节奏的停顿倒计时（tick）：>0 时先愣一下，不动手也不推进路线 */
    private static int humanizeDelay;

    /** 随机休息的剩余时长（tick）：>0 表示正在休息 */
    private static int restRemaining;

    /** 距离下一次休息已经连续工作了多少 tick */
    private static int workTicks;

    /** 本轮（从打开开关算起）真正干了活的 tick 数，给 HUD 详细统计用 */
    private static int sessionTicks;

    /**
     * 「危险反应延迟」的计时状态。
     *
     * <p>危险第一次出现时记下它的类型（{@link #pendingSafety}）并开始数 tick，
     * 数够 {@link AutoFarmConfig#reactionDelayTicks} 才真正停手。
     * 危险消失、或者换了一种危险，都重新计时。
     */
    private static String pendingSafety;
    private static int safetySeenTicks;

    // ================= 工作范围与作息（B 组）=================

    /**
     * 圈定工作区域：本轮回程路线的目标格。
     *
     * <p>回程走的是 {@link PathWalker} 的同一条通道，但它不是「作物目标」——
     * 走到之后不该挖也不该种，所以单独记一笔，用来在到站时把人放行。
     */
    private static BlockPos areaReturnTarget;

    /** 这一轮越界之后是否已经排过一次回程路线（排过还回不去就不再反复重排） */
    private static boolean areaReturnTried;

    /** 当前是否处在「越界返回」模式（用来抓进出边沿、复位上面两项） */
    private static boolean areaReturning;

    /** 疲劳收工：连续干活累计了多少 tick */
    private static int fatigueWorkTicks;

    /** 疲劳收工后的休息倒计时（tick）；>0 表示还在休息 */
    private static int fatigueRestRemaining;

    /** 疲劳「手动恢复」模式下已经收工、正等着玩家自己重开 */
    private static boolean fatigueStopped;

    // ================= 生存自保（C 组）=================

    /** 危险撤退：本次低血量周期内正在撤退（用来抓进出边沿、复位下面几项） */
    private static boolean retreating;
    /** 这一轮低血量是否已经排过一次回撤路线（排过还撤不了就不再反复重排） */
    private static boolean retreatTried;
    /** 撤退路线要走到的那一格；null = 还没排 */
    private static BlockPos retreatTarget;
    /** 这一轮低血量是否已经放弃撤退（转成原地停机）——血量恢复后自动复位 */
    private static boolean retreatDone;
    /** 「危险反应延迟」用在撤退上的计时：低血量 + 有威胁持续了多少 tick */
    private static int retreatSeenTicks;

    // ================= 每 tick 的威胁查询缓存 =================

    /** 本 tick 是否已经查过「最近的敌对生物」（每 tick 开头清掉，见 {@link #clearThreatCache}） */
    private static boolean threatCacheReady;
    /** 本 tick 缓存下来的最近敌对生物（可能是 null） */
    private static Mob threatCache;

    // ================= 每 tick 的背包缓存 =================

    /**
     * 背包统计（空格数、种子数）本 tick 是否已经算过。
     *
     * <p>「背包快满了吗」「还够种几次」这两件事在同一 tick 里会被问好几次
     * （停机判断、补给判断、HUD…），每次都全量数一遍 36 格太浪费。一 tick 算一次就够。
     */
    private static boolean invCacheReady;
    private static int freeSlotsCache;
    private static int seedCountCache;

    /** 每 tick 开头作废背包缓存 */
    private static void clearInventoryCache() {
        invCacheReady = false;
    }

    // ================= 每 tick 的容器坐标缓存 =================

    /** 四种补给目的各自缓存下来的容器坐标；每 tick 开头作废 */
    private static final BlockPos[] containerCache = new BlockPos[SupplyPurpose.values().length];
    private static final boolean[] containerCacheSet = new boolean[SupplyPurpose.values().length];

    private static void clearContainerCache() {
        for (int i = 0; i < containerCacheSet.length; i++) {
            containerCacheSet[i] = false;
            containerCache[i] = null;
        }
    }

    private static void refreshInventoryCache(Minecraft client) {
        invCacheReady = true;
        if (client == null || client.player == null) {
            freeSlotsCache = 0;
            seedCountCache = 0;
            return;
        }
        freeSlotsCache = InventoryScanner.countFreeSlots(client.player);
        seedCountCache = InventoryScanner.countSeeds(client.player, CropHarvester.getCropBlock());
    }

    /** 背包空格 ≤ {@code reserve}（本 tick 缓存，避免每 tick 反复数 36 格） */
    private static boolean isNearlyFull(Minecraft client, int reserve) {
        if (!invCacheReady) {
            refreshInventoryCache(client);
        }
        return freeSlotsCache <= reserve;
    }

    /**
     * 受击反制：上一次看到的受伤计时。
     *
     * <p>{@code hurtTime} 是原版那个「受伤变红」的倒计时，受伤瞬间被置成 10 再递减。
     * 这里盯的是它从 0 变成 &gt;0 的那一个<b>上升沿</b>，也就是「这一 tick 挨打了」。
     */
    private static int lastHurtTime;
    /** 受击反制期间要盯着看的那个攻击者 */
    private static LivingEntity hitTarget;
    /** 受击反制的剩余 tick；&gt;0 表示还在「停手 + 回头」的状态里 */
    private static int hitReactTicks;

    private FarmTaskManager() {
    }

    public static FarmState getState() {
        return state;
    }

    public static int getMatureCount() {
        return matureCrops.size();
    }

    public static List<BlockPos> getMatureCrops() {
        return matureCrops;
    }

    /** 附近空着的耕地（补种目标）。渲染层拿它画另一种颜色的框 */
    public static List<BlockPos> getFarmlandTargets() {
        return emptyFarmland;
    }

    /** 额外收割目标（瓜 / 自定义方块）。渲染层拿它画另一种颜色的框 */
    public static List<BlockPos> getExtraTargets() {
        return extraTargetsList;
    }

    /** 这一轮打算去捡的掉落物（渲染层拿它画框，让「拾取范围」看得见） */
    public static List<BlockPos> getPickupTargets() {
        return pickupTargetsList;
    }

    /** 附近空耕地的格数（面板上显示，好知道它为什么要往空地跑） */
    public static int getFarmlandCount() {
        return emptyFarmland.size();
    }

    /**
     * 当前实际生效的扫描间隔（tick）。
     *
     * <p>面板上的滑块是玩家的设定值；这里再叠一层「透视模式自动降频」，
     * 免得玩家把间隔拉到最小、又开着穿透显示，结果每帧都在刷上万格。
     */
    public static int getEffectiveScanInterval() {
        AutoFarmConfig config = ConfigManager.get();
        int configured = Math.max(2, config.scanIntervalTicks);
        return config.highlightThroughWalls
                ? Math.max(configured, THROUGH_WALLS_MIN_INTERVAL)
                : configured;
    }

    // ================= 路线信息（给面板和渲染层用） =================

    /** 角色是不是正在按路线行走 */
    public static boolean isWalking() {
        return PathWalker.isActive();
    }

    /** 当前路线上还剩多少个没到的目标 */
    public static int getRouteRemaining() {
        return PathWalker.isActive() ? PathWalker.getTotalCount() - PathWalker.getDoneCount() : 0;
    }

    /** 从当前位置起还要走多远（格） */
    public static double getRemainingDistance() {
        return PathWalker.getRemainingDistance();
    }

    /** 还没走到的作物目标，按访问顺序。它给的是「要去哪」，不是「怎么走」 */
    public static List<BlockPos> getRoute() {
        return PathWalker.getRoute();
    }

    /**
     * 当前正在走向 / 正在处理的那一格（「下一个目标」可视化用）。
     *
     * <p>走路时是下一个落脚目标，到站后是正在挖 / 种 / 摘 / 撒骨粉的那一格；
     * 没有路线时是 null。渲染层用它画一个白色的框，和其余描边区分开。
     */
    public static BlockPos getCurrentTarget() {
        return PathWalker.getCurrentTarget();
    }

    /**
     * 当前这一段 A* 算出来的实际行走路径。
     *
     * <p>和 {@link #getRoute()} 的区别：route 是「依次要去哪些作物」的战略顺序，
     * segment 是「接下来这几步具体踩哪几个格子」的战术路线 —— 而且还带着
     * 「这一步要先挖 / 要先垫」的标记。渲染层把它画成实线。
     */
    public static List<PathStep> getActiveSegment() {
        return PathWalker.getSegment();
    }

    /** 快捷栏里能拿来垫脚的方块总数。面板上显示出来，好让玩家知道垫不垫得动 */
    public static int getHotbarBlockCount() {
        Minecraft client = Minecraft.getInstance();
        return client == null || client.player == null
                ? 0
                : InventoryScanner.countPlaceableInHotbar(client.player);
    }

    /** 这一轮已经收了多少个（HUD 用） */
    public static int getHarvestedCount() {
        return harvestedCount;
    }

    /** 这一轮往空耕地上种了多少格 */
    public static int getPlantedCount() {
        return plantedCount;
    }

    /**
     * 这一轮实际干了多少秒（「HUD 详细统计」用）。
     *
     * <p>统计的是<b>干活的时间</b>：暂停（背包满、危险、休息、开着界面）的 tick 不算，
     * 所以它反映的是真实作业时长，而不是「从打开开关到现在过了多久」。
     */
    public static int getSessionSeconds() {
        return sessionTicks / 20;
    }

    /**
     * 手边还有多少「能补种的东西」（HUD 用）。
     *
     * <p>有明确的作物目标时只数那一种作物的种子 —— 你要看的正是「这种菜还够种几次」；
     * 还没收过东西时就数背包里所有能种的东西。
     */
    public static int getSeedCount() {
        // HUD 每帧都要它 —— 走本 tick 的缓存，别每帧数一遍 36 格背包
        if (!invCacheReady) {
            refreshInventoryCache(Minecraft.getInstance());
        }
        return seedCountCache;
    }

    /** 玩家退出世界时清空现场 */
    public static void reset() {
        state = FarmState.IDLE;
        matureCrops = Collections.emptyList();
        emptyFarmland = Collections.emptyList();
        lastScanTick = Long.MIN_VALUE;
        lastEnabled = false;
        handled.clear();
        PathWalker.stop();
        CropHarvester.reset();
        AutoEater.reset();
        BlockInteractor.clearDriving();
        ContainerTransfer.reset();
        supplyPhase = SupplyPhase.NONE;
        supplyDeposit = false;
        supplyRefill = false;
        supplyDepositJunk = false;
        supplyPurpose = SupplyPurpose.DEPOSIT_CROP;
        suppliesWanted = false;
        toolWanted = false;
        refillWanted = false;
        missingSeeds.clear();
        supplyNotice = null;
        supplyCooldown = 0;
        supplyContainer = null;
        harvestedCount = 0;
        plantedCount = 0;
        lastPauseAnnounce = null;
        lastEventAnnounce = null;
        // 附加功能的运行时状态也一并归零
        failedUntil.clear();
        humanizeDelay = 0;
        restRemaining = 0;
        workTicks = 0;
        // 累计统计：把本轮干活时长并入累计再清空（退出世界 = 这一轮结束）
        StatsManager.get().addWorkSeconds(sessionTicks / 20);
        sessionTicks = 0;
        pickupTargets.clear();
        extraTargets.clear();
        extraTargetsList = Collections.emptyList();
        pickupTargetsList = Collections.emptyList();
        boneMealTargets.clear();
        pickupWaitTicks = 0;
        pickupWaitPos = null;
        pickupRetryTicks = 0;
        urgentPickupRun = false;
        urgentPickupTargets.clear();
        pickupAges.clear();
        pickupUrgentBlocked.clear();
        hesitatedAt = null;
        pendingSafety = null;
        safetySeenTicks = 0;
        areaReturnTarget = null;
        areaReturnTried = false;
        areaReturning = false;
        fatigueWorkTicks = 0;
        fatigueRestRemaining = 0;
        fatigueStopped = false;
        retreating = false;
        retreatTried = false;
        retreatTarget = null;
        retreatDone = false;
        retreatSeenTicks = 0;
        lastHurtTime = 0;
        hitTarget = null;
        hitReactTicks = 0;
        Humanizer.reset();
        IdleLook.reset();
        RescueHelper.reset();
        KillAura.reset();
        // 统计写盘：退出世界是「收工」的明确时机，趁机把累计落盘
        StatsManager.get().save();
    }

    /**
     * 面板状态区那一行提示：优先显示补给点的结果，其次是上一棵作物的收割结果。
     *
     * <p>「没种子」「挖不动」「补给点到不了」这类跳过原因都走这里，
     * 不然你只会看到它绕开某一棵却不知道为什么。
     */
    public static String getHarvestNotice() {
        return supplyNotice != null ? supplyNotice : CropHarvester.getNotice();
    }

    /**
     * 一键开/关自动收割（快捷键调用）。
     *
     * <p>它做的不只是翻开关，还顺手做了一次「重新认一遍」：清空「已处理」记忆、
     * 把扫描时间戳归零，让下一个 tick 立刻重新扫一遍周围。
     * 所以当你发现某个成熟作物它死活不去挖时，按一下这个键（关掉再按一下打开）
     * 就能让它从头再认一次，不用重进世界。
     */
    public static void toggleEnabled(Minecraft client) {
        AutoFarmConfig config = ConfigManager.get();
        config.enabled = !config.enabled;

        // 关掉 → 立刻停手，把控制权还给你。
        // 但「纯拾取模式」还开着的话不算全停 —— 那种模式下模组仍在捡东西，
        // 清场会把它这一趟的路线也抹掉（下一轮能重排，但没必要抖这一下）。
        if (!config.enabled && !config.pickupOnlyMode) {
            PathWalker.stop();
            CropHarvester.reset();
            ContainerTransfer.reset();
            handled.clear();
            supplyPhase = SupplyPhase.NONE;
            supplyDeposit = false;
            supplyRefill = false;
            supplyDepositJunk = false;
            supplyPurpose = SupplyPurpose.DEPOSIT_CROP;
            suppliesWanted = false;
            toolWanted = false;
            refillWanted = false;
            missingSeeds.clear();
            supplyContainer = null;
            failedUntil.clear();
            humanizeDelay = 0;
            restRemaining = 0;
            pickupTargets.clear();
            extraTargets.clear();
            boneMealTargets.clear();
            pickupWaitTicks = 0;
            pickupWaitPos = null;
            pickupRetryTicks = 0;
            urgentPickupRun = false;
            urgentPickupTargets.clear();
            pickupAges.clear();
            pickupUrgentBlocked.clear();
            hesitatedAt = null;
            pendingSafety = null;
            safetySeenTicks = 0;
            retreating = false;
            retreatTried = false;
            retreatTarget = null;
            retreatDone = false;
            retreatSeenTicks = 0;
            hitTarget = null;
            hitReactTicks = 0;
            Humanizer.reset();
            IdleLook.reset();
            RescueHelper.reset();
            KillAura.reset();
            state = FarmState.IDLE;
            // 关掉开关 = 这一轮结束：把干活时长并入累计并落盘
            StatsManager.get().addWorkSeconds(sessionTicks / 20);
            sessionTicks = 0;
            StatsManager.get().save();
            notify(client, config, "stop");
        } else if (config.enabled) {
            // 打开 → 假装「刚进入这个世界」：清空记忆、下一 tick 立刻重扫
            handled.clear();
            lastScanTick = Long.MIN_VALUE;
            supplyNotice = null;
            harvestedCount = 0;
            plantedCount = 0;
            failedUntil.clear();
            workTicks = 0;
            sessionTicks = 0;
            pendingSafety = null;
            safetySeenTicks = 0;
            // 重新开工 = 人也歇够了：疲劳计时从零开始（这也是「手动恢复」档的复位方式）
            fatigueWorkTicks = 0;
            fatigueRestRemaining = 0;
            fatigueStopped = false;
            notify(client, config, "start");
        }
        // 给 tick 的「刚开工」边沿用：纯拾取模式开着时，关了总开关也仍然算在工作
        lastEnabled = config.enabled || config.pickupOnlyMode;

        ConfigManager.persist();

        if (client != null && client.player != null) {
            // 总开关关了、但纯拾取模式还在跑 —— 别播报成「整个停了」，那会误导
            Component state = config.enabled
                    ? Component.translatable("autofarm.on")
                    : (config.pickupOnlyMode
                            ? Component.translatable("autofarm.hud.pickup_only")
                            : Component.translatable("autofarm.off"));
            client.player.displayClientMessage(Component.translatable("autofarm.message.toggled", state), true);
        }
    }

    /** 挂在客户端 tick 末尾调用 */
    public static void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            reset();
            return;
        }

        // 每 tick 开头先复位「我们自己正在挖」的开关；这一 tick 真动了镐子的话，
        // BlockInteractor 会再把它置起来。原版读的就是这个标记（见 MinecraftClientMixin）。
        BlockInteractor.beginTick();

        // 防踢模式：推进一次内部随机量（瞄准微颤、疾跑节奏）。
        // 放在最前面，是为了保证「同一 tick 里不管谁读它，读到的都是同一个值」。
        Humanizer.beginTick();

        // 作废本 tick 的缓存：威胁查询、背包统计都改成「第一次问的时候才算、之后共用」。
        clearThreatCache();
        clearInventoryCache();
        clearContainerCache();

        // 累计统计的低频落盘：脏了才真正写（见 StatsManager.save），所以这里只是定个节拍，
        // 让它不用等退出世界才写盘，中途崩了也不至于把整局统计全丢。
        if (++statsSaveTicks >= 1200) {
            statsSaveTicks = 0;
            StatsManager.get().save();
        }

        AutoFarmConfig config = ConfigManager.get();
        // 「纯拾取模式」：一道独立于「自动收割」总开关的闸 —— 只开它，模组照样会工作，
        // 但只捡掉落物（见下面 farming 的定义）。
        boolean pickupOnly = config.pickupOnlyMode;
        // 这一 tick 到底要不要动：任一道闸开着就算「在工作」（保命动作、停机判据都看它）
        boolean working = config.enabled || pickupOnly;
        // 「干不干农活」：总开关开着、而且没切到纯拾取模式。收割 / 补种 / 催熟 / 挖瓜 /
        // 补种空地 / 去补给点，全都以它为闸 —— 纯拾取模式下它们一个都不跑。
        boolean farming = config.enabled && !pickupOnly;
        // 两道闸都关着就整个停手，连高亮也不扫 —— 关掉开关就该什么都不显示，
        // 而不是留着描边让人误以为还在工作。
        if (!working) {
            // 关掉开关 = 整个停手，连高亮也不扫（关掉就该什么都不显示）。
            // 只在「刚关掉」那一个 tick 清一次场 —— 不能每 tick 都调 reset()：
            // 它含 StatsManager.save()（写盘），关着开关挂机就会变成一直写盘。
            // reset() 内部会把 lastEnabled 置回 false，所以下一 tick 不会再进来。
            if (lastEnabled) {
                reset();
            }
            return;
        }

        // 自由视角（灵魂出窍）：由配置项「自由视角时停机」决定这一 tick 要不要整个停手。
        //
        // 之所以「不停手」时真的能干活：原版 ClientPlayerEntity.tickNewAi() 里那段
        // 「把输入抄成移动意图」的代码外面套着 if (isCamera())，摄像机一离开身体就被整段跳过，
        // 于是角色一步都走不了。我们把移动意图写在那个判断的下游（见 ClientPlayerInputMixin），
        // 绕过了它 —— 而且全程不碰键盘输入对象，自由视角模组拿 WASD 飞摄像机完全不受影响。
        boolean freecamPaused = CameraCompat.pauseForFreecam(config);

        // 「关 → 开」的边沿：清空「已处理」并强制重扫。
        // 抓边沿而不是只看当前值，是为了让「面板里点开」和「快捷键开」走同一套逻辑，
        // 都能把还没被挖掉的成熟作物重新认出来。
        if (!lastEnabled) {
            handled.clear();
            lastScanTick = Long.MIN_VALUE;
            CropHarvester.reset();
            supplyNotice = null;
            // 附加功能的计时也重来一遍，否则「休息」和「运行时长」会带着上一轮的账继续走
            failedUntil.clear();
            humanizeDelay = 0;
            restRemaining = 0;
            workTicks = 0;
            sessionTicks = 0;
            pickupTargets.clear();
            pickupWaitTicks = 0;
            pickupWaitPos = null;
            pickupRetryTicks = 0;
            hesitatedAt = null;
            pendingSafety = null;
            safetySeenTicks = 0;
            areaReturnTarget = null;
            areaReturnTried = false;
            areaReturning = false;
            fatigueWorkTicks = 0;
            fatigueRestRemaining = 0;
            fatigueStopped = false;
            retreating = false;
            retreatTried = false;
            retreatTarget = null;
            retreatDone = false;
            retreatSeenTicks = 0;
            hitTarget = null;
            hitReactTicks = 0;
            RescueHelper.reset();
            KillAura.reset();
            lastEnabled = true;
        }

        // ================================================================
        // 优先级 1 —— 吃
        // 饿到跑不动就先吃。这是所有事情的前提：走不动的话，去下一棵作物要多花一倍时间。
        // 它会接管使用键，期间走路和收割都给它让路。
        // ================================================================
        AutoEater.tick(client, config);

        if (supplyCooldown > 0) {
            supplyCooldown--;
        }

        // ================================================================
        // 附加功能「环境自救」（C 组）：溺水 / 着火的保命动作。
        // 排在所有事情之前 —— 人都在水里了，收菜的事先放一放。
        // 自救期间由 RescueHelper 接管移动输入（见 KeyboardInputMixin）。
        // ================================================================
        if (working && RescueHelper.tick(client, config)) {
            if (PathWalker.isActive() || supplyPhase != SupplyPhase.NONE) {
                cancelSupply(null);
                PathWalker.stop();
            }
            CropHarvester.reset();
            announcePause(client, RescueHelper.notice());
            state = FarmState.RESCUING;
            return;
        }

        // ================================================================
        // 附加功能「受击反制」（C 组）：挨打的那一 tick 记下来，接下来几秒停手 + 回头。
        // 捕获的是「受伤计时从 0 变成 >0」的上升沿 —— 也就是这一 tick 真的受了伤。
        // ================================================================
        int hurtTime = client.player.hurtTime;
        if (working && config.hitReactionEnabled) {
            if (hurtTime > 0 && lastHurtTime == 0) {
                beginHitReaction(client, config);
            }
        } else {
            hitTarget = null;
            hitReactTicks = 0;
        }
        lastHurtTime = hurtTime;

        // ================================================================
        // 背包满了，又没地方存 → 停手
        // 继续收下去，多出来的作物只会掉在地上烂掉。宁可停下来等你处理，
        // 也不做这种「收了个寂寞」的事。腾出空间（或者设好补给点）之后自动恢复。
        // ================================================================
        // 背包满停机对「纯拾取模式」同样有效：捡东西也要有地方放，满包还硬捡只会原地空转
        boolean stopForFull = working && shouldStopForFullInventory(client, config);

        // 附加功能「危险撤退」（C 组）：血少 + 附近有威胁时，用「主动撤离」代替「原地停机」。
        // 判据在这里先算好；它成立时下面会跳过「低血量停机」那一条 ——
        // 否则会出现「一边要撤、一边被自己的安全检查按住」的矛盾。
        boolean lowHealth = working
                && client.player.getHealth() < config.retreatHealthHearts * 2.0f;
        boolean wantRetreat = working
                && config.retreatEnabled
                && !retreatDone
                && lowHealth
                && nearestThreat(client) != null;
        // 「危险反应延迟」对撤退同样生效：真人不会在血量掉下去的同一 tick 就掉头跑，
        // 总要慢半拍才反应过来。延迟期间先不动，够 tick 了才真正开始撤 ——
        // 否则「反应延迟」这个卖点在撤退这一支上就形同虚设了。
        if (wantRetreat && config.reactionDelay) {
            retreatSeenTicks++;
            wantRetreat = retreatSeenTicks >= Math.max(1, config.reactionDelayTicks);
        } else {
            retreatSeenTicks = 0;
        }
        // 真要撤还得有「能操作角色」的前提：开着界面 / 自由视角时先别动，
        // 这时候退回普通停机（见下面的 safety），等能动了再撤。
        boolean retreatActive = wantRetreat
                && client.screen == null
                && !freecamPaused;
        if (!lowHealth) {
            // 血回来了：撤退相关的记忆全部复位，下一次低血量可以重新撤
            retreating = false;
            retreatTried = false;
            retreatTarget = null;
            retreatDone = false;
            retreatSeenTicks = 0;
        }

        // 安全条件：血量太低、或者附近有敌对生物。它比「收菜」重要 ——
        // 人被打了还低着头收菜，是最容易出事的状态。
        // 附加功能「危险反应延迟」：危险第一次出现时先愣一下再停手 ——
        // 机器会在冒出危险的同一 tick 停手，真人总要花上几百毫秒（见 applyReactionDelay）。
        // 撤退接管时不走这一条：那种情况下的正确动作是「走开」，不是「原地不动」。
        String safety = working && !retreatActive
                ? applyReactionDelay(config, safetyNotice(client, config))
                : null;

        if (stopForFull) {
            cancelSupply(null);
            PathWalker.stop();
            CropHarvester.reset();
            supplyNotice = "autofarm.notice.inventory_full_no_supply";
            state = FarmState.PAUSED;
        } else if (safety != null) {
            cancelSupply(null);
            PathWalker.stop();
            CropHarvester.reset();
            supplyNotice = safety;
            state = FarmState.PAUSED;
        } else if (isPauseNotice(supplyNotice)) {
            // 空间腾出来了 / 危险过去了，把那句提示撤掉
            supplyNotice = null;
        }

        // 附加功能「仅在指定时段工作」：不在工作时段就停手（账号仍然在线，只是不干活）
        boolean offSchedule = working && config.scheduleEnabled && !insideSchedule(config);
        // 附加功能「仅白天 / 仅晴天」：环境不合适就停手，条件一恢复自动继续
        String environment = working ? environmentNotice(client, config) : null;
        // 先算「除定时休息之外，是否已经因为别的原因停手」。定时休息的计时要用到它：
        // 否则夜间挂机 / 背包满停机那些时间也会被算进「连续工作」，一开灯就累到触发休息。
        boolean pausedBeforeRest = stopForFull || safety != null || offSchedule || environment != null;
        // 这一 tick 要不要整个人停手（背包满、安全保护、外加几个附加功能）
        boolean resting = working && updateRest(config, pausedBeforeRest);
        // 上面这些都成立 = 「已经在停手」。疲劳计时要用到它 ——
        // 只有真正在干活的那些 tick 才该被算进「连续工作」。
        boolean alreadyPaused = pausedBeforeRest || resting;
        // 附加功能「疲劳收工」：连续干到上限就整场停下来
        boolean fatigued = working && updateFatigue(config, alreadyPaused);
        boolean paused = alreadyPaused || fatigued;

        // 一停机就播报一次（actionbar），免得你以为是模组卡了 ——
        // 同一个原因只说一次，危险过去后自动允许下次再说。
        // 休息 / 工作时段 / 环境 / 疲劳在 supplyNotice 里没有位置（那栏留给
        // 「缺种子」「箱子满了」这类事），所以它们各用自己的键，同样走「只说一次」这套。
        String pauseKey;
        if (stopForFull || safety != null) {
            pauseKey = supplyNotice;
        } else if (resting) {
            pauseKey = "autofarm.notice.paused_resting";
        } else if (offSchedule) {
            pauseKey = "autofarm.notice.paused_schedule";
        } else if (environment != null) {
            pauseKey = environment;
        } else if (fatigued) {
            pauseKey = "autofarm.notice.paused_fatigue";
        } else {
            pauseKey = null;
        }
        announcePause(client, paused ? pauseKey : null);

        // 附加功能：HUD 详细统计的「运行时长」——只数真正干活的时间，
        // 暂停（满包 / 危险 / 休息 / 开着界面）的 tick 不算，这样它更像一块秒表。
        // 撤退是在「保命」而不是「干活」，所以也不算进去。
        if (working && !paused && !retreatActive) {
            sessionTicks++;
        }

        // 防踢模式：把「这一 tick 到底有没有在干活」喂给疲劳模型 ——
        // 干得越久，动作越容易走神；歇下来（暂停、开界面）它会慢慢回落
        Humanizer.feedWorking(working && !paused && !retreatActive);

        // 自由视角（灵魂出窍）：摄像机和身体分开之后，屏幕上看到的人是不动的，
        // 可世界里方块却在自己碎 —— 那太显眼了。打开「自由视角时停机」就先停手，
        // 只保留描边和路线；关掉（默认）则照常干活：这一 tick 的 freecamPaused 在 tick 开头算好了。

        // ================================================================
        // 附加功能「受击反制」（C 组）：挨打之后的这几秒，停手 + 回头看向攻击者。
        // 开着自动打怪时由 KillAura 负责转向与反击（见下面的 fightingActive），
        // 这里只处理「没开自动打怪」时的转向，避免两边抢视角。
        // ================================================================
        boolean hitReacting = false;
        if (working && config.hitReactionEnabled && hitReactTicks > 0) {
            hitReactTicks--;
            hitReacting = true; // 受击窗口内一律停手（能不能回头再另说）
            if (!config.autoAttack && hitTarget != null && hitTarget.isAlive()
                    && !freecamPaused) {
                BlockInteractor.aimAt(client.player, hitTarget.getX(), hitTarget.getEyeY(),
                        hitTarget.getZ());
            }
        }

        // ================================================================
        // 附加功能「危险撤退」（C 组）：血少 + 附近有威胁 → 朝反方向撤到安全距离。
        // 排在「工作区域返回」之前 —— 保命优先于回自己的地。
        // ================================================================
        if (retreatActive) {
            if (!retreating) {
                // 刚进入撤退：把手上的活全放下，准备撤离
                retreating = true;
                retreatTried = false;
                cancelSupply(null);
                PathWalker.stop();
                CropHarvester.reset();
                handled.clear();
                matureCrops = Collections.emptyList();
                emptyFarmland = Collections.emptyList();
                pickupTargets.clear();
            }
            if (runRetreat(client, config)) {
                PathWalker.setPaused(false);
                state = FarmState.RETREATING;
                announcePause(client, "autofarm.notice.retreating");
                IdleLook.tick(client, config, false);
                return;
            }
            // 撤不了（没威胁 / 走不通 / 已经到位）→ 放弃撤退，退回普通停机
            retreatDone = true;
        }

        // ================================================================
        // 附加功能「圈定工作区域」：人跑出圈了就停手、走回来
        // 排在战斗之前 —— 已经越界了，就别再追着怪往别人地里跑。
        // 判据只看「人在不在区域里」，和「目标在不在区域里」是两件事：
        // 后者在扫描那一步就把圈外的目标滤掉了（见下面 matureCrops 的过滤）。
        // ================================================================
        // 正在跑补给时不判越界 —— 补给点本身可能在圈外，去补给就是「有意出圈」，
        // 不能一出圈就被越界返回撤销整趟补给（否则圈外的箱子永远用不了）。
        boolean outsideArea = working
                && config.workAreaEnabled
                && supplyPhase == SupplyPhase.NONE
                && !config.insideWorkArea(client.player.getX(), client.player.getY(), client.player.getZ());

        // 回到圈里了：把返回状态复位，恢复正常作业
        if (!outsideArea && areaReturning) {
            areaReturning = false;
            areaReturnTried = false;
            areaReturnTarget = null;
            lastScanTick = Long.MIN_VALUE; // 立刻重扫一遍，好接着干活
        }

        if (outsideArea
                && !paused
                && client.screen == null
                && !freecamPaused) {
            if (!areaReturning) {
                // 刚出界：把手上的活全放下（这条路线本来就通不到圈外去），准备回程
                areaReturning = true;
                areaReturnTried = false;
                cancelSupply(null);
                PathWalker.stop();
                CropHarvester.reset();
                handled.clear();
                matureCrops = Collections.emptyList();
                emptyFarmland = Collections.emptyList();
                pickupTargets.clear();
            }
            runAreaReturn(client, config);
            PathWalker.setPaused(false);
            state = FarmState.RETURNING;
            announcePause(client, "autofarm.notice.out_of_area");
            IdleLook.tick(client, config, false);
            return;
        }

        // ================================================================
        // 附加功能「自动打怪」（高风险，默认关闭）
        // 排在「吃」和「安全停机」之后、「收割」之前：
        //   - 已经因低血量 / 玩家靠近 / 背包满而停手时，paused 为真，这里自然不接管，
        //     不会出现「说停手了却还在打怪」的矛盾；
        //   - 它一旦接管，这一 tick 的寻路与收割都让路（见下面的 walkPaused 与优先级 2），
        //     但<b>不丢弃</b>手上的路线 —— 目标清掉之后自动接着收。
        // ================================================================
        // 纯拾取模式下不打怪：这个模式的语义就是「只捡东西」，其余动作（含战斗）一律停。
        boolean fightingActive = farming
                && config.autoAttack
                && !paused
                // 吃 > 打怪：饿到要进食时先让 AutoEater 把这口饭吃完，别一边按着使用键啃、
                // 一边挥刀（两条路会同时跑，看着也不像人）。
                && !AutoEater.isHandlingHunger()
                && client.screen == null
                && !freecamPaused
                && KillAura.tick(client, config);

        // 玩家打开任意界面（背包、聊天、ESC……）、或者已经躺下了的时候立刻挂起，
        // 否则角色会在你翻背包的时候自顾自走开。危险、背包满、战斗也走同一条「暂停」通道。
        boolean walkPaused = client.screen != null
                || !client.player.isAlive()
                || freecamPaused
                || paused
                || fightingActive
                || hitReacting;
        PathWalker.setPaused(walkPaused);

        // 附加功能「空闲时环顾四周」：只有在「没在动、没在收、没在补给、也没因危险停机」时，
        // 才让视角慢慢游走。危险停机时不环顾 —— 那时候「装死」比「东张西望」更安全；
        // 自由视角下也不环顾（摄像机和身体是分开的，转头只会让人看着别扭）。
        boolean viewBusy = !walkPaused && (PathWalker.isActive() || CropHarvester.isBusy());
        IdleLook.tick(client, config, working
                && !stopForFull
                && safety == null
                // 「危险反应延迟」还没数够时 safety 仍是 null，但危险已经在逼近 ——
                // 这时候别悠闲地东张西望（pendingSafety 有值就说明正有一桩危险挂着）。
                && pendingSafety == null
                && !offSchedule
                && !fightingActive
                && !hitReacting
                && client.screen == null
                && !freecamPaused
                && !viewBusy);

        // ================================================================
        // 优先级 2 —— 补给 / 走 / 收
        // 战斗 / 受击反制接管时不走这一段：人在回头或挥刀，走路和收割都让路
        // ================================================================
        if (!paused && !fightingActive && !hitReacting) {
            // 「骨粉告罄」：田里还有没熟的目标等着催熟，可背包 + 快捷栏一点骨粉都不剩了 ——
            // 这时别再顺着当前路线空跑（后面全是撒不了骨粉的催熟目标），<b>中断它</b>，
            // 先去补给点取骨粉，取到再回来接着催熟。
            // 授权来自「耗材自给」开关（它才是「自动去补给点取耗材」的那道闸）。
            boolean boneMealRunDry = farming
                    && config.boneMealBoost
                    && config.autoFetchSupplies
                    && supplyPhase == SupplyPhase.NONE
                    && supplyCooldown == 0
                    && !boneMealTargets.isEmpty()
                    && InventoryScanner.findBoneMealSlotDeep(client.player) < 0;
            // 还要「手上没在干半步活」才动手 —— 正握着某棵在收割 / 种植（或半途移动）时
            // 先把它做完，再中断去补给（执行完上一步再进行下一步）。
            if (boneMealRunDry
                    && !PathWalker.isHolding()
                    && !CropHarvester.isBusy()
                    && containerFor(config, SupplyPurpose.FETCH_TOOL) != null) {
                PathWalker.stop();
                CropHarvester.reset();
                // 这一趟就是去取骨粉：把补给需求点上，别让搬运层以为「什么都不用取」。
                suppliesWanted = true;
                toolWanted = false;
                if (startSupplyRun(client, config, false, false, SupplyPurpose.FETCH_TOOL)) {
                    announceEvent(client, "autofarm.notice.out_of_bone_meal");
                    return;
                }
            }

            if (supplyPhase != SupplyPhase.NONE) {
                if (!freecamPaused) {
                    runSupply(client, config);
                }
            } else if (PathWalker.isActive()) {
                if (!freecamPaused) {
                    if (shouldGoDeposit(client, config) && PathWalker.getHeldTarget() == null) {
                        // 背包快满了：放下手上这条路线先去存东西。
                        // 剩下的作物不会丢 —— 它们没进 handled，下一轮扫描还会被规划进来。
                        PathWalker.stop();
                    } else {
                        drive(client, config);
                    }
                }
            }
        }

        // ================================================================
        // 扫描：面板计数和高亮都靠它，所以走路时也照样扫
        // ================================================================
        long now = client.level.getGameTime();
        if (lastScanTick != Long.MIN_VALUE && now - lastScanTick < getEffectiveScanInterval()) {
            // 「掉落物优先」是一次性的判定结果，不是跨 tick 的状态：
            // 这一轮没扫，也就没有新的紧急名单，别拿上一轮的旧名单去规划。
            urgentPickupRun = false;
            return;
        }
        lastScanTick = now;

        // 「补种空耕地」只在真正要干活时才去扫（关着时连这块开销都省掉）。
        // 顺便注意：成熟作物的过滤条件挂在扫描里，被筛掉的作物根本不会进列表 ——
        // 于是世界里不给它画描边、面板计数也对得上。
        boolean wantFarmland = farming && config.fillEmptyFarmland;
        // 顺手记下「被作物筛选挡掉」的作物种类 —— 只在「只收能补种的」模式下用它：
        // 那种模式挡掉几乎都是因为「没种子」，得据此触发一次种子补给，
        // 否则会死锁（没种子 → 不收 → 永远不去取种子 → 永远没种子）。
        Set<Block> filteredBySeed = new HashSet<>();
        // 纯拾取模式下压根不扫作物：扫出来的目标一个都用不上，白花一遍方块遍历的钱。
        CropScanner.ScanResult scanned = farming
                ? CropScanner.scan(
                        client.level,
                        client.player.blockPosition(),
                        config.scanRadius,
                        CropScanner.DEFAULT_DOWN,
                        CropScanner.DEFAULT_UP,
                        state -> {
                            boolean ok = CropFilter.allowsHarvest(state.getBlock(), config, client.player);
                            if (!ok && config.cropFilterMode == AutoFarmConfig.FILTER_ONLY_REPLANTABLE) {
                                filteredBySeed.add(state.getBlock());
                            }
                            return ok;
                        },
                        wantFarmland
                )
                : new CropScanner.ScanResult(List.of(), List.of());
        matureCrops = new ArrayList<>(scanned.mature());
        // 「只收能补种的」把整片田都挡下来了，而且挡下来的是真作物 → 就是缺种子。
        // 记一笔补给需求，让下面的补给流程去种子容器取；取到之前先不收（保持模式本意）。
        if (!filteredBySeed.isEmpty() && matureCrops.isEmpty()) {
            refillWanted = true;
            missingSeeds.addAll(filteredBySeed);
            announceEvent(client, "autofarm.notice.out_of_seeds");
        }
        emptyFarmland = wantFarmland
                ? new ArrayList<>(scanned.emptyFarmland())
                : Collections.emptyList();

        // 附加功能「圈定工作区域」：圈外的目标一律不要 —— 既防跑丢，也防踩到别人的田。
        // 过滤放在扫描之后、规划之前，而不是塞进扫描器：扫描器只管「什么值得动」，
        // 「这块地是不是我的」是工作范围的事。顺带一个好处是圈外的作物不会被画上描边。
        removeIfOutsideWorkArea(matureCrops, config);
        removeIfOutsideWorkArea(emptyFarmland, config);

        // 附加功能「瓜茎识别 + 自定义方块」：找出瓜茎旁的瓜、以及玩家自定义的方块。
        // 它们不是作物（没有 age、判不出成熟），所以不并进 matureCrops，而是单独一个
        // 集合 —— 收割时走「挖掉不补种」那条路（见 drive 里的 startBreak）。
        extraTargets.clear();
        if (farming && config.melonHarvest) {
            extraTargets.addAll(CropScanner.findGourdBlocks(
                    client.level, client.player.blockPosition(),
                    config.scanRadius, CropScanner.DEFAULT_DOWN, CropScanner.DEFAULT_UP));
        }
        if (farming && config.customHarvestBlocks != null && !config.customHarvestBlocks.isEmpty()) {
            Set<String> customIds = new HashSet<>(config.customHarvestBlocks);
            extraTargets.addAll(CropScanner.findCustomBlocks(
                    client.level, client.player.blockPosition(),
                    config.scanRadius, CropScanner.DEFAULT_DOWN, CropScanner.DEFAULT_UP, customIds));
        }
        removeIfOutsideWorkArea(extraTargets, config);
        // 刷新只读镜像（渲染层每帧要用），一次扫描只复制这一回
        extraTargetsList = List.copyOf(extraTargets);

        // 附加功能「骨粉催熟」：把田里还没熟、但已经种下的作物也找出来。
        // 关着时整块跳过 —— 成熟作物表里不会有它们，也不会有任何额外开销。
        boneMealTargets.clear();
        if (farming && config.boneMealBoost) {
            boneMealTargets.addAll(CropScanner.findImmatureCrops(
                    client.level, client.player.blockPosition(),
                    config.scanRadius, CropScanner.DEFAULT_DOWN, CropScanner.DEFAULT_UP));
            removeIfOutsideWorkArea(boneMealTargets, config);
            // 作物筛选的白名单 / 黑名单同样约束催熟 —— 名单里排除掉的作物不该被撒骨粉
            // （否则「白白催熟一个永远不收割的目标」）。「只收能补种的」模式不适用：
            // 它管的是收割，催熟本身不消耗种子。
            if (config.cropFilterMode == AutoFarmConfig.FILTER_WHITELIST
                    || config.cropFilterMode == AutoFarmConfig.FILTER_BLACKLIST) {
                boolean whitelist = config.cropFilterMode == AutoFarmConfig.FILTER_WHITELIST;
                boneMealTargets.removeIf(pos -> whitelist
                        != CropFilter.contains(config, client.level.getBlockState(pos).getBlock()));
            }
        }

        // 附加功能「优先拾取掉落物」：顺手看看田里有没有掉在外面、还没进包的作物掉落物。
        // 得到的是「在哪一格」，下一步会排进路线最前面。
        pickupTargets.clear();
        pickupAges.clear();
        // 「优先拾取掉落物」是收菜之余顺手捡；「纯拾取模式」是别的都不干、专门捡。
        // 两者共用同一套扫描与路线，谁开着都进这一块。
        if (config.pickupDroppedItems || pickupOnly) {
            collectPickupTargets(client, config, pickupTargets);
            // 掉落物若和作物 / 空耕地 / 额外 / 骨粉目标落在同一格，从掉落物集合里剔除 ——
            // 那格按「作物」处理（收割时顺手把脚下掉落物吸走）。否则掉落物分支会在 drive 里
            // 抢走收割：角色被精确走到作物格上、只「捡」不「收」，作物漏收还进 handled，
            // 下一轮就永久卡在「扫描中」。这里把 pickupTargets 本身清干净，setExactTargets
            // 和 drive 的「是不是掉落物」判断才不会误伤作物格。
            pickupTargets.removeAll(matureCrops);
            pickupTargets.removeAll(emptyFarmland);
            pickupTargets.removeAll(extraTargets);
            pickupTargets.removeAll(boneMealTargets);
        }
        // 刷新掉落物的只读镜像（渲染层每帧要拿它画框），一次扫描只复制这一回。
        // 放在剔除之后：描边画的就应该是「这一轮真会去捡的那些」，不含被作物抢走同格的。
        pickupTargetsList = List.copyOf(pickupTargets);

        // ================================================================
        // 附加功能「掉落物优先」：地上有掉落物躺够 pickupPrioritySeconds 秒还没进包
        //   → 中断手上的路线，专门为它跑一趟（urgentPickupRun 会让后面的规划只排掉落物）。
        //
        // 排在扫描之后（得先知道哪有掉落物）、规划之前（它决定这一轮排什么）。
        // 三条「不抢」的规矩和补给 / 骨粉那两条中断一致：
        //   - 正握着某个目标干活（isHolding / isBusy）→ 先把这半步做完；
        //   - 正在补给路上 → 那趟是「有意的出圈」，先跑完；
        //   - 停机 / 受击 / 打怪 → 这些优先级本来就在它前面。
        // ================================================================
        urgentPickupRun = !paused
                && !hitReacting
                && !fightingActive
                && supplyPhase == SupplyPhase.NONE
                && !PathWalker.isHolding()
                && !CropHarvester.isBusy()
                && collectUrgentPickups(client, config);
        if (urgentPickupRun) {
            // 中断手上的路线：把「正在走的那条」放掉，下一轮只规划掉落物。
            // 没进 handled 的作物不会丢 —— 它们还是成熟作物，捡完这一趟自然会重排。
            PathWalker.stop();
            CropHarvester.reset();
        }

        // 让「已处理」集合跟着扫描结果瘦身，避免玩家在同一片区域待久了无限膨胀。
        // 收掉的作物会变成空气、补种后变成幼苗，两者都不再是「成熟作物」，
        // 所以它们会自然地从 handled 里掉出去。
        // 掉落物也算「这一轮还在的目标」：如果某件东西够不到、一直留在地上，
        // 把它一起保留下来，就不会每一轮都重新去试同一件捡不到的东西。
        if (!handled.isEmpty()) {
            Set<BlockPos> current = new HashSet<>(matureCrops);
            current.addAll(emptyFarmland);
            current.addAll(pickupTargets);
            current.addAll(extraTargets);
            current.addAll(boneMealTargets);
            handled.retainAll(current);
        }
        // 兜底：成熟作物 / 空耕地若还躺在「已处理」里，说明上一次处理没成功（挖不动、
        // 被掉落物抢走、补种没种子……）—— 它明明还是目标却进不了路线，角色就会卡住。
        // 这里把所有「仍是成熟作物 / 仍是空耕地」的位置从 handled 撤回来，让下一轮重试。
        if (!matureCrops.isEmpty()) {
            handled.removeAll(matureCrops);
        }
        if (!emptyFarmland.isEmpty()) {
            handled.removeAll(emptyFarmland);
        }

        if (paused) {
            // 背包满 / 安全保护：这一 tick 只扫描、不动角色。
            // 状态已经在上面设成「已暂停」了，别被后面的规划覆盖掉。
            state = FarmState.PAUSED;
            return;
        }

        if (hitReacting) {
            // 附加功能「受击反制」：挨打之后的这几秒只「停手 + 回头」，不规划、不收割。
            state = FarmState.HIT_REACT;
            return;
        }

        if (fightingActive) {
            // 附加功能「自动打怪」：这一 tick 手在挥刀，不规划新路线、也不推进收割。
            // 手上那条路线没有丢 —— 只是被 PathWalker 挂着，目标清掉后自动接着走。
            state = FarmState.ATTACKING;
            return;
        }

        if (supplyPhase != SupplyPhase.NONE || PathWalker.isActive()) {
            // 补给 / 走路 / 收割的状态是上面那一段设好的，别覆盖掉
            return;
        }

        // ---- 优先级 3：手上没路线了，看看要不要去补给点，或者新规划一条 ----

        // 附加功能「资源自给」：先算清楚这一趟除了种子之外还要顺带取什么。
        // 两项都默认关着，所以关着的时候这两行是纯粹的常数判断，不会多花什么。
        // 「取耗材 / 取工具」是为农活服务的（撒骨粉、挖作物），纯拾取模式下一律不跑 ——
        // 那种模式下唯一还值得跑的补给是「背包满了去存东西」（见下面的 3a）。
        suppliesWanted = farming && config.autoFetchSupplies && config.boneMealBoost
                && InventoryScanner.countBoneMeal(client.player) < config.boneMealThreshold;
        toolWanted = farming && config.autoFetchTool
                && InventoryScanner.countUsableTools(client.player) == 0;

        // 3a. 背包快满了 → 先存作物产物（纯拾取模式同样需要：东西捡进来也要有地方放）
        if (shouldGoDeposit(client, config)
                && startSupplyRun(client, config, true, refillWanted, SupplyPurpose.DEPOSIT_CROP)) {
            return;
        }

        // 3a2. 杂物临时存储（可选）：背包满了 + 杂物容器启用 + 真有杂物 → 单独存一趟
        if (config.outJunkEnabled && config.hasJunkOut()
                && supplyCooldown == 0
                && isNearlyFull(client, INVENTORY_RESERVE)
                && hasDepositableJunk(client.player, config)
                && startSupplyRun(client, config, true, false, SupplyPurpose.DEPOSIT_JUNK)) {
            return;
        }

        // 3b. 缺种子 → 取种子（纯拾取模式不收也不种，用不上种子）
        boolean wantSeeds = farming && refillWanted && config.refillSeeds;
        if (wantSeeds && supplyCooldown == 0
                && containerFor(config, SupplyPurpose.REFILL_SEED) != null
                && startSupplyRun(client, config, false, true, SupplyPurpose.REFILL_SEED)) {
            return;
        }

        // 3c. 缺骨粉 / 没备用工具 → 取工具（和种子可能在不同容器，分开跑）
        if ((suppliesWanted || toolWanted) && supplyCooldown == 0
                && containerFor(config, SupplyPurpose.FETCH_TOOL) != null
                && startSupplyRun(client, config, false, false, SupplyPurpose.FETCH_TOOL)) {
            return;
        }

        // 附加功能「失败目标暂时拉黑」：先把已经到期的解禁掉，
        // 再把还在冷却里的目标跳过。全关时 failedUntil 恒为空，这两步都是空转。
        if (!failedUntil.isEmpty()) {
            failedUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
        }

        // 严格按步执行：如果角色正沿路线走向某个目标，而那个目标<b>仍然有效</b>，
        // 就不要用新路线打断它 —— 走完这一步、把它处理掉，再规划下一步。
        // 这样角色不会「走到一半又改道」，全程是「走一格 → 处理 → 再走一格」。
        // 上面所有更高优先级（吃 / 自救 / 危险 / 战斗 / 补给 / 骨粉告罄）都已提前 return，
        // 所以这里只保护「低优先级不互相打断」；高优先级照样能插入。
        if (PathWalker.isActive()) {
            BlockPos walking = PathWalker.getCurrentTarget();
            boolean stillValid = walking != null
                    && (matureCrops.contains(walking) || emptyFarmland.contains(walking)
                    || extraTargets.contains(walking) || boneMealTargets.contains(walking)
                    || pickupTargets.contains(walking));
            if (stillValid) {
                state = FarmState.MOVING; // 状态照旧
                return;
            }
        }

        List<BlockPos> pending = new ArrayList<>();

        // 掉落物不要和作物 / 空耕地 / 额外 / 骨粉目标撞在同一格：同一格既有作物又有掉落物时，
        // 按「作物」收割（收割时脚下掉落物会被顺手吸走），而不是当成掉落物站上去「捡」——
        // 否则掉落物分支会抢走收割，作物漏收还把它记进 handled，下一轮就卡在「扫描中」。
        Set<BlockPos> occupied = new HashSet<>();
        occupied.addAll(matureCrops);
        occupied.addAll(emptyFarmland);
        occupied.addAll(extraTargets);
        occupied.addAll(boneMealTargets);

        if (urgentPickupRun) {
            // ============================================================
            // 附加功能「掉落物优先」插队的这一趟：路线里<b>只放掉落物</b>。
            //
            // 地里的活也不是丢了 —— 它们还在 matureCrops / emptyFarmland 里等着，
            // 只是没进这一轮的 pending。捡完这一趟，下一轮扫描自然又把它们排进来。
            // 这就是「专门为掉落物跑一趟」：不为顺手，专门为它拐一趟路。
            // ============================================================
            for (BlockPos pos : urgentPickupTargets) {
                if (!handled.contains(pos)) {
                    pending.add(pos);
                }
            }
        } else {
            // 附加功能「优先拾取掉落物」：先把散在地上的作物掉落物排进路线（排在成熟作物前面）。
            // 它排在最后、只作「队列内的优先级」：掉落物不会自己长回去，还可能被水冲走、
            // 被怪撞飞，或者干脆因为存在太久而消失，所以先把它捡回来，再谈地里的活。
            for (BlockPos pos : pickupTargets) {
                if (!occupied.contains(pos) && !handled.contains(pos)) {
                    pending.add(pos);
                }
            }

            // 附加功能「骨粉催熟」：有未成熟作物 + 有骨粉时，<b>优先于收集</b>执行。
            // 催熟是连续性的 —— 先把能催的都催完（或骨粉彻底用完），再回头收割。
            // 手边没骨粉时干脆不排（深搜整个背包判断，主背包深处有骨粉也算「有」，
            // 撒的时候再换到快捷栏）。
            if (config.boneMealBoost && InventoryScanner.findBoneMealSlotDeep(client.player) >= 0) {
                for (BlockPos pos : boneMealTargets) {
                    addPending(pending, pos);
                }
            }

            for (BlockPos pos : matureCrops) {
                addPending(pending, pos);
            }

            // 附加功能「瓜茎识别 + 自定义方块」：额外目标（瓜 / 自定义方块）也排进同一条路线。
            // 排在成熟作物之后 —— 先把正儿八经的庄稼收完，再挖瓜和自定义方块。
            for (BlockPos pos : extraTargets) {
                addPending(pending, pos);
            }

            // 空耕地也排进同一条路线（如果开着补种空地）。
            // 一个前提：手边得真有种子 —— 一粒都没有的话，走过去也只是站着报「没种子」，
            // 白跑一趟。那种情况直接记账「该去补给点取种子」，让上面那条补给流程接手。
            if (config.fillEmptyFarmland && !emptyFarmland.isEmpty()) {
                if (InventoryScanner.countSeeds(client.player, null) > 0) {
                    for (BlockPos pos : emptyFarmland) {
                        addPending(pending, pos);
                    }
                } else {
                    refillWanted = true;
                    announceEvent(client, "autofarm.notice.plant_no_seed");
                }
            }
        }

        // 正在收割 / 捡掉落物（PathWalker 已经走到目标跟前、握着它等任务大脑处理）时，
        // 别用新路线打断它 —— PathWalker.start() 会把 holding 和 resolved 一起重置，
        // 正在收割的作物停在半路，和新的当前目标错位，最终卡死。等它干完放行，
        // 下一轮扫描自然重新规划。
        if (PathWalker.isHolding()) {
            return;
        }

        if (pending.isEmpty()) {
            // 这一轮都收完了。等新一批作物成熟，或者走回还没扫过的区域，才会重新规划。
            state = matureCrops.isEmpty() ? FarmState.IDLE : FarmState.SCANNING;
            return;
        }

        // 防踢模式：偶尔「偷个懒」不做 2-opt 优化 —— 真人规划路线本来就不会次次都是最优解，
        // 每次都被算出一条数学上最短的走法，反而比走岔一点更像机器
        boolean optimize = config.optimizePath && !Humanizer.skipOptimize();
        PathPlanner.Plan plan = PathPlanner.plan(
                client.player.blockPosition(),
                pending,
                optimize
        );
        // 掉落物是「要碰到才算数」的目标：必须走到它那一格上原版才会把它吸进背包，
        // 不能像作物那样站在旁边看着。把这类格子单独告诉执行器。
        PathWalker.setExactTargets(pickupTargets);
        PathWalker.start(plan);
        state = FarmState.MOVING;
    }

    /**
     * 把附近「掉在地上、还没进包」的农作物掉落物找出来（附加功能，默认关闭）。
     *
     * <p>三条判据，缺一不可：
     * <ol>
     *   <li>它得是<b>农作物产物</b>（见 {@link CropRegistry#isCropDrop}）——
     *       不然会把玩家自己丢的杂物也一股脑捡回来；</li>
     *   <li>它得在地上待够 {@link AutoFarmConfig#pickupDelaySeconds} 秒还没进背包 ——
     *       刚掉出来的那一小会儿，原版正把它往玩家身上吸，这时候不该算「漏捡」；</li>
     *   <li>它得离玩家有点距离 —— 就在脚边的，走过去顺手就捡了，不必当成一个专门的目标。</li>
     * </ol>
     */
    private static void collectPickupTargets(Minecraft client, AutoFarmConfig config,
                                             Set<BlockPos> out) {
        long minAge = Math.max(1, config.pickupDelaySeconds) * 20L; // 秒 → tick
        double ignoreSq = PICKUP_IGNORE_DISTANCE * PICKUP_IGNORE_DISTANCE;
        // 半径用「拾取范围」而不是作物扫描半径：掉落物是顺手能捡的东西，尺度跟作物不一样
        AABB box = client.player.getBoundingBox().inflate(config.pickupRadius);
        for (ItemEntity item : client.level.getEntitiesOfClass(ItemEntity.class, box,
                e -> e.isAlive()
                        && e.getAge() >= minAge
                        && e.distanceToSqr(client.player) > ignoreSq
                        // 默认只捡「像作物产物」的；开了「什么都捡」则一律收
                        && (config.pickupAllItems || CropRegistry.isCropDrop(e.getItem()))
                        && config.insideWorkArea(e.getX(), e.getY(), e.getZ()))) {
            BlockPos pos = item.blockPosition().immutable();
            out.add(pos);
            // 顺手把「这东西在地上躺了多久」记下来：掉落物优先（插队）的判定要读它，
            // 免得为了判个年龄再把这些实体遍历第二遍。同一格有多个时取最久的那个。
            pickupAges.merge(pos, (long) item.getAge(), Math::max);
        }
    }

    /**
     * 附加功能「掉落物优先」：挑出这一轮「值得插队」的掉落物。
     *
     * <p>判据只有一条 —— 它在{@link #pickupAges 地上躺的时间}达到了
     * {@link AutoFarmConfig#pickupPrioritySeconds} 秒。这类东西最可能在你「等会儿再捡」的
     * 工夫里被水冲走、被怪撞飞、或者直接到点消失，所以值得打断手上的活先跑一趟。
     *
     * <p>刚插队去捡过、结果没捡到的位置会进 {@link #pickupUrgentBlocked 冷却}，
     * 免得一件卡在够不着角落的掉落物把整场作业永久拖住。
     *
     * @return true 表示这一轮该中断手上的路线，专门去捡这些东西
     */
    private static boolean collectUrgentPickups(Minecraft client, AutoFarmConfig config) {
        urgentPickupTargets.clear();
        if (!config.pickupPriorityEnabled || pickupTargets.isEmpty() || pickupAges.isEmpty()) {
            return false;
        }
        long now = client.level.getGameTime();
        if (!pickupUrgentBlocked.isEmpty()) {
            pickupUrgentBlocked.entrySet().removeIf(entry -> entry.getValue() <= now);
        }
        long minAge = Math.max(1, config.pickupPrioritySeconds) * 20L; // 秒 → tick
        for (Map.Entry<BlockPos, Long> entry : pickupAges.entrySet()) {
            BlockPos pos = entry.getKey();
            if (entry.getValue() < minAge) {
                continue;
            }
            if (!pickupTargets.contains(pos) || handled.contains(pos)) {
                continue;
            }
            if (pickupUrgentBlocked.containsKey(pos)) {
                continue;
            }
            urgentPickupTargets.add(pos);
        }
        return !urgentPickupTargets.isEmpty();
    }

    /** 把工作区域外的目标从集合里剔掉（功能没开 / 没设坐标时原样保留，不影响既有行为） */
    private static void removeIfOutsideWorkArea(Collection<BlockPos> targets, AutoFarmConfig config) {
        if (targets.isEmpty() || !config.workAreaEnabled || !config.hasWorkArea()) {
            return;
        }
        targets.removeIf(pos -> !config.insideWorkArea(
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5));
    }

    /** 把一个目标排进本轮待办路线 —— 统一的「已处理 / 已在冷却」过滤 */
    private static void addPending(List<BlockPos> pending, BlockPos pos) {
        if (!handled.contains(pos) && !failedUntil.containsKey(pos)) {
            pending.add(pos);
        }
    }

    /** 这一格上现在还有没有「作物掉落物」实体 —— 用来判断东西是不是真的进包了 */
    private static boolean hasCropDropAt(Minecraft client, BlockPos pos) {
        return hasDropAt(client, pos, false);
    }

    /**
     * 这一格上现在还有没有掉落物实体。
     *
     * @param anyItem 为真时不限种类 —— 「什么都捡」/「纯拾取模式」下捡的可能根本不是作物，
     *                只认作物会把「石头还躺在那儿」误判成「已经捡到手了」
     */
    private static boolean hasDropAt(Minecraft client, BlockPos pos, boolean anyItem) {
        if (client == null || client.level == null || pos == null) {
            return false;
        }
        AABB box = new AABB(pos).inflate(0.5);
        return !client.level.getEntitiesOfClass(ItemEntity.class, box,
                e -> e.isAlive() && (anyItem || CropRegistry.isCropDrop(e.getItem()))).isEmpty();
    }

    /**
     * 走一步，或者干一步活。
     *
     * <p>这里是两个小状态机：
     * <ol>
     *   <li>人已经站到目标跟前（{@link PathWalker#isHolding()}）→ 交给收割机挖掉 + 补种；</li>
     *   <li>否则就照着 A* 算出的路线继续往前走。</li>
     * </ol>
     *
     * <p>「到站就停下来干活」这件事就是在这里落地的：{@code PathWalker}
     * 走到目标不再直接 index++ 去下一个，而是把目标交出来，活干完才放行。
     */
    private static void drive(Minecraft client, AutoFarmConfig config) {
        // 附加功能「拟人化节奏」：刚干完一棵，先愣一下再去下一棵。
        // 停顿期间不推进路线、也不挥镐子 —— 真人也不会以恒定的节拍连续操作。
        if (humanizeDelay > 0) {
            humanizeDelay--;
            return;
        }

        BlockPos held = PathWalker.getHeldTarget();

        // 还没到站（没在等收割）→ 只管走路
        if (held == null) {
            PathWalker.tick(client);
            drainResolved();
            return;
        }

        // 附加功能「优先拾取掉落物」：这一趟是去捡地上的东西。
        // 到了那一格不用挖也不用种，站上去等原版把它吸进背包即可 ——
        // 但要停一小会儿再放行，否则刚站到就转身走开，东西还留在原地。
        if (pickupTargets.contains(held)) {
            if (!held.equals(pickupWaitPos)) {
                pickupWaitPos = held;
                pickupWaitTicks = PICKUP_WAIT_TICKS;
                pickupRetryTicks = 0;
            }
            if (pickupWaitTicks > 0) {
                pickupWaitTicks--;
                state = FarmState.PICKUP;
                return;
            }
            // 站够了，看看东西是不是真的进包了。没进包（被水冲走、卡在角落、刚被怪撞飞）
            // 就先别急着放行，再站一小会儿；但设个上限，别在同一格无限耗下去。
            // 「什么都捡」/「纯拾取模式」下捡的可能不是作物，判定也要跟着放宽。
            boolean stillThere = hasDropAt(client, held, config.pickupAllItems);
            if (stillThere && ++pickupRetryTicks < PICKUP_MAX_RETRIES) {
                pickupWaitTicks = PICKUP_WAIT_TICKS;
                state = FarmState.PICKUP;
                return;
            }
            if (stillThere) {
                // 站了这么多轮还是没吸进来 —— 这件东西多半够不着（卡在栅栏缝里、泡在水里被推来推去）。
                // 记一笔冷却：接下来这段时间它不再算「值得插队」，免得它把整场作业永久拖住。
                pickupUrgentBlocked.put(held.immutable(),
                        client.level.getGameTime() + PICKUP_URGENT_COOLDOWN_TICKS);
            }
            pickupWaitPos = null;
            pickupRetryTicks = 0;
            // 只有真捡到手才记一笔 —— 「站够了但东西还在」不该算进拾取统计
            if (!stillThere) {
                StatsManager.get().recordPickup();
            }
            PathWalker.releaseTarget();
            drainResolved();
            // 掉落物会消失、会移动，位置不该被当成「已处理」长期锁死 —— 一旦锁进
            // handled，下一轮扫描若它还在原地，就会因为「已处理」而永远不再去捡，角色卡在
            // 「扫描中」不动。所以这里把它从 handled 里拿掉，下一轮重新评估。
            handled.remove(held);
            state = PathWalker.isActive() ? FarmState.MOVING : FarmState.IDLE;
            return;
        }

        // 两道闸都关掉了 → 放行，别把角色吊在一个点上
        // （纯拾取模式开着时也算「在工作」，见 tick 开头的 working）
        if (!config.enabled && !config.pickupOnlyMode) {
            PathWalker.releaseTarget();
            drainResolved();
            return;
        }

        // 你开着界面（背包/聊天/ESC）→ 原地等你。
        // 这里刻意不「放行」：万一你只是随手翻下背包，回头这一棵还得接着收。
        if (client.screen != null) {
            return;
        }

        // 吃 > 收：正在进食、或者刚吃完还没缓过来（马上还要再吃一口）就先别挥镐子
        if (AutoEater.isHandlingHunger()) {
            state = FarmState.HARVESTING;
            return;
        }

        if (!CropHarvester.isBusy()) {
            // 附加功能「工具耐久换新」：手上的工具剩余耐久见底了，先从背包换一把好的。
            // 换手要发一个换位包，所以这一 tick 先不动手，下一 tick 用新工具继续。
            if (config.autoReplaceTool
                    && InventoryScanner.replaceWornTool(client, client.player, config.toolDurabilityPercent)) {
                state = FarmState.HARVESTING;
                return;
            }
            // 防踢模式：动手之前先「打量一下」这一棵。每个目标只犹豫一次 ——
            // 犹豫过就记下来，否则站在同一棵前面来回打量，永远不开工。
            boolean fresh = !held.equals(hesitatedAt);
            hesitatedAt = held;
            // 「无间隔连发」时也不打量 —— 那是拟人化的停顿，和「不要间隔」是相反方向
            if (!config.instantActions && fresh && Humanizer.rollHesitate()) {
                humanizeDelay = Math.max(humanizeDelay, Humanizer.hesitateTicks());
                state = FarmState.HARVESTING;
                return;
            }

            supplyNotice = null; // 新的活开始了，上一次补给的那句话可以撤掉
            // 额外目标（瓜 / 自定义方块）不是作物，走「挖掉不补种」；
            // 骨粉目标不是收成，撒完就撒完了 —— 三者各有各的入口
            // 顺序很关键：成熟作物优先于「额外目标」。因为自定义方块名单里若不小心加进了
            // 普通作物，那一格会同时在 matureCrops 和 extraTargets 里 —— 先判成熟作物
            // 才能走「正常收割 + 补种」，否则会被当成「挖掉不补种」，补种被静默关掉。
            if (boneMealTargets.contains(held)) {
                CropHarvester.startBoneMeal(client.level, held, config);
            } else if (extraTargets.contains(held) && !matureCrops.contains(held)) {
                CropHarvester.startBreak(client.level, held);
            } else {
                CropHarvester.start(client.level, held, config);
            }
        }
        boolean finished = CropHarvester.tick(client, config);
        state = CropHarvester.isReplanting() ? FarmState.REPLANTING : FarmState.HARVESTING;

        if (finished) {
            // 先记下这一格是不是「骨粉目标」—— 它可能要多撒几次才熟，处理完得允许重排
            boolean boneMealStop = boneMealTargets.contains(held);
            // 统计：真的收到了才算数（判定「这格不该动」时 wasHarvested 是 false）
            if (CropHarvester.wasHarvested()) {
                harvestedCount++;
                StatsManager.get().recordHarvest();
            }
            // 空地补种单独记一笔 —— 它没「收」到任何东西，但确实干了活
            if (CropHarvester.wasPlanted()) {
                plantedCount++;
                StatsManager.get().recordPlant();
            }

            // 没种子补种 → 记下缺的是哪种，等这条路线走完就去补给点取
            if (CropHarvester.needsSeedRefill()) {
                // 第一次发现没种子时说一声 —— 这之前你可能一直以为它在正常干活
                if (!refillWanted) {
                    announceEvent(client, "autofarm.notice.out_of_seeds");
                }
                refillWanted = true;
                Block crop = CropHarvester.getCropBlock();
                if (crop != null) {
                    missingSeeds.add(crop);
                }
            }

            // 立刻把它从目标列表里摘掉：否则在下一轮扫描之前，
            // 那格已经变成空气/幼苗了，世界里却还画着一个描边框。
            if (!matureCrops.isEmpty()) {
                matureCrops.remove(held);
            }
            if (!emptyFarmland.isEmpty()) {
                emptyFarmland.remove(held);
            }
            if (!extraTargets.isEmpty()) {
                extraTargets.remove(held);
            }
            if (!boneMealTargets.isEmpty()) {
                boneMealTargets.remove(held);
            }
            PathWalker.releaseTarget();
            drainResolved();

            // 骨粉目标：撒一次未必就熟（原版一次只随机推进 0~2 段），必须允许下一轮
            // 重新排进催熟路线。否则它会带着「已处理」标记被永久跳过 —— 症状就是
            // 「催熟过一次之后，就算补了骨粉也不再继续催熟、人也不动了」。
            // 和掉落物同理，从 handled 撤回来重新评估。
            if (boneMealStop) {
                handled.remove(held);
            }

            // 附加功能「失败目标暂时拉黑」：这一棵没干成、而且给出了原因（挖不动、
            // 种不下去……）才拉黑。单纯「这一格已经不是成熟作物」不算失败 ——
            // 那是被别人收走了，重试也没意义。拉黑的做法是从 handled 里撤回来、
            // 记个到期时间，到点自然会重新排进路线，不用你手动关开一次。
            boolean failed = !CropHarvester.wasHarvested() && !CropHarvester.wasPlanted()
                    && CropHarvester.getNotice() != null;
            // 附加功能「异常日志」：没干成、又给了原因，就记一条 —— 不用管「失败拉黑」开没开，
            // 日志和拉黑是两回事：拉黑影响重试，日志只负责「记下来给人看」
            if (failed && held != null) {
                StatsManager.get().logIssue("harvest failed (" + CropHarvester.getNotice()
                        + ") at " + held.getX() + "," + held.getY() + "," + held.getZ());
                notify(client, config, "issue");
                // 失败 = 这一格没干成，绝不能留在 handled 里 —— 否则它还是成熟作物、
                // 却因为「已处理」被永久跳过，角色就卡在「扫描中」再也动不了。
                // 所以无论「失败拉黑」开没开，都先从 handled 撤回来让下一轮重试；
                // 开了拉黑再额外记个到期时间（到点才重试，避免每轮都去挖挖不动的东西）。
                handled.remove(held);
                // 「材料不够」不算这一格的错：没骨粉 / 没种子时先把它放回队伍，
                // 别拉黑——补到货就该马上回来继续，而不是干等冷却结束。
                String failKey = CropHarvester.getNotice();
                boolean materialShortage = "autofarm.notice.no_bone_meal".equals(failKey)
                        || "autofarm.notice.no_seed".equals(failKey)
                        || "autofarm.notice.plant_no_seed".equals(failKey);
                if (config.failureCooldown && !materialShortage) {
                    failedUntil.put(held.immutable(),
                            client.level.getGameTime() + config.failureCooldownSeconds * 20L);
                }
            }

            // 附加功能「拟人化节奏」：这一次真的动了手（收了或种了），就歇一小会儿。
            // 「无间隔连发」开着时不歇 —— 用户要的就是连续作业。
            if (!config.instantActions && config.humanizeTiming
                    && (CropHarvester.wasHarvested() || CropHarvester.wasPlanted())) {
                humanizeDelay = 2 + ThreadLocalRandom.current().nextInt(4); // 2~5 tick
            }

            state = PathWalker.isActive() ? FarmState.MOVING : FarmState.IDLE;
        }
    }

    /** 把 PathWalker 结算掉的点收进「已处理」 */
    private static void drainResolved() {
        BlockPos pos;
        while ((pos = PathWalker.pollResolved()) != null) {
            handled.add(pos);
        }
    }

    // ================= 补给点往返 =================

    /** 「背包快满了，而且真有东西可以存」→ 该跑一趟 */
    private static boolean shouldGoDeposit(Minecraft client, AutoFarmConfig config) {
        return config.depositWhenFull
                && containerFor(config, SupplyPurpose.DEPOSIT_CROP) != null
                && supplyCooldown == 0
                && isNearlyFull(client, INVENTORY_RESERVE)
                && InventoryScanner.hasDepositableItems(client.player, config);
    }

    /**
     * 「背包满了，可是没地方存」→ 该停手了。
     *
     * <p>判定里那个 {@link ContainerCompat#findContainer} 就是「有没有存储设备」：
     * 它按「方块实体实现了 Inventory」来认，所以箱子、木桶、陷阱箱、潜影盒，
     * 以及<b>任何照惯例实现的存储模组</b>都算数；只有「压根没设补给点」或者
     * 「设的那一格真的不是容器」才会返回 true。
     *
     * <p>注意它同时要求「真有东西可以存」：背包只剩 1 个空格、但里面全是种子和食物时，
     * 其实一格都存不掉，这时候停手反而莫名其妙。
     */
    private static boolean shouldStopForFullInventory(Minecraft client, AutoFarmConfig config) {
        if (!isNearlyFull(client, INVENTORY_RESERVE)) {
            return false;
        }
        if (!InventoryScanner.hasDepositableItems(client.player, config)) {
            return false;
        }
        return ContainerCompat.findContainer(client.level,
                containerFor(config, SupplyPurpose.DEPOSIT_CROP)) == null;
    }

    /**
     * 背包里有没有「该进杂物容器」的东西。
     *
     * <p>判定要和搬运时完全一致（见 {@code ContainerTransfer.nextDepositSlot}）：
     * 只数<b>主背包</b>里、<b>非作物相关</b>、且「存放策略」允许存（{@code budget >= 0}）的物品。
     * 少任何一条都会「白跑一趟」或「把快捷栏里保命的食物搬走」。
     */
    private static boolean hasDepositableJunk(LocalPlayer player, AutoFarmConfig config) {
        if (player == null) {
            return false;
        }
        Inventory inv = player.getInventory();
        int hotbar = Inventory.getSelectionSize();
        Map<Item, Integer> kept = new HashMap<>();
        for (int i = hotbar; i < InventoryScanner.INVENTORY_SLOTS; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            ItemCategory category = ItemCategory.classify(stack);
            if (category == null || category.isCropRelated()) {
                continue;
            }
            int budget = InventoryScanner.depositKeepBudget(stack, config);
            if (budget < 0) {
                continue;
            }
            int already = kept.getOrDefault(stack.getItem(), 0);
            if (already >= budget) {
                return true;
            }
            kept.merge(stack.getItem(), 1, Integer::sum);
        }
        return false;
    }

    /**
     * 开始一趟补给点往返。
     *
     * <p>先找「箱子里能站人的那一格」，把它当成路线的唯一目标；
     * 人到了之后由 {@link #runSupply} 接手去开箱和搬东西。
     *
     * @return 有没有真的开始（没设补给点、附近没有容器、旁边站不住人都算失败）
     */
    private static boolean startSupplyRun(Minecraft client, AutoFarmConfig config,
                                          boolean deposit, boolean refill, SupplyPurpose purpose) {
        BlockPos wanted = containerFor(config, purpose);
        if (wanted == null) {
            return false;
        }
        // 补给点那一格（或者它附近）得真有个容器。判定按能力来 —— 见 ContainerCompat，
        // 箱子、木桶、模组容器一律认得出来。
        BlockPos container = ContainerCompat.findContainer(client.level, wanted);
        if (container == null) {
            supplyNotice = "autofarm.notice.supply_not_container";
            return false;
        }
        BlockPos stand = BlockNavigator.findViewSpot(client.level, container, client.player.blockPosition());
        if (stand == null) {
            supplyNotice = "autofarm.notice.supply_no_spot";
            return false;
        }

        supplyContainer = container;
        supplyDeposit = deposit;
        supplyRefill = refill;
        supplyDepositJunk = purpose == SupplyPurpose.DEPOSIT_JUNK;
        supplyPurpose = purpose;
        supplyPhase = SupplyPhase.GOING;
        state = FarmState.TO_SUPPLY;

        // 已经站在旁边了：不用再走，直接开箱
        if (BlockInteractor.isWithinReach(client.player, container, 4.0)) {
            beginTransfer(config);
            return true;
        }

        PathPlanner.Plan plan = PathPlanner.plan(
                client.player.blockPosition(), List.of(stand), false);
        PathWalker.start(plan);
        if (!PathWalker.isActive()) {
            // 规划器会把「和起点重合的目标」去掉，于是在这里就是空路线 —— 说明人已经在落点上了
            beginTransfer(config);
        }
        return true;
    }

    /** 走到补给点旁边了，开始开箱存取 */
    private static void beginTransfer(AutoFarmConfig config) {
        PathWalker.stop();
        BlockPos container = currentContainer(config);
        if (container == null) {
            cancelSupply("autofarm.notice.supply_not_container");
            return;
        }
        supplyPhase = SupplyPhase.TRANSFERRING;
        state = FarmState.TRANSFERRING;
        ContainerTransfer.begin(container, supplyDeposit, supplyRefill, missingSeeds,
                suppliesWanted, toolWanted, supplyDepositJunk);
    }

    /**
     * 这一趟要开的那个容器。
     *
     * <p>出发时已经认过一次并记在 {@link #supplyContainer} 里了，这里优先用它；
     * 万一丢了（比如中途重进世界）就现场再认一次。
     */
    private static BlockPos currentContainer(AutoFarmConfig config) {
        if (supplyContainer != null) {
            return supplyContainer;
        }
        Minecraft client = Minecraft.getInstance();
        return client == null ? null
                : ContainerCompat.findContainer(client.level, containerFor(config, supplyPurpose));
    }

    /** 每 tick 推进一步：要么在走过去，要么在开箱搬东西 */
    private static void runSupply(Minecraft client, AutoFarmConfig config) {
        BlockPos container = currentContainer(config);
        if (container == null) {
            cancelSupply("autofarm.notice.supply_unreachable");
            return;
        }

        if (supplyPhase == SupplyPhase.GOING) {
            state = FarmState.TO_SUPPLY;

            // 到站了（路线走完、PathWalker 把目标交出来）→ 开箱
            if (PathWalker.getHeldTarget() != null) {
                PathWalker.releaseTarget();
                beginTransfer(config);
                return;
            }
            if (PathWalker.isActive()) {
                PathWalker.tick(client);
                drainResolved();
                return;
            }
            // 路线没了：要么人其实已经站在旁边，要么这次真的去不了
            if (BlockInteractor.isWithinReach(client.player, container, 4.0)) {
                beginTransfer(config);
            } else {
                cancelSupply("autofarm.notice.supply_unreachable");
            }
            return;
        }

        // ---- 开箱存取 ----
        state = FarmState.TRANSFERRING;
        boolean didRefill = supplyRefill;
        boolean didDeposit = supplyDeposit;
        boolean didTakeMore = suppliesWanted || toolWanted;

        ContainerTransfer.Result result = ContainerTransfer.tick(client);
        if (result == ContainerTransfer.Result.RUNNING) {
            return;
        }

        if (result == ContainerTransfer.Result.FAILED) {
            supplyNotice = ContainerTransfer.getFailure();
        } else if (didRefill) {
            supplyNotice = ContainerTransfer.getTakenStacks() > 0
                    ? "autofarm.notice.supply_refilled"
                    : "autofarm.notice.supply_no_seed";
        } else if (didDeposit) {
            supplyNotice = "autofarm.notice.supply_deposited";
        }
        // 补给点的结果是「一趟才有一次」的事，直接播报一条就够了
        announceEvent(client, supplyNotice);

        // 附加功能「异常日志 / 统计」：这一趟办成了记一笔补给；办砸了记一条异常
        if (result == ContainerTransfer.Result.FAILED && supplyNotice != null) {
            StatsManager.get().logIssue("supply failed: " + supplyNotice);
            notify(client, config, "issue");
        } else if (ContainerTransfer.getTakenStacks() > 0
                || ContainerTransfer.getDepositedStacks() > 0) {
            StatsManager.get().recordSupplyRun();
        }

        // 「要办的事一件都没办成」（箱子满了、箱子里没种子/没骨粉/没工具）→ 歇一会儿再去，
        // 否则下一 tick 条件照样成立，它会立刻掉头再跑一趟，变成两个点之间横跳
        boolean achieved = result == ContainerTransfer.Result.DONE
                && (!didDeposit || ContainerTransfer.getDepositedStacks() > 0)
                && (!(didRefill || didTakeMore) || ContainerTransfer.getTakenStacks() > 0);
        supplyCooldown = achieved ? 0 : SUPPLY_RETRY_COOLDOWN_TICKS;

        finishSupply();
    }

    /** 一趟存取正常结束 */
    private static void finishSupply() {
        supplyPhase = SupplyPhase.NONE;
        supplyDeposit = false;
        supplyRefill = false;
        supplyDepositJunk = false;
        supplyPurpose = SupplyPurpose.DEPOSIT_CROP;
        suppliesWanted = false;
        toolWanted = false;
        refillWanted = false;
        missingSeeds.clear();
        supplyContainer = null;
        // 允许下一次补给再说一遍（不然「种子已取回」这种提示一局只会出现一次）
        lastEventAnnounce = null;
        lastScanTick = Long.MIN_VALUE; // 立刻重扫一遍，好接着干活
        state = FarmState.IDLE;
    }

    /** 这趟去不成（关开关、没设补给点、走不到） */
    private static void cancelSupply(String notice) {
        PathWalker.stop();
        ContainerTransfer.reset();
        supplyPhase = SupplyPhase.NONE;
        supplyDeposit = false;
        supplyRefill = false;
        supplyDepositJunk = false;
        supplyPurpose = SupplyPurpose.DEPOSIT_CROP;
        suppliesWanted = false;
        toolWanted = false;
        refillWanted = false;
        missingSeeds.clear();
        supplyContainer = null;
        if (notice != null) {
            supplyNotice = notice;
        }
        state = FarmState.IDLE;
    }

    /** 补给点坐标，没设置返回 null */
    private static BlockPos supplyPos(AutoFarmConfig config) {
        return config.hasSupplyPoint()
                ? new BlockPos(config.supplyX, config.supplyY, config.supplyZ)
                : null;
    }

    /**
     * 按补给目的选容器坐标。
     *
     * <p>专用容器（输出 / 种子 / 工具 / 杂物）启用了、且坐标齐全就用它；
     * 没设或禁用了就退回默认补给点。杂物容器特殊：没设/禁用时<b>不存杂物</b>（返回 null），
     * 而不是把杂物和作物混进同一个默认箱子。
     */
    private static BlockPos containerFor(AutoFarmConfig config, SupplyPurpose purpose) {
        // 同一 tick 里会被问好几次（停机判断、补给判断、出发……），每次都 new BlockPos 太亏 ——
        // 一 tick 算一次。每 tick 开头由 clearContainerCache() 作废。
        int i = purpose.ordinal();
        if (containerCacheSet[i]) {
            return containerCache[i];
        }
        BlockPos result = computeContainerFor(config, purpose);
        containerCache[i] = result;
        containerCacheSet[i] = true;
        return result;
    }

    private static BlockPos computeContainerFor(AutoFarmConfig config, SupplyPurpose purpose) {
        BlockPos dedicated = switch (purpose) {
            case DEPOSIT_CROP -> config.outCropEnabled && config.hasCropOut()
                    ? new BlockPos(config.outCropX, config.outCropY, config.outCropZ) : null;
            case DEPOSIT_JUNK -> config.outJunkEnabled && config.hasJunkOut()
                    ? new BlockPos(config.outJunkX, config.outJunkY, config.outJunkZ) : null;
            case REFILL_SEED -> config.inSeedEnabled && config.hasSeedIn()
                    ? new BlockPos(config.inSeedX, config.inSeedY, config.inSeedZ) : null;
            case FETCH_TOOL -> config.inToolEnabled && config.hasToolIn()
                    ? new BlockPos(config.inToolX, config.inToolY, config.inToolZ) : null;
        };
        if (dedicated != null) {
            return dedicated;
        }
        return purpose == SupplyPurpose.DEPOSIT_JUNK ? null : supplyPos(config);
    }

    // ================= 安全保护 =================

    /**
     * 现在有没有「该马上停手」的危险。
     *
     * <p>三类条件，任意一类成立就返回对应的提示键：
     * <ol>
     *   <li><b>血量太低</b>（{@link AutoFarmConfig#lowHealthStopHearts}，单位心，0 = 关闭）。
     *       收割的时候人一直低着头，被偷袭或者掉进坑里都反应不过来 ——
     *       与其等你事后发现人没了，不如先站着不动；</li>
     *   <li><b>附近有敌对生物</b>（{@link AutoFarmConfig#pauseWhenMobsNear}）。
     *       认的是原版那套「敌对生物」标记，所以僵尸、骷髅、苦力怕、蜘蛛，
     *       以及按同样方式实现的模组怪都算数；</li>
     *   <li><b>附近有别的玩家</b>（{@link AutoFarmConfig#pauseWhenPlayerNear}，附加功能，
     *       默认关闭）—— 自动收割最容易被发现的就是「被别的玩家撞见」。</li>
     * </ol>
     *
     * <p>这两件事都<b>只是停手</b>，不会替你做任何反击或者逃跑的动作 ——
     * 它保证的是「不会一边挨打一边傻站着收菜」，不是「帮你打架」。
     *
     * @return 该停手的原因（翻译键）；没有危险返回 {@code null}
     */
    private static String safetyNotice(Minecraft client, AutoFarmConfig config) {
        if (client == null || client.player == null || client.level == null) {
            return null;
        }

        if (config.lowHealthStopHearts > 0) {
            float threshold = config.lowHealthStopHearts * 2.0f; // 心 → 点（1 心 = 2 点）
            if (client.player.getHealth() < threshold) {
                return "autofarm.notice.paused_low_health";
            }
        }

        // 附加功能「自动打怪」开着时，这一条让位 —— 怪由 KillAura 处理。
        // 不让位的话会出现自相矛盾的状态：它一边挥刀打怪、一边因为「附近有怪」而停手。
        if (config.pauseWhenMobsNear && !config.autoAttack) {
            if (nearestMonster(client) != null) {
                return "autofarm.notice.paused_mobs_near";
            }
        }

        // 附加功能：附近有别的玩家时停机。凭空多出来的人形很少是好事 ——
        // 要么是来自找你的，要么是来看你「演戏」的，两种都值得先停下装挂机。
        // 判据是服务端同步过来的玩家实体，离线单人世界里永远为空，所以不碍事。
        if (config.pauseWhenPlayerNear) {
            AABB box = client.player.getBoundingBox().inflate(config.playerNearRadius);
            List<Player> others = client.level.getEntitiesOfClass(Player.class, box,
                    other -> other != client.player && other.isAlive());
            if (!others.isEmpty()) {
                return "autofarm.notice.paused_player_near";
            }
        }
        return null;
    }

    /**
     * 推进「定时随机休息」的计时（附加功能，默认关闭）。
     *
     * <p>逻辑很简单：一直累计「连续工作了多久」，到点了就把人按下去休息一会儿，
     * 休息结束再重新累计。关掉这个功能时会把计时归零 —— 否则下次打开时
     * 可能「一上来就欠着几分钟」，立刻就开始休息，很莫名其妙。
     *
     * @param alreadyPaused 这一 tick 是否已经因为别的原因停手（背包满 / 危险 / 时段外 /
     *                      环境不合适）。为真时<b>不计入</b>「连续工作」——
     *                      口径要和 {@link #updateFatigue} 一致。
     * @return 这一 tick 是不是在休息
     */
    private static boolean updateRest(AutoFarmConfig config, boolean alreadyPaused) {
        if (!config.randomRest) {
            restRemaining = 0;
            workTicks = 0;
            return false;
        }
        if (restRemaining > 0) {
            restRemaining--;
            return true;
        }
        if (alreadyPaused) {
            return false; // 没在干活，就不算「连续工作」
        }
        workTicks++;
        int interval = Math.max(1, config.restIntervalMinutes) * 20 * 60; // 分钟 → tick
        if (workTicks >= interval) {
            workTicks = 0;
            restRemaining = Math.max(1, config.restDurationSeconds) * 20; // 秒 → tick
            return true;
        }
        return false;
    }

    /**
     * 给「危险提示」套一层反应延迟（附加功能，默认关闭）。
     *
     * <p>没开这个功能、或者本来就没有危险时，原样返回。
     * 开着的时候：危险第一次出现先记下类型、开始数 tick，数够
     * {@link AutoFarmConfig#reactionDelayTicks} 才真正把它当成「要停手」——
     * 在此之前返回 {@code null}，等于「还没反应过来，继续干」。
     *
     * <p>危险消失、或者换了一种危险（比如从「有怪」变成「血量低」），计时都重新开始。
     */
    private static String applyReactionDelay(AutoFarmConfig config, String safety) {
        if (!config.reactionDelay || safety == null) {
            pendingSafety = null;
            safetySeenTicks = 0;
            return safety;
        }
        if (safety.equals(pendingSafety)) {
            safetySeenTicks++;
        } else {
            pendingSafety = safety;
            safetySeenTicks = 0;
        }
        return safetySeenTicks >= Math.max(1, config.reactionDelayTicks) ? safety : null;
    }

    /**
     * 现在是不是在「允许工作的时段」内（附加功能，默认关闭）。
     *
     * <p>用<b>本机时间</b>判断：起止都是 0~23 的整点、终点不含。起止相同表示全天。
     * 起点大于终点表示跨午夜（比如 22 点到次日 6 点）。
     */
    private static boolean insideSchedule(AutoFarmConfig config) {
        int start = Math.max(0, Math.min(23, config.scheduleStartHour));
        int end = Math.max(0, Math.min(23, config.scheduleEndHour));
        if (start == end) {
            return true; // 起止相同 = 全天，等于没限制
        }
        int hour = java.time.LocalTime.now().getHour();
        return start < end ? (hour >= start && hour < end) : (hour >= start || hour < end);
    }

    /**
     * 环境偏好：现在适不适合干活（附加功能，默认关闭）。
     *
     * <p>两条独立的规矩，任意一条不满足就返回对应的提示键：
     * <ul>
     *   <li>{@link AutoFarmConfig#workDaylightOnly} —— 只白天干。判定用
     *       {@code world.isDay()}（世界时间，不是本机时间）；</li>
     *   <li>{@link AutoFarmConfig#workClearWeatherOnly} —— 只晴天干。下雨、雷暴都算「不是晴天」。</li>
     * </ul>
     * 两条都只是停手，条件一恢复自动继续，不需要你重新开开关。
     *
     * @return 该停手的原因（翻译键）；环境合适返回 {@code null}
     */
    private static String environmentNotice(Minecraft client, AutoFarmConfig config) {
        if (client == null || client.level == null) {
            return null;
        }
        if (config.workDaylightOnly && !client.level.isDay()) {
            return "autofarm.notice.paused_night";
        }
        if (config.workClearWeatherOnly && client.level.isRaining()) {
            return "autofarm.notice.paused_weather";
        }
        return null;
    }

    /**
     * 推进「疲劳收工」（附加功能，默认关闭）。
     *
     * <p>和 {@link #updateRest} 的区别是<b>尺度</b>：定时休息是「中途喝口水」，
     * 这个管的是整场作业 —— 连续干够 {@link AutoFarmConfig#fatigueWorkMinutes} 分钟就彻底收工，
     * 休息 {@link AutoFarmConfig#fatigueRestMinutes} 分钟再上工
     * （那一项设 0 就是「手动」：一直停着，直到你把开关关掉再打开）。
     *
     * <p>只在「这一 tick 本来就在干活」时累计：{@code alreadyPaused} 为真说明已经因为
     * 别的原因（满包、危险、定时休息、工作时段、环境）停下了，这些时间不该算进连续工作时长。
     *
     * @return 这一 tick 是不是在收工/休息
     */
    private static boolean updateFatigue(AutoFarmConfig config, boolean alreadyPaused) {
        if (!config.fatigueStop) {
            // 关掉这个功能 = 人歇够了，计时全部归零
            fatigueWorkTicks = 0;
            fatigueRestRemaining = 0;
            fatigueStopped = false;
            return false;
        }
        if (fatigueStopped) {
            return true; // 「手动恢复」档：一直停着，等玩家把开关（或总开关）关掉再打开
        }
        if (fatigueRestRemaining > 0) {
            fatigueRestRemaining--;
            if (fatigueRestRemaining == 0) {
                fatigueWorkTicks = 0; // 歇够了，重新开始计时
            }
            return true;
        }
        if (alreadyPaused) {
            return false;
        }
        fatigueWorkTicks++;
        int limit = Math.max(5, config.fatigueWorkMinutes) * 20 * 60; // 分钟 → tick
        if (fatigueWorkTicks < limit) {
            return false;
        }
        fatigueWorkTicks = 0;
        if (config.fatigueRestMinutes > 0) {
            fatigueRestRemaining = config.fatigueRestMinutes * 20 * 60;
        } else {
            fatigueStopped = true; // 手动：一直停到玩家重开
        }
        return true;
    }

    /**
     * 越界返回：把人带回工作区域（附加功能，默认关闭）。
     *
     * <p>回程走的还是 {@link PathWalker} 那条通道，但目标不是作物，所以用
     * {@link PathWalker#setExactTargets} 声明「要站到它本身那一格」，
     * 并在到站时手动放行（见 {@link #areaReturnTarget}）。
     *
     * <p>只尝试一次：路线排不出来、或者走完人还在圈外（被水冲走、被怪推出去……），
     * 就原地待命、不再反复重排 —— 否则会变成每 tick 重算一次 A* 的空转，多半还是走不通。
     * 这时候玩家自己走回去，或者关掉这个功能就行。
     */
    private static void runAreaReturn(Minecraft client, AutoFarmConfig config) {
        // 正在走「回程」这条路 → 推进一步
        if (PathWalker.isActive() && areaReturnTarget != null
                && areaReturnTarget.equals(PathWalker.getCurrentTarget())) {
            PathWalker.tick(client);
            drainResolved();
            BlockPos held = PathWalker.getHeldTarget();
            if (held != null && held.equals(areaReturnTarget)) {
                PathWalker.releaseTarget();
                drainResolved();
                areaReturnTarget = null;
            }
            return;
        }
        // 手上是别的路线（越界时开着界面，被正常流程插了一条收割路线）→ 扔掉，改成回程
        if (PathWalker.isActive()) {
            PathWalker.stop();
        }
        if (areaReturnTried) {
            return;
        }
        areaReturnTried = true;

        BlockPos goal;
        if (config.workAreaMode == AutoFarmConfig.AREA_MODE_BOX) {
            // 矩形模式往两个角点的正中走 —— 那是这片区域的正中心，最稳
            goal = new BlockPos(
                    (config.workAreaX + config.workAreaX2) / 2,
                    (config.workAreaY + config.workAreaY2) / 2,
                    (config.workAreaZ + config.workAreaZ2) / 2);
        } else {
            goal = new BlockPos(config.workAreaX, config.workAreaY, config.workAreaZ);
        }
        // 目标那一格可能站不住人（被填了方块），退而求其次找个能看见它的落脚点
        if (!BlockNavigator.canStand(client.level, goal)) {
            BlockPos spot = BlockNavigator.findViewSpot(client.level, goal, client.player.blockPosition());
            if (spot == null) {
                return;
            }
            goal = spot;
        }

        PathWalker.setExactTargets(List.of(goal));
        PathWalker.start(PathPlanner.plan(client.player.blockPosition(), List.of(goal), false));
        areaReturnTarget = PathWalker.isActive() ? goal : null;
    }

    // ================= 危险撤退（C 组）=================

    /**
     * 最近的「威胁」位置（危险撤退用）。
     *
     * <p>只认<b>敌对生物</b> —— 被别的玩家追着打属于 PVP，这个模组不介入。
     * 判定半径比「敌对生物靠近停机」那个稍大一些，好让人有机会在挨打之前就撤。
     */
    private static Vec3 nearestThreat(Minecraft client) {
        Mob mob = nearestMonster(client);
        return mob == null ? null : new Vec3(mob.getX(), mob.getY(), mob.getZ());
    }

    /** 每 tick 开头作废威胁缓存（清完第一次访问才真正去查） */
    private static void clearThreatCache() {
        threatCacheReady = false;
        threatCache = null;
    }

    /**
     * MOB_ALERT_RADIUS 内最近的敌对生物，<b>同一 tick 只查一次</b>。
     *
     * <p>「附近有怪停机」「危险撤退」「受击反制找嫌疑犯」三处都要做同一份查询，
     * 不缓存的话一 tick 要重复扫 2~3 次实体列表。用 {@link #threatCacheReady} 标记，
     * 每 tick 开头清一次即可（客户端主线程单线程，不存在并发问题）。
     */
    private static Mob nearestMonster(Minecraft client) {
        if (client == null || client.player == null || client.level == null) {
            return null;
        }
        if (threatCacheReady) {
            return threatCache;
        }
        threatCacheReady = true;
        AABB box = client.player.getBoundingBox().inflate(AutoFarmConfig.MOB_ALERT_RADIUS);
        Mob best = null;
        double bestDist = Double.MAX_VALUE;
        for (Mob mob : client.level.getEntitiesOfClass(Mob.class, box,
                m -> m.isAlive() && m instanceof Enemy)) {
            double d = mob.distanceToSqr(client.player);
            if (d < bestDist) {
                bestDist = d;
                best = mob;
            }
        }
        threatCache = best;
        return best;
    }

    /**
     * 撤退：朝「背离威胁」的方向走一段。
     *
     * <p>走的是 {@link PathWalker} 那条通道，但因为目标不是作物，要用
     * {@link PathWalker#setExactTargets} 说明「要站到它本身那一格」，到站后手动放行。
     *
     * <p>只尝试一次：走不通、或者走完还在威胁附近（被推、被围），就原地待命、
     * 不再反复重排 —— 否则会变成每 tick 重算 A* 的空转。
     *
     * @return true 表示还在撤（或刚排好路线）；false 表示已经到位 / 撤不了
     */
    private static boolean runRetreat(Minecraft client, AutoFarmConfig config) {
        if (PathWalker.isActive() && retreatTarget != null
                && retreatTarget.equals(PathWalker.getCurrentTarget())) {
            PathWalker.tick(client);
            drainResolved();
            BlockPos held = PathWalker.getHeldTarget();
            if (held != null && held.equals(retreatTarget)) {
                PathWalker.releaseTarget();
                drainResolved();
                retreatTarget = null;
                return false; // 到位了
            }
            return true;
        }
        if (PathWalker.isActive()) {
            PathWalker.stop();
        }
        if (retreatTried) {
            return false;
        }
        retreatTried = true;

        BlockPos goal = chooseRetreatGoal(client, config);
        if (goal == null) {
            return false;
        }
        PathWalker.setExactTargets(List.of(goal));
        PathWalker.start(PathPlanner.plan(client.player.blockPosition(), List.of(goal), false));
        retreatTarget = PathWalker.isActive() ? goal : null;
        return retreatTarget != null;
    }

    /**
     * 选一个撤退落脚点：从玩家位置出发，沿「远离威胁」的方向走 {@code retreatDistance} 格，
     * 再在那一列附近找一个真正能站的格子。
     */
    private static BlockPos chooseRetreatGoal(Minecraft client, AutoFarmConfig config) {
        Vec3 threat = nearestThreat(client);
        if (threat == null) {
            return null;
        }
        double dx = client.player.getX() - threat.x;
        double dz = client.player.getZ() - threat.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-4) {
            dx = 1.0;
            dz = 0.0;
            len = 1.0;
        }
        int dist = Math.max(4, config.retreatDistance);
        int gx = (int) Math.floor(client.player.getX() + dx / len * dist);
        int gz = (int) Math.floor(client.player.getZ() + dz / len * dist);
        int gy = client.player.blockPosition().getY();

        // 目标那一列可能站不住人（悬崖、水、树），往外螺旋找最近的可站格
        for (int r = 0; r <= 5; r++) {
            for (int ox = -r; ox <= r; ox++) {
                for (int oz = -r; oz <= r; oz++) {
                    if (Math.max(Math.abs(ox), Math.abs(oz)) != r) {
                        continue; // 只看这一圈的「外框」
                    }
                    BlockPos floor = BlockNavigator.findFloor(client.level, gx + ox, gz + oz, gy, 4, 3);
                    if (floor != null) {
                        return floor;
                    }
                }
            }
        }
        return null;
    }

    // ================= 受击反制（C 组）=================

    /** 挨打那一下：记下攻击者，进入「停手 + 回头」状态；开着自动打怪则顺带把目标交给它反击 */
    private static void beginHitReaction(Minecraft client, AutoFarmConfig config) {
        LivingEntity attacker = findAttacker(client);
        hitTarget = attacker;
        hitReactTicks = Math.max(1, config.hitReactionSeconds) * 20;
        if (config.autoAttack && attacker != null) {
            KillAura.focus(attacker); // 真正的「反制」：优先还手
        }
    }

    /**
     * 找出「刚才是谁打的我」。
     *
     * <p>首选原版同步过来的最近伤害来源；但很多时候服务端并不会把攻击者同步给客户端，
     * 那时候就退而求其次 —— 用最近的有威胁生物顶上。两者都没有就等于「说不出是谁」，
     * 这时受击反制只做「停手」，不转头。
     */
    private static LivingEntity findAttacker(Minecraft client) {
        if (client == null || client.player == null) {
            return null;
        }
        DamageSource source = client.player.getLastDamageSource();
        if (source != null && source.getEntity() instanceof LivingEntity living
                && living != client.player) {
            return living;
        }
        // 兜底「嫌疑犯」：最近的那只敌对生物（复用同一份 tick 缓存，不再单独查一次）
        return nearestMonster(client);
    }

    /** 这句话是不是「停机原因」——停机解除时要记得把它撤掉 */
    private static boolean isPauseNotice(String key) {
        return "autofarm.notice.inventory_full_no_supply".equals(key)
                || "autofarm.notice.paused_low_health".equals(key)
                || "autofarm.notice.paused_mobs_near".equals(key)
                || "autofarm.notice.paused_player_near".equals(key);
    }

    // ================= actionbar 播报 =================

    /**
     * 停机播报：同一个原因只说一次，危险过去（{@code key} 传 null）后自动解锁。
     *
     * <p>为什么要防重：停机条件每 tick 都成立，不拦一下就会变成一条刷屏的提示。
     */
    private static void announcePause(Minecraft client, String key) {
        if (key == null) {
            lastPauseAnnounce = null;
            return;
        }
        if (key.equals(lastPauseAnnounce)) {
            return;
        }
        lastPauseAnnounce = key;
        sendActionBar(client, key);
        // F 组「音效 / 桌面通知」：第一次进入某种停机状态时提示一次（去重已由上面保证）
        notify(client, ConfigManager.get(), "stop");
    }

    /** 事件播报：补给结果、种子用完这类「一个事件一次」的提示 */
    private static void announceEvent(Minecraft client, String key) {
        if (key == null || key.equals(lastEventAnnounce)) {
            return;
        }
        lastEventAnnounce = key;
        sendActionBar(client, key);
    }

    /** 在快捷栏上方飘一行字（{@code overlay = true} 就是 actionbar） */
    private static void sendActionBar(Minecraft client, String translationKey) {
        if (client != null && client.player != null) {
            client.player.displayClientMessage(Component.translatable(translationKey), true);
        }
    }

    /**
     * 音效 / 桌面通知（F 组，两个开关独立，默认全关）。
     *
     * <p>三档 {@code kind} 各配一个音和一个 toast 标题：
     * <ul>
     *   <li>{@code start} —— 打开自动收割；</li>
     *   <li>{@code stop} —— 停机（安全 / 满包 / 越界 / 休息……）或关掉开关；</li>
     *   <li>{@code issue} —— 收不动、补给失败这类「没干成」。</li>
     * </ul>
     * 音效走原版 {@code playSound}，toast 走原版 {@link SystemToast}，都不引入额外资源。
     */
    private static void notify(Minecraft client, AutoFarmConfig config, String kind) {
        if (client == null || client.player == null || config == null
                || (!config.notifySounds && !config.notifyToasts)) {
            return;
        }
        if (config.notifySounds) {
            client.player.playSound(soundFor(kind), 1.0f, 1.0f);
        }
        if (config.notifyToasts) {
            SystemToast.addOrUpdate(client.getToasts(), SystemToast.SystemToastIds.TUTORIAL_HINT,
                    Component.translatable("autofarm.notify." + kind), Component.empty());
        }
    }

    /** 通知类别 → 原版音效 */
    private static SoundEvent soundFor(String kind) {
        return switch (kind) {
            case "start" -> SoundEvents.EXPERIENCE_ORB_PICKUP;
            case "issue" -> SoundEvents.VILLAGER_NO;
            // 1.20.1 里音符盒这类音效是 RegistryEntry.Reference，要 .value() 取出 SoundEvent
            default -> SoundEvents.NOTE_BLOCK_BASS.value();
        };
    }
}
