package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import java.awt.Color
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

// LiquidBounce GUI 全局存储
object LBGuiStorage {
    var theme = 0 // 0 Dark,1 Light,2 Dark Rainbow,3 Light Rainbow
    var hueAnim = 0f
    var toastText = ""
    var toastTick = 0
    val windowPos = mutableMapOf(
        "combat" to Pair(30, 30),
        "move" to Pair(160, 30),
        "world" to Pair(290, 30),
        "render" to Pair(420, 30),
        "player" to Pair(30, 240),
        "utility" to Pair(160, 240),
        "net" to Pair(290, 240),
        "gui" to Pair(420, 240)
    )
    val windowCollapse = mutableMapOf<String, Boolean>()
    var dragWin: String? = null
    var dragOffX = 0
    var dragOffY = 0
}

// 纯英文文本
object LBText {
    fun get(key: String): String = when(key) {
        "Cannot disable this feature" -> "Cannot disable this feature"
        else -> key
    }
}

// 主题颜色管理
object LBColor {
    fun getAccent(): Int {
        LBGuiStorage.hueAnim += 0.8f
        return when (LBGuiStorage.theme) {
            0, 2 -> 0xFF00FF9D.toInt()
            1 -> 0xFF00CC88.toInt()
            else -> 0xFFFF6600.toInt()
        }
    }
    fun bgMain(): Int = if (LBGuiStorage.theme == 1 || LBGuiStorage.theme == 3) 0xFFF0F0F0.toInt() else 0xFF0C0C10.toInt()
    fun bgPanel(): Int = if (LBGuiStorage.theme == 1 || LBGuiStorage.theme == 3) 0xFFF5F5F5.toInt() else 0xFF14141A.toInt()
    fun textMain(): Int = if (LBGuiStorage.theme == 1 || LBGuiStorage.theme == 3) 0xFF1A1A1A.toInt() else 0xFFE0E0E0.toInt()
    fun textSub(): Int = if (LBGuiStorage.theme == 1 || LBGuiStorage.theme == 3) 0xFF666666.toInt() else 0xFFA0A0AA.toInt()
}

class ClickGuiScreen : Screen(Component.literal("LiquidBounce GUI")) {
    private var expanded: ClientModule? = null
    private var search = ""
    private var searchFocus = false
    private var listeningValue: Value<*>? = null
    private val collapsedGroups = mutableSetOf<Value<*>>()
    private var tOff2 = 0f
    private var sOff2 = 0f
    private var anim = 0f
    private val cats = ModuleCategories.entries.toList()

    override fun isPauseScreen() = false
    override fun shouldCloseOnEsc() = true

    // 圆角绘制
    private fun fillRoundedRect(ctx: GuiGraphics, x1: Float, y1: Float, x2: Float, y2: Float, radius: Float, color: Int) {
        val r = radius.coerceAtMost((x2 - x1) / 2f).coerceAtMost((y2 - y1) / 2f)
        ctx.fill((x1 + r).toInt(), y1.toInt(), (x2 - r).toInt(), y2.toInt(), color)
        ctx.fill(x1.toInt(), (y1 + r).toInt(), (x1 + r).toInt(), (y2 - r).toInt(), color)
        ctx.fill((x2 - r).toInt(), (y1 + r).toInt(), x2.toInt(), (y2 - r).toInt(), color)
        val corners = arrayOf(
            floatArrayOf(x1 + r, y1 + r, 180f, 270f),
            floatArrayOf(x2 - r, y1 + r, 270f, 360f),
            floatArrayOf(x2 - r, y2 - r, 0f, 90f),
            floatArrayOf(x1 + r, y2 - r, 90f, 180f)
        )
        for (c in corners) {
            val cx = c[0]; val cy = c[1]
            val startAng = c[2]; val endAng = c[3]
            var a = startAng
            while (a < endAng) {
                val rad1 = Math.toRadians(a.toDouble())
                val rad2 = Math.toRadians((a + 10).coerceAtMost(endAng).toDouble())
                val px1 = cx + (cos(rad1) * r).toFloat()
                val py1 = cy + (sin(rad1) * r).toFloat()
                val px2 = cx + (cos(rad2) * r).toFloat()
                val py2 = cy + (sin(rad2) * r).toFloat()
                val minX = cx.coerceAtMost(px1).coerceAtMost(px2).toInt()
                val maxX = cx.coerceAtLeast(px1).coerceAtLeast(px2).toInt()
                val minY = cy.coerceAtMost(py1).coerceAtMost(py2).toInt()
                val maxY = cy.coerceAtLeast(py1).coerceAtLeast(py2).toInt()
                ctx.fill(minX, minY, max(minX + 1, maxX), max(minY + 1, maxY), color)
                a += 10f
            }
        }
    }

