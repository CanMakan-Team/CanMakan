package sg.edu.nus.iss.canmakan.features.auth.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

@DisplayName("UC19: Android merged transport security")
class AndroidTransportSecurityTest {

    @Test
    fun debugVariantAllowsCleartextOnlyThroughItsMergedOverlay() {
        assertEquals("true", applicationAttribute("debug", "usesCleartextTraffic"))
        assertEquals("true", baseConfigAttribute("debug", "cleartextTrafficPermitted"))
    }

    @Test
    fun releaseVariantFailsClosedForCleartextTraffic() {
        assertEquals("false", applicationAttribute("release", "usesCleartextTraffic"))
        assertEquals("false", baseConfigAttribute("release", "cleartextTrafficPermitted"))
    }

    private fun applicationAttribute(variant: String, attribute: String): String {
        val variantTaskName = variant.replaceFirstChar { it.uppercase() }
        val manifest = generatedFile(
            "build/intermediates/merged_manifests/$variant/process${variantTaskName}Manifest/AndroidManifest.xml"
        )
        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }.newDocumentBuilder().parse(manifest)
        val application = document.getElementsByTagName("application").item(0) as Element
        return application.getAttributeNS(ANDROID_NAMESPACE, attribute)
    }

    private fun baseConfigAttribute(variant: String, attribute: String): String {
        val variantTaskName = variant.replaceFirstChar { it.uppercase() }
        val config = generatedFile(
            "build/intermediates/packaged_res/$variant/package${variantTaskName}Resources/xml/network_security_config.xml"
        )
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(config)
        val baseConfig = document.getElementsByTagName("base-config").item(0) as Element
        return baseConfig.getAttribute(attribute)
    }

    private fun generatedFile(relativePath: String): File {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        return sequenceOf(
            File(workingDirectory, relativePath),
            File(workingDirectory, "app/$relativePath"),
        ).firstOrNull(File::isFile)
            ?: error("Required merged Android artifact is missing: $relativePath")
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
