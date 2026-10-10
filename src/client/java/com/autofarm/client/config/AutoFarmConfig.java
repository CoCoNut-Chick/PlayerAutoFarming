package com.autofarm.client.config;

import com.autofarm.client.inventory.ItemCategory;

import java.util.ArrayList;
import java.util.List;

/**
 * AutoFarm 的全部可调参数。
 * 这是一个纯数据类，序列化成 config/autofarm.json。
 */
public class AutoFarmConfig {

    /** 「收获物即种子」那一类出厂默认留几组 */
    public static final int DEFAULT_BACKUP_STACKS = 1;

    /** 总开关：打开后模组才会开始工作 */
    public boolean enabled = false;

    /** 扫描半径（格），以玩家为中心 */
    public int scanRadius = 16;

    /**
     * 扫描间隔，单位 tick（20 tick = 1 秒）。
     * 数值越小刷新越及时，但每秒遍历的方块次数也越多、越吃 CPU。
     */
    public int scanIntervalTicks = 20;

    /** 收割后是否补种 */
    public boolean replant = true;

    /** 瞄准模式：默认（精准） */
    public static final int AIM_NORMAL = 0;
    /** 瞄准模式：效率（转到「视线命中方块」就挖） */
    public static final int AIM_EFFICIENT = 1;
    /** 瞄准模式：极效率（不转视角、不管准星，范围内直接挖） */
    public static final int AIM_EXTREME = 2;
    /** 瞄准模式档数（面板循环按钮用） */
    public static final int AIM_MODE_COUNT = 3;

    /**
     * 瞄准模式，取值见 {@code AIM_*} 常量。
     *
     * <p>三个档位，从稳到快：
     * <ul>
     *   <li>{@link #AIM_NORMAL}（默认）—— 先限速转头、把准星精确压到作物中心 8 度以内才动手，
     *       最稳、最像人；</li>
     *   <li>{@link #AIM_EFFICIENT} —— 也转头，但只要<b>视线已经命中这个方块</b>（准星落在它的
     *       可挖掘判定范围内）就动手，不用转到正中心，省掉每棵之间那一小段微调；</li>
     *   <li>{@link #AIM_EXTREME} —— 完全不转头、不管准星，只要在挖掘范围内就直接挖。
     *       这是最容易被服务器判成作弊的一档，选它要先过风险确认屏。</li>
     * </ul>
     */
    public int aimMode = AIM_NORMAL;

    /**
     * 「丝滑转向」一个完整转向大致用多少毫秒。
     *
     * <p>视角是缓动转过去的（角度越大一步跨得越多、越接近越慢）。这个值决定<b>整体快慢</b>：
     * 调小 → 转得又快又干脆；调大 → 转得慢而平滑。默认 600 毫秒左右，
     * 相当于「一次大角度转身用半秒多」。
     *
     * <p>它只影响转向的观感，不影响能不能挖到 —— 该命中的照样命中。
     */
    public int turnDurationMs = 600;

    /** 转向时间的合法区间（毫秒） */
    public static final int TURN_DURATION_MIN = 150;
    public static final int TURN_DURATION_MAX = 2000;

    /** 种子不够时是否自动前往补给点取种子 */
    public boolean refillSeeds = true;

    /** 背包满时是否自动前往补给点存放收获物 */
    public boolean depositWhenFull = true;

    // ---- 路径规划 ----

    /**
     * 是否用 2-opt 优化访问顺序。
     * 关掉就只保留「每次走向最近的一个」这种贪心顺序，路线会明显绕远。
     */
    public boolean optimizePath = true;

    /** 是否在世界里把规划出来的路线画成一条线 */
    public boolean showPath = true;

    /**
     * 沿路线行走时锁定疾跑。
     *
     * <p>打开后角色在赶路时会保持疾跑（速度几乎翻倍），到点停下、挖方块、
     * 你打开界面接管控制权时都会自动松开。原版的饥饿/吃东西等限制照样生效 ——
     * 它就是替你把「按住 Ctrl」这件事一直按着，不会绕过任何游戏规则。
     *
     * <p><b>与「防踢模式」的关系</b>：防踢策略里有一条「疾跑间歇」——真人不会一路从头跑到尾，
     * 每隔几秒会走两步喘口气。所以<b>防踢开到中档及以上时，它会盖过这里的「一直疾跑」</b>
     * （两者本质上是矛盾的：一个要恒速、一个要打散节奏）。想要全程疾跑就别开防踢的疾跑间歇档。
     *
     * <p>代价是饥饿值掉得更快，所以给了个开关。
     */
    public boolean lockSprint = true;

    /**
     * 饿到跑不动时自动吃东西。
     *
     * <p>触发条件就是「不能疾跑」（饥饿值 ≤ 6）：从快捷栏里挑一个
     * <b>回饱食度最多</b>的食物吃掉，直到又能跑为止。吃东西期间会停下来，
     * 吃完自动继续赶路。只动快捷栏，不翻背包。
     *
     * <p>默认开启：它服务的正是「锁定疾跑」，不然饿到 6 以下疾跑就断了、白开。
     */
    public boolean autoEat = true;

    /**
     * 进食时是否「一次吃到饱」。
     *
     * <p>打开（默认）：从饥饿值 ≤ 6 开始吃，一直吃到饱食度满 20 为止。
     * 关掉：吃到「又能跑了」（7 点）就停手。
     *
     * <p>为什么要吃到饱：只吃到能跑的话，饥饿值会一直在 7~8 附近晃，
     * 跑几十秒就得停下来再吃一口 —— 每次只吃一小口，频率高、打扰多。
     * 一口气吃到满，两次进食的间隔被拉长好几倍。
     */
    public boolean eatUntilFull = true;

    /**
     * 用「自由视角 / 灵魂出窍」时先停手。
     *
     * <p>开着自由视角的时候，摄像机和身体是分开的：屏幕上看到的人一动不动，
     * 可方块却自己在碎，画面很扎眼。打开这个开关，模组在这种状态下只保留
     * 描边和路线，不动你的角色；退出自由视角后自动继续。
     *
     * <p><b>默认关闭</b>：渲染色块和路线本来就是按摄像机算的，自由视角下画面不会错位，
     * 所以想「一边飞在天上看它干活」的话，保持关闭就好。
     */
    public boolean pauseOnFreecam = false;

    // ---- 安全与提示 ----

    /**
     * 在屏幕左上角常驻一行状态（HUD）。
     *
     * <p>显示三件事：当前在干什么、本趟已经收了多少、手边还有多少种子；
     * 有异常（背包满、缺种子、安全停机）时把原因也顶上来。
     * 以前这些只有打开面板才看得到，而收割的时候你最不想做的就是开界面。
     */
    public boolean showHud = true;

    /**
     * 血量低于这个值（单位：<b>心</b>）就停机，0 表示关掉这个保护。
     *
     * <p>收割的时候人是在低着头的，被怪偷袭、掉进坑里都反应不过来，
     * 所以给它一个「先保命」的开关。默认 4 心（半血）。
     * 回血到家之后自动继续，不用重新开开关。
     */
    public int lowHealthStopHearts = 4;

    /**
     * 附近有敌对生物时停机（默认开）。
     *
     * <p>判定半径固定 8 格，认的是原版那套「敌对生物」标记（僵尸、骷髅、苦力怕、
     * 蜘蛛……），所以模组的敌对怪一般也认得出来。
     * 它和血量保护是两个独立条件，任意一个成立就停手。
     */
    public boolean pauseWhenMobsNear = true;

    /** 敌对生物警戒半径（格）。固定值，不开放给面板 —— 免得条目越堆越多 */
    public static final double MOB_ALERT_RADIUS = 8.0;

    /**
     * 允许挖掉挡路的方块。
     *
     * <p>打开后，如果绕过去要付出更大代价，它会直接把挡路的方块挖穿走过去。
     * 代价按原版公式算真实挖掘时间，所以拿着铁镐挖石头很划算、
     * 徒手挖黑曜石则会被自动回避。
     *
     * <p><b>默认关闭</b>：这是会改变世界的操作，请确认你想让它动你的地形再打开。
     */
    public boolean allowMining = false;

    /**
     * 允许垫方块铺路（跨沟、过水、上不来的时候往脚下垫一块）。
     *
     * <p>消耗的是<b>快捷栏</b>里的方块：模组不会去翻背包深处，
     * 想让它拿来垫脚的东西请放进快捷栏。
     *
     * <p><b>默认关闭</b>：它会消耗你的材料。
     */
    public boolean allowPlacing = false;

    /**
     * 挖掘前是否自动换到快捷栏里最合适的工具。
     *
     * <p>挑工具的算法和原版破坏速度同一套（正确工具除数 30、否则 100），
     * 所以快捷栏里备一把镐子，遇到石头它会自己换过去；没有更好的工具就保持原样，不会乱动你的手。
     *
     * <p><b>默认开启</b>：它不改地形也不消耗东西，只是让挖掘更接近真人操作。
     */
    public boolean autoSelectTool = true;

