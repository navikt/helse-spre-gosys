val ktorVersion = "1.3.1"

group = "no.nav.helse"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":pdf"))

    implementation("com.github.navikt:rapids-and-rivers:1.47c31b4")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")

    testImplementation("io.ktor:ktor-client-mock-jvm:$ktorVersion")
    testImplementation("io.mockk:mockk:1.9.3")
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("app")

    manifest {
        attributes["Main-Class"] = "no.nav.helse.AppKt"
        attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(separator = " ") {
            it.name
        }
    }

    doLast {
        configurations.runtimeClasspath.get().forEach {
            val file = File("$buildDir/libs/${it.name}")
            if (!file.exists())
                it.copyTo(file)
        }
    }
}
