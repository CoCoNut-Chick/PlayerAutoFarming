package com.autofarm.client.path;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 访问顺序规划。
 *
 * <p>要解决的问题本质上是<b>旅行商问题（TSP）</b>：从玩家当前位置出发，
 * 把附近所有成熟作物都走一遍，怎么排队总路程最短？
 *
 * <p>TSP 是 NP-hard，上百个点时精确解根本算不动，所以这里把两个近似算法接起来用：
 *
 * <ol>
 *   <li><b>最近邻贪心</b>——每一步都挑「还没去过、离我最近」的那个点。
 *       复杂度只有 O(n²)，几乎是瞬间出结果。但贪心会挖坑：
 *       为了一个近点提前拐弯，可能把一个本来顺路的远点甩到了路线末尾。</li>
 *   <li><b>2-opt 优化</b>——反复检查「如果把路径上第 i 到第 j 个点<b>整段翻转</b>，
 *       总长度会不会变短」，会就翻。它消掉的是路线里那些<b>自己交叉</b>的走法：
 *       两条线段交叉一定意味着可以拆开重接变短，所以每一次成功翻转都是净赚。
 *       翻到翻不动为止（或超出时间预算）。</li>
 * </ol>
 *
 * <p>本类不碰世界、不碰输入、不改任何状态，纯计算。
 * 输入一串坐标，输出访问顺序，因此可以放心地在任何时机调用。
 */
public final class PathPlanner {

    /**
     * 规划的时间预算（纳秒）。
     *
     * <p>一个游戏 tick 只有 50ms，其中还要跑世界逻辑和渲染，
     * 所以规划最多只允许占用一小块。2-opt 是 O(n²) 一轮的迭代算法，
     * 极端情况（几百个目标全挤在一起）可能翻很久，超过预算就带着
     * <b>当前这版已经能用的结果</b>直接收工——宁可少优化一点，也不能卡帧。
     */
    public static final long DEFAULT_BUDGET_NANOS = 4_000_000L;

    /** 2-opt 最多来回扫多少轮 */
    private static final int MAX_OPT_ROUNDS = 24;

    /**
     * 单次规划的目标数上限。
     *
     * <p>距离矩阵是 (n+1)²，n=256 时约 66 万个 double（约 0.5MB），
     * 一次规划用完就丢，不至于有压力。再多就只取离玩家最近的一批。
     */
    private static final int MAX_TARGETS = 256;

    /** 竖直方向每走一格的「等效代价」——上坡下坡比平地挪一格费劲得多 */
    private static final double VERTICAL_WEIGHT = 1.5;

    private PathPlanner() {
    }

    /**
     * 一条规划好的路线。
     *
     * @param order     访问顺序（不含起点，起点就是调用时的玩家位置）
     * @param length    这条路线的总长度（格）
     * @param optimized 是否真的做过 2-opt（目标太少或整体没得优化时会是 false）
     */
    public record Plan(List<BlockPos> order, double length, boolean optimized) {

        public int size() {
            return order.size();
        }
    }

    public static Plan plan(BlockPos start, Collection<BlockPos> targets, boolean optimize) {
        return plan(start, targets, optimize, DEFAULT_BUDGET_NANOS);
    }

