package com.autofarm.client.path;

import com.autofarm.client.inventory.InventoryScanner;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * 格子图上的 A* 寻路，而且允许<b>改地形</b>。
 *
 * <h2>它和普通 A* 的区别</h2>
 * 普通寻路只在<b>已有的</b>空格子图上找路，遇到墙就绕。这一版把「挖开一格」
 * 和「垫一格」也当成合法的移动方式，于是能穿过围栏、跨过水渠。
 * 代价是搜索空间变大了，而且「能不能挖」这件事和玩家身上带什么有关，
 * 所以每一步都要现算。
 *
 * <h2>三种动作，三种代价</h2>
 * <ul>
 *   <li><b>走</b>：1.0 一格（斜着 1.414）。上台阶 +0.6，往下 +0.2。</li>
 *   <li><b>挖</b>：2.0 底价 + 挖掘时间 × 0.1。挖掘时间按原版公式
 *       {@code 速度 / 硬度 / (30 或 100)} 算，所以「有铁镐挖石头」只要 0.6 格等效代价，
 *       而「徒手挖黑曜石」要十几格——A* 自己就绕开了。这才是「看背包决定走哪条路」的落点：
 *       <b>背包里那点差别，全部体现在这条代价公式里</b>。</li>
 *   <li><b>垫</b>：2.5 底价，另外每垫一块消耗一个快捷栏方块。</li>
 * </ul>
 *
 * <p>一个细节：垫方块会消耗物品，所以「已经垫了几块」也是搜索状态的一部分
 * （同一个坐标，垫过 0 块和垫过 3 块到达，后续能做的事不一样）。
 * 这就是下面那个 {@code key(pos, placed)} 存在的原因。
 */
public final class PathFinder {

    private static final int[][] NEIGHBORS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private static final double COST_STRAIGHT = 1.0;
    private static final double COST_DIAGONAL = 1.4142135623730951;
    private static final double COST_CLIMB = 0.6;
    private static final double COST_DROP = 0.2;

    /** 挖一格的固定开销：相当于「多绕两格」，避免它为了抄近道到处乱挖 */
    private static final double MINE_BASE_COST = 2.0;

    /** 挖掘时间折算成路程的系数：10 tick 的挖掘 ≈ 多走一格 */
    private static final double MINE_TICK_COST = 0.1;

    /** 挖一格超过这个时间（tick）就直接放弃这条路，别去啃黑曜石 */
    private static final double MAX_MINE_TICKS = 120.0;

    /** 垫一格的固定开销：要消耗一个方块，还要抬手放下去 */
    private static final double PLACE_COST = 2.5;

    /** 单次寻路最多允许垫几块（就算背包里有几百个也不可能一路铺过去） */
    private static final int MAX_BRIDGE = 16;

    /** 单次搜索最多展开多少个格子，避免被封死的目标拖死 */
    private static final int MAX_EXPANDED = 12000;

    /** 起点到目标的水平距离上限 */
    private static final double MAX_RANGE = 96.0;

    /** 「已垫块数」在状态键里占的位数，必须大于 MAX_BRIDGE */
    private static final int PLACED_STATES = 32;

    private PathFinder() {
    }

    /** 搜索树上的一个节点 */
    private static final class Node {
        final BlockPos pos;
        /** 到达这里时已经垫掉了几块方块 */
        final int placed;
        final double g;
        final double f;
        /** 到达这个节点用的是哪种动作 */
        final PathAction action;
        final Node parent;

        Node(BlockPos pos, int placed, double g, double f, PathAction action, Node parent) {
            this.pos = pos;
            this.placed = placed;
            this.g = g;
            this.f = f;
            this.action = action;
            this.parent = parent;
        }
    }

    /** 挖开一格要付的代价：位置 + 需要的时间 */
    private record MinePlan(BlockPos pos, double ticks) {
    }

