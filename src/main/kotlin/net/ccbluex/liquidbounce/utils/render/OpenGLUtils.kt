/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2015 - 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 */
package net.ccbluex.liquidbounce.utils.render

import net.ccbluex.liquidbounce.utils.client.PlatformUtils
import net.ccbluex.liquidbounce.utils.client.logger
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.glGetFloatv

/**
 * Gets a range (i.e. GL_ALIASED_LINE_WIDTH_RANGE) from OpenGL as a kotlin range.
 *
 * On Android/GLES platforms, some GL features (like line width > 1.0)
 * are not supported. This function handles those gracefully.
 */
fun getGlFloatRange(key: Int): ClosedFloatingPointRange<Float> {
    val floats = floatArrayOf(0.0f, 0.0f)

    glGetFloatv(key, floats)

    return floats[0]..floats[1]
}

/**
 * Whether GL line width beyond 1.0 is supported.
 * OpenGL ES (Android) only supports line width of 1.0.
 */
val SUPPORTS_CUSTOM_LINE_WIDTH: Boolean by lazy {
    if (PlatformUtils.IS_ANDROID || PlatformUtils.IS_POJAV) {
        // GLES usually only supports line width = 1.0
        val range = try {
            getGlFloatRange(0x846E) // GL_ALIASED_LINE_WIDTH_RANGE
        } catch (e: Exception) {
            logger.warn("Failed to query line width range, assuming GLES 1.0 only", e)
            return@lazy false
        }
        range.endInclusive > 1.0f
    } else {
        true // Desktop GL supports custom line widths
    }
}

