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

package com.benwoodworth.parameterize.internal

/**
 * Create a subclass of [Throwable] without a `stack` being captured.
 *
 * This is a hack, since it depends on implementation details for [Throwable].
 * For classes extending [Throwable], Kotlin/JS checks if `captureStackTrace`
 * is defined, and if so, uses that to capture/set the [Throwable]'s `stack`
 * property. So, this function temporarily defines `captureStackTrace`, and has
 * it set `stack` to `null`, instead of taking time to collect the actual stack.
 *
 * In Kotlin 2.0.21:
 * ```
 * // The JS Constructor produced for subclasses of `Throwable`:
 * function ThrowableSubclass() {
 *   extendThrowable(this);
 *   captureStack(this, ThrowableSubclass);
 * }
 *
 * // The kotlin-stdlib-js code checking/calling `captureStackTrace`:
 * internal fun captureStack(instance: Throwable, constructorFunction: Any) {
 *     if (js("Error").captureStackTrace != null) {
 *         js("Error").captureStackTrace(instance, constructorFunction)
 *     } else {
 *         instance.asDynamic().stack = js("new Error()").stack
 *     }
 * }
 * ```
 */
@ParameterizeCoreFriendModuleApi
public inline fun <T : Throwable> createThrowableSubclassWithoutStack(create: () -> T): T {
    val originalCaptureStackTrace = js("Error").captureStackTrace

    return try {
        js("Error").captureStackTrace = captureNullStackTrace
        create()
    } finally {
        js("Error").captureStackTrace = originalCaptureStackTrace
    }
}

@PublishedApi
internal val captureNullStackTrace: dynamic = js(
    "function captureNullStackTrace(instance) { instance.stack = null }"
)