    /**
     * 从起点走到终点。
     *
     * @return <b>null</b> 表示走不到；<b>空列表</b>表示已经在终点上了；
     *         否则是不含起点的一串落脚点
     */
    public static List<PathStep> findPath(ClientWorld world, ClientPlayerEntity player,
                                          BlockPos start, BlockPos goal, PathOptions options) {
        if (world == null || player == null || start == null || goal == null) {
            return null;
        }
        double horizontal = Math.sqrt(sq(start.getX() - goal.getX()) + sq(start.getZ() - goal.getZ()));
        if (horizontal > MAX_RANGE) {
            return null;
        }

        // 玩家偶尔会卡在方块缝里，导致「脚下这一格」判不出来，先就近找一个合格起点
        BlockPos from = BlockNavigator.canStand(world, start)
                ? start
                : BlockNavigator.findFloor(world, start.getX(), start.getZ(), start.getY(), 3, 3);
        if (from == null) {
            return null;
        }
        // 目标本身（作物所在格）不一定站得住，比如头顶正好有块方块挡着
        BlockPos to = BlockNavigator.canStand(world, goal)
                ? goal
                : BlockNavigator.findFloor(world, goal.getX(), goal.getZ(), goal.getY(), 2, 2);
        if (to == null) {
            return null;
        }

        if (from.equals(to)) {
            return Collections.emptyList();
        }
        // 便宜的先试：中间没有障碍就直接一条直线，不用惊动 A*
        if (BlockNavigator.hasStraightPath(world, from, to)) {
            return List.of(PathStep.walk(to));
        }

        return search(world, player, from, to, options);
    }

    // ================= A* 主循环 =================

    private static List<PathStep> search(ClientWorld world, ClientPlayerEntity player,
                                         BlockPos from, BlockPos to, PathOptions options) {
        int maxPlaced = options.allowPlacing() ? Math.min(options.availableBlocks(), MAX_BRIDGE) : 0;

        Map<Long, Node> best = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(node -> node.f));

        Node startNode = new Node(from, 0, 0.0, heuristic(from, to), PathAction.WALK, null);
        best.put(key(from, 0), startNode);
        open.add(startNode);