    /**
     * 是否在世界中高亮显示扫描到的成熟作物。
     * 只在「自动收割」开启时生效 —— 关掉总开关就连高亮一起停，不会留着一片描边。
     */
    public boolean visualize = true;

    /**
     * 穿透显示：描边无视方块遮挡，隔着墙、土和草丛也能看见。
     * 关掉时描边会被前面的方块正常挡住（更接近原版观感）。
     *
     * <p>打开后绘制量会变大，所以扫描会自动降频（见 FarmTaskManager）。
     */
    public boolean highlightThroughWalls = false;

    // ---- 高亮描边的外观（面板里的调色板次级面板可调） ----

    /** 描边颜色，0xRRGGBB。默认橙红，和成熟作物对比明显 */
    public int highlightColor = 0xFF8C26;

    /**
     * 描边粗细，1~8，单位是<b>屏幕像素</b>。
     * 渲染时按「屏幕高度 + 视场角 + 距离」换算成世界尺寸，
     * 所以不管是 1080p 还是 4K、离得远还是近，看上去都是同样粗。
     */
    public int highlightWidth = 1;

    /** 描边不透明度，10~100（百分数） */
    public int highlightAlpha = 90;

    /** 补给点（箱子）坐标，null 表示尚未设置 */
    public Integer supplyX = null;
    public Integer supplyY = null;
    public Integer supplyZ = null;

    // ---- 扩展存储与补给：四个专用容器（独立坐标 + 独立启用） ----

    /** 农作物输出容器（存收获物）是否启用。禁用时收获物存回默认补给点 */
    public boolean outCropEnabled = true;
    public Integer outCropX = null;
    public Integer outCropY = null;
    public Integer outCropZ = null;

    /** 种子输入容器（取种子）是否启用。禁用时从默认补给点取种子 */
    public boolean inSeedEnabled = true;
    public Integer inSeedX = null;
    public Integer inSeedY = null;
    public Integer inSeedZ = null;

    /** 工具补给容器（取工具 / 骨粉）是否启用。禁用时从默认补给点取 */
    public boolean inToolEnabled = true;
    public Integer inToolX = null;
    public Integer inToolY = null;
    public Integer inToolZ = null;

    /** 杂物临时存储容器（存非农作物物品）是否启用。默认关闭 —— 开启后杂物单独存放 */
    public boolean outJunkEnabled = false;
    public Integer outJunkX = null;
    public Integer outJunkY = null;
    public Integer outJunkZ = null;

    /**
     * 默认种子槽位，取值 1~9（就是快捷栏从左数第几格）。
     *
     * <p>它有两个用处：
     * <ul>
     *   <li><b>补种时优先用它</b>：要种的作物种子正好在这一格，就直接拿；不在这一格，
     *       先在快捷栏里别处找，找不到再深搜整个背包；</li>
     *   <li><b>背包满时的落脚点</b>：从补给点的箱子里取种子回来时，如果背包一个空格都没有，
     *       就把那一组种子<b>直接换到这一格</b>（格子里原来那件东西会被塞回箱子），
     *       这样「种子该放哪」永远是确定的，不会因为没空格就取不回来。</li>
     * </ul>
     *
     * <p>默认 9：最右边那一格，离按数字键的常用手位最远，不容易和玩家自己拿的东西打架。
     */
    public int defaultSeedSlot = 9;

    /**
     * <b>旧的</b>「备用保留组数」，现在只用来做一次性迁移。
     *
     * <p>以前存放只有这一条能调的数：给「收获物本身就是种子」的作物（胡萝卜、土豆）
     * 留几组。现在改成了逐类设置的 {@link #storageKeep}，这个值只在
     * 「配置文件里还没有 storageKeep」时，当作「收获物即种子」那一类的初始值 ——
     * 这样老配置里调过 3 组的，升级后还是 3 组，不会被悄悄改回 1。
     */
    public int backupCropStacks = DEFAULT_BACKUP_STACKS;

    /**
     * 逐类物品的存放策略，下标 = {@link ItemCategory#ordinal()}。
     *
     * <p>每一个格子取三种值：
     * <ul>
     *   <li><b>-1</b> {@link ItemCategory#KEEP_ALL} —— 一栈都不存，全留身上；</li>
     *   <li><b>0</b> {@link ItemCategory#STORE_ALL} —— 全部存进箱子；</li>
     *   <li><b>n（1~5）</b> —— 最多留 n 组，多出来的存掉。</li>
     * </ul>
     *
     * <p>默认值就是以前那套行为：补种种子 / 食物 / 方块 / 工具 → 不存；
     * 胡萝卜、土豆、甘蔗这类「收获物即种子」→ 留 {@link #DEFAULT_BACKUP_STACKS} 组；
     * 甜菜根这类「收获食物」（能吃、但收下来种不回去）和小麦这类纯收获物 → 全存。
     *
     * <p><b>null 表示这份配置是旧版本存的</b>，还没有这个字段。
     * 这时 {@link #clamp()} 会按 {@link #backupCropStacks} 补一份出来。
     */
    public int[] storageKeep = null;

    /** 出厂默认的存放策略：七类各自的原样 + 「收获物即种子」留 {@code selfSeedStacks} 组 */
    public static int[] defaultStorageKeep(int selfSeedStacks) {
        int[] keep = new int[ItemCategory.count()];
        for (ItemCategory category : ItemCategory.values()) {
            keep[category.ordinal()] = category.defaultKeep();
        }
        keep[ItemCategory.SELF_SEED.ordinal()] = clampKeep(selfSeedStacks);
        return keep;
    }

    /** 「恢复默认」按钮用的那一份 */
    public static int[] factoryStorageKeep() {
        return defaultStorageKeep(DEFAULT_BACKUP_STACKS);
    }

    /** 把策略值拉回合法范围：负数一律当成 -1，超过上限的截断 */
    public static int clampKeep(int value) {
        if (value < 0) {
            return ItemCategory.KEEP_ALL;
        }
        return Math.min(value, ItemCategory.MAX_KEEP);
    }

    /**
     * 这一类物品最多留几栈。
     *
     * <p>顺手把 {@link #storageKeep} 补成完整的一份 ——
     * 面板里的「草稿」是新建对象、不走 {@link #clamp()}，所以这里自己兜底，
     * 免得任何一个调用点读到 null。
     */
    public int keepFor(ItemCategory category) {
        if (category == null) {
            return ItemCategory.KEEP_ALL;
        }
        int[] keep = storageKeep;
        if (keep == null || keep.length != ItemCategory.count()) {
            keep = defaultStorageKeep(backupCropStacks);
            storageKeep = keep;
        }
        return keep[category.ordinal()];
    }

    /** 改某一类的存放策略 */
    public void setKeepFor(ItemCategory category, int value) {
        if (category == null) {
            return;
        }
        keepFor(category); // 副作用：先把数组补成完整的一份
        storageKeep[category.ordinal()] = clampKeep(value);
    }

    // ---- 作物与筛选 ----

    /** 作物筛选：全收，不过滤 */
    public static final int FILTER_OFF = 0;
    /** 作物筛选：只收名单里勾选的 */
    public static final int FILTER_WHITELIST = 1;
    /** 作物筛选：收名单以外的（名单是「不收」的黑名单） */
    public static final int FILTER_BLACKLIST = 2;
    /** 作物筛选：只收「手边还有种子能补种」的作物 */
    public static final int FILTER_ONLY_REPLANTABLE = 3;
    /** 筛选模式的档数（面板上的循环按钮用） */
    public static final int FILTER_MODE_COUNT = 4;

    /**
     * 补种空耕地：把附近<b>空着的耕地</b>也种上（默认关闭）。
     *
     * <p>平时它只补「刚被自己收掉的那一格」—— 那块地是熟门熟路的，收完顺手种回去。
     * 打开这个开关之后，扫描会额外把「上面是空气、下面是耕地」的格子也当成目标，
     * 走过去把种子种上。适合那种「田里本来就有几垄没种」的情况。
     *
     * <p>它是「收」之外的动作，而且<b>会消耗种子</b>，所以和「允许垫方块」一样默认关着。
     */
    public boolean fillEmptyFarmland = false;

    /**
     * 空地补种哪一种作物（作物方块注册名，例如 {@code minecraft:wheat}）。
     * {@code null} = 自动：挑手边数量最多的那种种子。
     */
    public String plantCrop = null;

    /** 当前筛选模式，取值是上面那几个 {@code FILTER_*} 常量 */
    public int cropFilterMode = FILTER_OFF;

    /**
     * 筛选名单：勾选过的作物方块注册名。
     *
     * <p>同一个名单在两种模式下含义相反 —— 白名单里它是「只收这些」，
     * 黑名单里它是「不收这些」。这样切换模式时不用维护两份数据。
     */
    public List<String> cropFilter = new ArrayList<>();

