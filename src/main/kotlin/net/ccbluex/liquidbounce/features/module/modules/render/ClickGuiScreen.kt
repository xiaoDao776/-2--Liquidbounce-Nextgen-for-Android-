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

    /**
     * 将长文本截断以防止跑出右侧 GUI 边界
     */
    private fun trimText(font: Font, text: String, maxW: Int): String {
        if (font.width(text) <= maxW) return text
        var str = text
        while (str.isNotEmpty() && font.width("$str...") > maxW) {
            str = str.substring(0, str.length - 1)
        }
        return "$str..."
    }

    /**
     * 优雅格式化各种 Complex/Value 类型为简短字符串
     */
    private fun formatValueDisplay(v: Value<*>): String {
        val valObj = v.get() ?: return "null"
        val valStr = valObj.toString()
        
        // 专门处理 KeyBind 对象，避免输出 InputBind(boundKey=...)
        if (valStr.contains("boundKey=")) {
            val keyPart = valStr.substringAfter("boundKey=").substringBefore(",").substringBefore(")")
            return keyPart.replace("key.keyboard.", "").uppercase()
        }
        
        // 专门处理简单对象数组或长映射
        if (valStr.contains("[")) {
            val shortStr = valStr.substringBefore("[")
            if (shortStr.isNotBlank()) return shortStr
        }
        
        return valStr
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

        // 右侧设置子菜单绘制（包含边界裁剪防御）
        val exp = expanded
        if (exp != null) {
            val px = x + W - panelW - 2
            val py = listY
            val maxTextW = panelW - 16 // 设置列表最大可容纳文字宽度

            ctx.fill(px, py, x + W - 2, y + H - 2, panelBg)
            ctx.drawString(f, trimText(f, "§l" + exp.name, maxTextW), px + 6, py + 3, accent)

            val setList = exp.collectValuesRecursively()
            val setY = py + 14
            val setH = H - (setY - y) - 6

            var totalContentH = 0f
            for (v in setList) {
                val valObj = v.get()
                totalContentH += if (valObj is Number) 18f else 14f
            }

            tOff2 = max(5f, tOff2.coerceAtMost(max(0f, totalContentH - setH + 5f)))
            sOff2 += (tOff2 - sOff2) * 0.3f

            var curY = setY - sOff2
            for (v in setList) {
                val valObj = v.get()
                val itemH = if (valObj is Number) 18f else 14f

                if (curY >= setY - itemH && curY <= setY + setH) {
                    val mi2 = curY.toInt()
                    try {
                        when (valObj) {
                            is Boolean -> {
                                val text = trimText(f, "${v.name}: ${if (valObj) "§aON" else "§cOFF"}", maxTextW)
                                ctx.drawString(f, text, px + 8, mi2 + 2, -1)
                            }
                            is Number -> {
                                val fv = valObj.toFloat()
                                val mn = if (v is RangedValue<*>) (v.range.start as? Number)?.toFloat() ?: 0f else 0f
                                val mxr = if (v is RangedValue<*>) (v.range.endInclusive as? Number)?.toFloat() ?: 100f else 100f
                                val bw = panelW - 16; val bh = 4
                                val bx = px + 8; val by = mi2 + 11
                                ctx.fill(bx, by, bx + bw, by + bh, 0x30000000.toInt())
                                val r = ((fv - mn) / (mxr - mn)).coerceIn(0f, 1f)
                                ctx.fill(bx, by, (bx + bw * r).toInt(), by + bh, accent)

                                val text = trimText(f, "${v.name}: ${"%.1f".format(fv)}", maxTextW)
                                ctx.drawString(f, text, px + 8, mi2, -1)
                            }
                            else -> {
                                // 提取清理后的文字，并进行宽度截断
                                val displayVal = formatValueDisplay(v)
                                val text = trimText(f, "${v.name}: §7$displayVal", maxTextW)
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

        val exp = expanded
        if (exp != null && btn == 0) {
            val px = x + W - panelW - 2
            val py = listY
            val setY = py + 14
            val setH = H - (setY - y) - 6

            if (mx in px..(px + panelW) && my in setY..(setY + setH)) {
                val setList = exp.collectValuesRecursively()
                var curY = setY - sOff2

                for (v in setList) {
                    val valObj = v.get()
                    val itemH = if (valObj is Number) 18f else 14f

                    if (my >= curY && my < curY + itemH) {
                        try {
                            when (valObj) {
                                is Boolean -> {
                                    @Suppress("UNCHECKED_CAST")
                                    (v as Value<Boolean>).set(!valObj)
                                }
                                is Number -> {
                                    val mn = if (v is RangedValue<*>) (v.range.start as? Number)?.toFloat() ?: 0f else 0f
                                    val mxr = if (v is RangedValue<*>) (v.range.endInclusive as? Number)?.toFloat() ?: 100f else 100f
                                    val bw = panelW - 16
                                    val bx = px + 8
                                    val nr = ((mx - bx).toFloat() / bw).coerceIn(0f, 1f)
                                    val nv = mn + nr * (mxr - mn)
                                    if (valObj is Float) {
                                        @Suppress("UNCHECKED_CAST")
                                        (v as Value<Float>).set(nv)
                                    } else if (valObj is Int) {
                                        @Suppress("UNCHECKED_CAST")
                                        (v as Value<Int>).set(nv.toInt())
                                    }
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
