package com.autofarm.client.mixin;

import com.autofarm.client.human.RescueHelper;
import com.autofarm.client.path.PathWalker;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让 AutoFarm 能临时「接管」玩家的移动输入。
 *
 * <h2>为什么挑这个位置注入</h2>
 * {@code KeyboardInput.tick} 是每 tick 客户端把<b>键位绑定</b>翻译成
 * <b>移动意图</b>的地方：它把 {@code settings.forwardKey.isPressed()} 之类的结果
 * 抄进 {@link Input} 的 {@code pressingForward} / {@code movementForward} 等字段，
 * 之后 {@code ClientPlayerEntity} 就照着这份字段走。
 *
 * <p>注入在 {@code TAIL}（原版代码全部跑完之后），所以：
 * <ul>
 *   <li>我们改的是「这一 tick 最终生效的结果」，不会被后面的原版逻辑覆盖；</li>
 *   <li>玩家真实的按键状态（{@code KeyBinding.pressed}）<b>完全没有被改动</b>，
 *       接管结束时不需要「恢复」，也不会出现「玩家自己按 W 却不灵了」这种副作用。</li>
 * </ul>
 *
 * <p>真正要不要接管、接管成什么样，全部由 {@link PathWalker#applyInputOverride} 决定：
 * 它没有在控制角色时会直接返回，一个字段都不碰。
 */
@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void autofarm$applyWalkInput(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        Input input = (Input) (Object) this;
        // 寻路优先；它没在接管（手上没有路线）时才轮到环境自救。
        // 触发自救时任务大脑会先把寻路停掉，所以两边不会同时抢输入。
        PathWalker.applyInputOverride(input);
        RescueHelper.applyInputOverride(input);
    }
}
