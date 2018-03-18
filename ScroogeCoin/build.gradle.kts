group = "org.vieuxchameau"
version = "1.0.0-SNAPSHOT"

apply {
    plugin("java")
}

repositories {
    mavenCentral()
}

dependencies {
    testCompile("org.junit.jupiter", "junit-jupiter-engine", "5.0.3")
    testCompile("org.assertj:assertj-core:3.9.0")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}
