import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val junitJupiterVersion = "5.5.2"

plugins {
    kotlin("jvm") version "1.3.61"
}
allprojects {
    group = "no.nav.helse"

    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        jcenter()
        maven { url = uri("https://jitpack.io") }
    }

    dependencies {
        implementation(kotlin("stdlib-jdk8"))

        testImplementation("org.junit.jupiter:junit-jupiter-api:$junitJupiterVersion")
        testImplementation("org.junit.jupiter:junit-jupiter-params:$junitJupiterVersion")
        testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitJupiterVersion")
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_12
        targetCompatibility = JavaVersion.VERSION_12
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "1.8"
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }
}


