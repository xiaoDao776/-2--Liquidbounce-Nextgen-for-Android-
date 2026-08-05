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
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

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

    /**
     * 绘制圆角矩形核心方法
     */
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
        while (str.isNotEmpty() && font.width("$str...") > maxW) {
            str = str.substring(0, str.length - 1)
        }
        return "$str..."
    }

    /**
     * 解析按键绑定/选框展示文本，剔除 key.keyboard. 格式杂质
     */
    private fun formatDisplayValue(valObj: Any?): String {
        if (valObj == null) return "None"
        val rawStr = valObj.toString()

        // 优先利用反射提取 Bind/Keybind 内的实际按键名
        try {
            val cls = valObj.javaClass
            val keyField = cls.declaredFields.find { 
                it.name.equals("boundKey", true) || it.name.equals("key", true) || it.name.equals("name", true) 
            }
            if (keyField != null) {
                keyField.isAccessible = true
                val innerKey = keyField.get(valObj)
                if (innerKey != null) {
                    val keyStr = innerKey.toString()
                        .replace("key.keyboard.", "", ignoreCase = true)
                        .replace("key.", "", ignoreCase = true)
                        .uppercase()
                    if (keyStr.isNotEmpty()) return keyStr
                }
            }
        } catch (_: Exception) {}

        // 通用格式化清洗
        var cleaned = rawStr
            .replace("key.keyboard.", "", ignoreCase = true)
            .replace("key.", "", ignoreCase = true)
            .replace("InputBind", "", ignoreCase = true)

        if (cleaned.startsWith("(") && cleaned.endsWith(")")) {
            cleaned = cleaned.substring(1, cleaned.length - 1)
        }
        
        return if (cleaned.isBlank()) "NONE" else cleaned.take(20).uppercase()
    }

    private fun isSliderValue(v: Value<*>): Boolean {
        val obj = v.get() ?: return false
        return obj is Number || obj is ClosedRange<*> || v is RangedValue<*>
    }

    private fun toggleNextValue(v: Value<*>) {
        val cls = v.javaClass
        try {
            val nextMethod = cls.methods.find { it.name == "next" && it.parameterCount == 0 }
            if (nextMethod != null) {
                nextMethod.invoke(v)
                return
            }
        } catch (_: Exception) {}

        val curObj = v.get()
        
        if (curObj is Enum<*>) {
            val constants = curObj.javaClass.enumConstants
            if (constants != null && constants.isNotEmpty()) {
                val nextIdx = (curObj.ordinal + 1) % constants.size
                val nextVal: Any = constants[nextIdx]
                @Suppress("UNCHECKED_CAST")
                (v as Value<Any>).set(nextVal)
                return
            }
        }

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
                    val nextVal = choices[nextIdx]
                    if (nextVal != null) {
                        @Suppress("UNCHECKED_CAST")
                        (v as Value<Any>).set(nextVal)
                    }
                    return
                } else if (choices is List<*>) {
                    val idx = choices.indexOf(curObj)
                    val nextIdx = if (idx >= 0) (idx + 1) % choices.size else 0
                    val nextVal = choices[nextIdx]
                    if (nextVal != null) {
                        @Suppress("UNCHECKED_CAST")
                        (v as Value<Any>).set(nextVal)
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

        val x = (minecraft!!.window.guiScaledWidth - W) / 2f
        val y = (minecraft!!.window.guiScaledHeight - H) / 2f
        val f = minecraft!!.font
        val tabW = (W - 24) / cats.size

        // 1. 绘制带有 8px 圆角的整外框大背景
        val R = 8f
        fillRoundedRect(ctx, x, y, x + W, y + H, R, bg)
        
        // 顶部 Header 标题栏
        ctx.fill(x.toInt() + R.toInt(), y.toInt(), (x + W - R).toInt(), (y + 24).toInt(), headerBg)
        ctx.drawString(f, "§lClickGUI", x.toInt() + 10, y.toInt() + 5, accent)

        // 搜索框
        val searchY = y + 28
        ctx.fill(x.toInt() + 8, searchY.toInt(), (x + W - 8).toInt(), (searchY + 15).toInt(), 0x28000000.toInt())
        val disp = if (search.isEmpty()) "§7Search modules..." else "§f$search"
        ctx.drawString(f, trimText(f, disp, W - 30), x.toInt() + 12, searchY.toInt() + 2, -1)
        if (searchFocus) {
            val cx = x.toInt() + 12 + f.width(search)
            if (cx < x + W - 12) {
                ctx.fill(cx, searchY.toInt() + 2, cx + 1, searchY.toInt() + 13, 0xFFFFFFFF.toInt())
            }
        }

        // 分类 Tabs
        val tabY = searchY + 20
        ctx.fill(x.toInt() + 4, tabY.toInt(), (x + W - 4).toInt(), (tabY + 20).toInt(), 0x18000000.toInt())
        for (i in cats.indices) {
            val tx = x + 8 + i * tabW
            val sel = i == cat
            if (sel) {
                ctx.fill(tx.toInt(), tabY.toInt(), (tx + tabW - 2).toInt(), (tabY + 20).toInt(), accent)
                ctx.fill(tx.toInt(), (tabY + 18).toInt(), (tx + tabW - 2).toInt(), (tabY + 20).toInt(), 0xFF2A5DB0.toInt())
            } else if (mx in tx.toInt()..(tx + tabW - 2).toInt() && my in tabY.toInt()..(tabY + 20).toInt()) {
                ctx.fill(tx.toInt(), tabY.toInt(), (tx + tabW - 2).toInt(), (tabY + 20).toInt(), 0x20FFFFFF.toInt())
            }
            val tagStr = trimText(f, cats[i].tag, tabW - 4)
            val cw = f.width(tagStr)
            ctx.drawString(f, tagStr, tx.toInt() + ((tabW - 2) - cw) / 2, tabY.toInt() + 4, if (sel) -1 else textGray)
        }

        val divY = tabY + 22
        ctx.fill(x.toInt() + 8, divY.toInt(), (x + W - 8).toInt(), (divY + 1).toInt(), 0x20FFFFFF.toInt())

        // 左侧模块列表
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
            val hov = mx in (x.toInt() + 8)..listRight.toInt() && my in mi..(mi + rowH)

            if (hov) ctx.fill(x.toInt() + 8, mi, listRight.toInt(), mi + rowH, 0x14FFFFFF.toInt())
            if (flash > 0f && flashRow == i) {
                val fa = (flash * 80).toInt()
                ctx.fill(x.toInt() + 8, mi, listRight.toInt(), mi + rowH, (fa shl 24) or 0x00FFFFFF)
            }

            val isExpandedMod = expanded == mod
            val nameText = trimText(f, (if (isExpandedMod) "§n" else "") + mod.name, (listRight - x - 45).toInt())
            ctx.drawString(f, nameText, x.toInt() + 14, mi + 3, if (mod.enabled) accent else textGray)

            val switchW = 24
            val switchH = 12
            val btnX = listRight.toInt() - switchW - 4
            val btnY = mi + (rowH - switchH) / 2

            if (mod.enabled) {
                ctx.fill(btnX, btnY, btnX + switchW, btnY + switchH, accent)
                ctx.fill(btnX + switchW - 10, btnY + 2, btnX + switchW - 2, btnY + switchH - 2, 0xFFFFFFFF.toInt())
            } else {
                ctx.fill(btnX, btnY, btnX + switchW, btnY + switchH, 0x30FFFFFF.toInt())
                ctx.fill(btnX + 2, btnY + 2, btnX + 10, btnY + switchH - 2, 0xAA808080.toInt())
            }
        }

        // 右侧设置面板
        val exp = expanded
        if (exp != null) {
            val px = x + W - panelW - 2
            val py = listY
            val maxTextW = panelW - 16

            fillRoundedRect(ctx, px, py, x + W - 2, y + H - 2, 4f, panelBg)

            val setList = exp.collectValuesRecursively()
            val setY = py + 4
            val setH = H - (setY - y) - 6

            var totalContentH = 0f
            for (v in setList) {
                totalContentH += if (isSliderValue(v)) 18f else 14f
            }

            tOff2 = max(5f, tOff2.coerceAtMost(max(0f, totalContentH - setH + 5f)))
            sOff2 += (tOff2 - sOff2) * 0.3f

            // 开启坐标裁切限制，避免滑动时文本溢出 ClickGUI 底部
            ctx.enableScissor(px.toInt(), setY.toInt(), (x + W - 2).toInt(), (y + H - 2).toInt())

            var curY = setY - sOff2
            for (v in setList) {
                val isSlider = isSliderValue(v)
                val itemH = if (isSlider) 18f else 14f

                if (curY >= setY - itemH && curY <= setY + setH) {
                    val mi2 = curY.toInt()
                    try {
                        val valObj = v.get()
                        when {
                            valObj is Boolean -> {
                                val text = trimText(f, "${v.name}: ${if (valObj) "§aON" else "§cOFF"}", maxTextW)
                                ctx.drawString(f, text, px.toInt() + 8, mi2 + 2, -1)
                            }
                            isSlider -> {
                                var fv = 0f; var mn = 0f; var mxr = 20f
                                if (valObj is ClosedRange<*>) {
                                    fv = (valObj.endInclusive as? Number)?.toFloat() ?: 20f
                                    mn = 1f; mxr = 30f
                                } else if (valObj is Number) {
                                    fv = valObj.toFloat()
                                    if (v is RangedValue<*>) {
                                        mn = (v.range.start as? Number)?.toFloat() ?: 0f
                                        mxr = (v.range.endInclusive as? Number)?.toFloat() ?: 100f
                                    }
                                }

                                val bw = panelW - 16; val bh = 4
                                val bx = px.toInt() + 8; val by = mi2 + 11
                                ctx.fill(bx, by, bx + bw, by + bh, 0x30000000.toInt())
                                val r = ((fv - mn) / max(0.001f, mxr - mn)).coerceIn(0f, 1f)
                                ctx.fill(bx, by, (bx + bw * r).toInt(), by + bh, accent)

                                val dispVal = if (valObj is ClosedRange<*>) "${valObj.start} - ${valObj.endInclusive}" else "%.1f".format(fv)
                                val text = trimText(f, "${v.name}: $dispVal", maxTextW)
                                ctx.drawString(f, text, px.toInt() + 8, mi2, -1)
                            }
                            else -> {
                                // 使用格式化清洗函数，展示简洁干净的模式/按键名
                                val dispStr = formatDisplayValue(valObj)
                                val text = trimText(f, "${v.name}: §b$dispStr", maxTextW)
                                ctx.drawString(f, text, px.toInt() + 8, mi2 + 2, -1)
                            }
                        }
                    } catch (_: Exception) {}
                }
                curY += itemH
            }

            // 关闭裁切限制
            ctx.disableScissor()
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
                                valObj is Boolean -> {
                                    @Suppress("UNCHECKED_CAST")
                                    (v as Value<Boolean>).set(!valObj)
                                }
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
