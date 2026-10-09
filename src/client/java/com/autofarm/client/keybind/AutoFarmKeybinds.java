package com.autofarm.client.keybind;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * 键位绑定。默认 G 打开控制面板、` 一键开关，玩家可以在「选项 → 按键控制」里改。
 */
public final class AutoFarmKeybinds {

    public static final String CATEGORY = "category.autofarm";

    /** 打开控制面板 */
    public static KeyBinding openPanel;

    /**
     * 一键开/关自动收割。
     *
     * <p>默认反引号键（`` ` ``，键盘左上角 Esc 下面那个），设置界面里显示为 {@code `}。
     *
     * <p>它之所以必要，是因为「开关」还兼任「重新认一遍」：关掉再打开会清空
     * 「已经处理过」的记忆，于是那些<b>还没被挖掉的成熟作物会被重新识别</b>。
     * 光靠面板里的按钮要做同样的事得开界面、点两下、再保存，太笨重。
     */
    public static KeyBinding toggleEnabled;

    private AutoFarmKeybinds() {
    }

    public static void register() {
        openPanel = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autofarm.open_panel",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_G,
                CATEGORY
        ));

        toggleEnabled = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autofarm.toggle_enabled",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_GRAVE_ACCENT,
                CATEGORY
        ));
    }
}
