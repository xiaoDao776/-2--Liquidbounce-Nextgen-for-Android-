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
import kotlin.math.max

class ClickGuiScreen : Screen(Component.literal("ClickGUI")) {

    private var cat = 0
    private var expanded: ClientModule? = null
    private var search = ""
    private var searchFocus = false
    private var sOff = 5f; private var tOff = 5f
    private var sOff2 = 5f; private var tOff2 = 5f
    private var anim = 0f
    private var flash = 0f
    private var flashRow = -1

    private val cats = ModuleCategories.entries.toList()
    private val W = 430; private val H = 310
    private val panelW = 160

    private val accent = 0xFF4182E1.toInt()
    private val bg = 0xE80C0C10.toInt()
    private val panelBg = 0xE814141A.toInt()
    private val headerBg = 0xF0000000.toInt()
    private val textGray = 0xFFA0A0AA.toInt()

    override fun isPauseScreen() = false
    override fun shouldCloseOnEsc() = true

    private fun trimText(font: Font, text: String, maxW: Int): String {
        if (font.width(text) <= maxW) return text
        var str = text
        while (str.isNotEmpty() && font.width("$str...") > maxW) {
            str = str.substring(0, str.length - 1)
        }
        return "$str..."
    }

    /**
     * 判断 Value 是否支持滑条（单个数字或区间范围）
     */
    private fun isSliderValue(v: Value<*>): Boolean {
        val obj = v.get() ?: return false
        return obj is Number || obj is ClosedRange<*> || v is RangedValue<*>
    }

    /**
     * 切换枚举、模式（List/Choice/Enum）到下一个选项
     */
    private fun toggleNextValue(v: Value<*>) {
        val cls = v.javaClass
        
        // 1. 尝试直接调用 LiquidBounce Value 自带的 next() 方法
        try {
            val nextMethod = cls.methods.find { it.name == "next" && it.parameterCount == 0 }
            if (nextMethod != null) {
                nextMethod.invoke(v)
                return
            }
        } catch (_: Exception) {}

        // 2. 尝试切换 Enum 类型
        val curObj = v.get()
        if (curObj is Enum<*>) {
            val constants = curObj.declaringClass.enumConstants
            val nextIdx = (curObj.ordinal + 1) % constants.size
            @Suppress("UNCHECKED_CAST")
            (v as Value<Any>).set(constants[nextIdx])
            return
        }

        // 3. 尝试读取 values / choices 集合属性进行轮播
        try {
            val choicesField = cls.declaredFields.find { 
                it.name.equals("values", true) || it.name.equals("choices", true) || it.name.equals("range", true) 
            }
            if (choicesField != null) {
                choicesField.isAccessible = true
                val choices = choicesField.get(v)
                if (choices is Array<*>) {
                    val idx = choices.indexOf(curObj)
                    val nextIdx = if (idx >= 0) (idx + 1) % choices.size else 0
                    choices[nextIdx]?.let { 
                        @Suppress("UNCHECKED_CAST")
                        (v as Value<Any>).set(it)
                    }
                    return
                } else if (choices is List<*>) {
                    val idx = choices.indexOf(curObj)
                    val nextIdx = if (idx >= 0) (idx + 1) % choices.size else 0
                    choices[nextIdx]?.let { 
                        @Suppress("UNCHECKED_CAST")
                        (v as Value<Any>).set(it)
                    }
                    return
                }
            }
        } catch (_: Exception) {}
    }