    // ================================================================
    // 附加功能（Extras）
    //
    // 统一放在「附加功能」次级面板里，**全部默认关闭**：
    // 不打开它们，模组的行为和以前一模一样。它们是「锦上添花」的一层，
    // 有的改变节奏（拟人化、随机休息），有的改变安全边界（玩家接近停机），
    // 有的只是多干活（工具换新、失败重试、HUD 统计）——
    // 共同点是：不做任何绕过游戏规则的事，只是替玩家做他本来也会做的决定。
    // ================================================================

    /**
     * 附近有别的玩家时停机。
     *
     * <p>自动收割最怕的从来不是怪物，而是<b>被别的玩家看见</b>：一个低着头、
     * 动作整齐划一的人形生物，谁看都觉得不对劲。打开这个开关，附近出现其他玩家
     * 就先停手、装成挂机，人走远了再继续。
     *
     * <p>判定只看<b>服务端同步过来的玩家实体</b>，半径见 {@link #playerNearRadius}。
     * 离线单人世界里永远不会有别人，所以这项只在服务器上有意义。
     */
    public boolean pauseWhenPlayerNear = false;

    /** 「附近有玩家时停机」的警戒半径（格）。 */
    public int playerNearRadius = 16;

    /**
     * 拟人化节奏：收完一棵之后随机愣一下，再去下一棵。
     *
     * <p>机器和真人最大的区别往往不在「做了什么」，而在「节奏」：真人不会以
     * 恒定间隔连续操作几十次。打开之后，每个目标之间插入一小段随机停顿
     * （约 0.1~0.3 秒），让整段动作的时间分布不再是一条直线。
     *
     * <p>代价是会略微变慢，而且它是纯粹的「防检测」措施、不提升任何效率。
     */
    public boolean humanizeTiming = false;

    /**
     * 定时随机休息：连续工作一段时间后自动歇一会儿。
     *
     * <p>和拟人化节奏是同一个思路，只是尺度更大 —— 拟人化管的是「动作之间」，
     * 这个管的是「整场作业」：干 {@link #restIntervalMinutes} 分钟就停下来
     * {@link #restDurationSeconds} 秒，像真人一样中途喝水、看消息。
     */
    public boolean randomRest = false;

    /** 连续工作多少分钟后休息一次（分钟） */
    public int restIntervalMinutes = 10;

    /** 每次休息停多久（秒） */
    public int restDurationSeconds = 20;

    /**
     * 工具耐久自动换新：手里的工具快用坏了，自动从背包换一把好的。
     *
     * <p>收割本身不怎么耗耐久（作物硬度是 0），但开着「允许挖路」时镐子是真会坏的。
     * 打开这个开关，工具剩余耐久低于 {@link #toolDurabilityPercent} 时会自动
     * 从背包里找一把<b>同种、而且更耐用</b>的换上；找不到就保持不动，
     * 绝不会把你好好的工具换走。
     */
    public boolean autoReplaceTool = false;

    /** 换工具的耐久阈值（百分数）：剩余耐久低于这个比例就换 */
    public int toolDurabilityPercent = 10;

    /**
     * 失败目标暂时拉黑：收不动的作物先跳过一个冷却，之后自动重试。
     *
     * <p>默认行为是「一个目标失败就本轮不再碰它」，得等它自己不再是成熟作物、
     * 或者你手动关开一次自动收割才会重来。这对「暂时性失败」太苛刻了
     * —— 比如那一下准星没跟上、背包刚腾出一个格子。
     * 打开后，失败的目标只冷却 {@link #failureCooldownSeconds} 秒，
     * 到点自动重新排进路线，不用你插手。
     */
    public boolean failureCooldown = false;

    /** 失败目标的冷却时长（秒） */
    public int failureCooldownSeconds = 15;

    /**
     * HUD 详细统计：状态栏多显示一行本轮运行时长与补种数。
     *
     * <p>默认那两行告诉你「在干嘛、收了多少、种子够不够」；打开这个会再加一行
     * 「干了多久、补了几棵」，方便估算效率、或者单纯看看它这一趟的战果。
     */
    public boolean verboseHud = false;

    /**
     * 优先拾取掉落物：把散落在田里的作物掉落物专门捡回来（默认关闭）。
     *
     * <p>作物被挖掉之后，掉落物不一定乖乖待在原地：水流会把它推走、爆炸会把它崩飞、
     * 旁边的怪或者别的玩家也可能把它撞开。人接着往下一棵走，等回头时东西已经不在脚下了
     * —— 一格作物就这么白白丢在地里。
     *
     * <p>打开这个开关，模组会盯着「掉在地上、超过 {@link #pickupDelaySeconds} 秒还没进包」
     * 的作物掉落物，把它们排在路线最前面，走过去把它们收回来。判据是掉落物实体的
     * <b>存在时长</b>（原版自己会算），所以刚掉出来、正在被吸走的那一瞬间不会误触发。
     */
    public boolean pickupDroppedItems = false;

    /**
     * 掉落物「掉在外面了」的判定延迟（秒）。
     *
     * <p>掉落物刚落地的那几 tick，原版还在把它往玩家身上吸，这时候不该当成「漏捡」。
     * 等它在地上待够这么久还没进背包，才认为它是真的没被捡到，值得专门跑一趟。
     */
    public int pickupDelaySeconds = 1;

    // ---- 瓜类与自定义作物（瓜茎识别 + 自定义方块） ----
    //
    // 原版的西瓜 / 南瓜不是「作物方块」：它们没有 age，茎才带 age。
    // 所以默认扫描认不出「长在茎旁边的瓜」。这一组专补这个洞，再加一个
    // 玩家自定义方块的出口，让「别的模组的作物 / 你特意想收的东西」也能被认出来。

    /**
     * 瓜茎识别：收割「紧挨着瓜茎、由茎自然长出来的瓜方块」（默认关闭）。
     *
     * <p>西瓜 / 南瓜的植株是<b>茎</b>（{@code StemBlock}），瓜只是长在茎旁边的一格。
     * 默认扫描只认「带 age、长满了的作物」，所以茎长满后它旁边那格瓜会被当成
     * 普通方块跳过 —— 于是瓜田永远收不到。打开这个开关，扫描会额外找出
     * <b>紧挨瓜茎的那格瓜方块</b>（西瓜块 / 南瓜块），把它们当作「挖掉就收」的目标：
     * 挖掉瓜、茎留着，之后茎会继续长下一个瓜。
     *
     * <p>它刻意<b>只认紧挨着瓜茎的瓜</b>：那才是「茎自然长出来的」。
     * 视野里其他地方的瓜（哪怕是玩家自己放的）不会被扫到 —— 这既避免误挖你摆的装饰，
     * 也说明它<b>不是矿透（X-ray）</b>：它不透视、不高亮视野里的其它方块，
     * 只顺着作物本身的生长结构去找收成。
     */
    public boolean melonHarvest = false;

    /**
     * 自定义要识别的方块（方块注册名列表，例如 {@code minecraft:melon}）。
     *
     * <p>瓜茎识别只覆盖西瓜 / 南瓜两种，所以留了这道口子：把你想要模组顺带
     * 「挖掉收走」的方块加进来（别的模组的作物、或者特殊方块）。列表非空即生效，
     * 扫描时凡注册名在名单里的方块都会被当成收割目标，挖掉即收、不补种。
     *
     * <p><b>只加农作物类的方块</b>：这些方块挖下来之后，产物会按「作物收获物」去
     * 存放、归箱；加石头、矿物这类非作物方块，挖下来的东西对不上存放分类，
     * 可能一直留在背包里、甚至把「补种 / 存箱」的流程卡住。所以界面在添加时会
     * 专门提醒一次。同样地，本功能<b>不是矿透</b>：它只识别你手动加进名单、且
     * 和作物相关的方块，不透视、不高亮任何你没指定的东西。
     */
    public List<String> customHarvestBlocks = new ArrayList<>();

    // ---- 资源自给（D 组：骨粉催熟 + 从补给点自取耗材 / 备用工具） ----
    //
    // 前面那些附加功能管的是「像不像人」「安不安全」，这一组管的是**续航**：
    // 种子会自己补（refillSeeds），可骨粉用完了、镐子磨断了，仍然只能干等玩家回来。
    // 这一组让模组把这两样也自己接上。

    /**
     * 骨粉催熟：对<b>还没熟</b>的作物自动用骨粉推进生长（默认关闭）。
     *
     * <p>默认扫描只找「已经熟了」的作物，所以从种下到成熟那段时间，角色只能干等随机刻。
     * 打开这个开关后，它会把田里<b>还没熟、但已经种下了</b>的作物也当作目标：
     * 走到跟前、切一块骨粉到手上、对准作物右键一次。
     *
     * <p>骨粉的效果是<b>随机</b>推进 0~2 个生长阶段，所以可能要撒好几次才熟；
     * {@link #boneMealLimit} 就是「同一株最多撒几次」的上限 —— 撒满还没熟就放弃，
     * 免得在一株上耗掉整袋骨粉。
     *
     * <p>它会<b>消耗骨粉</b>，所以默认关闭。而且和「防踢」是矛盾的：
     * 真人不会对着每一棵没熟的苗挨个撒骨粉，服务器看到这种规律动作反而显眼 ——
     * 当成「赶时间的时候用一下」就好，别当成常规开着的功能。
     */
    public boolean boneMealBoost = false;

