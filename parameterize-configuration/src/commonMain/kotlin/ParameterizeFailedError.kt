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

import com.benwoodworth.parameterize.ParameterizeConfiguration.Builder
import com.benwoodworth.parameterize.ParameterizeConfiguration.OnCompleteScope

/**
 * Thrown to indicate that [parameterize] has completed with failures.
 *
 * The [message] summarizes the number of failures and total iterations. A plus after the total indicates that
 * [completedEarly][OnCompleteScope.completedEarly] was true in [onComplete][Builder.onComplete].
 *
 * The [suppressedExceptions] include [recordedFailures][OnCompleteScope.recordedFailures] from the
 * [onComplete][Builder.onComplete] handler, with each being decorated with a message to include a list of the
 * [arguments][ParameterizeFailure.arguments] that caused it.
 *
 * Can only be constructed from [onComplete][Builder.onComplete].
 */
public expect class ParameterizeFailedError : AssertionError {
    /** @suppress */
    public companion object {
        internal operator fun invoke(
            recordedFailures: List<ParameterizeFailure>,
            successCount: Long,
            failureCount: Long,
            completedEarly: Boolean
        ): ParameterizeFailedError
    }

    internal val recordedFailures: List<ParameterizeFailure>
    internal val successCount: Long
    internal val failureCount: Long
    internal val completedEarly: Boolean

    /** @suppress */
    override val message: String
}

internal expect class Failure : AssertionError {
    companion object {
        operator fun invoke(failure: ParameterizeFailure): Failure
    }

    internal val failure: ParameterizeFailure

    override val message: String
    override val cause: Throwable
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun ParameterizeFailedError.Companion.commonShouldCaptureStackTrace(
    recordedFailures: List<ParameterizeFailure>
): Boolean {
    return recordedFailures.isEmpty()
}

internal fun ParameterizeFailedError.commonInit() {
    recordedFailures.forEach { failure ->
        addSuppressed(Failure(failure))
    }
}

internal inline val ParameterizeFailedError.commonMessage
    get() = buildString {
        append("Failed ")
        append(failureCount)
        append('/')
        append(successCount + failureCount)

        if (completedEarly) {
            append('+')
        }

        append(" cases")

        if (recordedFailures.isNotEmpty()) {
            recordedFailures.forEach { failure ->
                append("\n\t")
                append(failure.failure::class.simpleName)
                append(": ")

                val message = failure.failure.message?.trim()
                if (message.isNullOrBlank()) {
                    append("<no message>")
                } else {
                    val firstNewLine = message.indexOfFirst { it == '\n' || it == '\r' }

                    if (firstNewLine == -1) {
                        append(message)
                    } else {
                        append(message, 0, firstNewLine)
                        append(" ...")
                    }
                }

                failure.parameters.forEach { parameter ->
                    append("\n\t\t")
                    append(parameter.argument)
                }
            }

            if (recordedFailures.size < failureCount) {
                append("\n\t...")
            }
        }
    }

internal inline val Failure.commonMessage: String
    get() = when (failure.parameters.size) {
        0 -> "Failed with no arguments"

        1 -> failure.parameters.single().let { argument ->
            "Failed with argument:\n\t\t$argument"
        }

        else -> failure.parameters.joinToString(
            prefix = "Failed with arguments:\n\t\t",
            separator = "\n\t\t"
        )
    }

internal inline val Failure.commonCause: Throwable
    get() = failure.failure
