package com.autofarm.client.compat;

import com.autofarm.client.gui.AutoFarmScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Mod Menu 的入口点：让「模组列表 → 配置」直接打开 AutoFarm 面板。
 *
 * <h2>为什么没装 Mod Menu 也不会出事</h2>
 * Fabric 的入口点是<b>按需加载</b>的 —— 只有某个模组去问「谁声明了 modmenu 入口」时，
 * 这个类才会被类加载器碰。没装 Mod Menu 就没人问，于是这个类（以及它引用的
 * {@link ModMenuApi}）永远不会被加载，也就不会出现「找不到类」的错误。
 *
 * <p>依赖同样也是 compileOnly 的：打包出去的 jar 里没有 Mod Menu，
 * 它只是编译期用来对签名的一把尺子。
 */
public class AutoFarmModMenu implements ModMenuApi {

    /** 点「配置」时创建控制面板；返回界面的 parent 会指向 Mod Menu，按 Esc 能退回它 */
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return AutoFarmScreen::new;
    }
}