    override fun render(ctx: GuiGraphics, mx: Int, my: Int, dt: Float) {
        anim += (1f - anim) * 0.25f
        val a = anim.coerceIn(0f, 1f)
        if (a < 0.01f) return

        if (flash > 0f) flash -= dt / 3f
        else flash = 0f

        val x = (minecraft!!.window.guiScaledWidth - W) / 2
        val y = (minecraft!!.window.guiScaledHeight - H) / 2
        val f = minecraft!!.font
        val tabW = (W - 24) / cats.size

        // 背景与标题
        ctx.fill(x, y, x + W, y + H, bg)
        ctx.fill(x, y, x + W, y + 24, headerBg)
        ctx.drawString(f, "§lClickGUI", x + 10, y + 5, accent)

        // 搜索框
        val searchY = y + 28
        ctx.fill(x + 8, searchY, x + W - 8, searchY + 15, 0x28000000.toInt())
        val disp = if (search.isEmpty()) "§7Search modules..." else "§f$search"
        ctx.drawString(f, trimText(f, disp, W - 30), x + 12, searchY + 2, -1)
        if (searchFocus) {
            val cx = x + 12 + f.width(search)
            if (cx < x + W - 12) {
                ctx.fill(cx, searchY + 2, cx + 1, searchY + 13, 0xFFFFFFFF.toInt())
            }
        }

        // 分类 Tabs
        val tabY = searchY + 20
        ctx.fill(x + 4, tabY, x + W - 4, tabY + 20, 0x18000000.toInt())
        for (i in cats.indices) {
            val tx = x + 8 + i * tabW
            val sel = i == cat
            if (sel) {
                ctx.fill(tx, tabY, tx + tabW - 2, tabY + 20, accent)
                ctx.fill(tx, tabY + 18, tx + tabW - 2, tabY + 20, 0xFF2A5DB0.toInt())
            } else if (mx in tx..(tx + tabW - 2) && my in tabY..(tabY + 20)) {
                ctx.fill(tx, tabY, tx + tabW - 2, tabY + 20, 0x20FFFFFF.toInt())
            }
            val tagStr = trimText(f, cats[i].tag, tabW - 4)
            val cw = f.width(tagStr)
            ctx.drawString(f, tagStr, tx + ((tabW - 2) - cw) / 2, tabY + 4, if (sel) -1 else textGray)
        }

        val divY = tabY + 22
        ctx.fill(x + 8, divY, x + W - 8, divY + 1, 0x20FFFFFF.toInt())

        // 模块列表绘制
        val mods = getMods()
        val listRight = x + W - panelW - 8
        val listY = divY + 6
        val listH = H - (listY - y) - 8
        val rowH = 18

        tOff = max(5f, tOff.coerceAtMost(max(0f, mods.size * rowH - listH + 5f)))
        sOff += (tOff - sOff) * 0.3f * a

        for (i in mods.indices) {
            val mod = mods[i]
            val my2 = listY + i * rowH - sOff
            if (my2 < listY - rowH || my2 > listY + listH) continue
            val mi = my2.toInt()
            val hov = mx in (x + 8)..listRight && my in mi..(mi + rowH)

            if (hov) ctx.fill(x + 8, mi, listRight, mi + rowH, 0x14FFFFFF.toInt())
            if (flash > 0f && flashRow == i) {
                val fa = (flash * 80).toInt()
                ctx.fill(x + 8, mi, listRight, mi + rowH, (fa shl 24) or 0x00FFFFFF)
            }

            val isExpandedMod = expanded == mod
            val nameText = trimText(f, (if (isExpandedMod) "§n" else "") + mod.name, listRight - x - 45)
            ctx.drawString(f, nameText, x + 14, mi + 3, if (mod.enabled) accent else textGray)

            // Switch 开关
            val switchW = 24
            val switchH = 12
            val btnX = listRight - switchW - 4
            val btnY = mi + (rowH - switchH) / 2

            if (mod.enabled) {
                ctx.fill(btnX, btnY, btnX + switchW, btnY + switchH, accent)
                ctx.fill(btnX + switchW - 10, btnY + 2, btnX + switchW - 2, btnY + switchH - 2, 0xFFFFFFFF.toInt())
            } else {
                ctx.fill(btnX, btnY, btnX + switchW, btnY + switchH, 0x30FFFFFF.toInt())
                ctx.fill(btnX + 2, btnY + 2, btnX + 10, btnY + switchH - 2, 0xAA808080.toInt())
            }
        }

        // 右侧设置子菜单绘制
        val exp = expanded
        if (exp != null) {
            val px = x + W - panelW - 2
            val py = listY
            val maxTextW = panelW - 16

            ctx.fill(px, py, x + W - 2, y + H - 2, panelBg)
            
            // 已完全按要求删除右侧顶部的蓝字模块名标题！

            val setList = exp.collectValuesRecursively()
            val setY = py + 4 // 由于删除了顶部模块名标题，起始偏移提升
            val setH = H - (setY - y) - 6

            var totalContentH = 0f
            for (v in setList) {
                totalContentH += if (isSliderValue(v)) 18f else 14f
            }

            tOff2 = max(5f, tOff2.coerceAtMost(max(0f, totalContentH - setH + 5f)))
            sOff2 += (tOff2 - sOff2) * 0.3f

            var curY = setY - sOff2
            for (v in setList) {
                val isSlider = isSliderValue(v)
                val itemH = if (isSlider) 18f else 14f

                if (curY >= setY - itemH && curY <= setY + setH) {
                    val mi2 = curY.toInt()
                    try {
                        val valObj = v.get()
                        when {
                            // 1. 开关 (Boolean)
                            valObj is Boolean -> {
                                val text = trimText(f, "${v.name}: ${if (valObj) "§aON" else "§cOFF"}", maxTextW)
                                ctx.drawString(f, text, px + 8, mi2 + 2, -1)
                            }

                            // 2. 区间数值滑条 / 单数值滑条 (CPS / Range / Number)
                            isSlider -> {
                                var fv = 0f
                                var mn = 0f
                                var mxr = 20f

                                if (valObj is ClosedRange<*>) {
                                    val start = (valObj.start as? Number)?.toFloat() ?: 0f
                                    val end = (valObj.endInclusive as? Number)?.toFloat() ?: 20f
                                    fv = end
                                    mn = 1f
                                    mxr = 30f
                                } else if (valObj is Number) {
                                    fv = valObj.toFloat()
                                    if (v is RangedValue<*>) {
                                        mn = (v.range.start as? Number)?.toFloat() ?: 0f
                                        mxr = (v.range.endInclusive as? Number)?.toFloat() ?: 100f
                                    }
                                }

                                val bw = panelW - 16; val bh = 4
                                val bx = px + 8; val by = mi2 + 11
                                ctx.fill(bx, by, bx + bw, by + bh, 0x30000000.toInt())
                                val r = ((fv - mn) / max(0.001f, mxr - mn)).coerceIn(0f, 1f)
                                ctx.fill(bx, by, (bx + bw * r).toInt(), by + bh, accent)

                                val dispVal = if (valObj is ClosedRange<*>) "${valObj.start} - ${valObj.endInclusive}" else "%.1f".format(fv)
                                val text = trimText(f, "${v.name}: $dispVal", maxTextW)
                                ctx.drawString(f, text, px + 8, mi2, -1)
                            }

                            // 3. 模式 / 枚举 / 点击切换类 (Mode / Choice / List)
                            else -> {
                                val dispStr = valObj?.toString()?.replace("InputBind", "")?.take(20) ?: "None"
                                val text = trimText(f, "${v.name}: §b$dispStr", maxTextW)
                                ctx.drawString(f, text, px + 8, mi2 + 2, -1)
                            }
                        }
                    } catch (_: Exception) {}
                }
                curY += itemH
            }
        }
    }