    /**
     * @param start       出发点（一般是玩家脚下）
     * @param targets     所有要去的点
     * @param optimize    true 用最近邻 + 2-opt，false 只用最近邻贪心
     * @param budgetNanos 优化阶段的时间预算
     */
    public static Plan plan(BlockPos start, Collection<BlockPos> targets,
                            boolean optimize, long budgetNanos) {
        // ---- 去重，并剔掉和起点重合的点（走到起点等于已经在终点） ----
        LinkedHashSet<BlockPos> unique = new LinkedHashSet<>();
        if (targets != null) {
            for (BlockPos pos : targets) {
                if (pos != null && !pos.equals(start)) {
                    unique.add(pos);
                }
            }
        }
        List<BlockPos> nodes = new ArrayList<>(unique);
        int n = nodes.size();
        if (n == 0) {
            return new Plan(List.of(), 0.0, false);
        }

        if (n > MAX_TARGETS) {
            // 保留离起点最近的一批。玩家本来也是按「从近到远」推进的，
            // 远的那些等扫到近处、重新规划时自然会排进来。
            nodes.sort(Comparator.comparingDouble((BlockPos p) -> roughDistance(start, p)));
            nodes = new ArrayList<>(nodes.subList(0, MAX_TARGETS));
            n = MAX_TARGETS;
        }

        // ---- 建距离矩阵。下标 0 留给起点，1..n 对应 nodes[0..n-1] ----
        double[][] dist = buildDistanceMatrix(start, nodes);

        // ---- 第一步：最近邻贪心，先拿到一条完整路线 ----
        int[] order = nearestNeighborOrder(dist, n);
        double length = pathLength(dist, order);
        boolean optimized = false;

        // ---- 第二步：2-opt 把它磨短 ----
        if (optimize && n >= 3) {
            long deadline = System.nanoTime() + budgetNanos;
            optimized = twoOpt(dist, order, deadline) > 0;
            if (optimized) {
                length = pathLength(dist, order);
            }
        }

        List<BlockPos> result = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) {
            result.add(nodes.get(order[i] - 1));
        }
        return new Plan(Collections.unmodifiableList(result), length, optimized);
    }

    // ================= 距离 =================

    /**
     * 两点之间「走过去大概要多少格」。
     *
     * <p>水平方向用直线距离，竖直方向单独乘一个权重：
     * 上一格要垫方块或者绕楼梯，实际代价远不止「一格」，
     * 而水平方向在开阔农田里基本就是直线，所以不额外惩罚。
     */
    private static double travelDistance(BlockPos a, BlockPos b) {
        double dx = a.getX() - (double) b.getX();
        double dz = a.getZ() - (double) b.getZ();
        double dy = Math.abs(a.getY() - (double) b.getY());
        return Math.sqrt(dx * dx + dz * dz) + dy * VERTICAL_WEIGHT;
    }

    /** 只用来「挑最近的 256 个」时粗排一下，不用开方 */
    private static double roughDistance(BlockPos a, BlockPos b) {
        double dx = a.getX() - (double) b.getX();
        double dz = a.getZ() - (double) b.getZ();
        double dy = Math.abs(a.getY() - (double) b.getY()) * VERTICAL_WEIGHT;
        return dx * dx + dz * dz + dy * dy;
    }

    private static double[][] buildDistanceMatrix(BlockPos start, List<BlockPos> nodes) {
        int n = nodes.size();
        double[][] dist = new double[n + 1][n + 1];

        for (int i = 0; i < n; i++) {
            double d = travelDistance(start, nodes.get(i));
            dist[0][i + 1] = d;
            dist[i + 1][0] = d;
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double d = travelDistance(nodes.get(i), nodes.get(j));
                dist[i + 1][j + 1] = d;
                dist[j + 1][i + 1] = d;
            }
        }
        return dist;
    }

    // ================= 最近邻 =================

    /**
     * 贪心排出一条初始路线。
     *
     * <p>{@code order[0]} 恒为 0（起点），后面依次是去到的点。
     */
    private static int[] nearestNeighborOrder(double[][] dist, int n) {
        int[] order = new int[n + 1];
        boolean[] used = new boolean[n + 1];
        order[0] = 0;
        used[0] = true;

        int current = 0;
        for (int step = 1; step <= n; step++) {
            int best = -1;
            double bestDist = Double.MAX_VALUE;
            for (int candidate = 1; candidate <= n; candidate++) {
                if (used[candidate]) {
                    continue;
                }
                double d = dist[current][candidate];
                if (d < bestDist) {
                    bestDist = d;
                    best = candidate;
                }
            }
            order[step] = best;
            used[best] = true;
            current = best;
        }
        return order;
    }

    // ================= 2-opt =================

    /**
     * 2-opt 优化。
     *
     * <p>标准 2-opt 是给「闭环」设计的，我们这里是「一条有头有尾的路线」，
     * 所以要处理一个特例：当第二条边取到<b>路线最后一段之后</b>（也就是没有后续点）时，
     * 收益只算前半截——那条不存在的边代价按 0 处理。
     *
     * @return 成功翻转的次数
     */
    private static int twoOpt(double[][] dist, int[] order, long deadlineNanos) {
        int n = order.length - 1;
        int swaps = 0;

        for (int round = 0; round < MAX_OPT_ROUNDS; round++) {
            boolean improvedThisRound = false;

            for (int i = 1; i < n; i++) {
                if (System.nanoTime() > deadlineNanos) {
                    return swaps;
                }
                for (int j = i + 1; j <= n; j++) {
                    int a = order[i - 1];
                    int b = order[i];
                    int c = order[j];
                    boolean hasTail = j + 1 <= n;
                    int d = hasTail ? order[j + 1] : -1;

                    // 翻转前的两条边：(a→b) 和 (c→d)
                    // 翻转后的两条边：(a→c) 和 (b→d)
                    double before = dist[a][b] + (hasTail ? dist[c][d] : 0.0);
                    double after = dist[a][c] + (hasTail ? dist[b][d] : 0.0);

                    if (after < before - 1.0e-9) {
                        reverse(order, i, j);
                        improvedThisRound = true;
                        swaps++;
                    }
                }
            }

            if (!improvedThisRound) {
                break;
            }
        }
        return swaps;
    }

    /** 把 order[from..to] 这一段原地倒过来 */
    private static void reverse(int[] order, int from, int to) {
        while (from < to) {
            int tmp = order[from];
            order[from] = order[to];
            order[to] = tmp;
            from++;
            to--;
        }
    }

    private static double pathLength(double[][] dist, int[] order) {
        double sum = 0.0;
        for (int i = 1; i < order.length; i++) {
            sum += dist[order[i - 1]][order[i]];
        }
        return sum;
    }
}
