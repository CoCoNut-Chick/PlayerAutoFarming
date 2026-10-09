package com.autofarm.client.container;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

/**
 * 「这一格是不是能存东西的容器」的通用判定。
 *
 * <h2>为什么不去认方块种类</h2>
 * 写死一张白名单（箱子、木桶、潜影盒……）看着直接，实际上是三种麻烦：
 * <ul>
 *   <li>原版自己就有十几种（箱子、陷阱箱、木桶、潜影盒、漏斗、发射器、投掷器……），
 *       漏一个就是「明明放了个箱子它却说没有存储设备」；</li>
 *   <li>材质包的箱子还是箱子，但加了「大箱子」「铜箱子」的模组就认不出来了；</li>
 *   <li>铁箱子、储物抽屉、背包模组……每个都有自己的方块类，白名单永远追不完。</li>
 * </ul>
 *
 * <p>换个角度就简单了：<b>能存东西的方块，必然挂着一个方块实体，而且那个方块实体
 * 实现了 {@link Inventory}</b>。这是 Fabric / 原版里容器共用的约定 ——
 * 箱子、木桶、模组容器全都满足它。所以这里判的是「能力」，不是「名字」：
 * 任何存储模组，只要它的方块实体照惯例实现了 {@code Inventory}，这里一律认得出来。
 *
 * <p>唯一的例外是那种自己造了一套存储协议、完全不用 {@code Inventory} 的模组，
 * 那就没法了 —— 但那种模组的方块通常也不接受普通的「右键打开 + Shift 点击搬运」，
 * 硬认出来也没有意义。
 */
public final class ContainerCompat {

    /**
     * 这一格是不是容器。
     *
     * <p>用 {@code world.getBlockEntity(pos) instanceof Inventory} 判定，
     * 和方块长什么样、叫什么名字都无关。
     */
    public static boolean isContainer(BlockView world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        BlockEntity entity = world.getBlockEntity(pos);
        return entity instanceof Inventory;
    }

    /**
     * 从 {@code pos} 出发找附近最近的容器。
     *
     * <p>为什么要找而不是直接用 {@code pos}：面板上「记录当前位置」记的是<b>玩家脚下那一格</b>。
     * 站在箱子旁边记录、站在箱子上面记录，都很正常 —— 那时候坐标指向的并不是箱子本身。
     * 所以这里以它为中心扫一圈（3×3×3），谁先被认成容器就用谁。
     *
     * <p>先看正中心，再看二十六格邻居，所以「坐标正好指着箱子」时永远是它自己。
     *
     * @return 找不到返回 null
     */
    public static BlockPos findContainer(BlockView world, BlockPos pos) {
        if (world == null || pos == null) {
            return null;
        }
        if (isContainer(world, pos)) {
            return pos.toImmutable();
        }
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos candidate = pos.add(dx, dy, dz);
                    if (isContainer(world, candidate)) {
                        return candidate.toImmutable();
                    }
                }
            }
        }
        return null;
    }

    private ContainerCompat() {
    }
}
