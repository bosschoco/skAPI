plugins {
    java
}

group = "io.github.bosschoco"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.skriptlang.org/releases")
}

dependencies {
    // Paper 26.2 (Gson 은 Paper 에 포함되어 있으므로 따로 넣지 않는다)
    compileOnly("io.papermc.paper:paper-api:26.2.build.133-stable")
    // Skript 2.16.2 = Paper 26.2 를 지원하는 최신 안정 버전
    compileOnly("com.github.SkriptLang:Skript:2.16.2") {
        isTransitive = false
    }

    testImplementation("io.papermc.paper:paper-api:26.2.build.133-stable")
    testImplementation("com.github.SkriptLang:Skript:2.16.2") {
        isTransitive = false
    }
    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveFileName.set("skAPI-${project.version}.jar")
}
