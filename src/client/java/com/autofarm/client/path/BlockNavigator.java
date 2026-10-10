package com.autofarm.client.path;

import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

import java.util.Arrays;

/**
 * 「这一格能不能站人」以及「这两点能不能直着走过去」的判断。
 *
 * <p>{@link PathFinder} 的 A* 要在一个「能走 / 不能走」的格子图上搜索，
 * 这一层就是那个图的地基：把 Minecraft 的方块世界翻译成布尔值。
 *
 * <h2>怎么判断一格能不能站</h2>
 * 不是看方块「是不是空气」——那样会把草、火把、地毯、半砖全判错。
 * 这里用的是<b>碰撞箱</b>：{@code getCollisionShape().isEmpty()}。
 * 凡是玩家能穿过去的（草、花、火把、水、告示牌……）碰撞箱都是空的，
 * 自动就算「能走」，不写白名单，模组方块也能自适应。
 *
 * <p>另外两个条件：
 * <ul>
 *   <li><b>头部那一格也要空</b>——玩家高 1.8 格，脚下空了头顶撞墙照样钻不过去；</li>
 *   <li><b>脚下必须有东西踩着</b>——否则 A* 会规划出「悬空行走」的路线，
 *       角色一走上去就直接掉下去。</li>
 * </ul>
 */
public final class BlockNavigator {

    /** 玩家碰撞箱高 1.8 格，站立时脚部 + 头部两格都要空出来 */
    private static final int BODY_HEIGHT = 2;

    /** 直线可达检查的采样步长（格）。够密，不会漏掉一格宽的柱子 */
    private static final double SAMPLE_STEP = 0.2;

    /**
     * 复用的临时坐标。
     *
     * <p>这些方法每秒会被调用几十万次（A* 展开节点时），每次都 new 一个
     * {@code BlockPos} 会给垃圾回收添不少麻烦，所以借一个可变对象周转。
     * 全部调用都发生在客户端主线程，不存在并发问题。
     */
    private static final BlockPos.Mutable CURSOR = new BlockPos.Mutable();

    private BlockNavigator() {
    }

    // ================= 基本判定 =================

    /** 这一格对玩家来说「是空的」（没有碰撞箱） */
    public static boolean isPassableAt(BlockView world, int x, int y, int z) {
        if (world == null) {
            return false;
        }
        if (y < world.getBottomY() || y >= world.getTopY()) {
            return false;
        }
        // 未加载的区块一律当成「走不通」，否则会把 A* 引到一片虚空里
        if (world instanceof ClientWorld clientWorld
                && !clientWorld.isChunkLoaded(x >> 4, z >> 4)) {
            return false;
        }

        CURSOR.set(x, y, z);
        BlockState state = world.getBlockState(CURSOR);
        if (state.isAir()) {
            return true;
        }
        return state.getCollisionShape(world, CURSOR).isEmpty();
    }

    /** 脚站在 (x, y, z) 这一格上是否成立 */
    public static boolean canStand(BlockView world, int x, int y, int z) {
        for (int i = 0; i < BODY_HEIGHT; i++) {
            if (!isPassableAt(world, x, y + i, z)) {
                return false;
            }
        }
        return !isPassableAt(world, x, y - 1, z);
    }

    public static boolean canStand(BlockView world, BlockPos pos) {
        return pos != null && canStand(world, pos.getX(), pos.getY(), pos.getZ());
    }

    // ================= 找落脚点 =================

    /**
     * 在 (x, z) 这一列上找离 {@code refY} 最近的、能站人的高度。
     *
     * <p>优先「同一层」，然后上下交替往外扩。这样在台阶旁边不会给出一个
     * 「明明要爬上去」的格子，路走起来才自然。
     *
     * @return 站不住的列返回 null
     */
    public static BlockPos findFloor(BlockView world, int x, int z, int refY, int maxDown, int maxUp) {
        if (canStand(world, x, refY, z)) {
            return new BlockPos(x, refY, z);
        }
        int limit = Math.max(maxDown, maxUp);
        for (int offset = 1; offset <= limit; offset++) {
            if (offset <= maxDown && canStand(world, x, refY - offset, z)) {
                return new BlockPos(x, refY - offset, z);
            }
            if (offset <= maxUp && canStand(world, x, refY + offset, z)) {
                return new BlockPos(x, refY + offset, z);
            }
        }
        return null;
    }

    // ================= 观察点 =================