    /**
     * 同一株作物最多撒几次骨粉。
     *
     * <p>原版一次骨粉随机推进 0~2 段，从幼苗到成熟通常 2~4 次就够；
     * 上限存在的意义是「万一这株是那种怎么催都不熟的（比如需要特定光照），
     * 别把整袋骨粉耗在它身上」。
     */
    public int boneMealLimit = 4;

    /**
     * 耗材自给：骨粉低于 {@link #boneMealThreshold} 个时，自动去补给点取（默认关闭）。
     *
     * <p>它是 {@link #boneMealBoost} 的续航配套 —— 光有催熟、没骨粉，等于白开。
     * 取的量和取种子一样：走「Shift + 左键」一键搬一栈，不多拿。
     */
    public boolean autoFetchSupplies = false;

    /**
     * 骨粉少于多少个就该去补给点补一趟。
     *
     * <p>不是「用光了才去」：真人是发现快没了就顺手补，而不是每次用到 0 才跑一趟。
     */
    public int boneMealThreshold = 16;

    /**
     * 备用工具自给：背包里一把<b>还带余量</b>的工具都没有时，自动去补给点取（默认关闭）。
     *
     * <p>和附加功能「工具维护」（从背包里换一把同种更好的）是两件事：
     * 那个解决「手上这把快断了」，这个解决「背包里全断了」。
     * 后者是真正会卡住作业的一种 —— 收割本身不怎么耗耐久，但「允许挖路」开着时镐子是真会坏的，
     * 一把都没了只能等玩家回来。
     *
     * <p>判定很保守：只认<b>还有至少一半耐久</b>的「有耐久物品」（工具、武器、鞘翅）。
     * 从箱子里取回来的是原版那把东西，取哪一把由原版规则决定，不做任何额外操作。
     */
    public boolean autoFetchTool = false;

    // ---- 交互便利（F 组：音效 / 桌面通知） ----
    //
    // 前面几组管的是「做什么、怎么做」，这一组管的是「怎么让人知道」。
    // 默认全关：切走屏幕干别的、或者不想被音效打扰时，它们就不出声。

    /**
     * 音效通知：开始 / 停止 / 异常时播一个提示音（默认关闭）。
     *
     * <p>三件事各配一个音，一听就知道发生了什么：
     * <ul>
     *   <li><b>开始</b> —— 打开自动收割时；</li>
     *   <li><b>停止</b> —— 因安全 / 满包 / 越界等原因停机，或关掉开关时；</li>
     *   <li><b>异常</b> —— 收不动、补给失败这类「没干成」时。</li>
     * </ul>
     * 走的都是原版音效（{@code client.player.playSound}），不额外引入资源。
     */
    public boolean notifySounds = false;

    /**
     * 桌面通知：开始 / 停止 / 异常时在右上角弹一个原版 toast（默认关闭）。
     *
     * <p>和音效是两条独立的通道 —— 挂机时人可能不在电脑前，音效听不到，
     * toast 会在右上角留几秒；音效则是「人在屏幕前」时更及时的那一个。
     * 覆盖的事件和 {@link #notifySounds} 完全一样。
     */
    public boolean notifyToasts = false;

    /**
     * 防踢模式：把整套「机器味」一次性抹掉（默认关闭）。
     *
     * <p>它和上面的「拟人化节奏」不是一回事：拟人化节奏只做了一件事 ——
     * 收完一棵停一下。防踢模式是一整套，见 {@code Humanizer}：
     * 转向不再是匀速的、视角不再精确得像刻上去的、脚步偶尔悬一下、
     * 疾跑有间歇、动手前会犹豫、干久了会「累」、甚至偶尔不走最优路线。
     *
     * <p>它<b>不改任何游戏规则</b>：不发额外的包、不突破移动速度上限、
     * 不绕过碰撞检测 —— 只是把本来恒定不变的那几个量换成有惯性的随机量。
     *
     * <p>代价是会慢一些，所以给了 {@link #antiKickLevel} 四档让你自己拿捏。
     */
    public boolean antiKickMode = false;

    /**
     * 防踢档位：0 = 关闭、1 = 轻度、2 = 中度、3 = 强力（常量见 {@code Humanizer}）。
     *
     * <p>档位越高，加进来的措施越多、越明显，也越慢。默认中度：
     * 打开开关就能感觉到区别，但还不至于明显拖慢作业。
     */
    public int antiKickLevel = 2;

    /**
     * 危险反应延迟：遇到危险时不「瞬间」停手（默认关闭）。
     *
     * <p>防踢模式解决的是「动作节奏」，这里解决的是<b>决策时机</b>。
     * 现在一旦附近冒出敌对生物、或者血量掉了下去，模组会在<b>同一 tick</b> 停手 ——
     * 而真人从「看见危险」到「松手」总要花上几百毫秒。人反应得比机器还快，本身就是破绽。
     *
     * <p>打开它之后，危险第一次出现会先「愣」{@link #reactionDelayTicks} tick 才真正停手；
     * 危险种类变了（比如从「有怪」变成「血量低」）会重新计时。
     * 代价是这几百毫秒里人是照常在干活的，所以默认关着。
     */
    public boolean reactionDelay = false;

    /** 「危险反应延迟」的延迟时长（tick）。默认 10 tick（0.5 秒） */
    public int reactionDelayTicks = 10;

    /**
     * 空闲时环顾四周：没事干的时候视角缓慢地东张西望（默认关闭）。
     *
     * <p>作物收完了、或者在休息，模组会把角色定在原地。真人的「站着不动」其实
     * 视角也在慢慢游走 —— 一个把视准星钉死在同一个方向、连呼吸般的小幅转头都没有的角色，
     * 反而比在动更显眼。
     *
     * <p>打开后只在这三种空闲状态生效：等作物成熟、定时休息、其它无事可做；
     * <b>危险停机时不会环顾</b>（那时候「装死」比「东张西望」安全）。
     * 而且它每次都会和你自己的视角比对：你一动手把鼠标，它立刻让开。
     */
    public boolean idleLookAround = false;

    /**
     * 仅在指定时段工作：只在设定的时间窗口内收割（默认关闭）。
     *
     * <p>前面那些措施都是在「看起来像不像人」，但有一条比它们都致命 ——
     * <b>这个账号 24 小时不下线</b>。真人再肝也要睡觉，一个从不出现在凌晨 3 点的角色，
     * 一眼就能看出不对。
     *
     * <p>用<b>本机时间</b>判断：在 {@link #scheduleStartHour} 到 {@link #scheduleEndHour}
     * 之间才干活，其余时间停手。注意它<b>只是停止收割，账号仍然在线</b> ——
     * 想彻底隐藏在线时长，还得配合「到点自己下线」。
     */
    public boolean scheduleEnabled = false;

    /** 工作时段起点（0~23 整点）。默认 8 点 */
    public int scheduleStartHour = 8;

    /** 工作时段终点（0~23 整点，不含这一小时）。默认 23 点 */
    public int scheduleEndHour = 23;

    // ---- 工作范围（B 组①：圈定自己的地） ----

    /**
     * 圈定工作区域：只在划定的范围里干活（默认关闭）。
     *
     * <p>上面那些措施管的是「像不像人」，这一项管的是两件更朴素的事 ——
     * <b>别跑丢、也别踩到别人的田</b>。打开之后，模组只认落在工作区域里的目标：
     * 圈外的成熟作物不去收，圈外的掉落物也不去捡。人一旦自己走出这个圈，
     * 模组会先停手，再沿一条回程路线把人带回区域中心附近。
     *
     * <p>区域有两种圈法（{@link #workAreaMode}）：
     * <ul>
     *   <li><b>圆形</b>（{@link #AREA_MODE_RADIUS}）—— 记一个中心点，配一个半径，
     *       适合「以我家这口箱子为中心的一片地」；判定只看水平距离（X/Z），上下坡不影响；</li>
     *   <li><b>两点矩形</b>（{@link #AREA_MODE_BOX}）—— 在一头记角点 A、另一头记角点 B，
     *       两点拉出一个长方体，适合「我那几垄是规规矩矩的长条」；判定含高度（Y）。</li>
     * </ul>
     * 两种模式共用 {@link #workAreaX}/{@link #workAreaY}/{@link #workAreaZ} 这组坐标：
     * 圆形模式里它是中心，矩形模式里它是角点 A（角点 B 另有三个字段）。
     * 坐标没记全时功能一律不生效（免得「开了功能却满世界跑」）。
     */
    public boolean workAreaEnabled = false;

    /** 圆形：以「中心 + 半径」圈定 */
    public static final int AREA_MODE_RADIUS = 0;
    /** 矩形：以「角点 A + 角点 B」圈定 */
    public static final int AREA_MODE_BOX = 1;
    /** 模式档数（面板上的循环按钮用） */
    public static final int AREA_MODE_COUNT = 2;