    private fun trimText(font: Font, text: String, maxW: Int): String {
        if (font.width(text) <= maxW) return text
        var str = text
        while (str.isNotEmpty() && font.width("$str...") > maxW) str = str.substring(0, str.length - 1)
        return "$str..."
    }

    // 读取模块配置
    private fun isGroupValue(v: Value<*>): Boolean {
        val clsName = v.javaClass.simpleName
        return clsName.contains("Group", true) || clsName.contains("Container", true) || getGroupChildren(v).isNotEmpty()
    }
    private fun getGroupChildren(v: Value<*>): List<Value<*>> {
        val list = mutableListOf<Value<*>>()
        try {
            for (m in v.javaClass.methods) {
                if ((m.name.equals("getValues", true) || m.name.equals("getSubValues", true)) && m.parameterCount == 0) {
                    val res = m.invoke(v)
                    if (res is Collection<*>) list.addAll(res.filterIsInstance<Value<*>>())
                    if (res != null && res.javaClass.isArray) list.addAll((res as Array<*>).filterIsInstance<Value<*>>())
                }
            }
            for (f in v.javaClass.declaredFields) {
                f.isAccessible = true
                val fVal = f.get(v)
                if (fVal is Value<*>) list.add(fVal)
                else if (fVal is Collection<*>) list.addAll(fVal.filterIsInstance<Value<*>>())
                else if (fVal != null && fVal.javaClass.isArray) list.addAll((fVal as Array<*>).filterIsInstance<Value<*>>())
            }
            val obj = v.get()
            if (obj != null && obj !is Number && obj !is String && obj !is Boolean && obj !is Enum<*>) {
                if (obj is Collection<*>) list.addAll(obj.filterIsInstance<Value<*>>())
                else if (obj.javaClass.isArray) list.addAll((obj as Array<*>).filterIsInstance<Value<*>>())
                else for (f in obj.javaClass.declaredFields) {
                    f.isAccessible = true
                    val fVal = f.get(obj)
                    if (fVal is Value<*>) list.add(fVal)
                }
            }
        } catch (_: Exception) {}
        return list.distinct()
    }
    private fun getVisibleValues(module: ClientModule): List<Pair<Value<*>, Int>> {
        val result = mutableListOf<Pair<Value<*>, Int>>()
        val topValues = module.collectValuesRecursively().toList()
        fun process(v: Value<*>, depth: Int) {
            result.add(Pair(v, depth))
            if (isGroupValue(v) && !collapsedGroups.contains(v)) getGroupChildren(v).forEach { process(it, depth + 1) }
        }
        for (v in topValues) {
            var skip = false
            for (other in topValues) if (other != v && isGroupValue(other) && getGroupChildren(other).contains(v)) skip = true
            if (!skip) process(v, 0)
        }
        return result
    }
    private fun getActualValue(v: Value<*>): Any? {
        var obj = try { v.get() } catch (_: Exception) { null } ?: return null
        var d = 0
        while (obj is Value<*> && d < 5) { obj = try { obj.get() } catch (_: Exception) { null }; d++ }
        return obj
    }
    private fun isBindValue(v: Value<*>): Boolean {
        val name = v.name.lowercase()
        if (name.contains("key") || name.contains("bind")) return true
        val actual = getActualValue(v) ?: return false
        return actual.javaClass.simpleName.lowercase().contains("key")
    }
    private fun formatDisplayValue(v: Value<*>): String {
        if (v == listeningValue) return "[LISTENING...]"
        val actual = getActualValue(v) ?: return "NONE"
        try {
            val f = actual.javaClass.declaredFields.find { it.name.equals("boundKey", true) || it.name.equals("key", true) }
            if (f != null) {
                f.isAccessible = true
                val k = f.get(actual)
                return k.toString().replace("key.keyboard.", "").replace("key.", "").uppercase()
            }
        } catch (_: Exception) {}
        var str = actual.toString()
        str = str.replace("key.keyboard.", "").replace("key.", "").replace("InputBind", "")
        if (str.startsWith("(") && str.endsWith(")")) str = str.substring(1, str.length - 1)
        return if (str.isBlank()) "NONE" else str.take(18).uppercase()
    }
    private fun toggleNextValue(v: Value<*>) {
        try {
            val m = v.javaClass.methods.find { (it.name == "next" || it.name == "toggle") && it.parameterCount == 0 }
            if (m != null) { m.invoke(v); return }
        } catch (_: Exception) {}
        val actual = getActualValue(v)
        if (actual is Enum<*>) {
            val arr = actual.javaClass.enumConstants ?: return
            val next = arr[(actual.ordinal + 1) % arr.size]
            @Suppress("UNCHECKED_CAST") (v as Value<Any>).set(next)
            return
        }
        try {
            val f = v.javaClass.declaredFields.find { listOf("values", "choices", "range").any { it.equals(it.name, true) } }
            if (f != null) {
                f.isAccessible = true
                val choices = f.get(v)
                val list = when (choices) {
                    is Array<*> -> choices.toList()
                    is List<*> -> choices
                    else -> emptyList()
                }
                val idx = list.indexOf(v.get())
                val next = list[if (idx >= 0) (idx + 1) % list.size else 0]
                @Suppress("UNCHECKED_CAST") (v as Value<Any>).set(next)
                return
            }
        } catch (_: Exception) {}
        if (actual is Boolean) @Suppress("UNCHECKED_CAST") (v as Value<Boolean>).set(!actual)
    }

