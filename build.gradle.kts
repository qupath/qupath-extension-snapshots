plugins {
    // To optionally create a shadow/fat jar that bundle up any non-core dependencies
    id("com.gradleup.shadow") version "8.3.5"
    // QuPath Gradle extension convention plugin
    id("qupath-conventions")
}

qupathExtension {
    name = "qupath-extension-screenshots"
    group = "io.github.qupath"
    version = "0.1.0-SNAPSHOT"
    description = "A simple QuPath extension"
    automaticModule = "io.github.qupath.extension.screenshots"
}

dependencies {

    // Main dependencies for most QuPath extensions
    shadow(libs.bundles.qupath)
    shadow(libs.bundles.logging)
    shadow(libs.qupath.fxtras)

    // If you aren't using Groovy, this can be removed
    shadow(libs.bundles.groovy)

    // For testing
    testImplementation(libs.bundles.qupath)
    testImplementation(libs.junit)

}