    /** 范围方式，取值是上面两个 {@code AREA_MODE_*} 常量 */
    public int workAreaMode = AREA_MODE_RADIUS;

    /**
     * 工作区域的第一个坐标点。
     * 圆形模式 = 中心点；矩形模式 = 角点 A。null = 未设置。
     */
    public Integer workAreaX = null;
    /** 第一个坐标点的 Y */
    public Integer workAreaY = null;
    /** 第一个坐标点的 Z */
    public Integer workAreaZ = null;

    /** 圆形模式的半径（格）。默认 24 —— 一般一块田足够，又不至于把邻居的地也圈进来 */
    public int workAreaRadius = 24;

    /** 矩形模式的角点 B（角点 A 见 {@link #workAreaX} 等）。null = 未设置 */
    public Integer workAreaX2 = null;
    /** 角点 B 的 Y */
    public Integer workAreaY2 = null;
    /** 角点 B 的 Z */
    public Integer workAreaZ2 = null;

    /** 工作区域半径的合法区间（格） */
    public static final int WORK_AREA_RADIUS_MIN = 4;
    public static final int WORK_AREA_RADIUS_MAX = 96;

    // ---- 环境与作息（B 组②③：看天干活 + 干够就收工） ----

    /**
     * 仅在白天工作（默认关闭）。
     *
     * <p>夜里怪物多，而且「一个每天凌晨都在线劳作的角色」本身就是破绽。
     * 打开后只在白天收割，日落即停手、天亮自动继续。用的是世界时间
     * （{@code world.isDay()}），不是本机时间 —— 你调过世界时间也没用。
     */
    public boolean workDaylightOnly = false;

    /**
     * 仅在晴天工作（默认关闭）。
     *
     * <p>下雨天本来就少见有人在外面割麦子，而且雨天视野差、容易漏看怪。
     * 打开后下雨（含雷暴）时停手，放晴自动继续。
     */
    public boolean workClearWeatherOnly = false;

    /**
     * 疲劳收工：连续工作太久就整场停下来（默认关闭）。
     *
     * <p>它和上面的 {@link #randomRest}（定时随机休息）不是一个尺度：
     * 定时休息是干 {@link #restIntervalMinutes} 分钟就歇 {@link #restDurationSeconds} 秒，
     * 属于「中途喝口水」；疲劳收工管的是<b>整场作业</b> —— 连续干
     * {@link #fatigueWorkMinutes} 分钟之后彻底收工，休息 {@link #fatigueRestMinutes}
     * 分钟（设为 0 则是「等你自己来开」）才重新上工。
     *
     * <p>它同时是省心和安全阀：一场下来连着干四五个小时、人却不吃不喝不挪窝，
     * 既容易被看出不对，也容易在你没盯屏幕的时候出事。
     */
    public boolean fatigueStop = false;

    /** 连续工作多少分钟后收工（分钟）。默认 60 */
    public int fatigueWorkMinutes = 60;

    /**
     * 收工后休息多久自动上工（分钟）。<b>0 = 手动</b>：一直停着，直到你把它
     * （或总开关）关掉再打开。
     */
    public int fatigueRestMinutes = 15;

    // ---- 生存自保（C 组：危险撤退 / 受击反制 / 环境自救 / 掉线重连） ----
    //
    // 前面那一大组管的是「像不像人」，这一组管的是更硬的四个字：别把号玩没了。
    // 它们同样默认全关 —— 不打开就等于不存在。

    /**
     * 危险撤退：血量偏低、附近又有威胁时，主动撤到安全距离（默认关闭）。
     *
     * <p>它和主面板的「低血量时停机」是一对：那个是<b>原地站着不动</b>等回血，
     * 这个更进一步 —— 血掉到 {@link #retreatHealthHearts} 心以下、而且附近
     * 有敌对生物时，模组会朝着<b>远离那只怪</b>的方向走开 {@link #retreatDistance} 格，
     * 先脱离接触再等回血。
     *
     * <p>撤退<b>只尝试一次</b>：这条路走不通就退回「原地停机」——不会为了逃命
     * 满世界乱跑。血量恢复到家以后自动继续干活，不用重新开开关。
     *
     * <p>只认敌对生物，<b>不针对玩家</b>：被别的玩家追着打属于 PVP，
     * 模组不介入，那边请自己按着 Shift 跑。
     */
    public boolean retreatEnabled = false;

    /** 撤退的血量门槛（心）。掉到它以下就撤；建议留在「低血量停机」门槛之上 */
    public int retreatHealthHearts = 6;

    /** 撤退时朝反方向走多远（格） */
    public int retreatDistance = 16;

    /** 撤退血量门槛的合法区间（心） */
    public static final int RETREAT_HEARTS_MIN = 3;
    public static final int RETREAT_HEARTS_MAX = 10;

    /**
     * 受击反制：挨打的那一下做出反应（默认关闭）。
     *
     * <p>模组的角色挨打时本来是<b>毫无反应</b>的 —— 继续低着头收菜。这在真人身上
     * 说不过去：被捅一刀，人总会停手、回头看是谁。打开这个开关，受伤的那一 tick
     * 模组会<b>立刻停下手上的活</b>，并把视角限速转向攻击者，持续
     * {@link #hitReactionSeconds} 秒。
     *
     * <p>如果同时还开着<b>自动打怪</b>，这个被锁定的攻击者会被优先交给战斗逻辑反击；
     * 没开自动打怪时，它就只是「停手 + 回头看一眼」——依旧不主动出手打任何人。
     */
    public boolean hitReactionEnabled = false;

    /** 受击反制持续多久（秒） */
    public int hitReactionSeconds = 2;

    /**
     * 环境自救：把角色从「环境危险」里捞出来（默认关闭）。
     *
     * <p>两类场景：
     * <ul>
     *   <li><b>溺水</b> —— 水下的氧气见底时自动上浮，别让角色在水里憋到掉血；</li>
     *   <li><b>着火</b> —— 身上着火、附近又正好有水时，朝最近的水跑过去。</li>
     * </ul>
     * 都走原版那套移动输入，不改任何游戏规则。着火时附近没有水就只能作罢
     * （那属于没法自救的情况）。它和「危险撤退」一样排在所有收割动作之前 ——
     * 人都在水里了，收菜的事先放一放。
     */
    public boolean selfRescueEnabled = false;

    /**
     * 掉线重连：意外断开时自动回到上一个多人服务器（默认关闭）。
     *
     * <p>它只处理<b>多人服务器</b>：进服务器之后记下地址，一旦连接被意外切断
     * （超时、服务器重启、被踢），等 {@link #reconnectDelaySeconds} 秒后自动重连，
     * 最多重试 {@link #reconnectMaxAttempts} 次（0 = 不限次数）。
     *
     * <p>你自己点「保存并退出」回到标题页<b>不会</b>被拉回来 —— 那是有意为之。
     * 单人存档也不重连。
     */
    public boolean autoReconnectEnabled = false;

    /** 掉线后等多少秒再重连 */
    public int reconnectDelaySeconds = 10;

    /** 最多重连几次；<b>0 = 不限</b> */
    public int reconnectMaxAttempts = 3;

    // ---- 战斗（高风险，默认关闭） ----
    //
    // 上面所有附加功能都在「不改变游戏规则」的框架里；这一组是唯一破例的：
    // 它替玩家出手攻击生物，等于把常见的「杀戮光环（Kill Aura）」搬进模组。
    // 所以它单独成组、默认关闭、面板上配警示色、打开前还要过一次风险确认屏。

    /**
     * 自动打怪（<b>高风险</b>，默认关闭）。
     *
     * <p>附近出现可攻击的生物时，模组会替玩家转向、出手。默认走
     * <b>非暴力</b>路线（见 {@link #autoAttackViolent}）：先限速转头把准星压到目标身上，
     * 转到位才挥手，而且两次出手之间留着随机间隔 —— 服务端看到的是一个
     * 「会转头、会瞄准、节奏像人」的普通玩家。
     *
     * <p>打开它有几层代价，都在风险确认屏里写了：它属于作弊级功能，
     * 在多人服务器使用可能被封；「非暴力」只是降低被识别的概率，
     * 并不保证安全，而且因为出手慢，角色本身仍有被怪打死的风险。
     *
     * <p><b>它不能脱离 {@link #enabled}（自动收割总开关）单独开启</b>：
     * 总开关关着时，本项会被 {@link #clamp()} 强制收掉 ——
     * 这个模组是「自动收菜」，而不是一台纯粹的杀戮光环。
     */
    public boolean autoAttack = false;

    /**
     * 暴力模式（默认关闭，<b>风险最高</b>）。
     *
     * <p>非暴力模式为了像人，每次出手前都要花几 tick 把头转过去，因此命中率与观感
     * 都受转向速度限制。打开这一项之后，模组<b>不再转视角</b>：只要目标在攻击距离内，
     * 保持当前视线直接出手 —— 服务端那一边看到的就是「玩家挥了下手，
     * 某个并不在准星上的生物掉了血」。
     *
     * <p>这正是反作弊最敏感的模式，因此它是<b>二级开关</b>：
     * 只有在 {@link #autoAttack} 打开之后才会出现，默认始终为关。
     */
    public boolean autoAttackViolent = false;

