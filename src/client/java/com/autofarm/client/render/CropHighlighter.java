package com.autofarm.client.render;

import com.autofarm.client.config.AutoFarmConfig;
import com.autofarm.client.config.ConfigManager;
import com.autofarm.client.path.PathAction;
import com.autofarm.client.path.PathStep;
import com.autofarm.client.task.FarmTaskManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 把扫描到的成熟作物在世界里描出粗线框。
 *
 * <p>纯客户端渲染，只画给你看，不会碰世界里的任何方块。
 *
 * <p>挂载点是 {@code WorldRenderEvents.AFTER_TRANSLUCENT} —— 这个阶段
 * 矩阵栈已经带上了相机位移（世界原点相对相机的偏移），
 * 所以方块坐标要先减去相机坐标。
 *
 * <h2>为什么不用原版那两个现成的东西</h2>
 * <ul>
 *   <li>{@code RenderSystem.lineWidth()}：OpenGL 核心模式只保证支持 1 像素线宽，
 *       多数驱动会把更大的线宽请求<b>静默忽略</b>（不报错，就是没效果）。</li>
 *   <li>{@code WorldRenderer.drawBox()}：反编译可以看到它内部固定写 24 个顶点、
 *       走 LINES 模式，也就是<b>只能画 12 条 1 像素细线</b>，
 *       在 QUADS 图层上用它只会画出一堆错乱的四边形。</li>
 * </ul>
 * 所以「加粗」改成自己写顶点：把 12 条棱各自当成一个很扁的实心盒子来画。
 * 这样没有叠线、没有缝隙，宽度还完全可控。
 *
 * <h2>透视（穿墙可见）为什么不能在外面改渲染状态</h2>
 * 在外面调 {@code RenderSystem.disableDepthTest()} 是<b>没用</b>的：
 * 顶点只是先被塞进缓冲，真正提交发生在后面的 flush，
 * 而 flush 时 {@code RenderLayer} 会执行它自己的渲染阶段状态，
 * 把深度测试<b>重新打开</b>——我们关掉的那一下被覆盖了，于是描边照样被墙挡住。
 *
 * <p>正确做法是让「关掉深度测试」和「提交顶点」<b>之间没有任何东西插进来</b>。
 * 所以透视走一条独立的提交路径：不走 {@code RenderLayer}，
 * 而是自己拿 {@code BufferBuilder} 写顶点、立刻交给 {@code BufferRenderer} 画出去，
 * 用 try/finally 保证渲染状态原样还原。这也是各类矿物透视 / 方块 ESP 模组的通用手法。
 *
 * <h2>宽度为什么能「不管多远都一样粗」</h2>
 * 距离 {@code d} 处，屏幕上 1 像素对应的世界尺寸是
 * {@code 2·d·tan(fov/2) / 屏幕高}。按这个比例把「想要几像素」换算成世界尺寸，
 * 那么 1080p 和 4K、站着和站远处，视觉粗细都一致。
 */
public final class CropHighlighter {

    /** 往外扩一点，避免描边和方块表面 z-fighting 闪烁 */
    private static final double BASE_INFLATE = 0.002;

    /** 描边半宽下限（世界单位）。再薄会因为浮点精度直接看不见 */
    private static final double MIN_HALF = 0.0015;

    /** 描边半宽上限（世界单位）。防止远处按像素换算出来的宽度粗到糊成一坨 */
    private static final double MAX_HALF = 0.05;

    /** 超过这个距离的作物不画描边。透视模式下这道闸尤其重要 */
    private static final double MAX_DISTANCE = 48.0;
    private static final double MAX_DISTANCE_SQ = MAX_DISTANCE * MAX_DISTANCE;

    /** 屏幕像素宽度换算的向上取整上限（和配置里 highlightWidth 的范围一致） */
    private static final int MAX_PIXELS = 8;

