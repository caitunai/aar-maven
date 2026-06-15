Publish AAR to MAVEN
-----

## Add AAR wrapper

### Step 1
copy an exist wrapper to extend.

```
cp -R ./aar-wrapper/jldecryption ./aar-wrapper/yourpackage
```

### Step 2

edit the `build.gradle.kts`, and copy code as below:
```kotlin
import org.gradle.api.tasks.bundling.Zip
import java.io.File

plugins {
    `maven-publish`
    signing
}

/**
 * ============================================================
 * Basic Maven artifact configuration.
 * Usually, you only need to update this section when releasing
 * a new version.
 * ============================================================
 */

// Maven coordinates
val mavenGroupId = "com.caitun.ble"
val mavenArtifactId = "jldecryption"
val mavenVersion = "0.4"

// AAR file configuration.
// Default expected path:
// libs/jldecryption_v0.4-release.aar
val aarFileName = "jldecryption_v${mavenVersion}-release.aar"
val aarFile = layout.projectDirectory.file("libs/$aarFileName")

// POM metadata
val pomNameValue = "jldecryption Android SDK"
val pomDescriptionValue = "Closed-source jldecryption AAR SDK."
val pomUrlValue = "https://www.caitun.com"

// License metadata
val pomLicenseNameValue = "Proprietary License"
val pomLicenseUrlValue = "https://www.caitun.com/license"

// Developer metadata
val pomDeveloperIdValue = "caitun"
val pomDeveloperNameValue = "Caitun Intelligence"
val pomDeveloperEmailValue = "help@caitun.com"

// SCM metadata.
// Maven Central requires SCM information even for closed-source artifacts.
// The URL does not have to point to a public repository.
val pomScmUrlValue = "https://github.com/caitunai/aar-maven"
val pomScmConnectionValue = "scm:git:https://github.com/caitunai/aar-maven.git"
val pomScmDeveloperConnectionValue = "scm:git:https://github.com/caitunai/aar-maven.git"

/**
 * Declare Maven dependencies required by this AAR.
 *
 * Most AAR wrapper artifacts should use runtime scope.
 * Use compile scope only when consumers need the dependency at compile time.
 *
 * Example:
 * MavenDependency("androidx.core", "core-ktx", "1.13.1", "runtime")
 */
data class MavenDependency(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val scope: String = "runtime"
)

val pomDependencies = listOf<MavenDependency>(
    // MavenDependency("androidx.core", "core-ktx", "1.13.1"),
    // MavenDependency("com.squareup.okhttp3", "okhttp", "4.12.0"),
)

/**
 * ============================================================
 * Internal publishing configuration.
 * Normally, you do not need to modify anything below this line.
 * ============================================================
 */

group = mavenGroupId
version = mavenVersion

val stagingDir = layout.buildDirectory.dir("staging-deploy")
val centralBundle = layout.buildDirectory.file(
    "central-bundle/${mavenArtifactId}-${mavenVersion}-central.zip"
)

val generatedPlaceholderDir = layout.buildDirectory.dir("generated/maven-placeholder")

tasks.register("generateMavenPlaceholders") {
    doLast {
        val dir = generatedPlaceholderDir.get().asFile
        dir.mkdirs()

        File(dir, "README-sources.md").writeText(
            "This is a closed-source binary artifact. Source code is not publicly distributed.\n"
        )

        File(dir, "README-javadoc.md").writeText(
            "This is a closed-source binary artifact. Public API documentation is provided separately.\n"
        )
    }
}

/**
 * Validate that the configured AAR file exists before publishing.
 */
tasks.register("checkAarFile") {
    doLast {
        val file = aarFile.asFile
        require(file.exists()) {
            """
            AAR file not found:
              ${file.absolutePath}

            Please check:
              aarFileName = "$aarFileName"
              expected path = libs/$aarFileName
            """.trimIndent()
        }
    }
}

/**
 * Empty sources.jar for Maven Central.
 * This does not contain source code.
 */
val emptySourcesJar by tasks.registering(Jar::class) {
    dependsOn("generateMavenPlaceholders")
    archiveClassifier.set("sources")
    from(generatedPlaceholderDir.map { it.file("README-sources.md") })
}

/**
 * Empty javadoc.jar for Maven Central.
 */
val emptyJavadocJar by tasks.registering(Jar::class) {
    dependsOn("generateMavenPlaceholders")
    archiveClassifier.set("javadoc")
    from(generatedPlaceholderDir.map { it.file("README-javadoc.md") })
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = mavenGroupId
            artifactId = mavenArtifactId
            version = mavenVersion

            artifact(aarFile.asFile) {
                extension = "aar"
                builtBy("checkAarFile")
            }

            artifact(emptySourcesJar)
            artifact(emptyJavadocJar)

            pom {
                name.set(pomNameValue)
                description.set(pomDescriptionValue)
                url.set(pomUrlValue)

                licenses {
                    license {
                        name.set(pomLicenseNameValue)
                        url.set(pomLicenseUrlValue)
                        distribution.set("repo")
                    }
                }

                developers {
                    developer {
                        id.set(pomDeveloperIdValue)
                        name.set(pomDeveloperNameValue)
                        email.set(pomDeveloperEmailValue)
                    }
                }

                scm {
                    url.set(pomScmUrlValue)
                    connection.set(pomScmConnectionValue)
                    developerConnection.set(pomScmDeveloperConnectionValue)
                }

                withXml {
                    if (pomDependencies.isNotEmpty()) {
                        val dependenciesNode = asNode().appendNode("dependencies")

                        pomDependencies.forEach { dep ->
                            val dependencyNode = dependenciesNode.appendNode("dependency")
                            dependencyNode.appendNode("groupId", dep.groupId)
                            dependencyNode.appendNode("artifactId", dep.artifactId)
                            dependencyNode.appendNode("version", dep.version)
                            dependencyNode.appendNode("scope", dep.scope)
                        }
                    }
                }
            }
        }
    }

    repositories {
        maven {
            name = "localStaging"
            url = stagingDir.get().asFile.toURI()
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["release"])
}

/**
 * Create a Central Portal upload bundle.
 */
tasks.register<Zip>("zipCentralBundle") {
    group = "publishing"
    description = "Create a Central Portal upload bundle."

    dependsOn("publishReleasePublicationToLocalStagingRepository")

    archiveFileName.set("${mavenArtifactId}-${mavenVersion}-central.zip")
    destinationDirectory.set(layout.buildDirectory.dir("central-bundle"))

    from(stagingDir)
}

/**
 * Print the generated Central Portal bundle path.
 */
tasks.register("printCentralBundle") {
    group = "publishing"
    description = "Print Central Portal bundle path."

    dependsOn("zipCentralBundle")

    doLast {
        val bundleFile = centralBundle.get().asFile

        println("Central bundle:")
        println("  ${bundleFile.absolutePath}")
        println()
        println("Check with:")
        println("  unzip -l ${bundleFile.absolutePath} | head -80")
    }
}

/**
 * Upload the Central Portal bundle.
 *
 * Default publishing type is USER_MANAGED.
 *
 * USER_MANAGED:
 * Upload the bundle and manually publish it in Central Portal.
 *
 * AUTOMATIC:
 * Upload and publish automatically after validation succeeds.
 */
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
            ?: "${mavenArtifactId}-${mavenVersion}"

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
```