    /**
     * 两次出手之间的间隔（毫秒）。
     *
     * <p>无论哪种模式，实际间隔都会在这附近随机浮动（约 ±20%），
     * 免得每一下都精确等时 —— 那是节拍器，不是人。
     *
     * <p>面板上是一个<b>输入框</b>（不是循环按钮），合法区间见
     * {@link #ATTACK_INTERVAL_MIN_MS}~{@link #ATTACK_INTERVAL_MAX_MS}；低于
     * {@link #ATTACK_INTERVAL_SAFE_MS} 会在面板上标红警示 ——
     * 出手频率异常是最容易被反作弊盯上的特征之一。
     */
    public int attackIntervalMs = 800;

    /**
     * 是否只打敌对生物（默认是）。
     *
     * <p>打开时只认原版那套「敌对生物」标记（僵尸、骷髅、苦力怕、蜘蛛……）；
     * 关掉则会打所有生物（动物、村民这类也会算进去）。
     * <b>玩家永远不在目标范围内</b> —— 打了玩家就是纯粹的 PVP 作弊，
     * 和「保护自己收菜」是两回事，这条不开放。
     */
    public boolean attackHostileOnly = true;

    // ---- 战斗：可调的「暴力」参数（改动都要过风险确认屏） ----

    /** 攻击间隔的合法区间（毫秒） */
    public static final int ATTACK_INTERVAL_MIN_MS = 50;
    public static final int ATTACK_INTERVAL_MAX_MS = 5000;

    /** 攻击间隔的「安全下限」：低于它会在面板上标红，提示出手频率异常 */
    public static final int ATTACK_INTERVAL_SAFE_MS = 300;

    /** 攻击距离的合法区间（格） */
    public static final double ATTACK_RANGE_MIN = 2.0;
    public static final double ATTACK_RANGE_MAX = 6.0;

    /**
     * 原版实体交互距离（格）。攻击距离超过它，服务端的攻击包多半会被拒收，
     * 还可能被记进「攻击距离异常」。调到它之上要先过风险确认屏。
     */
    public static final double ATTACK_RANGE_VANILLA = 3.0;

    /** 暴力程度档位 */
    public static final int VIOLENCE_LIGHT = 0;
    public static final int VIOLENCE_MEDIUM = 1;
    public static final int VIOLENCE_LIFESAVER = 2;
    /** 档位总数（面板循环按钮用） */
    public static final int VIOLENCE_COUNT = 3;

    /**
     * 自动打怪的最大攻击距离（格）。出厂 2.9 —— 原版实体交互距离是 3.0，
     * 往里收一点留余量。
     *
     * <p><b>可调</b>：在「附加功能 → 战斗」里能往上调，但超过
     * {@link #ATTACK_RANGE_VANILLA} 会先弹风险确认屏 ——
     * 超距的攻击包服务端通常会拒收，还可能被记进「攻击距离异常」。
     */
    public double attackRange = 2.9;

    /**
     * 暴力程度：只在 {@link #autoAttackViolent} 打开时生效，越高越凶。
     *
     * <ul>
     *   <li>{@link #VIOLENCE_LIGHT}（默认）—— 高危怪（末影人、爆炸/远程类）
     *       无视出手间隔优先打；</li>
     *   <li>{@link #VIOLENCE_MEDIUM} —— 上面全部 + 所有目标出手间隔减半；</li>
     *   <li>{@link #VIOLENCE_LIFESAVER} —— 上面全部 + 所有目标都无视间隔
     *       （每个 tick 都能出手），以最快速度清掉威胁，尽量让角色不被怪打死。
     *       这也是风险最高的一档。</li>
     * </ul>
     */
    public int attackViolence = VIOLENCE_LIGHT;

    /** 当前启用了几个附加功能（主面板摘要行用） */
    public int extrasEnabledCount() {
        int n = 0;
        if (pauseWhenPlayerNear) n++;
        if (humanizeTiming) n++;
        if (randomRest) n++;
        if (autoReplaceTool) n++;
        if (failureCooldown) n++;
        if (verboseHud) n++;
        if (pickupDroppedItems) n++;
        if (melonHarvest) n++;
        if (customHarvestBlocks != null && !customHarvestBlocks.isEmpty()) n++;
        if (boneMealBoost) n++;
        if (autoFetchSupplies) n++;
        if (autoFetchTool) n++;
        if (notifySounds) n++;
        if (notifyToasts) n++;
        if (antiKickMode) n++;
        if (reactionDelay) n++;
        if (idleLookAround) n++;
        if (scheduleEnabled) n++;
        if (workAreaEnabled) n++;
        if (workDaylightOnly) n++;
        if (workClearWeatherOnly) n++;
        if (fatigueStop) n++;
        if (retreatEnabled) n++;
        if (hitReactionEnabled) n++;
        if (selfRescueEnabled) n++;
        if (autoReconnectEnabled) n++;
        if (autoAttack) n++;
        return n;
    }

    /** 复制一份，供面板做「草稿」，取消时可以整体丢弃 */
    public AutoFarmConfig copy() {
        AutoFarmConfig c = new AutoFarmConfig();
        c.enabled = this.enabled;
        c.scanRadius = this.scanRadius;
        c.scanIntervalTicks = this.scanIntervalTicks;
        c.replant = this.replant;
        c.aimMode = this.aimMode;
        c.turnDurationMs = this.turnDurationMs;
        c.refillSeeds = this.refillSeeds;
        c.depositWhenFull = this.depositWhenFull;
        c.optimizePath = this.optimizePath;
        c.showPath = this.showPath;
        c.lockSprint = this.lockSprint;
        c.autoEat = this.autoEat;
        c.eatUntilFull = this.eatUntilFull;
        c.pauseOnFreecam = this.pauseOnFreecam;
        c.showHud = this.showHud;
        c.lowHealthStopHearts = this.lowHealthStopHearts;
        c.pauseWhenMobsNear = this.pauseWhenMobsNear;
        c.allowMining = this.allowMining;
        c.allowPlacing = this.allowPlacing;
        c.autoSelectTool = this.autoSelectTool;
        c.visualize = this.visualize;
        c.highlightThroughWalls = this.highlightThroughWalls;
        c.highlightColor = this.highlightColor;
        c.highlightWidth = this.highlightWidth;
        c.highlightAlpha = this.highlightAlpha;
        c.supplyX = this.supplyX;
        c.supplyY = this.supplyY;
        c.supplyZ = this.supplyZ;
        c.outCropEnabled = this.outCropEnabled;
        c.outCropX = this.outCropX;
        c.outCropY = this.outCropY;
        c.outCropZ = this.outCropZ;
        c.inSeedEnabled = this.inSeedEnabled;
        c.inSeedX = this.inSeedX;
        c.inSeedY = this.inSeedY;
        c.inSeedZ = this.inSeedZ;
        c.inToolEnabled = this.inToolEnabled;
        c.inToolX = this.inToolX;
        c.inToolY = this.inToolY;
        c.inToolZ = this.inToolZ;
        c.outJunkEnabled = this.outJunkEnabled;
        c.outJunkX = this.outJunkX;
        c.outJunkY = this.outJunkY;
        c.outJunkZ = this.outJunkZ;
        c.defaultSeedSlot = this.defaultSeedSlot;
        c.backupCropStacks = this.backupCropStacks;
        c.fillEmptyFarmland = this.fillEmptyFarmland;
        c.plantCrop = this.plantCrop;
        c.cropFilterMode = this.cropFilterMode;
        c.pauseWhenPlayerNear = this.pauseWhenPlayerNear;
        c.playerNearRadius = this.playerNearRadius;
        c.humanizeTiming = this.humanizeTiming;
        c.randomRest = this.randomRest;
        c.restIntervalMinutes = this.restIntervalMinutes;
        c.restDurationSeconds = this.restDurationSeconds;
        c.autoReplaceTool = this.autoReplaceTool;
        c.toolDurabilityPercent = this.toolDurabilityPercent;
        c.failureCooldown = this.failureCooldown;
        c.failureCooldownSeconds = this.failureCooldownSeconds;
        c.verboseHud = this.verboseHud;
        c.pickupDroppedItems = this.pickupDroppedItems;
        c.pickupDelaySeconds = this.pickupDelaySeconds;
        c.melonHarvest = this.melonHarvest;
        c.customHarvestBlocks = this.customHarvestBlocks == null
                ? new ArrayList<>() : new ArrayList<>(this.customHarvestBlocks);
        c.boneMealBoost = this.boneMealBoost;
        c.boneMealLimit = this.boneMealLimit;
        c.autoFetchSupplies = this.autoFetchSupplies;
        c.boneMealThreshold = this.boneMealThreshold;
        c.autoFetchTool = this.autoFetchTool;
        c.notifySounds = this.notifySounds;
        c.notifyToasts = this.notifyToasts;
        c.antiKickMode = this.antiKickMode;
        c.antiKickLevel = this.antiKickLevel;
        c.reactionDelay = this.reactionDelay;
        c.reactionDelayTicks = this.reactionDelayTicks;
        c.idleLookAround = this.idleLookAround;
        c.scheduleEnabled = this.scheduleEnabled;
        c.scheduleStartHour = this.scheduleStartHour;
        c.scheduleEndHour = this.scheduleEndHour;
        c.workAreaEnabled = this.workAreaEnabled;
        c.workAreaMode = this.workAreaMode;
        c.workAreaX = this.workAreaX;
        c.workAreaY = this.workAreaY;
        c.workAreaZ = this.workAreaZ;
        c.workAreaRadius = this.workAreaRadius;
        c.workAreaX2 = this.workAreaX2;
        c.workAreaY2 = this.workAreaY2;
        c.workAreaZ2 = this.workAreaZ2;
        c.workDaylightOnly = this.workDaylightOnly;
        c.workClearWeatherOnly = this.workClearWeatherOnly;
        c.fatigueStop = this.fatigueStop;
        c.fatigueWorkMinutes = this.fatigueWorkMinutes;
        c.fatigueRestMinutes = this.fatigueRestMinutes;
        c.retreatEnabled = this.retreatEnabled;
        c.retreatHealthHearts = this.retreatHealthHearts;
        c.retreatDistance = this.retreatDistance;
        c.hitReactionEnabled = this.hitReactionEnabled;
        c.hitReactionSeconds = this.hitReactionSeconds;
        c.selfRescueEnabled = this.selfRescueEnabled;
        c.autoReconnectEnabled = this.autoReconnectEnabled;
        c.reconnectDelaySeconds = this.reconnectDelaySeconds;
        c.reconnectMaxAttempts = this.reconnectMaxAttempts;
        c.autoAttack = this.autoAttack;
        c.autoAttackViolent = this.autoAttackViolent;
        c.attackIntervalMs = this.attackIntervalMs;
        c.attackHostileOnly = this.attackHostileOnly;
        c.attackRange = this.attackRange;
        c.attackViolence = this.attackViolence;
        // 数组 / 列表都要深拷贝：不然「取消」时把草稿改回去，会把原配置一起改掉
        c.storageKeep = this.storageKeep == null ? null : this.storageKeep.clone();
        c.cropFilter = this.cropFilter == null ? new ArrayList<>() : new ArrayList<>(this.cropFilter);
        return c;
    }

