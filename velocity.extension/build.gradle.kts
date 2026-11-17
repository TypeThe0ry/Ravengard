import java.net.URI
import java.security.MessageDigest
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    application
    `maven-publish`
    alias(libs.plugins.shadow)
    id("org.jetbrains.gradle.plugin.idea-ext") version "1.4.1"
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots/")
    maven("https://repo.viaversion.com")

    maven { url = uri("https://repo.codemc.io/repository/maven-releases/") }
    maven { url = uri("https://repo.codemc.io/repository/maven-snapshots/") }
}

val velocityProxy = layout.buildDirectory.file("velocity/velocity-4.1.0-SNAPSHOT-9.jar")

val fetchVelocityProxy = tasks.register("fetchVelocityProxy") {
    val target = velocityProxy
    val expected = "635ffe27b4fe1b97e61479012121d4e7c61a9eec99e6bd5a1f923053c2a259ce"
    outputs.file(target)
    doLast {
        val file = target.get().asFile
        val matches = file.isFile && MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            .joinToString("") { "%02x".format(it) } == expected
        if (!matches) {
            file.parentFile.mkdirs()
            URI.create("https://fill-data.papermc.io/v1/objects/$expected/velocity-4.1.0-SNAPSHOT-9.jar")
                .toURL().openStream().use { input -> file.outputStream().use { input.copyTo(it) } }
            val actual = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }
            check(actual == expected) { "The downloaded Velocity proxy does not match its checksum" }
        }
    }
}

tasks.withType<JavaCompile>().configureEach { dependsOn(fetchVelocityProxy) }

dependencies {
    compileOnly(libs.velocity.api)
    testImplementation(libs.velocity.api)
    annotationProcessor(libs.velocity.api)
    compileOnly(files(velocityProxy))

    implementation(libs.atlas.redis)
    implementation(libs.jedis)
    implementation(project(":commons"))
    implementation(project(":proxy.api"))
    implementation(libs.mongodb.bson)
    implementation(libs.mongodb.driver.sync)

    implementation(libs.viabackwards.common)
    implementation(libs.viarewind.common)
    implementation(libs.viaversion)

    implementation(libs.packetevents.velocity)

    implementation(platform(libs.netty.bom))
    implementation(libs.netty.buffer)
    implementation(libs.netty.codec)
    implementation(libs.netty.codec.http)
    implementation(libs.netty.transport)
    implementation(libs.netty.handler)

    implementation(libs.byte.buddy)
    implementation(libs.byte.buddy.agent)
    implementation(libs.tinylog.api)
    implementation(libs.tinylog.impl)
}

evaluationDependsOn(":commons")

application {
    mainClass.set("net.swofty.velocity.SkyBlockVelocity")
}

tasks.withType<ShadowJar> {
    manifest {
        attributes["Main-Class"] = "net.swofty.velocity.SkyBlockVelocity"
    }
    archiveBaseName.set("SkyBlockProxy")
    archiveClassifier.set("")
    archiveVersion.set("")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifact(tasks["shadowJar"])
        }
    }
}
