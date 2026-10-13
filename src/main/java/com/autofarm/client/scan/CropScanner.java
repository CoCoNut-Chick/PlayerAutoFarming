package com.autofarm.client.scan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 扫描玩家周围指定范围，找出「该去动一动的格子」。
 * 只在客户端跑，只读世界，不改任何东西。
 *
 * <h2>为什么要一次扫出三样东西</h2>
 * 同一片区域里我们关心的其实是三件事：
 * <ol>
 *   <li><b>成熟作物</b> —— 收割目标；</li>
 *   <li><b>空着的耕地</b> —— 补种目标（见 {@link CropRegistry#isPlantingSpot}）；</li>
 *   <li><b>有哪些作物种类</b> —— 只给「作物筛选」面板列清单用。</li>
 * </ol>
 * 它们天然是同一趟遍历就算得出来的（都要逐格看方块状态），所以抽了一个
 * {@link #forEachBlock} 的公共循环，上面三件事各自注册一个「看到一格做什么」的回调，
 * 而不是把同一段三重循环抄三遍。
 *
 * <h2>成熟作物为什么要能过滤</h2>
 * {@link #scan} 收一个 {@code matureAllowed} 谓词，判的是「这棵值不值得收」。
 * 作物筛选（白名单 / 黑名单 / 只收能补种的）就挂在这儿 —— 好处是
 * <b>被筛掉的作物根本不会进「成熟作物」列表</b>，于是世界里不会给它画描边，
 * 面板上的计数也对得上，不会出现「框在那儿却永远不去收」的怪事。
 */
public final class CropScanner {

    /** 往下找多少格（作物一般和脚同高，留点余量给梯田和坑地） */
    public static final int DEFAULT_DOWN = 6;
    /** 往上找多少格 */
    public static final int DEFAULT_UP = 4;

    private CropScanner() {
    }

    /**
     * 一趟扫描的全部结果。
     *
     * @param mature        成熟作物（已经按 {@code matureAllowed} 筛过）
     * @param emptyFarmland 空着的耕地（耕作格，也就是「作物该长在的那一格」）
     */
    public record ScanResult(List<BlockPos> mature, List<BlockPos> emptyFarmland) {
    }

    /**
     * 扫一遍。
     *
     * @param world         客户端世界
     * @param center        扫描中心（一般传玩家脚下方块坐标）
     * @param radius        水平半径，单位格
     * @param matureAllowed 成熟作物的过滤条件；传 {@code null} 表示全要
     * @param collectFarmland 要不要顺便收集空耕地（开关关着时传 false，省一趟判定）
     */
    public static ScanResult scan(ClientLevel world, BlockPos center, int radius, int down, int up,
                                  Predicate<BlockState> matureAllowed, boolean collectFarmland) {
        List<BlockPos> mature = new ArrayList<>();
        List<BlockPos> farmland = new ArrayList<>();

        forEachBlock(world, center, radius, down, up, (pos, state) -> {
            if (CropRegistry.isMature(state)) {
                if (matureAllowed == null || matureAllowed.test(state)) {
                    mature.add(pos.immutable());
                }
            } else if (collectFarmland && CropRegistry.isPlantingSpot(world, pos)) {
                farmland.add(pos.immutable());
            }
        });
        return new ScanResult(mature, farmland);
    }

    /**
     * 附近都有哪些种类的作物（不管熟没熟）。
     *
     * <p>给「作物筛选」面板列清单用 —— 站在田里打开面板，它就能把你周围实际种着的
     * 作物列出来让你勾。用的是 {@link CropRegistry#isHarvestCrop}，所以瓜茎不会混进来。
     */
    public static Set<Block> findCropTypes(ClientLevel world, BlockPos center, int radius,
                                          int down, int up) {
        Set<Block> types = new LinkedHashSet<>();
        forEachBlock(world, center, radius, down, up, (pos, state) -> {
            Block block = state.getBlock();
            if (CropRegistry.isHarvestCrop(block)) {
                types.add(block);
            }
        });
        return types;
    }

    /**
     * 找「还没熟、但已经种下了」的作物 —— 附加功能「骨粉催熟」的目标。
     *
     * <p>判据是 {@link CropRegistry#isGrowing}：是作物、还没熟、而且骨粉对它有效
     * （瓜茎和发光浆果已经被排除在外）。这类格子<b>不会</b>进「成熟作物」列表，
     * 所以在「骨粉催熟」关着的时候，这里连扫都不扫。
     */
    public static List<BlockPos> findImmatureCrops(ClientLevel world, BlockPos center, int radius,
                                                  int down, int up) {
        List<BlockPos> result = new ArrayList<>();
        forEachBlock(world, center, radius, down, up, (pos, state) -> {
            if (CropRegistry.isGrowing(state)) {
                result.add(pos.immutable());
            }
        });
        return result;
    }

    // ================= 瓜茎识别 + 自定义方块 =================

    /**
     * 找「紧挨着瓜茎、由茎自然长出来的瓜方块」（西瓜块 / 南瓜块）。
     *
     * <p>遍历时盯的是<b>茎</b>（{@link StemBlock}，含连着瓜的 attached stem），
     * 对每个茎看它东南西北四个水平方向，哪一格是瓜方块就把那一格收进结果。
     * 这样「瓜是不是茎长出来的」这件事自然成立 —— 玩家自己摆在别处的瓜、
     * 视野里的其它瓜，都不在结果里，因此这也不是「透视所有瓜」的矿透。
     */
    public static List<BlockPos> findGourdBlocks(ClientLevel world, BlockPos center, int radius,
                                                 int down, int up) {
        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        forEachBlock(world, center, radius, down, up, (pos, state) -> {
            if (!(state.getBlock() instanceof StemBlock)) {
                return;
            }
            for (Direction dir : HORIZONTALS) {
                BlockPos side = pos.relative(dir);
                if (CropRegistry.isGourdBlock(world.getBlockState(side).getBlock())
                        && seen.add(side.immutable())) {
                    result.add(side.immutable());
                }
            }
        });
        return result;
    }

    /**
     * 找「注册名在 ids 名单里」的方块（自定义要识别的东西）。
     *
     * <p>名单由玩家在面板上维护（见 {@code ExtrasScreen}）。这里只是逐格比对注册名，
     * 命中就收进结果 —— 空气和认不出名字的方块一律跳过。
     */
    public static List<BlockPos> findCustomBlocks(ClientLevel world, BlockPos center, int radius,
                                                  int down, int up, Set<String> ids) {
        List<BlockPos> result = new ArrayList<>();
        if (ids == null || ids.isEmpty()) {
            return result;
        }
        forEachBlock(world, center, radius, down, up, (pos, state) -> {
            if (state.isAir()) {
                return;
            }
            String id = CropRegistry.idOf(state.getBlock());
            if (id != null && ids.contains(id)) {
                result.add(pos.immutable());
            }
        });
        return result;
    }

    /** 瓜茎结瓜的四个水平方向 */
    private static final Direction[] HORIZONTALS = {
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    // ================= 公共遍历 =================

    /** 「看到一格就做点什么」。{@code pos} 是复用的游标，想留着坐标必须自己 {@code toImmutable()} */
    @FunctionalInterface
    private interface BlockVisitor {
        void visit(BlockPos.MutableBlockPos pos, BlockState state);
    }

    /**
     * 从 center 出发，在 (2r+1)² × (down+up+1) 的盒子里逐格回调。
     *
     * <p>两个坑都在这里一次性挡掉了：
     * <ul>
     *   <li><b>未加载的区块不去碰</b>：客户端 {@code getBlockState} 在区块缺失时会直接抛空指针，
     *       所以按区块整体跳过，而不是逐格 try/catch；</li>
     *   <li><b>游标只建一份</b>：这是个几十万次的循环，每格 new 一个 BlockPos 会明显拖慢扫描。</li>
     * </ul>
     */
    private static void forEachBlock(ClientLevel world, BlockPos center, int radius, int down, int up,
                                     BlockVisitor visitor) {
        if (world == null || center == null || radius <= 0) {
            return;
        }
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int minY = center.getY() - down;
        int maxY = center.getY() + up;

        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                if (!world.hasChunk(x >> 4, z >> 4)) {
                    continue;
                }
                for (int y = minY; y <= maxY; y++) {
                    cursor.set(x, y, z);
                    visitor.visit(cursor, world.getBlockState(cursor));
                }
            }
        }
    }
}
