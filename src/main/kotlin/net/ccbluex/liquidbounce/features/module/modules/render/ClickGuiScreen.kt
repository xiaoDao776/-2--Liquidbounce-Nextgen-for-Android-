package net.ccbluex.liquidbounce.features.module.modules.render

import net.ccbluex.liquidbounce.config.types.RangedValue
import net.ccbluex.liquidbounce.config.types.Value
import net.ccbluex.liquidbounce.features.module.ClientModule
import net.ccbluex.liquidbounce.features.module.ModuleCategories
import net.ccbluex.liquidbounce.features.module.ModuleManager
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
    private var sOff = 5f; private var tOff = 5f      // module list scroll
    private var sOff2 = 5f; private var tOff2 = 5f    // settings panel scroll
    private var pm = false
    private var anim = 0f
    private var clickButton = 0
    private var flash = 0f        // click flash animation
    private var flashRow = -1     // which row is flashing

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

    override fun render(ctx: GuiGraphics, mx: Int, my: Int, dt: Float) {
        anim += (1f - anim) * 0.25f
        val a = anim.coerceIn(0f, 1f)
        if (a < 0.01f) return

        // decay flash animation
        if (flash > 0f) flash -= dt / 3f
        else flash = 0f

        val x = (minecraft!!.window.guiScaledWidth - W) / 2
        val y = (minecraft!!.window.guiScaledHeight - H) / 2
        val f = minecraft!!.font
        val tabW = (W - 24) / cats.size

        // main panel
        ctx.fill(x, y, x + W, y + H, bg)
        // header
        ctx.fill(x, y, x + W, y + 24, headerBg)
        ctx.drawString(f, "§lClickGUI", x + 10, y + 5, accent)

        // search
        val searchY = y + 28
        ctx.fill(x + 8, searchY, x + W - 8, searchY + 15, 0x28000000.toInt())
        val disp = if (search.isEmpty()) "§7Search modules..." else "§f$search"
        ctx.drawString(f, disp, x + 12, searchY + 2, -1)
        if (searchFocus) {
            val cx = x + 12 + f.width(search)
            ctx.fill(cx, searchY + 2, cx + 1, searchY + 13, 0xFFFFFFFF.toInt())
        }

        // tabs
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
            val cw = f.width(cats[i].tag)
            ctx.drawString(f, cats[i].tag, tx + (tabW - cw) / 2, tabY + 4, if (sel) -1 else textGray)
        }

        // divider
        val divY = tabY + 22
        ctx.fill(x + 8, divY, x + W - 8, divY + 1, 0x20FFFFFF.toInt())

        // module list (left)
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

            // hover bg
            if (hov) ctx.fill(x + 8, mi, listRight, mi + rowH, 0x14FFFFFF.toInt())
            // click flash bg (with alpha animation)
            if (flash > 0f && flashRow == i) {
                val fa = (flash * 80).toInt()
                ctx.fill(x + 8, mi, listRight, mi + rowH, (fa shl 24) or 0x00FFFFFF)
            }

            ctx.drawString(f, mod.name, x + 14, mi + 3, if (mod.enabled) accent else textGray)

            val btnX = listRight - 22
            if (mod.enabled) {
                ctx.fill(btnX, mi + 2, btnX + 14, mi + 16, accent)
                ctx.drawString(f, "ON", btnX + 1, mi + 3, -1)
            } else {
                ctx.fill(btnX, mi + 2, btnX + 14, mi + 16, 0x30FFFFFF.toInt())
                ctx.drawString(f, "OFF", btnX - 1, mi + 3, textGray)
            }

            // interaction: LEFT=toggle only, RIGHT=expand only
            if (hov && !pm) {
                if (clickButton == 0) {
                    mod.enabled = !mod.enabled
                    flash = 1f; flashRow = i
                } else if (clickButton == 1) {
                    expanded = if (expanded == mod) null else mod
                    flash = 1f; flashRow = i
                }
            }
        }

        // settings panel (right) - uses collectValuesRecursively for FULL settings
        val exp = expanded
        if (exp != null) {
            val px = x + W - panelW - 2
            val py = listY
            ctx.fill(px, py, x + W - 2, y + H - 2, panelBg)
            ctx.drawString(f, "§l" + exp.name, px + 6, py + 3, accent)

            val setList = exp.collectValuesRecursively()
            val setY = py + 14
            val setH = H - (setY - y) - 6
            tOff2 = max(5f, tOff2.coerceAtMost(max(0f, setList.size * 18f - setH + 5f)))
            sOff2 += (tOff2 - sOff2) * 0.3f

            var idx = 0
            for (v in setList) {
                val sy2 = setY + idx * 18 - sOff2
                if (sy2 < setY - 18 || sy2 > setY + setH) { idx++; continue }
                val mi2 = sy2.toInt()
                try {
                    when (val iv = v.get()) {
                        is Boolean -> {
                            ctx.drawString(f, "${v.name}: ${if (iv) "§aON" else "§cOFF"}", px + 8, mi2 + 2, -1)
                            if (!pm && clickButton == 0 && mx in px..(px + panelW) && my in mi2..(mi2 + 18))
                                (v as Value<Boolean>).set(!iv)
                        }
                        is Number -> {
                            val fv = iv.toFloat()
                            val mn = if (v is RangedValue<*>) (v.range.start as? Number)?.toFloat() ?: 0f else 0f
                            val mxr = if (v is RangedValue<*>) (v.range.endInclusive as? Number)?.toFloat() ?: 100f else 100f
                            val bw = panelW - 16; val bh = 5
                            val bx = px + 8; val by = mi2 + 11
                            ctx.fill(bx, by, bx + bw, by + bh, 0x30000000.toInt())
                            val r = ((fv - mn) / (mxr - mn)).coerceIn(0f, 1f)
                            ctx.fill(bx, by, (bx + bw * r).toInt(), by + bh, accent)
                            ctx.drawString(f, "${v.name}: ${"%.1f".format(fv)}", px + 8, mi2, -1)
                            if (!pm && clickButton == 0 && mx in bx..(bx + bw) && my in by..(by + bh)) {
                                val nr = ((mx - bx).toFloat() / bw).coerceIn(0f, 1f)
                                val nv = mn + nr * (mxr - mn)
                                if (iv is Float) (v as Value<Float>).set(nv)
                                else if (iv is Int) (v as Value<Int>).set(nv.toInt())
                            }
                        }
                    }
                } catch (_: Exception) {}
                idx++
            }
        }

        pm = true
    }

    override fun renderBackground(ctx: GuiGraphics, mx: Int, my: Int, dt: Float) {}

    override fun mouseClicked(click: MouseButtonEvent, doubled: Boolean): Boolean {
        clickButton = click.button()
        val mx = click.x.toInt(); val my = click.y.toInt()
        val x = (minecraft!!.window.guiScaledWidth - W) / 2
        val y = (minecraft!!.window.guiScaledHeight - H) / 2
        val tabW = (W - 24) / cats.size

        if (mx in (x + 8)..(x + W - 8) && my in (y + 28)..(y + 43)) { searchFocus = true; return true }
        if (mx !in (x + 8)..(x + W - 8) || my !in (y + 8)..(y + H - 8)) searchFocus = false

        if (clickButton == 0) {
            val tabY = y + 48
            for (i in cats.indices) {
                val tx = x + 8 + i * tabW
                if (mx in tx..(tx + tabW) && my in tabY..(tabY + 20)) { cat = i; sOff = 5f; tOff = 5f; expanded = null; return true }
            }
        }
        return false
    }

    override fun mouseReleased(click: MouseButtonEvent): Boolean { pm = false; clickButton = 0; return true }

    override fun mouseScrolled(mx: Double, my: Double, h: Double, v: Double): Boolean {
        val x = (minecraft!!.window.guiScaledWidth - W) / 2
        val panelX = x + W - panelW - 2
        if (expanded != null && mx >= panelX) tOff2 = (tOff2 - v.toFloat() * 18f).coerceAtLeast(5f)
        else tOff = (tOff - v.toFloat() * 18f).coerceAtLeast(5f)
        return true
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        if (input.key == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true }
        if (searchFocus) {
            when (input.key) {
                GLFW.GLFW_KEY_BACKSPACE -> { if (search.isNotEmpty()) search = search.dropLast(1); return true }
                GLFW.GLFW_KEY_SPACE -> { search += " "; return true }
                else -> {
                    val n = GLFW.glfwGetKeyName(input.key, 0)
                    if (n != null && n.length == 1) { search += n; return true }
                }
            }
        }
        return false
    }

    // support IME / Chinese input
    override fun charTyped(characterEvent: CharacterEvent): Boolean {
        if (searchFocus) { search += characterEvent.codePoint.toChar(); return true }
        return false
    }

    override fun onClose() { minecraft?.setScreen(null); anim = 0f }

    private fun getMods(): List<ClientModule> {
        val catObj = cats.getOrElse(cat) { ModuleCategories.COMBAT }
        return ModuleManager.getModules()
            .filter { it.category == catObj && it.name != "ClickGUI" }
            .filter { search.isEmpty() || it.name.contains(search, ignoreCase = true) }
    }
}
