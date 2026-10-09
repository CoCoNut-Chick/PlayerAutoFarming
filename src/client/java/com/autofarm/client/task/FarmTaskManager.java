package com.autofarm.client.task;

import com.autofarm.client.compat.CameraCompat;
import com.autofarm.client.config.AutoFarmConfig;
import com.autofarm.client.config.ConfigManager;
import com.autofarm.client.container.ContainerCompat;
import com.autofarm.client.container.ContainerTransfer;
import com.autofarm.client.harvest.CropHarvester;
import com.autofarm.client.interact.BlockInteractor;
import com.autofarm.client.inventory.AutoEater;
import com.autofarm.client.inventory.InventoryScanner;
import com.autofarm.client.path.BlockNavigator;
import com.autofarm.client.path.PathPlanner;
import com.autofarm.client.path.PathStep;
import com.autofarm.client.path.PathWalker;
import com.autofarm.client.scan.CropFilter;
import com.autofarm.client.scan.CropScanner;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

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
 * <h2>优先级链</h2>
 * <p>同一 tick 里可能同时有好几件事想做，所以定了严格的先后顺序 ——
 * <b>先保证角色能正常行动，再谈干活</b>：
 * <ol>
 *   <li><b>吃</b>（{@link AutoEater}）：饿到跑不动就先吃。走不动的话，
 *       去下一棵作物的时间要翻倍，所以它排在最前面，期间走路和收割都让路；</li>
 *   <li><b>补给 / 走 / 收</b>（{@link ContainerTransfer} + {@link PathWalker} +
 *       {@link CropHarvester}）：沿路线移动，到站就挖掉 + 补种。
 *       背包快满时会中断路线先去补给点存一趟（作物不会丢，下一轮还会扫到）；</li>
 *   <li><b>扫描 / 规划</b>：只有前两件事都不占着人（没在走、也没在收、没在补给）
 *       才会重新排路线。缺种子和背包满都会在这时候转成一次补给点往返。</li>
 * </ol>
 * 换句话说：<b>疾跑和进食 > 走路 > 收割 > 规划</b>。
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

    private static SupplyPhase supplyPhase = SupplyPhase.NONE;
    /** 这一趟要不要存东西 */
    private static boolean supplyDeposit;
    /** 这一趟要不要取种子 */
    private static boolean supplyRefill;

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

    /** 拟人化节奏的停顿倒计时（tick）：>0 时先愣一下，不动手也不推进路线 */
    private static int humanizeDelay;

    /** 随机休息的剩余时长（tick）：>0 表示正在休息 */
    private static int restRemaining;

    /** 距离下一次休息已经连续工作了多少 tick */
    private static int workTicks;

    /** 本轮（从打开开关算起）真正干了活的 tick 数，给 HUD 详细统计用 */
    private static int sessionTicks;

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

    /** 当前路线的目标总数 */
    public static int getRouteTotal() {
        return PathWalker.getTotalCount();
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
        MinecraftClient client = MinecraftClient.getInstance();
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
        MinecraftClient client = MinecraftClient.getInstance();
        return client == null || client.player == null
                ? 0
                : InventoryScanner.countSeeds(client.player, CropHarvester.getCropBlock());
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
        sessionTicks = 0;
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
    public static void toggleEnabled(MinecraftClient client) {
        AutoFarmConfig config = ConfigManager.get();
        config.enabled = !config.enabled;

        // 关掉 → 立刻停手，把控制权还给你
        if (!config.enabled) {
            PathWalker.stop();
            CropHarvester.reset();
            ContainerTransfer.reset();
            handled.clear();
            supplyPhase = SupplyPhase.NONE;
            supplyDeposit = false;
            supplyRefill = false;
            refillWanted = false;
            missingSeeds.clear();
            supplyContainer = null;
            failedUntil.clear();
            humanizeDelay = 0;
            restRemaining = 0;
            state = FarmState.IDLE;
        } else {
            // 打开 → 假装「刚进入这个世界」：清空记忆、下一 tick 立刻重扫
            handled.clear();
            lastScanTick = Long.MIN_VALUE;
            supplyNotice = null;
            harvestedCount = 0;
            plantedCount = 0;
            failedUntil.clear();
            workTicks = 0;
            sessionTicks = 0;
        }
        lastEnabled = config.enabled;

        ConfigManager.persist();

        if (client != null && client.player != null) {
            client.player.sendMessage(Text.translatable("autofarm.message.toggled",
                    Text.translatable(config.enabled ? "autofarm.on" : "autofarm.off")), true);
        }
    }

    /** 挂在客户端 tick 末尾调用 */
    public static void tick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            reset();
            return;
        }

        // 每 tick 开头先复位「我们自己正在挖」的开关；这一 tick 真动了镐子的话，
        // BlockInteractor 会再把它置起来。原版读的就是这个标记（见 MinecraftClientMixin）。
        BlockInteractor.beginTick();

        AutoFarmConfig config = ConfigManager.get();
        if (!config.enabled && !config.visualize) {
            reset();
            return;
        }

        // 「关 → 开」的边沿：清空「已处理」并强制重扫。
        // 抓边沿而不是只看当前值，是为了让「面板里点开」和「快捷键开」走同一套逻辑，
        // 都能把还没被挖掉的成熟作物重新认出来。
        if (config.enabled != lastEnabled) {
            if (config.enabled) {
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
            }
            lastEnabled = config.enabled;
        }

        // 玩家关掉「自动收割」→ 立刻停手，并把「已处理」清空，
        // 这样下次打开开关时会重新从头规划一遍
        if (!config.enabled) {
            if (PathWalker.isActive() || supplyPhase != SupplyPhase.NONE) {
                cancelSupply(null);
                PathWalker.stop();
                CropHarvester.reset();
                handled.clear();
            }
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
        // 背包满了，又没地方存 → 停手
        // 继续收下去，多出来的作物只会掉在地上烂掉。宁可停下来等你处理，
        // 也不做这种「收了个寂寞」的事。腾出空间（或者设好补给点）之后自动恢复。
        // ================================================================
        boolean stopForFull = config.enabled && shouldStopForFullInventory(client, config);
        // 安全条件：血量太低、或者附近有敌对生物。它比「收菜」重要 ——
        // 人被打了还低着头收菜，是最容易出事的状态
        String safety = config.enabled ? safetyNotice(client, config) : null;

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

        // 这一 tick 要不要整个人停手（背包满、安全保护、或者附加功能里的「定时休息」）
        boolean resting = config.enabled && updateRest(config);
        boolean paused = stopForFull || safety != null || resting;

        // 一停机就播报一次（actionbar），免得你以为是模组卡了 ——
        // 同一个原因只说一次，危险过去后自动允许下次再说。
        // 休息在 supplyNotice 里没有位置（那栏留给「缺种子」「箱子满了」这类事），
        // 所以它用自己的键，同样走「只说一次」这套。
        String pauseKey = stopForFull || safety != null
                ? supplyNotice
                : (resting ? "autofarm.notice.paused_resting" : null);
        announcePause(client, paused ? pauseKey : null);

        // 附加功能：HUD 详细统计的「运行时长」——只数真正干活的时间，
        // 暂停（满包 / 危险 / 休息 / 开着界面）的 tick 不算，这样它更像一块秒表。
        if (config.enabled && !paused) {
            sessionTicks++;
        }

        // 自由视角（masa 全家桶的「灵魂出窍」）：摄像机和身体分开之后，
        // 屏幕上看到的人是不动的，可世界里方块却在自己碎 —— 那太显眼了。
        // 打开这个开关就先停手，只保留描边和路线；关掉开关则照常干活
        // （渲染本来就是按摄像机算的，所以画面不会错位）。
        boolean freecamPaused = config.pauseOnFreecam && CameraCompat.isCameraDetached();

        // 玩家打开任意界面（背包、聊天、ESC……）、或者已经躺下了的时候立刻挂起，
        // 否则角色会在你翻背包的时候自顾自走开。危险和背包满也走同一条「暂停」通道。
        PathWalker.setPaused(client.currentScreen != null
                || !client.player.isAlive()
                || freecamPaused
                || paused);

        // ================================================================
        // 优先级 2 —— 补给 / 走 / 收
        // ================================================================
        if (!paused) {
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
        long now = client.world.getTime();
        if (lastScanTick != Long.MIN_VALUE && now - lastScanTick < getEffectiveScanInterval()) {
            return;
        }
        lastScanTick = now;

        // 「补种空耕地」只在真正要干活时才去扫（关着时连这块开销都省掉）。
        // 顺便注意：成熟作物的过滤条件挂在扫描里，被筛掉的作物根本不会进列表 ——
        // 于是世界里不给它画描边、面板计数也对得上。
        boolean wantFarmland = config.enabled && config.fillEmptyFarmland;
        CropScanner.ScanResult scanned = CropScanner.scan(
                client.world,
                client.player.getBlockPos(),
                config.scanRadius,
                CropScanner.DEFAULT_DOWN,
                CropScanner.DEFAULT_UP,
                state -> CropFilter.allowsHarvest(state.getBlock(), config, client.player),
                wantFarmland
        );
        matureCrops = scanned.mature();
        emptyFarmland = wantFarmland ? scanned.emptyFarmland() : Collections.emptyList();

        // 让「已处理」集合跟着扫描结果瘦身，避免玩家在同一片区域待久了无限膨胀。
        // 收掉的作物会变成空气、补种后变成幼苗，两者都不再是「成熟作物」，
        // 所以它们会自然地从 handled 里掉出去。
        if (!handled.isEmpty()) {
            Set<BlockPos> current = new HashSet<>(matureCrops);
            current.addAll(emptyFarmland);
            handled.retainAll(current);
        }

        if (!config.enabled) {
            // 只开了「可视化」：照样扫描（这样你能单独看到它认出了哪些方块），
            // 但状态显示为「空闲」，因为它不会去动角色。
            state = FarmState.IDLE;
            return;
        }

        if (paused) {
            // 背包满 / 安全保护：这一 tick 只扫描、不动角色。
            // 状态已经在上面设成「已暂停」了，别被后面的规划覆盖掉。
            state = FarmState.PAUSED;
            return;
        }

        if (supplyPhase != SupplyPhase.NONE || PathWalker.isActive()) {
            // 补给 / 走路 / 收割的状态是上面那一段设好的，别覆盖掉
            return;
        }

        // ---- 优先级 3：手上没路线了，看看要不要去补给点，或者新规划一条 ----

        // 3a. 背包快满了 → 先去存一趟
        if (shouldGoDeposit(client, config)
                && startSupplyRun(client, config, true, refillWanted)) {
            return;
        }

        // 3b. 缺种子 → 去取一趟
        if (refillWanted && config.refillSeeds && config.hasSupplyPoint()
                && supplyCooldown == 0
                && startSupplyRun(client, config, false, true)) {
            return;
        }

        // 附加功能「失败目标暂时拉黑」：先把已经到期的解禁掉，
        // 再把还在冷却里的目标跳过。全关时 failedUntil 恒为空，这两步都是空转。
        if (!failedUntil.isEmpty()) {
            failedUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
        }

        List<BlockPos> pending = new ArrayList<>();
        for (BlockPos pos : matureCrops) {
            if (!handled.contains(pos) && !failedUntil.containsKey(pos)) {
                pending.add(pos);
            }
        }

        // 空耕地也排进同一条路线（如果开着补种空地）。
        // 一个前提：手边得真有种子 —— 一粒都没有的话，走过去也只是站着报「没种子」，
        // 白跑一趟。那种情况直接记账「该去补给点取种子」，让上面那条补给流程接手。
        if (config.fillEmptyFarmland && !emptyFarmland.isEmpty()) {
            if (InventoryScanner.countSeeds(client.player, null) > 0) {
                for (BlockPos pos : emptyFarmland) {
                    if (!handled.contains(pos) && !failedUntil.containsKey(pos)) {
                        pending.add(pos);
                    }
                }
            } else {
                refillWanted = true;
                announceEvent(client, "autofarm.notice.plant_no_seed");
            }
        }

        if (pending.isEmpty()) {
            // 这一轮都收完了。等新一批作物成熟，或者走回还没扫过的区域，才会重新规划。
            state = matureCrops.isEmpty() ? FarmState.IDLE : FarmState.SCANNING;
            return;
        }

        PathPlanner.Plan plan = PathPlanner.plan(
                client.player.getBlockPos(),
                pending,
                config.optimizePath
        );
        PathWalker.start(plan);
        state = FarmState.MOVING;
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
    private static void drive(MinecraftClient client, AutoFarmConfig config) {
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

        // 开关被关掉了 → 放行，别把角色吊在一个点上
        if (!config.enabled) {
            PathWalker.releaseTarget();
            drainResolved();
            return;
        }

        // 你开着界面（背包/聊天/ESC）→ 原地等你。
        // 这里刻意不「放行」：万一你只是随手翻下背包，回头这一棵还得接着收。
        if (client.currentScreen != null) {
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
            supplyNotice = null; // 新的活开始了，上一次补给的那句话可以撤掉
            CropHarvester.start(client.world, held, config);
        }
        boolean finished = CropHarvester.tick(client, config);
        state = CropHarvester.isReplanting() ? FarmState.REPLANTING : FarmState.HARVESTING;

        if (finished) {
            // 统计：真的收到了才算数（判定「这格不该动」时 wasHarvested 是 false）
            if (CropHarvester.wasHarvested()) {
                harvestedCount++;
            }
            // 空地补种单独记一笔 —— 它没「收」到任何东西，但确实干了活
            if (CropHarvester.wasPlanted()) {
                plantedCount++;
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
            PathWalker.releaseTarget();
            drainResolved();

            // 附加功能「失败目标暂时拉黑」：这一棵没干成、而且给出了原因（挖不动、
            // 种不下去……）才拉黑。单纯「这一格已经不是成熟作物」不算失败 ——
            // 那是被别人收走了，重试也没意义。拉黑的做法是从 handled 里撤回来、
            // 记个到期时间，到点自然会重新排进路线，不用你手动关开一次。
            boolean failed = !CropHarvester.wasHarvested() && !CropHarvester.wasPlanted()
                    && CropHarvester.getNotice() != null;
            if (config.failureCooldown && failed && held != null) {
                failedUntil.put(held.toImmutable(),
                        client.world.getTime() + config.failureCooldownSeconds * 20L);
                handled.remove(held);
            }

            // 附加功能「拟人化节奏」：这一次真的动了手（收了或种了），就歇一小会儿
            if (config.humanizeTiming
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
    private static boolean shouldGoDeposit(MinecraftClient client, AutoFarmConfig config) {
        return config.depositWhenFull
                && config.hasSupplyPoint()
                && supplyCooldown == 0
                && InventoryScanner.isNearlyFull(client.player, INVENTORY_RESERVE)
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
    private static boolean shouldStopForFullInventory(MinecraftClient client, AutoFarmConfig config) {
        if (!InventoryScanner.isNearlyFull(client.player, INVENTORY_RESERVE)) {
            return false;
        }
        if (!InventoryScanner.hasDepositableItems(client.player, config)) {
            return false;
        }
        return ContainerCompat.findContainer(client.world, supplyPos(config)) == null;
    }

    /**
     * 开始一趟补给点往返。
     *
     * <p>先找「箱子里能站人的那一格」，把它当成路线的唯一目标；
     * 人到了之后由 {@link #runSupply} 接手去开箱和搬东西。
     *
     * @return 有没有真的开始（没设补给点、附近没有容器、旁边站不住人都算失败）
     */
    private static boolean startSupplyRun(MinecraftClient client, AutoFarmConfig config,
                                          boolean deposit, boolean refill) {
        BlockPos wanted = supplyPos(config);
        if (wanted == null) {
            return false;
        }
        // 补给点那一格（或者它附近）得真有个容器。判定按能力来 —— 见 ContainerCompat，
        // 箱子、木桶、模组容器一律认得出来。
        BlockPos container = ContainerCompat.findContainer(client.world, wanted);
        if (container == null) {
            supplyNotice = "autofarm.notice.supply_not_container";
            return false;
        }
        BlockPos stand = BlockNavigator.findViewSpot(client.world, container);
        if (stand == null) {
            supplyNotice = "autofarm.notice.supply_no_spot";
            return false;
        }

        supplyContainer = container;
        supplyDeposit = deposit;
        supplyRefill = refill;
        supplyPhase = SupplyPhase.GOING;
        state = FarmState.TO_SUPPLY;

        // 已经站在旁边了：不用再走，直接开箱
        if (BlockInteractor.isWithinReach(client.player, container, 4.0)) {
            beginTransfer(config);
            return true;
        }

        PathPlanner.Plan plan = PathPlanner.plan(
                client.player.getBlockPos(), List.of(stand), false);
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
        ContainerTransfer.begin(container, supplyDeposit, supplyRefill, missingSeeds);
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
        MinecraftClient client = MinecraftClient.getInstance();
        return client == null ? null : ContainerCompat.findContainer(client.world, supplyPos(config));
    }

    /** 每 tick 推进一步：要么在走过去，要么在开箱搬东西 */
    private static void runSupply(MinecraftClient client, AutoFarmConfig config) {
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

        // 「要办的事一件都没办成」（箱子满了、箱子里没种子）→ 歇一会儿再去，
        // 否则下一 tick 条件照样成立，它会立刻掉头再跑一趟，变成两个点之间横跳
        boolean achieved = result == ContainerTransfer.Result.DONE
                && (!didDeposit || ContainerTransfer.getDepositedStacks() > 0)
                && (!didRefill || ContainerTransfer.getTakenStacks() > 0);
        supplyCooldown = achieved ? 0 : SUPPLY_RETRY_COOLDOWN_TICKS;

        finishSupply();
    }

    /** 一趟存取正常结束 */
    private static void finishSupply() {
        supplyPhase = SupplyPhase.NONE;
        supplyDeposit = false;
        supplyRefill = false;
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
    private static String safetyNotice(MinecraftClient client, AutoFarmConfig config) {
        if (client == null || client.player == null || client.world == null) {
            return null;
        }

        if (config.lowHealthStopHearts > 0) {
            float threshold = config.lowHealthStopHearts * 2.0f; // 心 → 点（1 心 = 2 点）
            if (client.player.getHealth() < threshold) {
                return "autofarm.notice.paused_low_health";
            }
        }

        if (config.pauseWhenMobsNear) {
            Box box = client.player.getBoundingBox().expand(AutoFarmConfig.MOB_ALERT_RADIUS);
            List<MobEntity> threats = client.world.getEntitiesByClass(MobEntity.class, box,
                    mob -> mob.isAlive() && mob instanceof Monster);
            if (!threats.isEmpty()) {
                return "autofarm.notice.paused_mobs_near";
            }
        }

        // 附加功能：附近有别的玩家时停机。凭空多出来的人形很少是好事 ——
        // 要么是来自找你的，要么是来看你「演戏」的，两种都值得先停下装挂机。
        // 判据是服务端同步过来的玩家实体，离线单人世界里永远为空，所以不碍事。
        if (config.pauseWhenPlayerNear) {
            Box box = client.player.getBoundingBox().expand(config.playerNearRadius);
            List<PlayerEntity> others = client.world.getEntitiesByClass(PlayerEntity.class, box,
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
     * @return 这一 tick 是不是在休息
     */
    private static boolean updateRest(AutoFarmConfig config) {
        if (!config.randomRest) {
            restRemaining = 0;
            workTicks = 0;
            return false;
        }
        if (restRemaining > 0) {
            restRemaining--;
            return true;
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
    private static void announcePause(MinecraftClient client, String key) {
        if (key == null) {
            lastPauseAnnounce = null;
            return;
        }
        if (key.equals(lastPauseAnnounce)) {
            return;
        }
        lastPauseAnnounce = key;
        sendActionBar(client, key);
    }

    /** 事件播报：补给结果、种子用完这类「一个事件一次」的提示 */
    private static void announceEvent(MinecraftClient client, String key) {
        if (key == null || key.equals(lastEventAnnounce)) {
            return;
        }
        lastEventAnnounce = key;
        sendActionBar(client, key);
    }

    /** 在快捷栏上方飘一行字（{@code overlay = true} 就是 actionbar） */
    private static void sendActionBar(MinecraftClient client, String translationKey) {
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.translatable(translationKey), true);
        }
    }
}
