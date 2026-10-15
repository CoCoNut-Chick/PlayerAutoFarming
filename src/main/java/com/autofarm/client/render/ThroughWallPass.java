package com.autofarm.client.render;

import com.autofarm.client.path.PathStep;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
//? if <1.21.5 {
import com.mojang.blaze3d.vertex.BufferUploader;
//?}
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
//? if <1.21.5 {
//? if <1.21.2 {
import net.minecraft.client.renderer.GameRenderer;
//?} else {
/*import net.minecraft.client.renderer.CoreShaders;
*///?}
//?}
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * 透视描边：绕开 MC 的图层系统，自己提交顶点，让描边压过一切遮挡。
 *
 * <h2>为什么单独成类</h2>
 * 这里是全项目<b>唯一</b>会「穿透不透明方块」的代码，而 Modrinth 的
 * 《Content Rules》第 3 条把「X-ray 或穿视不透明方块的能力」列为硬红线。
 * 所以合规版构建<b>整个排除这个文件</b>，换成 {@code src/compliant/java}
 * 下的空实现 —— 产物里不再存在这段能力，而不是靠开关关掉。
 *
 * <p>普通描边（{@link CropHighlighter} 里的 {@code renderDepthTested}）
 * 走的是 {@code RenderType.debugQuads}，它的 RenderPipeline 用的是默认深度测试
 * （LEQUAL，反编译确认过），所以会被前方方块正常遮挡 —— 那条路径与穿透无关。
 *
 * <h2>顺序很关键</h2>
 * <b>先关深度测试，紧接着写顶点并立刻画出去</b>，中间不留任何可能把状态改回去的环节。
 * 全程用 try/finally 还原，避免污染后面的天气、粒子、手部渲染。
 */
final class ThroughWallPass {
    private ThroughWallPass() {
    }

    static void draw(CropHighlighter.Params params, List<BlockPos> crops, List<BlockPos> spots,
                     List<BlockPos> extras, List<BlockPos> drops,
                     BlockPos current,
                     List<BlockPos> route, List<PathStep> segment) {
        //? if <1.21.5 {
        // 1.21.2 把「选哪个着色器」从 Supplier<ShaderInstance> 改成了 ShaderProgram 对象：
        //   GameRenderer::getPositionColorShader  →  CoreShaders.POSITION_COLOR
        //? if <1.21.2 {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        //?} else {
        /*RenderSystem.setShader(CoreShaders.POSITION_COLOR);
        *///?}
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        // 不往深度缓冲里写，否则描边会把它后面的东西错误地挡掉
        RenderSystem.depthMask(false);
        // 核心的一行：深度比较永不通过失败 → 描边无条件画在最前面，穿透一切遮挡
        RenderSystem.disableDepthTest();
        try {
            // 1.21 重写了缓冲的取法：
            //   老：Tesselator.getInstance().getBuilder() 拿一个**全局复用**的缓冲，
            //       再自己 begin(模式, 格式)；
            //   新：Tesselator.getInstance().begin(模式, 格式) 直接给一个**新的**缓冲。
            //      正因为每回都是新的，「上次被谁 fixedColor() 过、颜色被静默忽略」这个
            //      老坑在新版里自然不存在了，所以下面那句清状态也不需要了。
            //? if <1.21 {
            BufferBuilder buffer = Tesselator.getInstance().getBuilder();
            buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            // Tessellator 的缓冲是全局复用的，万一之前被谁 fixedColor() 过，
            // 我们的颜色会被静默忽略（画出来全白）。这一行把状态清掉。
            buffer.unsetDefaultColor();
            //?} else {
            /*BufferBuilder buffer = Tesselator.getInstance()
                    .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            *///?}
            CropHighlighter.drawAll(buffer, params, crops, spots, extras, drops, current, route, segment);
            // BufferBuilder#end 改名为 buildOrThrow（返回的 MeshData 就是原来的 RenderedBuffer）
            //? if <1.21 {
            BufferUploader.drawWithShader(buffer.end());
            //?} else {
            /*BufferUploader.drawWithShader(buffer.buildOrThrow());
            *///?}
        } finally {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
        //?} else {
        /*// 1.21.5 把 RenderSystem 那一整排状态开关（enableBlend / defaultBlendFunc /
        // disableCull / depthMask / disableDepthTest …）全删了 —— 渲染状态改成由
        // RenderType / RenderPipeline 声明式描述：「想怎么画」写在类型里，
        // 而不是一行行拨开关。
        //
        // 于是这里借用原版的 RenderType.debugQuads：它本来就是给调试线框用的、
        // 不写深度，正好就是「穿透一切遮挡」想要的效果。
        // 顶点接口（VertexConsumer）没变，所以 drawAll 一行都不用动。
        MultiBufferSource.BufferSource buffers =
                Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(CropHighlighter.highlightRenderType());
        CropHighlighter.drawAll(vc, params, crops, spots, extras, drops, current, route, segment);
        buffers.endBatch(CropHighlighter.highlightRenderType());
        *///?}
    }
}