Then update the information and configuration of your package.

## Artifact Coordinates

Current Maven coordinates:

```kotlin
com.caitun.ble:jldecryption:0.4
```

The main publishing configuration is located in `build.gradle.kts`.

When releasing a new version, usually only the following values need to be updated:

```kotlin
val mavenGroupId = "com.caitun.ble"
val mavenArtifactId = "jldecryption"
val mavenVersion = "0.4"

val aarFileName = "jldecryption_v${mavenVersion}-release.aar"
```

The expected AAR file location is:

```text
libs/jldecryption_v0.4-release.aar
```

For version `0.5`, the expected file path becomes:

```text
libs/jldecryption_v0.5-release.aar
```

## Local Gradle Credentials

Maven Central credentials and GPG signing configuration should be stored in:

```text
~/.gradle/gradle.properties
```

Example:

```properties
mavenCentralUsername=YOUR_CENTRAL_PORTAL_TOKEN_USERNAME
mavenCentralPassword=YOUR_CENTRAL_PORTAL_TOKEN_PASSWORD

signing.gnupg.executable=/opt/homebrew/bin/gpg
signing.gnupg.useLegacyGpg=false
signing.gnupg.keyName=YOUR_GPG_KEY_ID
```

For Intel-based macOS machines, the GPG path may be:

