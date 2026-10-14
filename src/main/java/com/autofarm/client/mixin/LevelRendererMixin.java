package com.autofarm.client.mixin;

import com.autofarm.client.render.CropHighlighter;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
//? if 1.21.9 {
/*import net.minecraft.client.renderer.state.LevelRenderState;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.21.9 专用的描边挂载点。
 *
 * <h2>为什么需要它</h2>
 * 这一版 Minecraft 把世界渲染拆成了「抽取（extraction）→ 绘制（render）」两拍：
 * 先把这一帧要画的东西收集成 {@code LevelRenderState}，再统一绘制。
 * 而 Fabric 的世界渲染事件（{@code WorldRenderEvents}）恰好在这一版缺席 ——
 * 前一版被移除、下一版（1.21.10）才以新的包名挂回来。
 * 所以这一版没有任何现成的事件可以让模组挂上去。
 *
 * <h2>为什么挑「方块轮廓」这一步</h2>
 * 翻完 {@code LevelRenderer} 的两拍流程，最合适的注入点是：
 * <pre>
 *   抽取：extractBlockOutline(...)   →  绘制：renderBlockOutline(MultiBufferSource, PoseStack, ...)
 * </pre>
 * 理由有三：① 它手里正好有 <b>矩阵</b> 和 <b>缓冲</b>，画东西需要的都在；
 * ② 它干的事和我们的描边<b>同性质</b>（都是给方块描边），时机也正好在世界几何之后；
 * ③ 这是原版本来就有的步骤，不需要我们去猜更深的插入点。
 *
 * <h2>为什么方法体要写版本条件</h2>
 * 注入方法的参数里带着 {@code LevelRenderState} 这个只在 1.21.9+ 存在的类，
 * 直接写在类里会让老版本编译不过。所以：<b>类本身在所有版本都存在</b>
 * （这样 mixin 配置可以无条件注册它），<b>只有注入方法包在版本条件里</b>。
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    //? if 1.21.9 {
    /*@Inject(method = "renderBlockOutline", at = @At("TAIL"))
    private void autofarm$drawCropHighlight(MultiBufferSource.BufferSource buffers,
                                            PoseStack poseStack,
                                            boolean renderBlockOutline,
                                            LevelRenderState levelRenderState,
                                            CallbackInfo ci) {
        CropHighlighter.renderViaOutlineHook(poseStack, buffers);
    }
    *///?}
}
