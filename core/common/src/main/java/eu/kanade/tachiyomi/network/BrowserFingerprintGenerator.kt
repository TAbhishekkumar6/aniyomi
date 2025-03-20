package eu.kanade.tachiyomi.network

import kotlin.random.Random

class BrowserFingerprintGenerator {

    private val chromeVersions = listOf("120.0.0.0", "121.0.0.0", "122.0.0.0")
    private val platforms = listOf("Win32", "Win64", "Linux x86_64", "MacIntel")
    private val languages = listOf("en-US", "en-GB", "en", "es-ES", "fr-FR", "de-DE")
    private val timezones = listOf("UTC", "America/New_York", "Europe/London", "Europe/Paris")
    private val vendors = listOf(
        "Google Inc.",
        "Apple Computer, Inc.",
        "Mozilla",
        "Opera Software ASA"
    )

    // Common browser plugins
    private val plugins = listOf(
        "PDF Viewer",
        "Chrome PDF Plugin", 
        "Chrome PDF Viewer",
        "Native Client",
        "Widevine Content Decryption Module"
    )

    private val screenResolutions = listOf(
        Pair(1920, 1080),
        Pair(1366, 768),
        Pair(1536, 864),
        Pair(1440, 900),
        Pair(1280, 720)
    )

    fun generateFingerprint(): Map<String, String> {
        val chromeVersion = chromeVersions.random()
        val platform = platforms.random()
        val language = languages.random()
        val (width, height) = screenResolutions.random()
        
        return mapOf(
            "User-Agent" to generateUserAgent(chromeVersion, platform),
            "Accept-Language" to language,
            "sec-ch-ua" to "\"Not A(Brand\";v=\"99\", \"Google Chrome\";v=\"${chromeVersion.split(".")[0]}\", \"Chromium\";v=\"${chromeVersion.split(".")[0]}\"",
            "sec-ch-ua-mobile" to "?0",
            "sec-ch-ua-platform" to "\"$platform\"",
            "Vendor" to vendors.random(),
            "Platform" to platform,
            "Screen-Resolution" to "${width}x${height}",
            "Color-Depth" to "24",
            "Timezone" to timezones.random(),
            "Language" to language,
            "Hardware-Concurrency" to "${Random.nextInt(2, 16)}",
            "Device-Memory" to "${Random.nextInt(2, 32)}",
            "Plugins" to plugins.shuffled().take(Random.nextInt(2, 5)).joinToString(",")
        )
    }

    fun getDefaultFingerprint(): Map<String, String> {
        return mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
            "Accept-Language" to "en-US,en;q=0.9",
            "sec-ch-ua" to "\"Not A(Brand\";v=\"99\", \"Google Chrome\";v=\"121\", \"Chromium\";v=\"121\"",
            "sec-ch-ua-mobile" to "?0", 
            "sec-ch-ua-platform" to "\"Windows\"",
            "Vendor" to "Google Inc.",
            "Platform" to "Win64",
            "Screen-Resolution" to "1920x1080",
            "Color-Depth" to "24",
            "Timezone" to "UTC",
            "Language" to "en-US",
            "Hardware-Concurrency" to "8",
            "Device-Memory" to "8",
            "Plugins" to "Chrome PDF Plugin,Chrome PDF Viewer,Native Client"
        )
    }

    private fun generateUserAgent(chromeVersion: String, platform: String): String {
        return when(platform) {
            "Win32", "Win64" -> "Mozilla/5.0 (Windows NT 10.0; ${platform}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${chromeVersion} Safari/537.36"
            "Linux x86_64" -> "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${chromeVersion} Safari/537.36" 
            "MacIntel" -> "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${chromeVersion} Safari/537.36"
            else -> getDefaultFingerprint()["User-Agent"]!!
        }
    }

    // Generate WebGL parameters to bypass canvas fingerprinting
    fun generateWebGLParams(): String {
        val webGLVendors = listOf(
            "Google Inc. (NVIDIA)",
            "Google Inc. (AMD)",
            "Google Inc. (Intel)", 
            "Intel Inc.",
            "NVIDIA Corporation",
            "ATI Technologies Inc."
        )
        val renderers = listOf(
            "ANGLE (NVIDIA, NVIDIA GeForce GTX 1060 Direct3D11 vs_5_0 ps_5_0)",
            "ANGLE (AMD, AMD Radeon RX 580 Direct3D11 vs_5_0 ps_5_0)",
            "ANGLE (Intel, Intel(R) UHD Graphics 630 Direct3D11 vs_5_0 ps_5_0)",
            "ANGLE (Intel, Mesa Intel(R) UHD Graphics 630 (CFL GT2))",
            "ANGLE (NVIDIA GeForce RTX 2060 Direct3D11 vs_5_0 ps_5_0)"
        )

        return """
            const getParameter = WebGLRenderingContext.prototype.getParameter;
            WebGLRenderingContext.prototype.getParameter = function(parameter) {
                if (parameter === 37445) {
                    return '${webGLVendors.random()}';
                }
                if (parameter === 37446) {
                    return '${renderers.random()}';
                }
                return getParameter.apply(this, arguments);
            };
        """.trimIndent()
    }
}