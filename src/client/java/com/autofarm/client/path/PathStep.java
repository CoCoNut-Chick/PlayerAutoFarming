package com.autofarm.client.path;

import net.minecraft.util.math.BlockPos;

/**
 * 路线上的一个落脚点。
 *
 * @param pos    要站上去的那一格
 * @param action 站上去之前要先做什么（见 {@link PathAction}）
 */
public record PathStep(BlockPos pos, PathAction action) {

    public static PathStep walk(BlockPos pos) {
        return new PathStep(pos, PathAction.WALK);
    }

    /** 这一步需要先改变世界吗（挖或垫） */
    public boolean needsWork() {
        return action != PathAction.WALK;
    }

    /** 这一步大约要占用多少额外时间，用来估算剩余路程（格） */
    public double extraCost() {
        return switch (action) {
            case WALK -> 0.0;
            case MINE -> 3.0;
            case PLACE -> 2.5;
        };
    }
}
