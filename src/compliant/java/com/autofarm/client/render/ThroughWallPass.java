package com.autofarm.client.render;

import com.autofarm.client.path.PathStep;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * 透视描边的<b>合规版空实现</b>。
 *
 * <p>Modrinth《Content Rules》第 3 条禁止客户端模组具备
 * 「X-ray 或穿视不透明方块的能力」，且不以「默认关闭」免责 ——
 * 判定看的是 jar 里有没有这段能力。所以合规版构建把真正的
 * {@code ThroughWallPass} 整个排除掉，用这份空实现顶上，
 * 让 {@link CropHighlighter} 的调用点照常编译但什么也不做。
 *
 * <p>合规版里这个分支其实也走不到：{@code Edition.COMPLIANT} 是编译期常量，
 * 调用点的 {@code if} 会被 javac 折叠掉。留这一份是为了让「排除 + 替换」
 * 两件事都不依赖常量折叠，多一层保险。
 */
final class ThroughWallPass {
    private ThroughWallPass() {
    }

    static void draw(CropHighlighter.Params params, List<BlockPos> crops, List<BlockPos> spots,
                     List<BlockPos> extras, List<BlockPos> drops,
                     BlockPos current,
                     List<BlockPos> route, List<PathStep> segment) {
        // 合规版不提供穿透描边。
    }
}