        int expanded = 0;
        while (!open.isEmpty()) {
            Node current = open.poll();
            // 同一个 (坐标, 已垫数) 可能被塞进去过好几次，取出时确认它还是最优的那份
            if (best.get(key(current.pos, current.placed)) != current) {
                continue;
            }
            if (current.pos.equals(to)) {
                return buildPath(current);
            }
            if (++expanded > MAX_EXPANDED) {
                return null;
            }

            for (int[] direction : NEIGHBORS) {
                int dx = direction[0];
                int dz = direction[1];
                boolean diagonal = dx != 0 && dz != 0;
                int nx = current.pos.getX() + dx;
                int nz = current.pos.getZ() + dz;

                // ---- 动作一：直接走 ----
                BlockPos walkPos = walkTarget(world, current.pos, nx, nz);
                if (walkPos != null
                        && (!diagonal || diagonalClear(world, current.pos, walkPos))) {
                    relax(best, open, current, walkPos, PathAction.WALK, current.placed,
                            stepCost(diagonal, walkPos.getY() - current.pos.getY()), to);
                }

                // ---- 动作二：挖开挡路的方块 ----
                if (options.allowMining()) {
                    MinePlan mine = minePlan(world, player, current.pos, nx, nz, options);
                    if (mine != null && (!diagonal || mineClear(world, current.pos, nx, nz))) {
                        double cost = stepCost(diagonal, mine.pos().getY() - current.pos.getY())
                                + MINE_BASE_COST + mine.ticks() * MINE_TICK_COST;
                        relax(best, open, current, mine.pos(), PathAction.MINE, current.placed, cost, to);
                    }
                }

                // ---- 动作三：垫一块方块 ----
                if (current.placed < maxPlaced) {
                    BlockPos placePos = placeTarget(world, current.pos, nx, nz);
                    if (placePos != null
                            && (!diagonal || diagonalClear(world, current.pos, placePos))) {
                        double cost = stepCost(diagonal, placePos.getY() - current.pos.getY())
                                + PLACE_COST;
                        relax(best, open, current, placePos, PathAction.PLACE,
                                current.placed + 1, cost, to);
                    }
                }
            }
        }
        return null;
    }

    private static void relax(Map<Long, Node> best, PriorityQueue<Node> open, Node current,
                              BlockPos pos, PathAction action, int placed,
                              double extraCost, BlockPos goal) {
        double g = current.g + extraCost;
        long k = key(pos, placed);
        Node known = best.get(k);
        if (known != null && known.g <= g) {
            return;
        }
        Node node = new Node(pos, placed, g, g + heuristic(pos, goal), action, current);
        best.put(k, node);
        open.add(node);
    }

    // ================= 三种动作的判定 =================

    /**
     * 这一个方向的「走过去」候选：优先平走，其次往下、往上。
     *
     * @return 那一列上下都没地方站时返回 null
     */
    private static BlockPos walkTarget(ClientWorld world, BlockPos from, int x, int z) {
        int y = from.getY();
        if (BlockNavigator.canStand(world, x, y, z)) {
            return new BlockPos(x, y, z);
        }
        if (BlockNavigator.canStand(world, x, y - 1, z)) {
            return new BlockPos(x, y - 1, z);
        }
        if (BlockNavigator.canStand(world, x, y + 1, z)) {
            return new BlockPos(x, y + 1, z);
        }
        return null;
    }

    /**
     * 这一个方向的「挖开」候选。
     *
     * <p>要站到 (x, y, z) 上，需要脚部那格和头部那格都空出来，
     * 并且脚下得有支撑（没支撑那是「垫」的活，不是「挖」的活）。
     *
     * @return 走不通、挖不动、或者太费时间时返回 null
     */
    private static MinePlan minePlan(ClientWorld world, ClientPlayerEntity player,
                                     BlockPos from, int x, int z, PathOptions options) {
        int y = from.getY();

        // 便宜的前置判断放最前面：脚下空着就根本不用考虑挖，直接出局
        if (BlockNavigator.isPassableAt(world, x, y - 1, z)) {
            return null;
        }

        boolean needFeet = !BlockNavigator.isPassableAt(world, x, y, z);
        boolean needHead = !BlockNavigator.isPassableAt(world, x, y + 1, z);
        if (!needFeet && !needHead) {
            return null;
        }

        double ticks = 0.0;
        if (needFeet) {
            double t = breakTicks(world, player, x, y, z, options);
            if (t < 0) {
                return null;
            }
            ticks += t;
        }
        if (needHead) {
            double t = breakTicks(world, player, x, y + 1, z, options);
            if (t < 0) {
                return null;
            }
            ticks += t;
        }
        if (ticks > MAX_MINE_TICKS) {
            return null;
        }
        return new MinePlan(new BlockPos(x, y, z), ticks);
    }

    /**
     * 挖掉 (x, y, z) 这一格要多少 tick。
     *
     * @return 负数表示挖不动（基岩、水、岩浆……）
     */
    private static double breakTicks(ClientWorld world, ClientPlayerEntity player,
                                     int x, int y, int z, PathOptions options) {
        BlockPos pos = new BlockPos(x, y, z);
        BlockState state = world.getBlockState(pos);
        if (!state.getFluidState().isEmpty()) {
            return -1.0;
        }
        float hardness = state.getHardness(world, pos);
        if (hardness < 0.0f) {
            return -1.0;
        }
        if (hardness == 0.0f) {
            return 0.0;
        }

        // 原版公式：每 tick 累积「速度 / 硬度 / (30 或 100)」的进度，攒满 1 就破坏
        float speed = player.getBlockBreakingSpeed(state);
        boolean canHarvest = player.canHarvest(state);

        // 开着「自动换最佳工具」时，按换手之后那把工具来估 ——
        // 估的和真挖的是同一套标准，不然会出现「算着很便宜、真挖起来磨半天」的假路径
        if (options.autoSelectTool()) {
            InventoryScanner.ToolChoice best = InventoryScanner.bestTool(player, state);
            if (best != null) {
                speed = Math.max(speed, best.speed());
                canHarvest = canHarvest || best.suitable();
            }
        }

        int divisor = canHarvest ? 30 : 100;
        float perTick = speed / hardness / divisor;
        if (perTick <= 0.0f) {
            return -1.0;
        }
        return Math.ceil(1.0f / perTick);
    }

    /**
     * 这一个方向的「垫一块」候选。
     *
     * <p>条件是：目标那格本身是空的（不用挖），但下面那格也是空的——
     * 也就是悬空。往下面垫一块，就有了落脚点。
     *
     * @return 不需要垫、或者垫了也没用时返回 null
     */
    private static BlockPos placeTarget(ClientWorld world, BlockPos from, int x, int z) {
        int y = from.getY();
        if (!BlockNavigator.isPassableAt(world, x, y - 1, z)) {
            // 脚下本来就有东西，不用垫
            return null;
        }
        if (!BlockNavigator.isPassableAt(world, x, y, z) || !BlockNavigator.isPassableAt(world, x, y + 1, z)) {
            // 身子那两格被占着，那是「挖」的活
            return null;
        }
        return new BlockPos(x, y, z);
    }

    /** 挖完之后的斜向通行检查和平时一样：至少有一侧要留得下人 */
    private static boolean mineClear(ClientWorld world, BlockPos from, int x, int z) {
        return diagonalClear(world, from, new BlockPos(x, from.getY(), z));
    }

    /**
     * 斜向移动能不能过。
     *
     * <p>从 (x,z) 斜着到 (x+dx, z+dz)，玩家身体会同时扫过 (x+dx, z) 和 (x, z+dz)
     * 这两个侧边格子。两边都被堵死就会卡在角上，所以要求至少有一边是通的。
     */
    private static boolean diagonalClear(ClientWorld world, BlockPos from, BlockPos to) {
        int lowY = Math.min(from.getY(), to.getY());
        int highY = Math.max(from.getY(), to.getY());
        return columnClear(world, to.getX(), from.getZ(), lowY, highY)
                || columnClear(world, from.getX(), to.getZ(), lowY, highY);
    }

    /** 检查一列在 [lowY, highY] 这一段（含头部那一格）是不是都空着 */
    private static boolean columnClear(ClientWorld world, int x, int z, int lowY, int highY) {
        for (int y = lowY; y <= highY + 1; y++) {
            if (!BlockNavigator.isPassableAt(world, x, y, z)) {
                return false;
            }
        }
        return true;
    }

    // ================= 小工具 =================

    private static double stepCost(boolean diagonal, int deltaY) {
        double cost = diagonal ? COST_DIAGONAL : COST_STRAIGHT;
        if (deltaY > 0) {
            cost += COST_CLIMB;
        } else if (deltaY < 0) {
            cost += COST_DROP;
        }
        return cost;
    }

    /**
     * 启发函数：从 a 到 b 大约还要走多远。
     *
     * <p>必须<b>不高估</b>真实代价（「可采纳性」），A* 才能保证最短路。
     * 这里全按最便宜的「直着走」估算，而真实移动至少要这个价，条件满足。
     */
    private static double heuristic(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        double dy = Math.abs(a.getY() - b.getY());
        return Math.sqrt(dx * dx + dz * dz) + dy * COST_DROP;
    }

    /**
     * 搜索状态键：同一个坐标，垫过几块方块也算不同的状态。
     *
     * <p>{@code asLong()} 是一个很大的有符号数，乘上一个奇数再取模是双射，
     * 所以「坐标 × 32 + 已垫数」不会撞车（已垫数一定小于 32）。
     */
    private static long key(BlockPos pos, int placed) {
        return pos.asLong() * PLACED_STATES + placed;
    }

    private static List<PathStep> buildPath(Node goal) {
        ArrayList<PathStep> reversed = new ArrayList<>();
        // 终点本身要留在路径里（走到它才算到达），起点不用（玩家已经在上面了）
        for (Node node = goal; node != null && node.parent != null; node = node.parent) {
            reversed.add(new PathStep(node.pos, node.action));
        }
        Collections.reverse(reversed);
        return reversed;
    }

    private static double sq(double value) {
        return value * value;
    }
}
