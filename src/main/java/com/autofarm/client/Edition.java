package com.autofarm.client;

/**
 * 构建变体开关 —— 一份源码，两种产物。
 *
 * <h2>为什么要这个</h2>
 * 项目要同时往两个地方发：
 * <ul>
 *   <li><b>完整版</b>（GitHub Releases / Gitee / 国内社区）—— 全功能。</li>
 *   <li><b>合规版</b>（Modrinth / CurseForge）—— 去掉模组平台明令禁止的几项功能。</li>
 * </ul>
 * Modrinth 的《Content Rules》第 3 条把「穿透不透明方块（X-ray）」和
 * 「自动 / 辅助战斗」列为硬红线，且<b>不因为「默认关闭」而免责</b> ——
 * 判定标准是「jar 里<b>有没有</b>这个功能」，不是「默认开没开」。
 * 所以合规版必须把这些代码<b>从产物里真正拿掉</b>，不能只靠开关关掉。
 *
 * <h2>怎么做到的</h2>
 * 这个字段是 <b>编译期常量</b>。合规版由构建脚本换成
 * {@code COMPLIANT = true} 的另一份文件（见 {@code src/compliant/java}），
 * 于是 {@code if (Edition.COMPLIANT) {...}} 里的分支会被 javac
 * 当作「不可达代码」直接从字节码里删掉 —— 开关关掉 UI 入口的同时，
 * 那一段代码也不再存在于产物中。
 *
 * <p>功能本体（穿透渲染、战斗）则由构建脚本<b>整个源文件排除</b>并换成空实现，
 * 这一层比常量折叠更彻底。
 *
 * <h2>注意</h2>
 * 完整版的这份文件里 {@code COMPLIANT = false}；
 * 切勿把它改成 true —— 合规版走的是另一个目录里的同名文件，
 * 两份文件互不干扰，改这里只会让完整版也变成删减版。
 */
public final class Edition {
    private Edition() {
    }

    /** 是否为合规版（Modrinth / CurseForge 上架用）。完整版恒为 false。 */
    public static final boolean COMPLIANT = false;
}
