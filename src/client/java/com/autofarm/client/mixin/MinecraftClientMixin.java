package com.autofarm.client.mixin;

import com.autofarm.client.interact.BlockInteractor;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让 AutoFarm 能「自己挖方块」而不被原版每 tick 打断。
 *
 * <h2>问题出在哪</h2>
 * {@code MinecraftClient.handleBlockBreaking(boolean)} 是原版处理挖掘的入口，它做两件事：
 * <ol>
 *   <li>如果「没在挖」（也就是攻击键没按着），就调 {@code cancelBlockBreaking()}，
 *       把当前的破坏进度清零，并给服务端发一个放弃包；</li>
 *   <li>如果「在挖」，就用<b>准星指着的那一格</b>去推进破坏进度。</li>
 * </ol>
 *
 * <p>我们挖方块走的是 {@code ClientPlayerInteractionManager.updateBlockBreakingProgress}，
 * 是直接指定坐标的，不依赖准星，也不需要按攻击键。但这正好撞上第 1 条：
 * 我们没按攻击键 → 原版每 tick 都判定「没在挖」→ 每 tick 都把我们刚攒的进度清掉。
 * 硬度 0 的作物一次就碎所以看不出来，稍微硬一点的方块就永远挖不动。
 *
 * <h2>怎么解决</h2>
 * 注入到这个方法的<b>开头</b>，当这一 tick 的破坏是我们自己发起的时候直接取消整个方法：
 * 原版既不会清我们的进度，也不会拿准星去挖别的方块。
 *
 * <p>开关由 {@link BlockInteractor#isDriving()} 提供，它只在「这一 tick 我们真的动了镐子」
 * 时为 true。不挖掘的 tick 一律 false，原版行为一字不变 ——
 * 玩家自己按住左键挖矿、松开左键停手，全都照旧。
 */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void autofarm$keepOurBreaking(boolean breaking, CallbackInfo ci) {
        if (BlockInteractor.isDriving()) {
            ci.cancel();
        }
    }
}
