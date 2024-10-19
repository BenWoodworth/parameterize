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

import com.benwoodworth.parameterize.ParameterizeScope.DeclaredParameter
import com.benwoodworth.parameterize.test.NativeIgnore
import com.benwoodworth.parameterize.test.WasmJsIgnore
import com.benwoodworth.parameterize.test.WasmWasiIgnore
import com.benwoodworth.parameterize.test.stackTraceLines
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ParameterizeFailedErrorSpec {
    private val arguments = buildList {
        parameterize {
            // Intercept constructed ParameterDelegates so they can be used in failures
            val propertyA by ReadOnlyProperty { _, property ->
                parameterOf("argumentA").provideDelegate(null, property)
            }
            val propertyB by ReadOnlyProperty { _, property ->
                parameterOf("argumentA").provideDelegate(null, property)
            }

            add(propertyA.argument)
            add(propertyB.argument)
        }
    }

    private val argumentA = arguments[0]
    private val argumentB = arguments[1]

    @Test
    fun message_should_have_failure_count_and_total() {
        val error = ParameterizeFailedError(
            emptyList(),
            successCount = 4,
            failureCount = 3,
            completedEarly = false
        )

        assertEquals("Failed 3/7 cases", error.message)
    }

    @Test
    fun message_total_when_completed_early_should_have_a_plus_after_the_total() {
        val error = ParameterizeFailedError(
            emptyList(),
            successCount = 4,
            failureCount = 3,
            completedEarly = true
        )

        assertEquals("Failed 3/7+ cases", error.message)
    }

    @Test
    fun message_with_recorded_failures_should_have_colon_and_indented_list_of_failures() {
        val failures = listOf(
            IllegalStateException("Failure 0"),
            AssertionError("Failure 1"),
            IllegalArgumentException("Failure 2")
        )

        val error = ParameterizeFailedError(
            failures.map { ParameterizeFailure(it, emptyList()) },
            successCount = 4,
            failureCount = 3,
            completedEarly = false
        )

        // Tabs for indentation, since that's how the stack traces print.
        // Simple name, since the fully qualified name will be shown in the suppressed failures
        val expectedMessage = """
            Failed 3/7 cases
            ${'\t'}${failures[0]::class.simpleName}: Failure 0
            ${'\t'}${failures[1]::class.simpleName}: Failure 1
            ${'\t'}${failures[2]::class.simpleName}: Failure 2
        """.trimIndent()

        assertEquals(expectedMessage, error.message)
    }

    @Test
    fun message_failures_list_should_display_no_message_for_blank_messages() {
        class NullMessage : Throwable(message = null)
        class EmptyMessage : Throwable(message = "")
        class BlankMessage : Throwable(message = "   ")

        val failures = listOf(NullMessage(), EmptyMessage(), BlankMessage())

        val error = ParameterizeFailedError(
            failures.map { ParameterizeFailure(it, emptyList()) },
            successCount = 4,
            failureCount = 3,
            completedEarly = false
        )

        // Inspired by org.opentest4j.MultipleFailuresError's use of "<no message>"
        val expectedMessage = """
            Failed 3/7 cases
            ${'\t'}NullMessage: <no message>
            ${'\t'}EmptyMessage: <no message>
            ${'\t'}BlankMessage: <no message>
        """.trimIndent()

        assertEquals(expectedMessage, error.message)
    }

    @Test
    fun message_failures_list_should_display_multiline_messages_as_the_first_line_with_an_ellipsis() {
        val failures = listOf(
            Throwable("Multiline with \\n" + "\nSecond line"),
            Throwable("Multiline with \\r\\n" + "\r\nSecond line"),
            Throwable("  \nBlank first line" + "\nThird line"),
        )

        val error = ParameterizeFailedError(
            failures.map { ParameterizeFailure(it, emptyList()) },
            successCount = 4,
            failureCount = 3,
            completedEarly = false
        )

        // Simple name, since the fully qualified name will be shown in the suppressed failures
        val expectedMessage = """
            Failed 3/7 cases
            ${'\t'}Throwable: Multiline with \n ...
            ${'\t'}Throwable: Multiline with \r\n ...
            ${'\t'}Throwable: Blank first line ...
        """.trimIndent()

        assertEquals(expectedMessage, error.message)
    }

    @Test
    fun message_failures_list_should_trim_the_failure_messages() {
        val failures = listOf(
            Throwable("   \n   Surrounding spaces and blank lines   \n   ")
        )

        val error = ParameterizeFailedError(
            failures.map { ParameterizeFailure(it, emptyList()) },
            successCount = 6,
            failureCount = 1,
            completedEarly = false
        )

        // Simple name, since the fully qualified name will be shown in the suppressed failures
        val expectedMessage = """
            Failed 1/7 cases
            ${'\t'}Throwable: Surrounding spaces and blank lines
        """.trimIndent()

        assertEquals(expectedMessage, error.message)
    }

    @Test
    fun message_failure_list_should_end_with_an_ellipsis_if_not_all_failures_were_recorded() {
        val error = ParameterizeFailedError(
            List(2) { i -> ParameterizeFailure(Throwable("Failure $i"), emptyList()) },
            successCount = 4,
            failureCount = 3,
            completedEarly = false
        )

        val expectedMessage = """
            Failed 3/7 cases
            ${'\t'}Throwable: Failure 0
            ${'\t'}Throwable: Failure 1
            ${'\t'}...
        """.trimIndent()

        assertEquals(expectedMessage, error.message)
    }

    @Test
    fun message_failure_arguments_list_after_failure_message() {
        val failures = listOf(
            IllegalStateException("Failure 0"),
            AssertionError("Failure 1"),
            IllegalArgumentException("Failure 2")
        )

        val error = ParameterizeFailedError(
            failures.mapIndexed { index, it -> ParameterizeFailure(it, arguments.take(index)) },
            successCount = 4,
            failureCount = 3,
            completedEarly = false
        )

        // Tabs for indentation, since that's how the stack traces print.
        // Simple name, since the fully qualified name will be shown in the suppressed failures
        val expectedMessage = """
            Failed 3/7 cases
            ${'\t'}${failures[0]::class.simpleName}: Failure 0
            ${'\t'}${failures[1]::class.simpleName}: Failure 1
            ${'\t'}${'\t'}${arguments[0]}
            ${'\t'}${failures[2]::class.simpleName}: Failure 2
            ${'\t'}${'\t'}${arguments[0]}
            ${'\t'}${'\t'}${arguments[1]}
        """.trimIndent()

        assertEquals(expectedMessage, error.message)
    }

    @Test
    fun recorded_failures_should_be_added_to_suppressed_exceptions_as_the_cause_in_an_augmented_failure() {
        val failures = List(20) { i -> Throwable("Failure $i") }

        val error = ParameterizeFailedError(
            failures.map { ParameterizeFailure(it, arguments) },
            successCount = 4,
            failureCount = 3,
            completedEarly = false
        )

        val actualSuppressedCauses = error.suppressedExceptions
            .map { it.cause }

        assertEquals(failures, actualSuppressedCauses, "Suppressed causes")
    }

    @Test
    fun recorded_failure_message_with_no_arguments_should_say_failed_with_no_arguments() {
        val error = ParameterizeFailedError(
            listOf(ParameterizeFailure(Throwable(), emptyList())),
            successCount = 0,
            failureCount = 1,
            completedEarly = false
        )

        val augmentedFailure = error.suppressedExceptions.single()

        assertEquals("Failed with no arguments", augmentedFailure.message)
    }

    @Test
    fun recorded_failure_message_with_one_used_parameter_should_list_its_argument() {
        val error = ParameterizeFailedError(
            listOf(ParameterizeFailure(Throwable(), arguments.take(1))),
            successCount = 0,
            failureCount = 1,
            completedEarly = false
        )

        val augmentedFailure = error.suppressedExceptions.single()

        // Double indent, since the suppressed exceptions get an extra level of indentation
        val expectedMessage = """
            Failed with argument:
            ${"\t\t"}$argumentA
        """.trimIndent()

        assertEquals(expectedMessage, augmentedFailure.message)
    }

    @Test
    fun recorded_failure_message_with_two_used_parameters_should_list_their_arguments() {
        val error = ParameterizeFailedError(
            listOf(ParameterizeFailure(Throwable(), arguments)),
            successCount = 0,
            failureCount = 1,
            completedEarly = false
        )

        val augmentedFailure = error.suppressedExceptions.single()

        val expectedMessage = """
            Failed with arguments:
            ${"\t\t"}$argumentA
            ${"\t\t"}$argumentB
        """.trimIndent()

        assertEquals(expectedMessage, augmentedFailure.message)
    }

    @Test
    @[NativeIgnore WasmJsIgnore WasmWasiIgnore]
    fun stack_trace_without_recorded_failures_should_be_captured() {
        val error = ParameterizeFailedError(
            listOf(),
            successCount = 0,
            failureCount = 1,
            completedEarly = false
        )

        assertNotEquals(emptyList(), error.stackTraceLines)
    }

    @Test
    @[NativeIgnore WasmJsIgnore WasmWasiIgnore]
    fun stack_trace_with_recorded_failures_should_not_be_captured() {
        val failure = Throwable("Failure message")

        val error = ParameterizeFailedError(
            listOf(ParameterizeFailure(failure, arguments)),
            successCount = 0,
            failureCount = 1,
            completedEarly = false
        )

        assertEquals(emptyList(), error.stackTraceLines)
    }

    @Test
    @[NativeIgnore WasmJsIgnore WasmWasiIgnore]
    fun augmented_failure_stack_trace_should_not_be_captured() {
        val failure = Throwable("Failure message")

        val error = ParameterizeFailedError(
            listOf(ParameterizeFailure(failure, arguments)),
            successCount = 0,
            failureCount = 1,
            completedEarly = false
        )

        assertEquals(emptyList(), error.suppressedExceptions.single().stackTraceLines)
    }
}
