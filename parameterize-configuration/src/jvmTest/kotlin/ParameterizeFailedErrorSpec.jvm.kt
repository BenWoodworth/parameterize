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

package com.benwoodworth.parameterize

import com.benwoodworth.parameterize.test.testAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.opentest4j.MultipleFailuresError
import java.lang.reflect.Modifier
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.fail

class ParameterizeFailedErrorSpecJvm {
    @Test
    fun get_failures_should_be_the_undecorated_recorded_failures() {
        val recordedFailures = (1..5).map {
            ParameterizeFailure(Throwable("Failure $it"), listOf())
        }

        val error: MultipleFailuresError = ParameterizeFailedError(
            recordedFailures,
            successCount = 0,
            failureCount = recordedFailures.size.toLong(),
            completedEarly = false
        )

        val expectedFailures = recordedFailures.map { it.failure }

        assertEquals(expectedFailures, error.failures)
    }

    @Test
    fun has_failures_should_be_correct() = testAll(
        "empty" to emptyList(),
        "non-empty" to listOf(Throwable("Failure"))
    ) { failures ->
        val recordedFailures = failures
            .map { ParameterizeFailure(it, emptyList()) }

        val error: MultipleFailuresError = ParameterizeFailedError(
            recordedFailures,
            successCount = 0,
            failureCount = recordedFailures.size.toLong(),
            completedEarly = false
        )

        assertEquals(failures.isNotEmpty(), error.hasFailures())
    }

    /**
     * [MultipleFailuresError] only exists for test tooling so the failures can be listed nicely. The API it provides
     * isn't desirable though (since this library could provide a richer list of failures), so anything inherited from
     * it should be suppressed.
     */
    @Test
    fun methods_inherited_from_MultipleFailuresError_should_be_hidden_from_the_API() {
        fun KClass<*>.inheritableMethods(): List<String> =
            java.methods
                .filter { Modifier.isPublic(it.modifiers) }
                .filterNot { Modifier.isStatic(it.modifiers) }
                .map { it.name }

        val methodsFromMultipleFailuresError =
            MultipleFailuresError::class.inheritableMethods() - AssertionError::class.inheritableMethods().toSet()

        testAll(
            methodsFromMultipleFailuresError.map { it to it }
        ) { method ->
            val deprecatedAnnotation = ParameterizeFailedError::class.java
                .getMethod(method)
                .annotations
                .filterIsInstance<Deprecated>()
                .firstOrNull()
                ?: fail("No @Deprecated annotation")

            val expectedMessage = "Exists for ${MultipleFailuresError::class.simpleName} tooling, and is not API"
            assertEquals(expectedMessage, deprecatedAnnotation.message, "message")

            assertEquals(DeprecationLevel.HIDDEN, deprecatedAnnotation.level, "level")
        }
    }
}
