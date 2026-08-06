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
package net.ccbluex.liquidbounce.utils.client.android

import net.ccbluex.liquidbounce.utils.client.logger

/**
 * Touch input handler for Android devices running LiquidBounce via PojavLauncher/ZalithLauncher.
 *
 * Provides:
 * - Virtual button overlay management
 * - Touch-to-key mapping for common actions
 * - Gesture detection (pinch zoom, swipe, etc.)
 * - HUD touch zone management
 */
object TouchInputHandler {

    private val logger = logger

    /**
     * Whether touch input is active (only on Android).
     */
    val isActive: Boolean
        get() = AndroidCompat.useTouchInput

    /**
     * Touch zones that can be tapped to trigger actions.
     * These represent areas on screen that can be tapped for specific module toggles.
     */
    data class TouchZone(
        val name: String,
        val x: Float,           // Normalized 0-1
        val y: Float,           // Normalized 0-1
        val width: Float = 0.1f,
        val height: Float = 0.1f,
        val action: () -> Unit
    ) {
        fun contains(px: Float, py: Float, screenWidth: Int, screenHeight: Int): Boolean {
            val zoneX = x * screenWidth
            val zoneY = y * screenHeight
            val zoneW = width * screenWidth
            val zoneH = height * screenHeight
            return px >= zoneX && px <= zoneX + zoneW && py >= zoneY && py <= zoneY + zoneH
        }
    }

    /**
     * Registered touch zones for quick actions.
     */
    private val touchZones = mutableListOf<TouchZone>()

    /**
     * Long press detection state.
     */
    private data class LongPressState(
        val startX: Float,
        val startY: Float,
        val startTime: Long,
        var triggered: Boolean = false
    )

    private var longPressState: LongPressState? = null
    private val longPressDurationMs = 500L

    /**
     * Track whether a pinch gesture is in progress.
     */
    private var isPinching = false
    private var lastPinchDistance = 0f

    /**
     * Register a touch zone for quick action triggering.
     */
    fun registerTouchZone(zone: TouchZone) {
        touchZones.add(zone)
        if (AndroidCompat.isAndroidLauncher) {
            logger.debug("Registered touch zone: ${zone.name}")
        }
    }

    /**
     * Remove a registered touch zone.
     */
    fun unregisterTouchZone(name: String) {
        touchZones.removeAll { it.name == name }
    }

    /**
     * Clear all registered touch zones.
     */
    fun clearTouchZones() {
        touchZones.clear()
    }

    /**
     * Handle a touch down event.
     * Returns true if the touch was handled by a touch zone.
     */
    fun onTouchDown(x: Float, y: Float, screenWidth: Int, screenHeight: Int): Boolean {
        if (!isActive) return false

        // Start long press detection
        longPressState = LongPressState(x, y, System.currentTimeMillis())

        return false
    }

    /**
     * Handle a touch up event.
     * Returns true if the touch triggered an action.
     */
    fun onTouchUp(x: Float, y: Float, screenWidth: Int, screenHeight: Int): Boolean {
        if (!isActive) return false

        val longPress = longPressState

        // Check if it was a short tap (not a long press)
        if (longPress != null && !longPress.triggered) {
            val duration = System.currentTimeMillis() - longPress.startTime
            if (duration < longPressDurationMs) {
                // Short tap - check touch zones
                for (zone in touchZones) {
                    if (zone.contains(x, y, screenWidth, screenHeight)) {
                        try {
                            zone.action()
                            logger.debug("Touch zone '${zone.name}' activated")
                            return true
                        } catch (e: Exception) {
                            logger.error("Error executing touch zone '${zone.name}'", e)
                        }
                    }
                }
            }
        }

        longPressState = null
        isPinching = false
        return false
    }

    /**
     * Handle a touch move event (drag/swipe).
     */
    fun onTouchMove(x: Float, y: Float, screenWidth: Int, screenHeight: Int) {
        if (!isActive) return

        val longPress = longPressState ?: return

        // Check long press
        if (!longPress.triggered) {
            val duration = System.currentTimeMillis() - longPress.startTime
            val dx = x - longPress.startX
            val dy = y - longPress.startY
            val distance = kotlin.math.sqrt(dx * dx + dy * dy)

            if (duration >= longPressDurationMs && distance < 20f) {
                longPress.triggered = true
                onLongPress(longPress.startX, longPress.startY, screenWidth, screenHeight)
            }
        }
    }

    /**
     * Handle a long press event.
     */
    private fun onLongPress(x: Float, y: Float, screenWidth: Int, screenHeight: Int) {
        logger.debug("Long press detected at ($x, $y)")

        // Could be used for advanced features like:
        // - Opening QuickMenu
        // - Toggling specific modules
        // - Adjusting GUI elements
    }

    /**
     * Handle multi-touch for pinch gestures.
     */
    fun onMultiTouch(x1: Float, y1: Float, x2: Float, y2: Float): Boolean {
        if (!isActive) return false

        val dx = x2 - x1
        val dy = y2 - y1
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)

        if (isPinching && lastPinchDistance > 0) {
            val scale = distance / lastPinchDistance

            // Pinch gesture detected
            if (scale > 1.2f) {
                // Pinch out - zoom in
                logger.debug("Pinch out (zoom in): scale=$scale")
                return true
            } else if (scale < 0.8f) {
                // Pinch in - zoom out
                logger.debug("Pinch in (zoom out): scale=$scale")
                return true
            }
        }

        isPinching = true
        lastPinchDistance = distance
        return false
    }

    /**
     * Setup default touch zones for common actions on Android.
     * Should be called after GUI initialization.
     */
    fun setupDefaultTouchZones() {
        if (!isActive) return

        clearTouchZones()

        // Example: Add a touch zone in the top-right corner for ClickGUI
        // These would be customized based on user preferences
        touchZones.add(TouchZone(
            name = "ClickGUI",
            x = 0.88f,
            y = 0.02f,
            width = 0.1f,
            height = 0.08f,
            action = {
                // Toggle ClickGUI
                val moduleManager = net.ccbluex.liquidbounce.features.module.ModuleManager
                moduleManager.getModule("ClickGui")?.let { module ->
                    module.enabled = !module.enabled
                }
            }
        ))

        logger.info("Default touch zones configured for Android")
    }

    /**
     * Get the recommended GUI scale for Android devices.
     * Larger scale for easier touch interaction.
     */
    fun getRecommendedGuiScale(screenWidth: Int, screenHeight: Int): Int {
        if (!isActive) return 2

        // On phones, use larger GUI scale for touch friendliness
        val minDimension = minOf(screenWidth, screenHeight)
        return when {
            minDimension < 720 -> 3  // Small phone screens
            minDimension < 1080 -> 2 // Medium phone screens
            minDimension < 1440 -> 2 // Large phone screens
            else -> 1                 // Tablets
        }
    }

    /**
     * Print touch input configuration for debugging.
     */
    fun printConfig() {
        if (!isActive) return

        logger.info("=== Touch Input Configuration ===")
        logger.info("Touch Input Active: $isActive")
        logger.info("Registered Touch Zones: ${touchZones.size}")
        touchZones.forEach { zone ->
            logger.info("  - ${zone.name}: (${zone.x}, ${zone.y}) ${zone.width}x${zone.height}")
        }
        logger.info("=================================")
    }
}
