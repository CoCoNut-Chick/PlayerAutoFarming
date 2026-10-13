package com.autofarm.client.mixin;

import com.autofarm.client.human.RescueHelper;
import com.autofarm.client.path.PathWalker;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让 AutoFarm 能「替玩家迈步」—— 包括<b>自由视角 / 灵魂出窍开着的时候</b>。
 *
 * <h2>为什么注入在 {@code tickNewAi} 的末尾</h2>
 * 原版把「按键」翻译成「这一 tick 要往哪走」分两步：
 * <ol>
 *   <li>{@code ClientPlayerEntity.tickMovement()} 里调 {@code input.tick(...)}，
 *       把键位抄成 {@link net.minecraft.client.player.Input} 的
 *       {@code movementForward / movementSideways / jumping}；</li>
 *   <li>{@code tickNewAi()} 里再把这份输入抄进实体自己的移动意图字段
 *       {@code sidewaysSpeed / forwardSpeed / jumping}。</li>
 * </ol>
 *
 * <p>第 2 步外面套着一个判断：
 * <pre>{@code
 *  if (this.isCamera()) {                 // 摄像机和身体重合？
 *      this.sidewaysSpeed = this.input.movementSideways;
 *      this.forwardSpeed  = this.input.movementForward;
 *      this.jumping       = this.input.jumping;
 *  }
 * }</pre>
 * <b>自由视角一开，{@code isCamera()} 就成了 false，这整段被跳过</b> ——
 * 于是无论怎么改 {@code Input}，角色都一步也走不了。这就是「开了灵魂出窍角色不动」的根因。
 *
 * <p>所以这里注入在 {@code tickNewAi} 的 {@code TAIL}（原版那段判断跑完之后），
 * 直接把移动意图写到 {@code sidewaysSpeed / forwardSpeed / jumping} 上：
 * <ul>
 *   <li>自由视角下：补上原版跳过的那一步，角色照常走；</li>
 *   <li>正常视角下：写在我们自己的意图上，覆盖掉原版刚抄进来的值 —— 效果与以前一致；</li>
 *   <li><b>全程不碰键盘输入对象</b>：自由视角模组拿 WASD 飞摄像机的那套逻辑完全不受影响，
 *       两边不再互相抢输入。玩家自己的按键也原样留着。</li>
 * </ul>
 */
@Mixin(LocalPlayer.class)
public class ClientPlayerInputMixin {

    @Inject(method = "serverAiStep()V", at = @At("TAIL"))
    private void autofarm$applyMovementIntent(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer) (Object) this;
        // 寻路优先；它没在接管（手上没有路线 / 已挂起）时才轮到环境自救。
        // 触发自救时任务大脑会先把寻路停掉，所以两边不会同时抢。
        boolean driving = PathWalker.applyInputOverride(player);
        driving |= RescueHelper.applyInputOverride(player);
        if (!driving) {
            PathWalker.clearIdleIntent(player);
        }
    }
}