    override fun renderBackground(ctx: GuiGraphics, mx: Int, my: Int, dt: Float) {}

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val btn = click.button()
        val mx = click.x.toInt(); val my = click.y.toInt()
        val x = (minecraft!!.window.guiScaledWidth - W) / 2
        val y = (minecraft!!.window.guiScaledHeight - H) / 2
        val tabW = (W - 24) / cats.size

        if (mx in (x + 8)..(x + W - 8) && my in (y + 28)..(y + 43)) {
            searchFocus = true
            return true
        }
        searchFocus = false

        val tabY = y + 48
        if (btn == 0 && my in tabY..(tabY + 20)) {
            for (i in cats.indices) {
                val tx = x + 8 + i * tabW
                if (mx in tx..(tx + tabW)) {
                    cat = i
                    sOff = 5f; tOff = 5f
                    expanded = null
                    return true
                }
            }
        }

        val divY = tabY + 22
        val listY = divY + 6
        val listH = H - (listY - y) - 8
        val listRight = x + W - panelW - 8
        val rowH = 18

        if (mx in (x + 8)..listRight && my in listY..(listY + listH)) {
            val mods = getMods()
            val clickIdx = ((my - listY + sOff) / rowH).toInt()

            if (clickIdx in mods.indices) {
                val mod = mods[clickIdx]
                if (btn == 0) {
                    mod.enabled = !mod.enabled
                    flash = 1f; flashRow = clickIdx
                } else if (btn == 1) {
                    expanded = if (expanded == mod) null else mod
                    flash = 1f; flashRow = clickIdx
                }
                return true
            }
        }

