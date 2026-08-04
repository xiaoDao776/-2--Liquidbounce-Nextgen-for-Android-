package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleManager
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.max

class ClickGuiScreen : Screen(Component.literal("ClickGUI")) {

    private var cat = 0
    private var expanded: ClientModule? = null
    private var search = ""
    private var sOff = 5f; private var tOff = 5f
    private val cats = ModuleCategories.entries.toList()
    private val W = 370; private val H = 290
    private val accent = 0xFF4182E1.toInt()
    private var pm = false

    override fun isPauseScreen() = false
    override fun shouldCloseOnEsc() = true

    override fun render(ctx: GuiGraphics, mx: Int, my: Int, dt: Float) {
        val x = (minecraft!!.window.guiScaledWidth - W) / 2
        val y = (minecraft!!.window.guiScaledHeight - H) / 2
        val f = minecraft!!.font

        // bg
        ctx.fill(x, y, x + W, y + H, 0xDD101014.toInt())
        // title bar
        ctx.fill(x, y, x + W, y + 24, 0xF0000000.toInt())
        ctx.drawString(f, "ClickGUI", x + 10, y + 5, accent)

        // search
        val sy = y + 28
        ctx.fill(x + 6, sy, x + W - 6, sy + 14, 0x50000000.toInt())
        ctx.drawString(f, search.ifEmpty { "Search..." }, x + 10, sy + 2, if (search.isNotEmpty()) -1 else 0xFFA0A0AA.toInt())

        // category tabs
        val ty = sy + 18; val tw = (W - 12) / cats.size
        for (i in cats.indices) {
            val c = cats[i]; val tx = x + 6 + i * tw
            val sel = i == cat
            if (sel) ctx.fill(tx, ty, tx + tw - 1, ty + 18, accent)
            else if (mx in tx..(tx + tw - 1) && my in ty..(ty + 18))
                ctx.fill(tx, ty, tx + tw - 1, ty + 18, 0x19FFFFFF.toInt())
            ctx.drawString(f, c.tag, tx + 3, ty + 3, if (sel) -1 else 0xFFA0A0AA.toInt())
        }

        // module list
        val mods = getMods(); val ly = ty + 24; val vh = H - (ly - y) - 4
        tOff = max(5f, minOf(tOff, max(0f, mods.size * 20f - vh + 5f)))
        sOff += (tOff - sOff) * 0.3f

        for (i in mods.indices) {
            val mod = mods[i]; val my2 = ly + i * 20f - sOff
            if (my2 < ly - 20 || my2 > ly + vh) continue
            val mi = my2.toInt(); val hov = mx in (x + 6)..(x + W - 6) && my in mi..(mi + 20)

            if (hov) ctx.fill(x + 7, mi, x + W - 7, mi + 20, 0x14FFFFFF.toInt())
            ctx.drawString(f, mod.name, x + 12, mi + 3, if (mod.enabled) accent else 0xFF8C8C96.toInt())
            if (mod.enabled) ctx.drawString(f, "ON", x + W - 34, mi + 3, 0xFF50DC50.toInt())

            // click handler
            if (hov && !pm) {
                if (mx > x + W - 30) mod.enabled = !mod.enabled
                else expanded = if (expanded == mod) null else mod
            }

            // settings
            if (mod == expanded) {
                var off = 0
                for (v in mod.inner) {
                    if (v !is Value<*>) continue
                    val sy2 = my2 + 20f + off * 14f
                    try {
                        when (val iv = v.get()) {
                            is Boolean -> {
                                ctx.drawString(f, "${v.name}: ${if (iv) "ON" else "OFF"}", x + 22, sy2.toInt(),
                                    if (iv) 0xFF50DC50.toInt() else 0xFFDC5050.toInt())
                                off++
                            }
                            is Number -> {
                                val fv = iv.toFloat()
                                val mn = if (v is RangedValue<*>) (v.range.start as? Number)?.toFloat() ?: 0f else 0f
                                val mxr = if (v is RangedValue<*>) (v.range.endInclusive as? Number)?.toFloat() ?: 100f else 100f
                                val bw = W - 70; val bh = 4; val bx = x + 22; val by = sy2.toInt() + 9
                                ctx.fill(bx, by, bx + bw, by + bh, 0x50000000.toInt())
                                val r = ((fv - mn) / (mxr - mn)).coerceIn(0f, 1f)
                                ctx.fill(bx, by, (bx + bw * r).toInt(), by + bh, accent)
                                ctx.drawString(f, "${v.name}: ${"%.1f".format(fv)}", x + 22, sy2.toInt(), -1)
                                off++
                            }
                            else -> { ctx.drawString(f, "${v.name}: $iv", x + 22, sy2.toInt(), -1); off++ }
                        }
                    } catch (_: Exception) {}
                    if (off >= 20) break
                }
            }
        }
        pm = true
    }

    override fun renderBackground(ctx: GuiGraphics, mx: Int, my: Int, dt: Float) {}

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        val mx = click.x.toInt(); val my = click.y.toInt()
        val x = (minecraft!!.window.guiScaledWidth - W) / 2
        val y = (minecraft!!.window.guiScaledHeight - H) / 2

        // search
        val sy = y + 28
        if (mx in (x + 6)..(x + W - 6) && my in sy..(sy + 14)) { search = ""; return true }

        // tabs
        val ty = sy + 18; val tw = (W - 12) / cats.size
        for (i in cats.indices) {
            val tx = x + 6 + i * tw
            if (mx in tx..(tx + tw) && my in ty..(ty + 18)) { cat = i; sOff = 5f; tOff = 5f; expanded = null; return true }
        }

        // modules
        val mods = getMods(); val ly = ty + 24
        for (i in mods.indices) {
            val mod = mods[i]; val my2 = ly + i * 20f - sOff; val mi = my2.toInt()
            if (mx in (x + 6)..(x + W - 6) && my in mi..(mi + 20)) {
                if (mx > x + W - 30) mod.enabled = !mod.enabled
                else expanded = if (expanded == mod) null else mod
                return true
            }
            // Bool settings click
            if (mod == expanded) {
                var off = 0
                for (v in mod.inner) {
                    if (v !is Value<*>) continue
                    val sy2 = my2 + 20f + off * 14f
                    if (mx in (x + 22)..(x + W - 22) && my in sy2.toInt()..(sy2 + 14).toInt()) {
                        if (v.get() is Boolean) (v as Value<Boolean>).set(!v.get())
                        return true
                    }
                    off++
                }
            }
        }
        return false
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean { pm = false; return true }

    override fun mouseScrolled(mx: Double, my: Double, h: Double, v: Double): Boolean {
        tOff = (tOff - v.toFloat() * 20f).coerceAtLeast(5f); return true
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (input.key == GLFW.GLFW_KEY_ESCAPE || input.key == GLFW.GLFW_KEY_RIGHT_SHIFT) { onClose(); return true }
        if (input.key == GLFW.GLFW_KEY_BACKSPACE && search.isNotEmpty()) { search = search.dropLast(1); return true }
        if (input.key == GLFW.GLFW_KEY_SPACE) { search += " "; return true }
        val n = GLFW.glfwGetKeyName(input.key, 0)
        if (n != null && n.length == 1) { search += n; return true }
        return false
    }

    override fun onClose() { minecraft?.setScreen(null) }

    private fun getMods(): List<ClientModule> {
        val catObj = cats.getOrElse(cat) { ModuleCategories.COMBAT }
        return ModuleManager.getModules()
            .filter { it.category == catObj && it.name != "ClickGUI" }
            .filter { search.isEmpty() || it.name.contains(search, ignoreCase = true) }
    }
}
