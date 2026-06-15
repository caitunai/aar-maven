import org.gradle.kotlin.dsl.`maven-publish`
import org.gradle.kotlin.dsl.signing

plugins {
    `maven-publish`
    signing
}

group = "com.caitun.ble"
version = "4.2.0_40250"

val artifactIdValue = "jl_bluetooth_rcsp"
val aarFile = layout.projectDirectory.file("libs/jl_bluetooth_rcsp_V4.2.0_40250-release.aar")
val stagingDir = layout.buildDirectory.dir("staging-deploy")
val centralBundle = layout.buildDirectory.file("central-bundle/${artifactIdValue}-${version}-central.zip")

val emptySourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(layout.projectDirectory.file("README-sources.md"))
}

val emptyJavadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    from(layout.projectDirectory.file("README-javadoc.md"))
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = artifactIdValue
            version = project.version.toString()

            artifact(aarFile.asFile) {
                extension = "aar"
            }

            artifact(emptySourcesJar)
            artifact(emptyJavadocJar)

            pom {
                name.set("jl_bluetooth_rcsp Android SDK")
                description.set("Closed-source jl_bluetooth_rcsp AAR SDK.")
                url.set("https://www.caitun.com")

                licenses {
                    license {
                        name.set("Proprietary License")
                        url.set("https://raw.githubusercontent.com/caitunai/aar-maven/refs/heads/main/LICENSE")
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set("caitun")
                        name.set("Caitun Intelligence")
                        email.set("help@caitun.com")
                    }
                }

                scm {
                    url.set("https://github.com/caitunai/aar-maven")
                    connection.set("scm:git:https://github.com/caitunai/aar-maven.git")
                    developerConnection.set("scm:git:https://github.com/caitunai/aar-maven.git")
                }

                // 如果这个 AAR 依赖其他库，需要手动写进 POM
                withXml {
                    val dependenciesNode = asNode().appendNode("dependencies")

                    fun addDependency(
                        groupId: String,
                        artifactId: String,
                        version: String,
                        scope: String = "runtime"
                    ) {
                        val dependencyNode = dependenciesNode.appendNode("dependency")
                        dependencyNode.appendNode("groupId", groupId)
                        dependencyNode.appendNode("artifactId", artifactId)
                        dependencyNode.appendNode("version", version)
                        dependencyNode.appendNode("scope", scope)
                    }
                }
            }
        }
    }

    repositories {
        maven {
            name = "localStaging"
            url = layout.buildDirectory.dir("staging-deploy").get().asFile.toURI()
        }
    }
}

tasks.register<Zip>("zipCentralBundle") {
    group = "publishing"
    description = "Create a Central Portal upload bundle."

    dependsOn("publishReleasePublicationToLocalStagingRepository")

    archiveFileName.set("${artifactIdValue}-${version}-central.zip")
    destinationDirectory.set(layout.buildDirectory.dir("central-bundle"))

    from(stagingDir)
}

tasks.register("uploadCentralBundle") {
    group = "publishing"
    description = "Upload the Central Portal bundle. Default mode is USER_MANAGED."

    dependsOn("zipCentralBundle")

    doLast {
        val username = providers.gradleProperty("mavenCentralUsername").orNull
            ?: error("Missing mavenCentralUsername in ~/.gradle/gradle.properties")

        val password = providers.gradleProperty("mavenCentralPassword").orNull
            ?: error("Missing mavenCentralPassword in ~/.gradle/gradle.properties")

        val publishingType = providers.gradleProperty("centralPublishingType").orNull ?: "USER_MANAGED"
        val deploymentName = providers.gradleProperty("centralDeploymentName").orNull
            ?: "${artifactIdValue}-${version}"

        val bundleFile = centralBundle.get().asFile

        require(bundleFile.exists()) {
            "Central bundle not found: ${bundleFile.absolutePath}"
        }


        val uploadUrl =
            "https://central.sonatype.com/api/v1/publisher/upload" +
                    "?name=$deploymentName" +
                    "&publishingType=$publishingType"

        println("Uploading Central Portal bundle:")
        println("  bundle: ${bundleFile.absolutePath}")
        println("  name: $deploymentName")
        println("  publishingType: $publishingType")

        val process = ProcessBuilder(
            "/usr/bin/curl",
            "--fail-with-body",
            "--request", "POST",
            "--user", "$username:$password",
            "--form", "bundle=@${bundleFile.absolutePath}",
            uploadUrl
        )
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        println(output)

        if (exitCode != 0) {
            error("Central Portal upload failed. curl exit code: $exitCode")
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["release"])
}