package bisq.gradle.packaging

import java.io.File
import java.nio.file.Path

/**
 * Utility class that generates files needed for AUR (Arch User Repository) package.
 */
class AurPackageGenerator {

    /**
     * Generates AUR package files in the specified output directory.
     */
    fun generateAurPackage(
        appName: String,
        appVersion: String,
        outputDir: Path
    ) {
        // Create output directories
        val aurPackageDir = outputDir.resolve("aur").toFile()
        aurPackageDir.mkdirs()

        // Generate PKGBUILD file
        generatePkgbuild(aurPackageDir, appName, appVersion)

        // Generate .SRCINFO file
        generateSrcInfo(aurPackageDir, appName, appVersion)
    }

    /**
     * Generates the PKGBUILD file for AUR
     */
    private fun generatePkgbuild(aurPackageDir: File, appName: String, appVersion: String) {
        val pkgName = appName.lowercase().replace(" ", "-")
        val licenseType = "AGPL3"

        val pkgbuildContent = """
            # Maintainer: Bisq developers <contact@bisq.io>
            
            pkgname=$pkgName
            pkgver=$appVersion
            pkgrel=1
            pkgdesc="A decentralized bitcoin exchange network"
            arch=('x86_64' 'aarch64')
            url="https://bisq.network"
            license=('$licenseType')
            depends=('java-runtime>=21' 'tor')
            makedepends=('jdk21-openjdk')
            provides=('$pkgName')
            conflicts=('$pkgName')
            options=(!strip)
            
            package() {
                cd "${'$'}{srcdir}"
                
                # Create directories
                install -dm755 "${'$'}{pkgdir}/opt/$pkgName"
                install -dm755 "${'$'}{pkgdir}/usr/bin"
                install -dm755 "${'$'}{pkgdir}/usr/share/applications"
                install -dm755 "${'$'}{pkgdir}/usr/share/pixmaps"
                
                # Copy application files
                cp -r * "${'$'}{pkgdir}/opt/$pkgName/"
                
                # Create launcher script
                cat > "${'$'}{pkgdir}/usr/bin/$pkgName" << EOF
            #!/bin/sh
            exec /opt/$pkgName/bin/$pkgName "${'$'}@"
            EOF
                chmod +x "${'$'}{pkgdir}/usr/bin/$pkgName"
                
                # Desktop file
                cat > "${'$'}{pkgdir}/usr/share/applications/$pkgName.desktop" << EOF
            [Desktop Entry]
            Name=${appName}
            GenericName=Bitcoin Exchange
            Comment=A decentralized bitcoin exchange network
            Exec=$pkgName
            Icon=$pkgName
            Terminal=false
            Type=Application
            Categories=Network;Finance;
            Keywords=bitcoin;exchange;p2p;
            EOF
                
                # Icon
                install -Dm644 "${'$'}{pkgdir}/opt/$pkgName/lib/icon.png" "${'$'}{pkgdir}/usr/share/pixmaps/$pkgName.png"
            }
        """.trimIndent()

        File(aurPackageDir, "PKGBUILD").writeText(pkgbuildContent)
    }

    /**
     * Generates the .SRCINFO file for AUR
     */
    private fun generateSrcInfo(aurPackageDir: File, appName: String, appVersion: String) {
        val pkgName = appName.lowercase().replace(" ", "-")
        val licenseType = "AGPL3"

        val srcInfoContent = """
            pkgbase = $pkgName
            pkgdesc = A decentralized bitcoin exchange network
            pkgver = $appVersion
            pkgrel = 1
            url = https://bisq.network
            arch = x86_64
            arch = aarch64
            license = $licenseType
            depends = java-runtime>=21
            depends = tor
            makedepends = jdk21-openjdk
            provides = $pkgName
            conflicts = $pkgName
            options = !strip
            
            pkgname = $pkgName
        """.trimIndent()

        File(aurPackageDir, ".SRCINFO").writeText(srcInfoContent)
    }
}