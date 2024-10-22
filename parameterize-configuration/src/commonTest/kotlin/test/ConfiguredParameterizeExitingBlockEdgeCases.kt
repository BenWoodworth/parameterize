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

package com.benwoodworth.parameterize.test

import com.benwoodworth.parameterize.*

/**
 * Configured [parameterize] calls with blocks that exit different ways.
 */
val configuredParameterizeExitingBlockEdgeCases = listOf(
    "Return normally" to { configuration: ParameterizeConfiguration ->
        parameterize(configuration) {}
    },
    "Non-local return" to { configuration ->
        run {
            parameterize(configuration) { return@run }
        }
    },
    "Throw" to { configuration ->
        class ExitingThrowable : Throwable()

        try {
            parameterize(configuration) { throw ExitingThrowable() }
        } catch (_: ExitingThrowable) {
        }
    },
    "Continue" to { configuration ->
        parameterize(configuration) {
            val skip by parameterOf<Unit>()
        }
    },
    "Break" to { configuration ->
        try {
            parameterize(configuration) {
                val illegalNesting by parameter {
                    with (this@parameterize) {
                        val inner by parameterOf(Unit)
                    }
                    listOf<Unit>()
                }
            }
        } catch (_: ParameterizeException) {
        }
    }
)
