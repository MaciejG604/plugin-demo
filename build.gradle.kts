import com.google.protobuf.gradle.*
import org.gradle.internal.os.OperatingSystem

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij") version "1.17.4"
    id("com.google.protobuf") version "0.9.4"
}

group = "com.virtuslab"
version = "3.3-SNAPSHOT"

val grpcVersion = "1.48.1"
val grpcKotlinVersion = "1.3.0"
val protobufVersion = "3.21.5"
val coroutinesVersion = "1.6.4"
val nettyVersion = "4.1.79.Final"

repositories {
    mavenCentral()
}

intellij {
    version.set("2023.3.8")
    type.set("IU")
    plugins.set(listOf(
        "intellij.indexing.shared:233.13135.65",
        "intellij.indexing.shared.core"
    ))
}

dependencies {
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    implementation("io.grpc:grpc-netty:$grpcVersion")
    implementation("io.grpc:grpc-kotlin-stub:$grpcKotlinVersion")
//    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    runtimeOnly("io.grpc:grpc-netty:$grpcVersion")

    implementation("io.netty:netty-transport-native-epoll:$nettyVersion")
    implementation("io.netty:netty-transport-native-kqueue:$nettyVersion")

    implementation("com.beust:jcommander:1.82")

    implementation("io.netty:netty-transport-native-kqueue:$nettyVersion:osx-aarch_64")


//    val arch = System.getProperty("os.arch")
//    val is86_64 = setOf("x86_64", "amd64", "x64", "x86-64").contains(arch)
//    val isArm64 = arch == "arm64"
//    if (OperatingSystem.current().isLinux) {
//        if (is86_64) {
//            implementation("io.netty:netty-transport-native-epoll:$nettyVersion:linux-x86_64")
//        } else if (isArm64) {
//            implementation("io.netty:netty-transport-native-epoll:$nettyVersion:linux-aarch_64")
//        }
//    } else if (OperatingSystem.current().isMacOsX) {
//        if (is86_64) {
//            implementation("io.netty:netty-transport-native-kqueue:$nettyVersion:osx-x86_64")
//        } else if (isArm64) {
//            implementation("io.netty:netty-transport-native-kqueue:$nettyVersion:osx-aarch_64")
//        }
//    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:$grpcKotlinVersion:jdk8@jar"
        }
    }
    generateProtoTasks {
        ofSourceSet("main").forEach {
            it.plugins {
                id("grpc")
                id("grpckt")
            }
        }
    }
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

    processResources {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    patchPluginXml {
        sinceBuild.set("233")
        untilBuild.set("242.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
