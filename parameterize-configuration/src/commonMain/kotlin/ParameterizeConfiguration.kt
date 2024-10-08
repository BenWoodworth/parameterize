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
import com.benwoodworth.parameterize.ParameterizeScope.DeclaredParameter
import kotlin.coroutines.Continuation
import kotlin.coroutines.RestrictsSuspension
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.native.concurrent.ThreadLocal

/**
 * Configuration options for [parameterize].
 */
public class ParameterizeConfiguration internal constructor(
    /** @see Builder.decorator */
    public val decorator: suspend DecoratorScope.(iteration: suspend DecoratorScope.() -> Unit) -> Unit,

    /** @see Builder.onFailure */
    public val onFailure: OnFailureScope.(failure: Throwable) -> Unit,

    /** @see Builder.onComplete */
    public val onComplete: OnCompleteScope.() -> Unit
) {
    /** @suppress */
    @ThreadLocal
    public companion object {
        @PublishedApi
        internal val default: ParameterizeConfiguration = Builder(null).build()

        public operator fun invoke(
            from: ParameterizeConfiguration = default,
            builderAction: Builder.() -> Unit,
        ): ParameterizeConfiguration =
            Builder(from).apply(builderAction).build()
    }

    /** @suppress */
    override fun toString(): String =
        "ParameterizeConfiguration(" +
                "decorator=$decorator, " +
                "onFailure=$onFailure, " +
                "onComplete=$onComplete)"

    /** @see ParameterizeConfiguration */
    public class Builder internal constructor(from: ParameterizeConfiguration?) {
        /**
         * Decorates the [parameterize] block, enabling additional shared logic to be inserted around each iteration.
         *
         * The provided `iteration` function must be invoked exactly once. It will return without throwing even if the
         * iteration fails, guaranteeing that any post-iteration cleanup will always be executed.
         *
         * **Note:** Failures from within the [decorator] will propagate out uncaught, terminating [parameterize]
         * without [onFailure] or [onComplete] being executed.
         *
         * Defaults to:
         * ```
         * decorator = { iteration ->
         *     iteration()
         * }
         * ```
         *
         * @throws ParameterizeException if the `iteration` function is not invoked exactly once.
         *
         * @see DecoratorScope.isFirstIteration
         * @see DecoratorScope.isLastIteration
         */
        public var decorator: suspend DecoratorScope.(iteration: suspend DecoratorScope.() -> Unit) -> Unit =
            from?.decorator
                ?: { iteration ->
                    iteration()
                }

        /**
         * Invoked after each failing iteration to configure how a failure should be handled.
         *
         * This handler is executed after the [parameterize] block's [decorator] completes, and before [onComplete].
         *
         * **Note:** Failures from within [onFailure] will propagate out uncaught, terminating [parameterize] without
         * [onComplete] being executed. Immediately re-throwing the provided failure in this way may be desirable if the
         * [parameterize] block is not meant to fail.
         *
         * Defaults to:
         * ```
         * onFailure = { failure ->
         *     throw failure
         * }
         * ```
         *
         * @see OnFailureScope.breakEarly
         * @see OnFailureScope.recordFailure
         */
        public var onFailure: OnFailureScope.(failure: Throwable) -> Unit = from?.onFailure
            ?: { failure ->
                throw failure
            }

        /**
         * Invoked after [parameterize] has finished iterating, enabling the final results to be handled.
         *
         * **Note:** The default [onFailure] handler prevents [onComplete] from executing when there is a failure,
         * immediately throwing instead of continuing and tracking failures to be handled here.
         *
         *
         * Defaults to:
         * ```
         * onComplete = {
         *     if (failureCount > 0) throw ParameterizeFailedError()
         * }
         * ```
         */
        public var onComplete: OnCompleteScope.() -> Unit = from?.onComplete
            ?: {
                if (failureCount > 0) throw ParameterizeFailedError()
            }

        internal fun build(): ParameterizeConfiguration = ParameterizeConfiguration(
            decorator = decorator,
            onFailure = onFailure,
            onComplete = onComplete,
        )
    }

    /** @see Builder.decorator */
    @RestrictsSuspension
    public class DecoratorScope internal constructor(
        private val parameterizeState: ParameterizeState
    ) {
        private var initializedIsLastIteration = false

        /**
         * True if this is the first [parameterize] iteration.
         */
        public val isFirstIteration: Boolean = parameterizeState.isFirstIteration

        /**
         * True if this is the last [parameterize] iteration. Cannot be known until after the iteration runs.
         *
         * @throws ParameterizeException if accessed before the iteration function has been invoked.
         */
        public var isLastIteration: Boolean = false
            get() {
                if (!initializedIsLastIteration) {
                    throw ParameterizeException("Last iteration cannot be known until after the iteration function is invoked")
                }

                return field
            }
            internal set(value) {
                field = value
                initializedIsLastIteration = true
            }

        internal suspend inline fun suspendDecorator(
            crossinline block: (Continuation<Unit>) -> Unit
        ): Unit =
            suspendCoroutineUninterceptedOrReturn { c ->
                block(c)
                COROUTINE_SUSPENDED
            }
    }

    /** @see Builder.onFailure */
    public class OnFailureScope internal constructor(
        private val state: ConfiguredParameterizeState,

        /**
         * The number of iterations that have been executed, including this failing iteration.
         */
        public val iterationCount: Long,

        /**
         * The number of failures that have occurred, including this failure.
         */
        public val failureCount: Long
    ) {
        /**
         * The parameter arguments that resulted in the failure.
         */
        @Deprecated(
            "Replaced with parameters",
            ReplaceWith("this.parameters"),
            DeprecationLevel.ERROR
        )
        @Suppress("DEPRECATION_ERROR")
        public val arguments: List<ParameterizeFailure.Argument<*>>
            get() = throw UnsupportedOperationException("Replaced with parameters")

        /**
         * The parameters that resulted in the failure.
         */
        public val parameters: List<DeclaredParameter<*>> by lazy {
            state.getDeclaredParameters()
        }

        /**
         * Specifies whether [parameterize] should break and complete early, or continue to the next iteration.
         *
         * Defaults to `false`.
         */
        public var breakEarly: Boolean = false

        /**
         * Specifies whether this iteration's [arguments] and the resulting failure should be added to the list of
         * [recordedFailures][OnCompleteScope.recordedFailures] provided to [ParameterizeConfiguration.onComplete].
         *
         * Defaults to `false`.
         */
        public var recordFailure: Boolean = false
    }

    /** @see Builder.onComplete */
    public class OnCompleteScope internal constructor(
        /**
         * The total number of iterations, including [skips][skipCount] and [failures][failureCount].
         */
        public val iterationCount: Long,

        /**
         * The number of skipped iterations.
         *
         * A [parameterize] iteration will be skipped when a [parameter] is declared without any
         * [arguments][ParameterizeScope.Parameter.arguments].
         */
        public val skipCount: Long,

        /**
         * The number of failing iterations.
         */
        public val failureCount: Long,

        /**
         * True if [parameterize] completed with more combinations of arguments left to iterate, because of
         * [onFailure]'s [breakEarly][OnFailureScope.breakEarly] being set.
         *
         * **Note:** Setting [breakEarly][OnFailureScope.breakEarly] on the last iteration will not make this property
         * true, since all combinations have been iterated through.
         */
        public val completedEarly: Boolean,

        /** @see OnFailureScope.recordFailure */
        public val recordedFailures: List<ParameterizeFailure>
    ) {
        /**
         * The number of successful iterations, completed without [skipping][skipCount] or [failing][failureCount].
         */
        public val successCount: Long
            get() = iterationCount - skipCount - failureCount

        /** @see ParameterizeFailedError */
        public operator fun ParameterizeFailedError.Companion.invoke(): ParameterizeFailedError =
            ParameterizeFailedError(recordedFailures, successCount, failureCount, completedEarly)
    }
}