```properties
signing.gnupg.executable=/usr/local/bin/gpg
```

Do not commit credentials or GPG private keys to Git.

## Build and Publish Tasks

### Publish to Local Staging Directory

```bash
./gradlew :aar-wrapper:jldecryption:publishReleasePublicationToLocalStagingRepository
```

The generated Maven repository layout will be located at:

```text
aar-wrapper/jldecryption/build/staging-deploy/
```

### Create Central Portal Bundle

```bash
./gradlew :aar-wrapper:jldecryption:zipCentralBundle
```

The generated bundle will be located at:

```text
aar-wrapper/jldecryption/build/central-bundle/jldecryption-0.4-central.zip
```

### Print Bundle Path

```bash
./gradlew :aar-wrapper:jldecryption:printCentralBundle
```

### Check Bundle Contents

```bash
unzip -l aar-wrapper/jldecryption/build/central-bundle/jldecryption-0.4-central.zip | head -80
```

The bundle should contain files similar to:

```text
com/caitun/ble/jldecryption/0.4/jldecryption-0.4.aar
com/caitun/ble/jldecryption/0.4/jldecryption-0.4.aar.asc
com/caitun/ble/jldecryption/0.4/jldecryption-0.4.pom
com/caitun/ble/jldecryption/0.4/jldecryption-0.4.pom.asc
com/caitun/ble/jldecryption/0.4/jldecryption-0.4-sources.jar
com/caitun/ble/jldecryption/0.4/jldecryption-0.4-sources.jar.asc
com/caitun/ble/jldecryption/0.4/jldecryption-0.4-javadoc.jar
com/caitun/ble/jldecryption/0.4/jldecryption-0.4-javadoc.jar.asc
```

## Upload to Maven Central

### Upload and Manually Publish

This mode uploads the bundle to Central Portal. You still need to manually publish it in the Central Portal UI.

```bash
./gradlew :aar-wrapper:jldecryption:uploadCentralBundle -PcentralPublishingType=USER_MANAGED
```

### Upload and Automatically Publish

This mode uploads the bundle and publishes it automatically after validation succeeds.

```bash
./gradlew :aar-wrapper:jldecryption:uploadCentralBundle -PcentralPublishingType=AUTOMATIC
```

### Custom Deployment Name

```bash
./gradlew :aar-wrapper:jldecryption:uploadCentralBundle \
  -PcentralDeploymentName=jldecryption-0.4 \
  -PcentralPublishingType=USER_MANAGED
```

## Consumer Usage

After the artifact is published and synchronized to Maven Central, consumers can use it as follows:

```kotlin
repositories {
    google()
    mavenCentral()
}

dependencies {
    implementation("com.caitun.ble:jldecryption:0.4")
}
```

## Adding POM Dependencies

If the AAR depends on other Maven artifacts, add them to the `pomDependencies` list in `build.gradle.kts`.

Example:

```kotlin
val pomDependencies = listOf(
    MavenDependency("androidx.core", "core-ktx", "1.13.1"),
    MavenDependency("com.squareup.okhttp3", "okhttp", "4.12.0")
)
```

Most AAR wrapper dependencies should use `runtime` scope. Use `compile` scope only when consumers need the dependency directly at compile time.

## Release Checklist

Before publishing a new version:

1. Update `mavenVersion`.
2. Place the matching AAR file under `libs/`.
3. Run `zipCentralBundle`.
4. Inspect the generated zip file.
5. Run `uploadCentralBundle`.
6. Check the deployment result in Central Portal.
7. Publish manually if using `USER_MANAGED`.

## Notes

Maven Central does not allow overwriting an already published version. If a release has already been published, use a new version number for the next release.