        // 右侧设置项交互处理
        val exp = expanded
        if (exp != null && btn == 0) {
            val px = x + W - panelW - 2
            val py = listY
            val setY = py + 4
            val setH = H - (setY - y) - 6

            if (mx in px..(px + panelW) && my in setY..(setY + setH)) {
                val setList = exp.collectValuesRecursively()
                var curY = setY - sOff2

                for (v in setList) {
                    val isSlider = isSliderValue(v)
                    val itemH = if (isSlider) 18f else 14f

                    if (my >= curY && my < curY + itemH) {
                        try {
                            val valObj = v.get()
                            when {
                                // 1. 开关 (Boolean) 切换
                                valObj is Boolean -> {
                                    @Suppress("UNCHECKED_CAST")
                                    (v as Value<Boolean>).set(!valObj)
                                }

                                // 2. 数值 / 区间滑条调整
                                isSlider -> {
                                    var mn = 0f; var mxr = 20f
                                    if (valObj is ClosedRange<*>) {
                                        mn = 1f; mxr = 30f
                                    } else if (v is RangedValue<*>) {
                                        mn = (v.range.start as? Number)?.toFloat() ?: 0f
                                        mxr = (v.range.endInclusive as? Number)?.toFloat() ?: 100f
                                    }

                                    val bw = panelW - 16; val bx = px + 8
                                    val nr = ((mx - bx).toFloat() / bw).coerceIn(0f, 1f)
                                    val nv = mn + nr * (mxr - mn)

                                    if (valObj is IntRange) {
                                        val center = nv.toInt()
                                        @Suppress("UNCHECKED_CAST")
                                        (v as Value<IntRange>).set((center - 1).coerceAtLeast(1)..center)
                                    } else if (valObj is Float) {
                                        @Suppress("UNCHECKED_CAST")
                                        (v as Value<Float>).set(nv)
                                    } else if (valObj is Int) {
                                        @Suppress("UNCHECKED_CAST")
                                        (v as Value<Int>).set(nv.toInt())
                                    }
                                }

                                // 3. 点击切换模式（单点/蝴蝶点/更多模式）
                                else -> {
                                    toggleNextValue(v)
                                }
                            }
                        } catch (_: Exception) {}
                        return true
                    }
                    curY += itemH
                }
            }
        }

        return super.mouseClicked(click, doubled)
    }

    override fun mouseScrolled(mx: Double, my: Double, h: Double, v: Double): Boolean {
        val x = (minecraft!!.window.guiScaledWidth - W) / 2
        val panelX = x + W - panelW - 2
        if (expanded != null && mx >= panelX) {
            tOff2 = (tOff2 - v.toFloat() * 18f).coerceAtLeast(5f)
        } else {
            tOff = (tOff - v.toFloat() * 18f).coerceAtLeast(5f)
        }
        return true
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (input.key == GLFW.GLFW_KEY_ESCAPE) {
            onClose()
            return true
        }
        if (searchFocus) {
            when (input.key) {
                GLFW.GLFW_KEY_BACKSPACE -> {
                    if (search.isNotEmpty()) search = search.dropLast(1)
                    return true
                }
                GLFW.GLFW_KEY_SPACE -> {
                    search += " "
                    return true
                }
                else -> {
                    val n = GLFW.glfwGetKeyName(input.key, 0)
                    if (n != null && n.length == 1) {
                        search += n
                        return true
                    }
                }
            }
        }
        return super.keyPressed(input)
    }

    override fun charTyped(characterEvent: CharacterEvent): Boolean {
        if (searchFocus) {
            try {
                val obj: Any = characterEvent
                val cls = obj.javaClass
                var cp: Int? = null

                for (m in cls.methods) {
                    if (m.parameterCount == 0 && (m.name.equals("codepoint", true) || m.name.equals("codePoint", true) || m.name.equals("character", true))) {
                        val res = m.invoke(obj)
                        if (res is Int) cp = res
                        else if (res is Char) cp = res.code
                        if (cp != null) break
                    }
                }

                if (cp == null) {
                    for (f in cls.declaredFields) {
                        if (f.type == Int::class.javaPrimitiveType || f.type == Char::class.javaPrimitiveType) {
                            f.isAccessible = true
                            val v = f.get(obj)
                            if (v is Int) cp = v
                            else if (v is Char) cp = v.code
                            if (cp != null) break
                        }
                    }
                }

                if (cp != null && cp > 31) {
                    search += cp.toChar().toString()
                    return true
                }
            } catch (_: Exception) {}
        }
        return super.charTyped(characterEvent)
    }

    override fun onClose() {
        minecraft?.setScreen(null)
        anim = 0f
    }

    private fun getMods(): List<ClientModule> {
        val catObj = cats.getOrElse(cat) { ModuleCategories.COMBAT }
        return ModuleManager.getModules()
            .filter { it.category == catObj && it.name != "ClickGUI" }
            .filter { search.isEmpty() || it.name.contains(search, ignoreCase = true) }
    }
}