    /** 把取值范围拉回合法区间，防止手改 JSON 写坏 */
    public void clamp() {
        if (scanRadius < 4) scanRadius = 4;
        if (scanRadius > 48) scanRadius = 48;

        if (scanIntervalTicks < 2) scanIntervalTicks = 2;
        if (scanIntervalTicks > 200) scanIntervalTicks = 200;

        if (aimMode < AIM_NORMAL || aimMode >= AIM_MODE_COUNT) {
            aimMode = AIM_NORMAL;
        }
        if (turnDurationMs < TURN_DURATION_MIN) turnDurationMs = TURN_DURATION_MIN;
        if (turnDurationMs > TURN_DURATION_MAX) turnDurationMs = TURN_DURATION_MAX;

        highlightColor &= 0xFFFFFF;
        if (highlightWidth < 1) highlightWidth = 1;
        if (highlightWidth > 8) highlightWidth = 8;
        if (highlightAlpha < 10) highlightAlpha = 10;
        if (highlightAlpha > 100) highlightAlpha = 100;

        if (defaultSeedSlot < 1) defaultSeedSlot = 1;
        if (defaultSeedSlot > 9) defaultSeedSlot = 9;
        if (backupCropStacks < 0) backupCropStacks = 0;
        if (backupCropStacks > 5) backupCropStacks = 5;

        // 血量门槛：0 表示关闭；1~2 心太灵敏（被打一下就停），所以往上抬到 3，
        // 上限 6 心 —— 再高就变成「一直是停的」了
        if (lowHealthStopHearts < 0) lowHealthStopHearts = 0;
        if (lowHealthStopHearts > 0 && lowHealthStopHearts < 3) lowHealthStopHearts = 3;
        if (lowHealthStopHearts > 6) lowHealthStopHearts = 6;

        // 存放策略：旧配置按 backupCropStacks 补一份出来。
        // 注意「旧」有两种：完全没有这个字段（null），以及字段在、但长度是上几个版本的
        // 类别数（比如这次多了「收获食物」）。两种都按「前几项原样保留、新增的类走出厂默认」
        // 来迁移 —— 类别是追加在枚举末尾的，所以下标一一对得上。
        if (storageKeep == null) {
            storageKeep = defaultStorageKeep(backupCropStacks);
        } else {
            int[] migrated = defaultStorageKeep(backupCropStacks);
            int overlap = Math.min(storageKeep.length, migrated.length);
            for (int i = 0; i < overlap; i++) {
                migrated[i] = clampKeep(storageKeep[i]);
            }
            storageKeep = migrated;
        }

        // 作物筛选：模式拉回 0~3，名单去掉空串（手改 JSON 写坏了也不至于崩）
        if (cropFilterMode < 0 || cropFilterMode >= FILTER_MODE_COUNT) {
            cropFilterMode = FILTER_OFF;
        }
        if (cropFilter == null) {
            cropFilter = new ArrayList<>();
        } else {
            cropFilter.removeIf(id -> id == null || id.isBlank());
        }
        // 作物名单里没东西时，白名单会变成「什么都不收」——那不是玩家的本意，
        // 所以这种组合直接退回「全收」，免得他打开开关发现它一动不动
        if (cropFilterMode == FILTER_WHITELIST && cropFilter.isEmpty()) {
            cropFilterMode = FILTER_OFF;
        }

        // 附加功能：单纯把数值拉回合理区间。它们的「关 / 开」是布尔量，不需要钳制。
        if (playerNearRadius < 4) playerNearRadius = 4;
        if (playerNearRadius > 48) playerNearRadius = 48;

        if (restIntervalMinutes < 1) restIntervalMinutes = 1;
        if (restIntervalMinutes > 60) restIntervalMinutes = 60;
        if (restDurationSeconds < 5) restDurationSeconds = 5;
        if (restDurationSeconds > 120) restDurationSeconds = 120;

        if (toolDurabilityPercent < 1) toolDurabilityPercent = 1;
        if (toolDurabilityPercent > 50) toolDurabilityPercent = 50;

        if (failureCooldownSeconds < 3) failureCooldownSeconds = 3;
        if (failureCooldownSeconds > 120) failureCooldownSeconds = 120;

        if (pickupDelaySeconds < 1) pickupDelaySeconds = 1;
        if (pickupDelaySeconds > 30) pickupDelaySeconds = 30;

        // 自定义方块名单：去掉空串（手改 JSON 写坏了也不至于崩）。
        // 名单里的名字认不出方块（模组被卸了、名字拼错了）不在这里过滤 ——
        // 扫描时按名找不到方块自然就跳过，留在这里只是别让它把空串带进名单。
        if (customHarvestBlocks == null) {
            customHarvestBlocks = new ArrayList<>();
        } else {
            customHarvestBlocks.removeIf(id -> id == null || id.isBlank());
        }

        // 资源自给（D 组）
        if (boneMealLimit < 1) boneMealLimit = 1;
        if (boneMealLimit > 20) boneMealLimit = 20;
        if (boneMealThreshold < 1) boneMealThreshold = 1;
        if (boneMealThreshold > 256) boneMealThreshold = 256;

        // 防踢档位：0~3（具体的档位含义在 Humanizer 里）
        if (antiKickLevel < 0) antiKickLevel = 0;
        if (antiKickLevel > 3) antiKickLevel = 3;

        // 行为真实感：反应延迟不能为 0（0 就等于「瞬间反应」，那这个开关就没意义了）
        if (reactionDelayTicks < 2) reactionDelayTicks = 2;
        if (reactionDelayTicks > 60) reactionDelayTicks = 60;

        // 工作时段：都拉回 0~23 整点
        if (scheduleStartHour < 0) scheduleStartHour = 0;
        if (scheduleStartHour > 23) scheduleStartHour = 23;
        if (scheduleEndHour < 0) scheduleEndHour = 0;
        if (scheduleEndHour > 23) scheduleEndHour = 23;

        // 工作区域：半径拉回合法区间；模式认不出来的退回圆形
        if (workAreaRadius < WORK_AREA_RADIUS_MIN) workAreaRadius = WORK_AREA_RADIUS_MIN;
        if (workAreaRadius > WORK_AREA_RADIUS_MAX) workAreaRadius = WORK_AREA_RADIUS_MAX;
        if (workAreaMode < 0 || workAreaMode >= AREA_MODE_COUNT) {
            workAreaMode = AREA_MODE_RADIUS;
        }
        // 坐标点「要么三个都有、要么三个都没有」——只设了一半的算作没设，
        // 免得 hasWorkArea() 半真半假。中心/角点 A 与角点 B 各查一遍。
        if (!complete(workAreaX, workAreaY, workAreaZ)) {
            workAreaX = null;
            workAreaY = null;
            workAreaZ = null;
        }
        if (!complete(workAreaX2, workAreaY2, workAreaZ2)) {
            workAreaX2 = null;
            workAreaY2 = null;
            workAreaZ2 = null;
        }

        // 扩展存储与补给：四个容器的坐标同样「要么三个都有、要么都没有」
        if (!complete(outCropX, outCropY, outCropZ)) {
            outCropX = null;
            outCropY = null;
            outCropZ = null;
        }
        if (!complete(inSeedX, inSeedY, inSeedZ)) {
            inSeedX = null;
            inSeedY = null;
            inSeedZ = null;
        }
        if (!complete(inToolX, inToolY, inToolZ)) {
            inToolX = null;
            inToolY = null;
            inToolZ = null;
        }
        if (!complete(outJunkX, outJunkY, outJunkZ)) {
            outJunkX = null;
            outJunkY = null;
            outJunkZ = null;
        }

        // 疲劳收工：连干多久、歇多久，都拉回合理区间（0 分钟休息 = 手动，是合法值）
        if (fatigueWorkMinutes < 5) fatigueWorkMinutes = 5;
        if (fatigueWorkMinutes > 480) fatigueWorkMinutes = 480;
        if (fatigueRestMinutes < 0) fatigueRestMinutes = 0;
        if (fatigueRestMinutes > 480) fatigueRestMinutes = 480;

        // 生存自保（C 组）：数值都拉回合理区间
        if (retreatHealthHearts < RETREAT_HEARTS_MIN) retreatHealthHearts = RETREAT_HEARTS_MIN;
        if (retreatHealthHearts > RETREAT_HEARTS_MAX) retreatHealthHearts = RETREAT_HEARTS_MAX;
        if (retreatDistance < 4) retreatDistance = 4;
        if (retreatDistance > 64) retreatDistance = 64;
        if (hitReactionSeconds < 1) hitReactionSeconds = 1;
        if (hitReactionSeconds > 10) hitReactionSeconds = 10;
        if (reconnectDelaySeconds < 3) reconnectDelaySeconds = 3;
        if (reconnectDelaySeconds > 60) reconnectDelaySeconds = 60;
        if (reconnectMaxAttempts < 0) reconnectMaxAttempts = 0;
        if (reconnectMaxAttempts > 50) reconnectMaxAttempts = 50;

        // 战斗：间隔 / 距离都拉回合法区间。注意下界放得很低（50ms）——
        // 「保命」档本来就要尽量快，所以这里只做「能不能填」的兜底，
        // 「该不该这么快」交给面板上的红色警示和风险确认屏去提醒。
        if (attackIntervalMs < ATTACK_INTERVAL_MIN_MS) attackIntervalMs = ATTACK_INTERVAL_MIN_MS;
        if (attackIntervalMs > ATTACK_INTERVAL_MAX_MS) attackIntervalMs = ATTACK_INTERVAL_MAX_MS;
        if (attackRange < ATTACK_RANGE_MIN) attackRange = ATTACK_RANGE_MIN;
        if (attackRange > ATTACK_RANGE_MAX) attackRange = ATTACK_RANGE_MAX;
        if (attackViolence < VIOLENCE_LIGHT) attackViolence = VIOLENCE_LIGHT;
        if (attackViolence >= VIOLENCE_COUNT) attackViolence = VIOLENCE_COUNT - 1;

        // 暴力模式与暴力程度都是「自动打怪」的子项：主子项关着时它们没有意义，一并收掉，
        // 免得出现「自动打怪关着、暴力标记却留着」这种会让面板显示错乱的状态
        if (!autoAttack) {
            autoAttackViolent = false;
            attackViolence = VIOLENCE_LIGHT;
        }
        // 「自动打怪」不能脱离「自动收割」总开关单独开启。规则是双向的：
        // 面板上开不了（见 ExtrasScreen），配置层面也强制收掉 ——
        // 手改 JSON、或者先开打怪再关总开关的，都会被这一步拉回一致状态。
        if (!enabled) {
            autoAttack = false;
            autoAttackViolent = false;
            attackViolence = VIOLENCE_LIGHT;
        }
    }