    // 弹窗提示
    private fun drawToast(ctx: GuiGraphics) {
        if (LBGuiStorage.toastTick <= 0) return
        LBGuiStorage.toastTick--
        val font = minecraft.font
        val w = font.width(LBGuiStorage.toastText) + 36
        val x = minecraft.window.guiScaledWidth - w - 20
        val y = minecraft.window.guiScaledHeight - 40
        fillRoundedRect(ctx, x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + 22).toFloat(), 6f, LBColor.bgPanel())
        ctx.drawString(font, LBGuiStorage.toastText, x + 12, y + 6, LBColor.getAccent())
    }
    fun showToast(msg: String) {
        LBGuiStorage.toastText = msg
        LBGuiStorage.toastTick = 180
    }

    // 仅悬浮窗口渲染
    override fun render(ctx: GuiGraphics, mx: Int, my: Int, dt: Float) {
        anim += (1f - anim) * 0.25f
        val a = anim.coerceIn(0f, 1f)
        if (a < 0.01f) return
        ctx.fill(0, 0, minecraft.window.guiScaledWidth, minecraft.window.guiScaledHeight, LBColor.bgMain())
        LBGuiStorage.hueAnim += dt
        val font = minecraft.font
        val winMeta = listOf(
            Triple("combat", "Combat", ModuleCategories.COMBAT),
            Triple("move", "Movement", ModuleCategories.MOVEMENT),
            Triple("world", "World", ModuleCategories.WORLD),
            Triple("render", "Render", ModuleCategories.RENDER),
            Triple("player", "Player", ModuleCategories.PLAYER),
            Triple("utility", "Utility", ModuleCategories.UTILITY),
            Triple("net", "Network", ModuleCategories.NETWORK),
            Triple("gui", "GUI", ModuleCategories.RENDER)
        )
        val winWidth = 230
        val headHeight = 18
        val winRadius = 6f
        winMeta.forEach { (winId, catName, catEnum) ->
            val (wx, wy) = LBGuiStorage.windowPos[winId]!!
            val collapsed = LBGuiStorage.windowCollapse[winId] == true
            fillRoundedRect(ctx, wx.toFloat(), wy.toFloat(), (wx + winWidth).toFloat(), (wy + headHeight).toFloat(), winRadius, LBColor.bgPanel())
            ctx.drawString(font, catName, wx + 8, wy + 4, LBColor.getAccent())
            val arrow = if (collapsed) "▶" else "▼"
            ctx.drawString(font, arrow, wx + winWidth - 12, wy + 4, LBColor.textSub())
            if (!collapsed) {
                var renderY = wy + headHeight + 4
                val modules = ModuleManager.getModules()
                    .filter { it.category == catEnum && it.name != "ClickGUI" }
                    .filter { search.isEmpty() || it.name.contains(search, ignoreCase = true) }
                modules.forEach { mod ->
                    ctx.drawString(font, mod.name, wx + 6, renderY, if (mod.enabled) LBColor.getAccent() else LBColor.textMain())
                    val lineH = 14
                    renderY += lineH
                    if (expanded == mod) {
                        val values = getVisibleValues(mod)
                        values.forEach { (v, depth) ->
                            val indent = depth * 6
                            val text = trimText(font, "- ${v.name}", winWidth - 20)
                            ctx.drawString(font, text, wx + 10 + indent, renderY, LBColor.textSub())
                            renderY += 12
                        }
                    }
                }
            }
        }
        drawToast(ctx)
    }

    // 鼠标点击（标准父类签名，无类型冲突）
    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val mx = click.x.toInt()
        val my = click.y.toInt()
        val btn = click.button()
        val winWidth = 230
        val headH = 18
        for ((winId, pos) in LBGuiStorage.windowPos) {
            val (wx, wy) = pos
            if (mx in wx..wx + winWidth && my in wy..wy + headH) {
                if (btn == 0) {
                    LBGuiStorage.dragWin = winId
                    LBGuiStorage.dragOffX = mx - wx
                    LBGuiStorage.dragOffY = my - wy
                } else if (btn == 1) {
                    LBGuiStorage.windowCollapse[winId] = !LBGuiStorage.windowCollapse.getOrDefault(winId, false)
                }
                return true
            }
        }
        val winMeta = listOf(
            Triple("combat", "Combat", ModuleCategories.COMBAT),
            Triple("move", "Movement", ModuleCategories.MOVEMENT),
            Triple("world", "World", ModuleCategories.WORLD),
            Triple("render", "Render", ModuleCategories.RENDER),
            Triple("player", "Player", ModuleCategories.PLAYER),
            Triple("utility", "Utility", ModuleCategories.UTILITY),
            Triple("net", "Network", ModuleCategories.NETWORK),
            Triple("gui", "GUI", ModuleCategories.RENDER)
        )
        winMeta.forEach { (winId, catName, catEnum) ->
            val (wx, wy) = LBGuiStorage.windowPos[winId]!!
            if (LBGuiStorage.windowCollapse[winId] == true) return@forEach
            var yOff = wy + headH + 4
            val mods = ModuleManager.getModules()
                .filter { it.category == catEnum && it.name != "ClickGUI" }
                .filter { search.isEmpty() || it.name.contains(search, ignoreCase = true) }
            val lineH = 14
            mods.forEach { mod ->
                if (mx in wx..wx + winWidth && my in yOff..yOff + lineH) {
                    if (btn == 0) {
                        if (mod.name == "ClickGUI") {
                            showToast(LBText.get("Cannot disable this feature"))
                        } else {
                            mod.enabled = !mod.enabled
                            if (mod.enabled) showToast("Enabled: ${mod.name}") else showToast("Disabled: ${mod.name}")
                        }
                        return true
                    } else if (btn == 1) {
                        expanded = if (expanded == mod) null else mod
                        sOff2 = 0f
                        return true
                    }
                }
                if (expanded == mod) {
                    val vals = getVisibleValues(mod)
                    vals.forEach { (v, depth) ->
                        val paramH = 12
                        val paramTop = yOff + lineH
                        if (mx in wx..wx + winWidth && my in paramTop..paramTop + paramH) {
                            if (btn == 0) toggleNextValue(v)
                            return true
                        }
                        yOff += paramH
                    }
                }
                yOff += lineH
            }
        }
        val searchW = 220
        val searchX = 20
        val searchY = minecraft.window.guiScaledHeight - 40
        if (mx in searchX..searchX + searchW && my in searchY..searchY + 16) {
            searchFocus = true
            return true
        }
        searchFocus = false
        return super.mouseClicked(click, doubled)
    }

    // 拖拽函数【修复标准签名，无类型报错】
    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int) {
        super.mouseDragged(mouseX, mouseY, button)
        val dragId = LBGuiStorage.dragWin ?: return
        val nx = (mouseX - LBGuiStorage.dragOffX).toInt().coerceAtLeast(0)
        val ny = (mouseY - LBGuiStorage.dragOffY).toInt().coerceAtLeast(0)
        LBGuiStorage.windowPos[dragId] = Pair(nx, ny)
    }

    // 鼠标释放【修复标准签名】
    override fun mouseReleased(button: Int) {
        super.mouseReleased(button)
        LBGuiStorage.dragWin = null
    }

    override fun mouseScrolled(mx: Double, my: Double, h: Double, v: Double): Boolean {
        tOff2 = (tOff2 - v * 18f).coerceAtLeast(0f)
        sOff2 += (tOff2 - sOff2) * 0.3f
        return true
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        val lv = listeningValue
        if (lv != null) {
            val key = input.key
            val name = if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW_KEY_DELETE) "NONE"
            else GLFW.glfwGetKeyName(key, 0)?.uppercase() ?: "KEY_$key"
            try {
                val actual = getActualValue(lv)
                val f = actual?.javaClass?.declaredFields?.find { it.name.contains("key", true) }
                if (f != null) {
                    f.isAccessible = true
                    f.set(actual, key)
                } else if (actual is Int) @Suppress("UNCHECKED_CAST") (lv as Value<Int>).set(key)
                else if (actual is String) @Suppress("UNCHECKED_CAST") (lv as Value<String>).set(name)
            } catch (_: Exception) {}
            listeningValue = null
            return true
        }
        if (input.key == GLFW.GLFW_KEY_ESCAPE) {
            onClose()
            return true
        }
        if (searchFocus) {
            when (input.key) {
                GLFW.GLFW_KEY_BACKSPACE -> if (search.isNotEmpty()) search = search.dropLast()
                GLFW.GLFW_KEY_SPACE -> search += " "
                else -> {
                    val n = GLFW.glfwGetKeyName(input.key, 0)
                    if (n != null && n.length == 1) search += n
                }
            }
            return true
        }
        return super.keyPressed(input)
    }

    override fun charTyped(characterEvent: CharacterEvent): Boolean {
        if (!searchFocus) return super.charTyped(characterEvent)
        try {
            var cp: Int? = null
            val cls = characterEvent.javaClass
            for (m in cls.methods) {
                if (m.name.contains("code", true) && m.parameterCount == 0) {
                    val r = m.invoke(characterEvent)
                    cp = if (r is Int) r else if (r is Char) r.code else null
                    if (cp != null) break
                }
            }
            if (cp != null && cp > 31) search += cp.toChar()
        } catch (_: Exception) {}
        return true
    }

    override fun onClose() {
        minecraft?.setScreen(null)
        anim = 0f
        expanded = null
        listeningValue = null
    }

    override fun renderBackground(ctx: GuiGraphics) {}
}
