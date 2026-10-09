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