    // ---- 描边外观的派生值 ----

    /** 描边的 ARGB 颜色，给界面里画预览用 */
    public int highlightArgb() {
        int alpha = Math.round(Math.max(0, Math.min(100, highlightAlpha)) * 255f / 100f);
        return (alpha << 24) | (highlightColor & 0xFFFFFF);
    }

    public float highlightRed() {
        return ((highlightColor >> 16) & 0xFF) / 255f;
    }

    public float highlightGreen() {
        return ((highlightColor >> 8) & 0xFF) / 255f;
    }

    public float highlightBlue() {
        return (highlightColor & 0xFF) / 255f;
    }

    /** 不透明度，转成 0~1 的小数（留 0.1 下限，全透明就等于看不见了） */
    public float highlightAlpha01() {
        return Math.max(0.1f, Math.min(1f, highlightAlpha / 100f));
    }

    /** 补给点是否已完整设置 */
    public boolean hasSupplyPoint() {
        return supplyX != null && supplyY != null && supplyZ != null;
    }

    // ---- 扩展存储与补给（四个专用容器） ----

    /** 农作物输出容器是否已完整设置 */
    public boolean hasCropOut() {
        return outCropX != null && outCropY != null && outCropZ != null;
    }

    /** 种子输入容器是否已完整设置 */
    public boolean hasSeedIn() {
        return inSeedX != null && inSeedY != null && inSeedZ != null;
    }

    /** 工具补给容器是否已完整设置 */
    public boolean hasToolIn() {
        return inToolX != null && inToolY != null && inToolZ != null;
    }

    /** 杂物临时存储容器是否已完整设置 */
    public boolean hasJunkOut() {
        return outJunkX != null && outJunkY != null && outJunkZ != null;
    }

    // ---- 工作区域（B 组①） ----

    /**
     * 工作区域是否已经设置完整。
     *
     * <p>按当前模式判：圆形要一个中心点（三个坐标都在），矩形要两个角点（六个都在）。
     * 任何一处缺失都算「没设」，这时功能不生效。
     */
    public boolean hasWorkArea() {
        if (!complete(workAreaX, workAreaY, workAreaZ)) {
            return false;
        }
        return workAreaMode != AREA_MODE_BOX || complete(workAreaX2, workAreaY2, workAreaZ2);
    }

    /** 三个坐标是不是「要么都有、要么都没有」 */
    private static boolean complete(Integer x, Integer y, Integer z) {
        return (x != null && y != null && z != null) || (x == null && y == null && z == null);
    }

    /** 记下第一个坐标点（圆形 = 中心、矩形 = 角点 A） */
    public void setWorkArea(int x, int y, int z) {
        workAreaX = x;
        workAreaY = y;
        workAreaZ = z;
    }

    /** 记下矩形模式的角点 B */
    public void setWorkAreaSecond(int x, int y, int z) {
        workAreaX2 = x;
        workAreaY2 = y;
        workAreaZ2 = z;
    }

    /** 第一个坐标点的可读文本，未设置时返回 null */
    public String workAreaText() {
        if (!complete(workAreaX, workAreaY, workAreaZ) || workAreaX == null) {
            return null;
        }
        return workAreaX + ", " + workAreaY + ", " + workAreaZ;
    }

    /** 矩形模式角点 B 的可读文本，未设置时返回 null */
    public String workAreaSecondText() {
        if (!complete(workAreaX2, workAreaY2, workAreaZ2) || workAreaX2 == null) {
            return null;
        }
        return workAreaX2 + ", " + workAreaY2 + ", " + workAreaZ2;
    }

    /**
     * 这个世界坐标在不在工作区域里。
     *
     * <p>没开这个功能、或者坐标还没记全，一律算「在里面」——
     * 功能关着的时候不能影响任何既有行为。
     *
     * <p>圆形模式只看水平（X/Z），竖直方向不限：地块通常是平的，
     * 把高度也算进去只会让上下坡误判。矩形模式是完整的长方体，三个轴都算。
     */
    public boolean insideWorkArea(double x, double y, double z) {
        if (!workAreaEnabled || !hasWorkArea()) {
            return true;
        }
        if (workAreaMode == AREA_MODE_BOX) {
            int bx = (int) Math.floor(x);
            int by = (int) Math.floor(y);
            int bz = (int) Math.floor(z);
            return bx >= Math.min(workAreaX, workAreaX2) && bx <= Math.max(workAreaX, workAreaX2)
                    && by >= Math.min(workAreaY, workAreaY2) && by <= Math.max(workAreaY, workAreaY2)
                    && bz >= Math.min(workAreaZ, workAreaZ2) && bz <= Math.max(workAreaZ, workAreaZ2);
        }
        double dx = x - (workAreaX + 0.5);
        double dz = z - (workAreaZ + 0.5);
        double r = workAreaRadius;
        return dx * dx + dz * dz <= r * r;
    }
}
