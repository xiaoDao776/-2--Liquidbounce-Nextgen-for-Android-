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

/**
 * Android compatibility utilities for LiquidBounce
 *
 * Detects whether the client is running on an Android device
 * (via PojavLauncher, ZalithLauncher, or similar Android Minecraft launchers)
 * and provides feature flags to disable incompatible functionality.
 */
object AndroidCompat {

    /**
     * Whether the client is running on an Android device.
     *
     * Detection is based on multiple heuristics:
     * 1. Java system property "java.vendor" contains "Android" or "The Android Project"
     * 2. System property "os.name" contains "Android" or "Linux" with Android-specific paths
     * 3. Classpath contains Android-specific libraries
     * 4. Environment variable "POJAV_LAUNCHER" or "ZALITH_LAUNCHER" is set
     * 5. The presence of Android-specific directories
     */
    val isAndroid: Boolean by lazy {
        detectAndroid()
    }

    /**
     * Whether the client is running via PojavLauncher specifically.
     */
    val isPojavLauncher: Boolean by lazy {
        System.getenv("POJAV_LAUNCHER") != null ||
            System.getProperty("pojav.launcher") != null ||
            javaClass.classLoader.getResource("net/kdt/pojavlaunch") != null
    }

    /**
     * Whether the client is running via ZalithLauncher specifically.
     */
    val isZalithLauncher: Boolean by lazy {
        System.getenv("ZALITH_LAUNCHER") != null ||
            System.getProperty("zalith.launcher") != null
    }

    /**
     * Whether the client is running on any Android Minecraft launcher.
     */
    val isAndroidLauncher: Boolean by lazy {
        isPojavLauncher || isZalithLauncher || isAndroid
    }

    /**
     * Whether the current device is a low-end Android device.
     * Used to disable resource-intensive features automatically.
     */
    val isLowEndDevice: Boolean by lazy {
        if (!isAndroidLauncher) return@lazy false

        val maxMemory = Runtime.getRuntime().maxMemory()
        val cpuCores = Runtime.getRuntime().availableProcessors()

        // Consider low-end if less than 2GB heap or less than 4 cores
        maxMemory < 2L * 1024 * 1024 * 1024 || cpuCores < 4
    }

    /**
     * Features that should be disabled on Android.
     */
    val disabledFeatures: Set<String> by lazy {
        val features = mutableSetOf<String>()

        if (isAndroidLauncher) {
            features.add("jcef_browser")       // JCEF (Chromium) doesn't run on Android
            features.add("discord_rpc")         // Discord IPC not available on Android
            features.add("deep_learning")       // DJL/PyTorch not supported on Android
            features.add("cef_screens")         // CEF-based screens not available
            features.add("vulkan_renderer")     // Vulkan may not be available on all Android devices
        }

        if (isLowEndDevice) {
            features.add("blur_effects")        // GPU blur effects are expensive
            features.add("particle_enhancer")   // Particle effects are expensive
            features.add("shader_effects")      // Custom shaders may be slow
        }

        features
    }

    /**
     * Check if a specific feature is available on the current platform.
     */
    fun isFeatureAvailable(feature: String): Boolean {
        return feature !in disabledFeatures
    }

    /**
     * Android-specific paths for configuration and data storage.
     */
    val dataDirectory: String by lazy {
        if (!isAndroidLauncher) return@lazy System.getProperty("user.dir")

        // PojavLauncher typically stores data in /sdcard/Android/data/
        val externalStorage = System.getenv("EXTERNAL_STORAGE")
            ?: "/sdcard"

        "${externalStorage}/Android/data/net.kdt.pojavlaunch/files/liquidbounce"
    }

    /**
     * Get the touch-compatible key codes mapping.
     * Android uses virtual keyboard with different key handling.
     */
    val useTouchInput: Boolean by lazy {
        isAndroidLauncher
    }

    private fun detectAndroid(): Boolean {
        // Check Java vendor
        val javaVendor = System.getProperty("java.vendor", "")
        if (javaVendor.contains("Android", ignoreCase = true) ||
            javaVendor.contains("The Android Project", ignoreCase = true)
        ) {
            return true
        }

        // Check Java runtime name
        val javaRuntime = System.getProperty("java.runtime.name", "")
        if (javaRuntime.contains("Android", ignoreCase = true)) {
            return true
        }

        // Check Java VM name
        val javaVm = System.getProperty("java.vm.name", "")
        if (javaVm.contains("Dalvik", ignoreCase = true) ||
            javaVm.contains("ART", ignoreCase = true)
        ) {
            return true
        }

        // Check OS name combined with Android paths
        val osName = System.getProperty("os.name", "")
        if (osName.contains("Android", ignoreCase = true)) {
            return true
        }

        // Check for Android-specific environment variables
        val androidRoot = System.getenv("ANDROID_ROOT")
        val androidData = System.getenv("ANDROID_DATA")
        if (androidRoot != null || androidData != null) {
            return true
        }

        // Check for PojavLauncher/ZalithLauncher environment variables
        if (System.getenv("POJAV_LAUNCHER") != null ||
            System.getenv("ZALITH_LAUNCHER") != null
        ) {
            return true
        }

        // Check for Android-specific system properties
        if (System.getProperty("java.vm.version", "").contains("android", ignoreCase = true)) {
            return true
        }

        // Check file system for Android paths
        try {
            val androidSystemPath = java.io.File("/system/build.prop")
            val androidDataPath = java.io.File("/data/data")
            if (androidSystemPath.exists() || androidDataPath.exists()) {
                return true
            }
        } catch (_: Exception) {
            // Ignore - can't access these paths
        }

        return false
    }

    /**
     * Print Android environment information for debugging.
     */
    fun printAndroidInfo() {
        if (!isAndroidLauncher) return

        val logger = net.ccbluex.liquidbounce.utils.client.logger
        logger.info("=== Android Environment Detected ===")
        logger.info("Android Launcher: ${if (isPojavLauncher) "PojavLauncher" else if (isZalithLauncher) "ZalithLauncher" else "Unknown"}")
        logger.info("Java Vendor: ${System.getProperty("java.vendor")}")
        logger.info("Java Version: ${System.getProperty("java.version")}")
        logger.info("OS Name: ${System.getProperty("os.name")}")
        logger.info("OS Version: ${System.getProperty("os.version")}")
        logger.info("Max Memory: ${Runtime.getRuntime().maxMemory() / (1024 * 1024)} MB")
        logger.info("CPU Cores: ${Runtime.getRuntime().availableProcessors()}")
        logger.info("Data Directory: $dataDirectory")
        logger.info("Low End Device: $isLowEndDevice")
        logger.info("Disabled Features: $disabledFeatures")
        logger.info("=====================================")
    }
}
