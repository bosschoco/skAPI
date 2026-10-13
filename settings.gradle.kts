plugins {
    // JDK 25 가 없으면 Gradle 이 자동으로 내려받도록 한다 (Paper 26.x 는 Java 25 필요)
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "skAPI"
