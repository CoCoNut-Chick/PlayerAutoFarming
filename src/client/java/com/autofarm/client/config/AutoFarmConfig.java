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
     * <p>打开后角色在赶路时会一直保持疾跑（速度几乎翻倍），到点停下、挖方块、
     * 你打开界面接管控制权时都会自动松开。原版的饥饿/吃东西等限制照样生效 ——
     * 它就是替你把「按住 Ctrl」这件事一直按着，不会绕过任何游戏规则。
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
     * 打开后即使「自动收割」是关的，也会持续扫描，方便你先看清它到底认出了哪些方块。
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
        if (antiKickMode) n++;
        if (reactionDelay) n++;
        if (idleLookAround) n++;
        if (scheduleEnabled) n++;
        return n;
    }

    /** 复制一份，供面板做「草稿」，取消时可以整体丢弃 */
    public AutoFarmConfig copy() {
        AutoFarmConfig c = new AutoFarmConfig();
        c.enabled = this.enabled;
        c.scanRadius = this.scanRadius;
        c.scanIntervalTicks = this.scanIntervalTicks;
        c.replant = this.replant;
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
        c.antiKickMode = this.antiKickMode;
        c.antiKickLevel = this.antiKickLevel;
        c.reactionDelay = this.reactionDelay;
        c.reactionDelayTicks = this.reactionDelayTicks;
        c.idleLookAround = this.idleLookAround;
        c.scheduleEnabled = this.scheduleEnabled;
        c.scheduleStartHour = this.scheduleStartHour;
        c.scheduleEndHour = this.scheduleEndHour;
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

    /** 补给点的可读文本，未设置时返回 null */
    public String supplyPointText() {
        if (!hasSupplyPoint()) return null;
        return supplyX + ", " + supplyY + ", " + supplyZ;
    }
}