    /** 水平方向的四个邻居，顺序决定了「优先站在哪一侧」 */
    private static final int[][] HORIZONTAL = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};

    /**
     * 找一个「站在上面能看到 target」的落脚点。
     *
     * <p>为什么需要它：作物方块（小麦、胡萝卜、甘蔗……）的碰撞箱是空的，
     * 所以 {@link #canStand} 会认为<b>作物自己那一格就能站</b>——照直走的话，
     * 角色会走进作物里，脚和作物重叠。那样不但看着别扭，
     * 挖它的时候视角要垂直朝下，服务端看到的姿势非常不正常。
     *
     * <p>所以收割的目标不再是「作物那一格」，而是「站在旁边能看着它的一格」。
     * 优先级：同层四邻 → 上一层四邻 → 下一层四邻（甘蔗这类两格高的作物要站在下层）。
     * 优先选「视线不被挡住」的候选；实在都没有就退回第一个能站的，
     * 让它走近了再挖，总比放弃这一棵好。
     *
     * @return 旁边一格都站不住时返回 null
     */
    public static BlockPos findViewSpot(BlockView world, BlockPos target) {
        return findViewSpot(world, target, null);
    }

    /**
     * 找一个「站在上面能看到 target」的落脚点，并且<b>尽量让角色到站后脸正好朝 target</b>。
     *
     * <p>{@code from} 是玩家当前大致位置（可为 null）。它决定了四邻的<b>遍历顺序</b>：
     * 优先选「朝向玩家来向」那一侧的格子。原因是走路的最后一步，角色是脸朝「上一个
     * 落脚点 → 这个落脚点」的方向停下的 —— 若落脚点就选在玩家和目标之间的那一侧，
     * 到站时脸自然朝目标，省掉一次掉头；反之选到背面那一格，就得先回头再挖/种/催熟。
     */
    public static BlockPos findViewSpot(BlockView world, BlockPos target, BlockPos from) {
        if (world == null || target == null) {
            return null;
        }
        BlockPos fallback = null;
        int[][] dirs = orderedDirections(target, from);
        for (int dy : new int[]{0, 1, -1}) {
            for (int[] direction : dirs) {
                int x = target.getX() + direction[0];
                int y = target.getY() + dy;
                int z = target.getZ() + direction[1];
                if (!canStand(world, x, y, z)) {
                    continue;
                }
                BlockPos candidate = new BlockPos(x, y, z);
                if (fallback == null) {
                    fallback = candidate;
                }
                if (hasLineOfSight(world, candidate, target)) {
                    return candidate;
                }
            }
        }
        return fallback;
    }

    /**
     * 按「玩家来向」给四邻排序：越指向玩家那一侧的越靠前。
     *
     * <p>玩家相对目标的向量是 {@code (from - target)}，每个邻居偏移 {@code (ox, oz)} 和它的
     * 点积越大，说明这个邻居越在「玩家来的方向」上 —— 走到那里停下，脸正好朝目标。
     * {@code from} 为 null（或和目标同格）时保持原顺序，行为不变。
     */
    private static int[][] orderedDirections(BlockPos target, BlockPos from) {
        if (from == null) {
            return HORIZONTAL;
        }
        int dx = from.getX() - target.getX();
        int dz = from.getZ() - target.getZ();
        if (dx == 0 && dz == 0) {
            return HORIZONTAL;
        }
        // 浅拷贝外层数组再排 —— 只重排 int[] 引用，不动内层内容、也不动共享常量
        int[][] ordered = Arrays.copyOf(HORIZONTAL, HORIZONTAL.length);
        Arrays.sort(ordered, (a, b) ->
                Integer.compare(b[0] * dx + b[1] * dz, a[0] * dx + a[1] * dz));
        return ordered;
    }

    /**
     * 从 {@code from} 这一格的「眼睛高度」能不能看到 {@code target} 的中心。
     *
     * <p>沿直线采样，一旦采样点落进目标方块就认为通了（停在目标方块上，
     * 不再往后查）—— 这样「目标本身不是空气」也不会被误判成挡住。
     */
    public static boolean hasLineOfSight(BlockView world, BlockPos from, BlockPos target) {
        if (world == null || from == null || target == null) {
            return false;
        }
        double ax = from.getX() + 0.5;
        double ay = from.getY() + 1.62;
        double az = from.getZ() + 0.5;
        double bx = target.getX() + 0.5;
        double by = target.getY() + 0.5;
        double bz = target.getZ() + 0.5;

        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(1, (int) Math.ceil(distance / SAMPLE_STEP));

        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            int x = (int) Math.floor(ax + dx * t);
            int y = (int) Math.floor(ay + dy * t);
            int z = (int) Math.floor(az + dz * t);
            if (x == target.getX() && y == target.getY() && z == target.getZ()) {
                return true;
            }
            if (!isPassableAt(world, x, y, z)) {
                return false;
            }
        }
        return true;
    }

    // ================= 直线可达 =================

    /**
     * 从 from 到 to 能不能「直着走过去」（一路上都有地方落脚）。
     *
     * <p>用密集采样近似：每 {@value #SAMPLE_STEP} 格采一个点，看落点附近
     * （本层或上下各一层）有没有能站的格子。
     *
     * <p>它比 A* 便宜一两个数量级，而「同一片农田里两点之间没有障碍」正是
     * 最常遇到的情况，所以先用它兜一道：它说能走就直着走，
     * 只有它说不行才去跑 A*。这一道挡掉了绝大部分寻路开销。
     */
    public static boolean hasStraightPath(BlockView world, BlockPos from, BlockPos to) {
        double ax = from.getX() + 0.5;
        double az = from.getZ() + 0.5;
        double bx = to.getX() + 0.5;
        double bz = to.getZ() + 0.5;
        double distance = Math.sqrt((bx - ax) * (bx - ax) + (bz - az) * (bz - az));
        int steps = Math.max(1, (int) Math.ceil(distance / SAMPLE_STEP));

        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            int x = (int) Math.floor(ax + (bx - ax) * t);
            int z = (int) Math.floor(az + (bz - az) * t);
            double y = from.getY() + (to.getY() - (double) from.getY()) * t;
            int rounded = (int) Math.round(y);

            // 本层优先，不行再看上下各一层：直线插值出来的高度不一定正好落在台阶上
            boolean ok = false;
            for (int dy = 0; dy <= 1 && !ok; dy++) {
                ok = canStand(world, x, rounded + dy, z)
                        || canStand(world, x, rounded - dy, z);
            }
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
