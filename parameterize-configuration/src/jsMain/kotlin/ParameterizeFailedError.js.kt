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

public actual class ParameterizeFailedError private constructor(
    internal actual val recordedFailures: List<ParameterizeFailure>,
    internal actual val successCount: Long,
    internal actual val failureCount: Long,
    internal actual val completedEarly: Boolean
) : AssertionError() {
    public actual companion object {
        internal actual operator fun invoke(
            recordedFailures: List<ParameterizeFailure>,
            successCount: Long,
            failureCount: Long,
            completedEarly: Boolean
        ): ParameterizeFailedError {
            return if (commonShouldCaptureStackTrace(recordedFailures)) {
                ParameterizeFailedError(recordedFailures, successCount, failureCount, completedEarly)
            } else {
                createThrowableSubclassWithoutStack {
                    ParameterizeFailedError(recordedFailures, successCount, failureCount, completedEarly)
                }
            }
        }
    }

    init {
        commonInit()
    }

    public actual override val message: String
        get() = commonMessage
}

internal actual class Failure private constructor(
    actual val failure: ParameterizeFailure
) : AssertionError() {
    actual companion object {
        actual operator fun invoke(failure: ParameterizeFailure): Failure {
            return createThrowableSubclassWithoutStack {
                Failure(failure)
            }
        }
    }

    actual override val message: String
        get() = commonMessage

    actual override val cause: Throwable
        get() = commonCause
}
