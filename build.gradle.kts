plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.23"
    id("org.jetbrains.intellij") version "1.17.3"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.23"
}

group = "cn.suso"
version = "1.0.1"

repositories {
    //mavenCentral()
    maven {
        url = uri("https://maven.aliyun.com/repository/public")
    }
}

dependencies {
    // Ktor client for HTTP requests
    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-cio:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    implementation("io.ktor:ktor-client-logging:2.3.7")
    
    // Kotlinx serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.14")
    
    // Test dependencies
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.2")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.2")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.9.2")
    testImplementation("org.mockito:mockito-core:5.1.1")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.1.0")
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    localPath.set("D:\\application\\tool\\IntelliJ IDEA 2024.1.2")
    // version.set("2023.2.6")
    // type.set("IC") // Target IDE Platform
    
    // 禁用下载源码和文档以避免网络问题
    downloadSources.set(false)
    
    // 禁用版本检查以避免网络问题
    updateSinceUntilBuild.set(false)
    
    plugins.set(listOf("com.intellij.java", "org.jetbrains.kotlin"))
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }
    
    test {
        useJUnitPlatform()
    }

    patchPluginXml {
        sinceBuild.set("241")
        // 明确设置 untilBuild 为空，支持所有未来版本
        untilBuild.set("")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
    
    register<JavaExec>("runLoggingVerification") {
        group = "verification"
        description = "Run logging service verification"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("cn.suso.aicodetransformer.LoggingVerificationKt")
    }
    
    register<JavaExec>("runLocalModelVerification") {
        group = "verification"
        description = "Run local model functionality verification"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("cn.suso.aicodetransformer.LocalModelVerificationKt")
    }
    
    register<JavaExec>("runSerializationTest") {
        group = "verification"
        description = "运行序列化测试"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("LocalModelSerializationTestKt")
    }
    

}