    private CropHighlighter() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(CropHighlighter::render);
    }

    /** 这一帧画描边需要的全部参数，打包传给两条提交路径 */
    private record Params(Matrix4f matrix, Vec3d camera, double tanHalfFov, int framebufferHeight,
                          double pixels, float red, float green, float blue, float alpha) {
    }

    private static void render(WorldRenderContext context) {
        AutoFarmConfig config = ConfigManager.get();
        if (!config.visualize && !config.showPath) {
            return;
        }

        // 空列表就等于「这一块不画」，下面两条提交路径不用再各自判断开关
        List<BlockPos> crops = config.visualize ? FarmTaskManager.getMatureCrops() : List.of();
        // 空耕地（补种目标）用另一种颜色描出来 —— 不然你会看到角色往一片空地上跑，
        // 而画面上什么都没有，根本不知道它去干嘛
        List<BlockPos> spots = config.visualize ? FarmTaskManager.getFarmlandTargets() : List.of();
        // 额外目标（瓜茎旁的瓜 / 自定义方块）再用第三种颜色 —— 它们不是作物，却也是收割目标
        List<BlockPos> extras = config.visualize ? FarmTaskManager.getExtraTargets() : List.of();
        List<BlockPos> route = config.showPath ? FarmTaskManager.getRoute() : List.of();
        List<PathStep> segment = config.showPath ? FarmTaskManager.getActiveSegment() : List.of();
        if (crops.isEmpty() && spots.isEmpty() && extras.isEmpty()
                && route.isEmpty() && segment.isEmpty()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getWindow() == null) {
            return;
        }

        MatrixStack matrices = context.matrixStack();
        Params params = new Params(
                matrices.peek().getPositionMatrix(),
                context.camera().getPos(),
                // 「1 像素等于多少格」的换算基准（见类注释）
                Math.tan(Math.toRadians(client.options.getFov().getValue()) / 2.0),
                Math.max(1, client.getWindow().getFramebufferHeight()),
                Math.max(1, Math.min(MAX_PIXELS, config.highlightWidth)),
                config.highlightRed(),
                config.highlightGreen(),
                config.highlightBlue(),
                config.highlightAlpha01());

        if (config.highlightThroughWalls) {
            renderThroughWalls(params, crops, spots, extras, route, segment);
        } else {
            renderDepthTested(context, params, crops, spots, extras, route, segment);
        }
    }

    /**
     * 普通路径：交给 MC 的图层系统。
     *
     * <p>{@code RenderLayer.getDebugQuads()} 的配置正好合用（反编译确认过）：
     * {@code POSITION_COLOR} 顶点 + {@code QUADS} 模式 + {@code COLOR_PROGRAM}
     * + 透明混合 + 不剔除背面 + 标准的深度测试。所以描边会被前方方块正常遮挡。
     */
    private static void renderDepthTested(WorldRenderContext context, Params params,
                                          List<BlockPos> crops, List<BlockPos> spots,
                                          List<BlockPos> extras,
                                          List<BlockPos> route, List<PathStep> segment) {
        VertexConsumerProvider consumers = context.consumers();
        if (consumers == null) {
            return;
        }

        VertexConsumer vertexConsumer = consumers.getBuffer(RenderLayer.getDebugQuads());
        drawAll(vertexConsumer, params, crops, spots, extras, route, segment);

        // 事件返回后 MC 还会统一绘制一次，这里主动 flush 让绘制时机确定可控
        if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw();
        }
    }

    /**
     * 透视路径：绕开图层系统，自己提交。
     *
     * <p>顺序很关键——<b>先关深度测试，紧接着写顶点并立刻画出去</b>，
     * 中间不留任何可能把状态改回去的环节。
     *
     * <p>全程用 try/finally 还原，避免污染后面的天气、粒子、手部渲染。
     */
    private static void renderThroughWalls(Params params, List<BlockPos> crops, List<BlockPos> spots,
                                          List<BlockPos> extras,
                                          List<BlockPos> route, List<PathStep> segment) {
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        // 不往深度缓冲里写，否则描边会把它后面的东西错误地挡掉
        RenderSystem.depthMask(false);
        // 核心的一行：深度比较永不通过失败 → 描边无条件画在最前面，穿透一切遮挡
        RenderSystem.disableDepthTest();
        try {
            BufferBuilder buffer = Tessellator.getInstance().getBuffer();
            buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            // Tessellator 的缓冲是全局复用的，万一之前被谁 fixedColor() 过，
            // 我们的颜色会被静默忽略（画出来全白）。这一行把状态清掉。
            buffer.unfixColor();
            drawAll(buffer, params, crops, spots, extras, route, segment);
            BufferRenderer.drawWithGlobalProgram(buffer.end());
        } finally {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    /**
     * 把所有成熟作物画成线框。
     *
     * <p>参数写成 {@link VertexConsumer}，所以普通路径（图层缓冲）和
     * 透视路径（{@code BufferBuilder}，它本身就是个 {@code VertexConsumer}）
     * 可以共用这一份代码。
     */
    private static void drawAll(VertexConsumer vc, Params p, List<BlockPos> crops,
                                List<BlockPos> spots, List<BlockPos> extras,
                                List<BlockPos> route, List<PathStep> segment) {
        drawBoxes(vc, p, crops, p.red(), p.green(), p.blue(), p.alpha());
        // 空耕地用固定的青色：和成熟作物的橙红区分开，也避开「挖红 / 垫蓝」这两个路线色
        if (!spots.isEmpty()) {
            drawBoxes(vc, p, spots, EMPTY_FARMLAND_R, EMPTY_FARMLAND_G, EMPTY_FARMLAND_B, p.alpha());
        }
        // 额外目标（瓜 / 自定义方块）用金黄：既不是「成熟作物」也不是「空地」，单独一档
        if (!extras.isEmpty()) {
            drawBoxes(vc, p, extras, EXTRA_R, EXTRA_G, EXTRA_B, p.alpha());
        }
        drawRoute(vc, p, route, segment);
    }

    /** 空耕地描边的颜色（青绿），刻意和成熟作物的橙红拉开距离 */
    private static final float EMPTY_FARMLAND_R = 0.32f;
    private static final float EMPTY_FARMLAND_G = 0.86f;
    private static final float EMPTY_FARMLAND_B = 0.70f;

    /** 额外目标描边的颜色（金黄），和橙红（作物）、青绿（空耕地）都区分开 */
    private static final float EXTRA_R = 0.98f;
    private static final float EXTRA_G = 0.78f;
    private static final float EXTRA_B = 0.16f;

    /**
     * 把一串方块坐标各画成一个粗线框。
     *
     * <p>成熟作物和空耕地共用这一份实现，只有颜色不同 —— 两者的画法是同一件事
     * （12 条棱，每条一个扁盒子）。
     */
    private static void drawBoxes(VertexConsumer vc, Params p, List<BlockPos> boxes,
                                  float r, float g, float b, float a) {
        Vec3d camera = p.camera();
        double pixelWorld = p.tanHalfFov() / p.framebufferHeight() * p.pixels();

        for (BlockPos pos : boxes) {
            double dx = pos.getX() + 0.5 - camera.x;
            double dy = pos.getY() + 0.5 - camera.y;
            double dz = pos.getZ() + 0.5 - camera.z;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > MAX_DISTANCE_SQ) {
                continue;
            }

            double half = Math.max(MIN_HALF, Math.min(MAX_HALF, Math.sqrt(distSq) * pixelWorld));

            double x1 = pos.getX() - camera.x - BASE_INFLATE;
            double y1 = pos.getY() - camera.y - BASE_INFLATE;
            double z1 = pos.getZ() - camera.z - BASE_INFLATE;
            double x2 = pos.getX() + 1 - camera.x + BASE_INFLATE;
            double y2 = pos.getY() + 1 - camera.y + BASE_INFLATE;
            double z2 = pos.getZ() + 1 - camera.z + BASE_INFLATE;

            Matrix4f m = p.matrix();

            // 12 条棱，每条一个扁盒子（厚度 2*half）
            // —— 沿 X 的 4 条
            solidBox(vc, m, x1, y1 - half, z1 - half, x2, y1 + half, z1 + half, r, g, b, a);
            solidBox(vc, m, x1, y1 - half, z2 - half, x2, y1 + half, z2 + half, r, g, b, a);
            solidBox(vc, m, x1, y2 - half, z1 - half, x2, y2 + half, z1 + half, r, g, b, a);
            solidBox(vc, m, x1, y2 - half, z2 - half, x2, y2 + half, z2 + half, r, g, b, a);
            // —— 沿 Y 的 4 条
            solidBox(vc, m, x1 - half, y1, z1 - half, x1 + half, y2, z1 + half, r, g, b, a);
            solidBox(vc, m, x2 - half, y1, z1 - half, x2 + half, y2, z1 + half, r, g, b, a);
            solidBox(vc, m, x1 - half, y1, z2 - half, x1 + half, y2, z2 + half, r, g, b, a);
            solidBox(vc, m, x2 - half, y1, z2 - half, x2 + half, y2, z2 + half, r, g, b, a);
            // —— 沿 Z 的 4 条
            solidBox(vc, m, x1 - half, y1 - half, z1, x1 + half, y1 + half, z2, r, g, b, a);
            solidBox(vc, m, x2 - half, y1 - half, z1, x2 + half, y1 + half, z2, r, g, b, a);
            solidBox(vc, m, x1 - half, y2 - half, z1, x1 + half, y2 + half, z2, r, g, b, a);
            solidBox(vc, m, x2 - half, y2 - half, z1, x2 + half, y2 + half, z2, r, g, b, a);
        }
    }

    /**
     * 把路线画成折线。这里其实画了两层，粗细和透明度都不一样：
     *
     * <ol>
     *   <li><b>实线（亮）</b>——{@code segment}，也就是 A* 针对当前目标算出来的
     *       真实落脚点序列。这一层绕开障碍、上台阶、钻门洞，是角色实际会走出来的轨迹；</li>
     *   <li><b>淡线</b>——后面还没轮到算路的那些作物之间的直连。
     *       它们只表示<b>去的顺序</b>，不代表真的会直着穿过去，
     *       所以画得很淡，免得看起来像要穿墙。</li>
     * </ol>
     *
     * <p>每一段都是一根独立的细方管（见 {@link #beam}），
     * 粗细同样按屏幕像素换算，所以远处不会细成一根头发丝。
     */
    private static void drawRoute(VertexConsumer vc, Params p,
                                  List<BlockPos> route, List<PathStep> segment) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || (route.isEmpty() && segment.isEmpty())) {
            return;
        }

        Vec3d camera = p.camera();
        // 实线比描边稍粗一点：它是「面」上的东西，1 像素会看不清
        double solidPixels = Math.max(2.0, p.pixels());
        double fadedPixels = Math.max(1.5, p.pixels() * 0.6);

        // 起点：角色脚下，稍微抬一点免得埋进地里
        double ax = client.player.getX() - camera.x;
        double ay = client.player.getY() + 0.15 - camera.y;
        double az = client.player.getZ() - camera.z;

        // ---- 第一层：A* 的实际路径 ----
        for (PathStep step : segment) {
            BlockPos waypoint = step.pos();
            double bx = waypoint.getX() + 0.5 - camera.x;
            double by = waypoint.getY() + 0.5 - camera.y;
            double bz = waypoint.getZ() + 0.5 - camera.z;

            // 要挖的那一格画红、要垫的那一格画蓝，一眼就能看出它打算怎么开路
            float red = p.red();
            float green = p.green();
            float blue = p.blue();
            if (step.action() == PathAction.MINE) {
                red = 0.89f;
                green = 0.29f;
                blue = 0.29f;
            } else if (step.action() == PathAction.PLACE) {
                red = 0.22f;
                green = 0.54f;
                blue = 0.87f;
            }

            beamBetween(vc, p, ax, ay, az, bx, by, bz, solidPixels, p.alpha(), red, green, blue);
            ax = bx;
            ay = by;
            az = bz;
        }

        if (segment.isEmpty() && !route.isEmpty()) {
            // 还没来得及给当前目标算路：至少把「朝它去」这一截连上
            BlockPos first = route.get(0);
            double bx = first.getX() + 0.5 - camera.x;
            double by = first.getY() + 0.5 - camera.y;
            double bz = first.getZ() + 0.5 - camera.z;
            beamBetween(vc, p, ax, ay, az, bx, by, bz, solidPixels, p.alpha(),
                    p.red(), p.green(), p.blue());
            ax = bx;
            ay = by;
            az = bz;
        }

        // ---- 第二层：后面那些目标之间的顺序示意 ----
        for (int i = 1; i < route.size(); i++) {
            BlockPos next = route.get(i);
            double bx = next.getX() + 0.5 - camera.x;
            double by = next.getY() + 0.5 - camera.y;
            double bz = next.getZ() + 0.5 - camera.z;
            beamBetween(vc, p, ax, ay, az, bx, by, bz, fadedPixels, p.alpha() * 0.3f,
                    p.red(), p.green(), p.blue());
            ax = bx;
            ay = by;
            az = bz;
        }
    }

    /** 画一条按屏幕像素定粗细的线段（宽度用线段中点到相机的距离换算） */
    private static void beamBetween(VertexConsumer vc, Params p,
                                    double ax, double ay, double az,
                                    double bx, double by, double bz,
                                    double pixels, float alpha,
                                    float red, float green, float blue) {
        double mx = (ax + bx) * 0.5;
        double my = (ay + by) * 0.5;
        double mz = (az + bz) * 0.5;
        double pixelWorld = p.tanHalfFov() / p.framebufferHeight() * pixels;
        double half = clampHalf(Math.sqrt(mx * mx + my * my + mz * mz) * pixelWorld);
        beam(vc, p.matrix(), ax, ay, az, bx, by, bz, half, red, green, blue, alpha);
    }

    /**
     * 画一根从 A 到 B 的方管（横截面是边长 2r 的正方形）。
     *
     * <p>任意方向的线段没法用轴对齐的盒子拼，所以先把方向单位化，
     * 再和它叉乘出两个互相垂直的单位向量 u、v；用 u、v 把线段两端各撑成 4 个角，
     * 最后把 4 个侧面连起来。因为渲染时关掉了背面剔除，不用操心面朝哪一边。
     */
    private static void beam(VertexConsumer vc, Matrix4f m,
                             double ax, double ay, double az,
                             double bx, double by, double bz,
                             double r,
                             float cr, float cg, float cb, float ca) {
        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-6) {
            return;
        }
        dx /= len;
        dy /= len;
        dz /= len;

        // u：垂直于方向的一个单位向量。
        // 方向不竖直时取 dir × (0,1,0)，竖直时换成 dir × (1,0,0)（否则叉乘退化成零向量）
        double ux;
        double uy;
        double uz;
        if (Math.abs(dy) < 0.9) {
            double inv = 1.0 / Math.sqrt(dz * dz + dx * dx);
            ux = -dz * inv;
            uy = 0.0;
            uz = dx * inv;
        } else {
            double inv = 1.0 / Math.sqrt(dz * dz + dy * dy);
            ux = 0.0;
            uy = dz * inv;
            uz = -dy * inv;
        }

        // v = dir × u，天然也是单位向量且与 dir、u 都垂直
        double vx = dy * uz - dz * uy;
        double vy = dz * ux - dx * uz;
        double vz = dx * uy - dy * ux;

        double[] px = new double[4];
        double[] py = new double[4];
        double[] pz = new double[4];
        double[] qx = new double[4];
        double[] qy = new double[4];
        double[] qz = new double[4];
        double[][] corners = {{1, 1}, {1, -1}, {-1, -1}, {-1, 1}};

        for (int i = 0; i < 4; i++) {
            double su = r * corners[i][0];
            double sv = r * corners[i][1];
            double ox = ux * su + vx * sv;
            double oy = uy * su + vy * sv;
            double oz = uz * su + vz * sv;
            px[i] = ax + ox;
            py[i] = ay + oy;
            pz[i] = az + oz;
            qx[i] = bx + ox;
            qy[i] = by + oy;
            qz[i] = bz + oz;
        }

        for (int i = 0; i < 4; i++) {
            int j = (i + 1) & 3;
            quad(vc, m,
                    px[i], py[i], pz[i],
                    px[j], py[j], pz[j],
                    qx[j], qy[j], qz[j],
                    qx[i], qy[i], qz[i],
                    cr, cg, cb, ca);
        }
    }

    private static double clampHalf(double value) {
        return Math.max(MIN_HALF, Math.min(MAX_HALF, value));
    }

    /** 画一个实心长方体（6 个面）。因为不剔除背面，不用担心朝向 */
    private static void solidBox(VertexConsumer vc, Matrix4f m,
                                 double x1, double y1, double z1,
                                 double x2, double y2, double z2,
                                 float r, float g, float b, float a) {
        // z 方向的两个面
        quad(vc, m, x1, y1, z1, x2, y1, z1, x2, y2, z1, x1, y2, z1, r, g, b, a);
        quad(vc, m, x1, y1, z2, x1, y2, z2, x2, y2, z2, x2, y1, z2, r, g, b, a);
        // y 方向的两个面
        quad(vc, m, x1, y1, z1, x1, y1, z2, x2, y1, z2, x2, y1, z1, r, g, b, a);
        quad(vc, m, x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2, r, g, b, a);
        // x 方向的两个面
        quad(vc, m, x1, y1, z1, x1, y2, z1, x1, y2, z2, x1, y1, z2, r, g, b, a);
        quad(vc, m, x2, y1, z1, x2, y1, z2, x2, y2, z2, x2, y2, z1, r, g, b, a);
    }

    /** 按顺序塞 4 个顶点。顶点格式是 POSITION_COLOR，所以只要坐标 + 颜色 */
    private static void quad(VertexConsumer vc, Matrix4f m,
                             double ax, double ay, double az,
                             double bx, double by, double bz,
                             double cx, double cy, double cz,
                             double dx, double dy, double dz,
                             float r, float g, float b, float a) {
        vc.vertex(m, (float) ax, (float) ay, (float) az).color(r, g, b, a).next();
        vc.vertex(m, (float) bx, (float) by, (float) bz).color(r, g, b, a).next();
        vc.vertex(m, (float) cx, (float) cy, (float) cz).color(r, g, b, a).next();
        vc.vertex(m, (float) dx, (float) dy, (float) dz).color(r, g, b, a).next();
    }
}
