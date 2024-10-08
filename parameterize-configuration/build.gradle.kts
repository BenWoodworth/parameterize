/*
 * Copyright 2024 Ben Woodworth
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.jetbrains.dokka.gradle.DokkaTask

plugins {
    id("kotlin-multiplatform-conventions")
    id("dokka-conventions")
    id("binary-compatibility-validator-conventions")
    id("publishing-conventions")
    id("ci-conventions")
}

repositories {
    mavenCentral()
}

kotlin {
    sourceSets {
        configureEach {
            languageSettings {
                optIn("com.benwoodworth.parameterize.internal.ParameterizeApiFriendModuleApi")
            }
        }

        val commonMain by getting {
            dependencies {
                api(project(":parameterize-core"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(libs.opentest4j)
            }
        }
    }
}

tasks.withType<DokkaTask>().configureEach {
    doLast {
        layout.buildDirectory.asFileTree.asSequence()
            .filter { it.isFile && it.extension == "html" }
            .forEach { file ->
                file.readText()
                    // Remove "ParameterizeConfiguration." prefix from link text for *Scope classes
                    .replace(Regex("""(?<=>)ParameterizeConfiguration\.(?=\w+Scope</a>)"""), "")
                    .let { file.writeText(it) }
            }
    }
}
