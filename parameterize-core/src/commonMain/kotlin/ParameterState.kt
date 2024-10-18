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
import kotlin.reflect.KProperty

/**
 * The parameter state is responsible for managing a parameter in the
 * [parameterize] DSL, maintaining an argument, and lazily loading the next ones
 * in as needed.
 *
 * The state can also be reset for reuse later with another parameter, allowing
 * the same instance to be shared, saving on unnecessary instantiations. Since
 * this means the underlying argument type can change, this class doesn't have a
 * generic type for it, and instead has each function pull a generic type from a
 * provided property, and validates it against the property this parameter was
 * declared with. This ensures that the argument type is correct at runtime, and
 * also validates that the parameter is in fact being used with the expected
 * property.
 *
 * When first declared, the parameter [property] and the [arguments] it was
 * declared with will be stored, along with a new argument iterator and the
 * first argument from it. The arguments are lazily read in from the iterator as
 * they're needed, and will seamlessly continue with the start again after the
 * last argument, using [isLastArgument] as an indicator. The stored iterator
 * will always have a next argument available, and will be set to null when its
 * last argument is read in to release its reference until the next iterator is
 * created to begin from the start again.
 *
 * Since each [parameterize] iteration should declare the same parameters,
 * in the same order with the same arguments, declared with the same
 * already-declared state instance as the previous iteration. Calling [declare]
 * again will leave the state unchanged, only serving to validate that the
 * parameter was in fact declared the same as before. The new arguments are
 * ignored since they're assumed to be the same, and the state remains unchanged
 * in favor of continuing through the current iterator where it left off.
 */
internal class ParameterState(
    private val parameterizeState: ParameterizeState
) {
    private var declaredParameter: DeclaredParameter<*>? = null
    private var arguments: Sequence<*>? = null
    private var argumentIterator: Iterator<*>? = null

    internal fun reset() {
        declaredParameter = null
        arguments = null
        argumentIterator = null
    }

    /**
     * @throws IllegalStateException if used before the argument has been declared.
     */
    val isLastArgument: Boolean
        get() {
            checkNotNull(declaredParameter) { "Parameter has not been declared" }
            return argumentIterator == null
        }


    /**
     * Returns a string representation of the current argument, or a "not declared" message.
     */
    override fun toString(): String =
        declaredParameter?.toString() ?: "Parameter not declared yet."

    /**
     * Set up the delegate for a parameter [property] with the given [arguments].
     *
     * If this delegate is already [declare]d, [property] and [arguments] should be equal to those that were originally passed in.
     * The [property] will be checked to make sure it's the same, and the current argument will remain the same.
     * The new [arguments] will be ignored in favor of reusing the existing arguments, under the assumption that they're equal.
     *
     * @throws ParameterizeException if already declared for a different [property].
     * @throws ParameterizeContinue if [arguments] is empty.
     */
    fun <T> declare(property: KProperty<*>, arguments: Sequence<T>) {
        // Nothing to do if already declared (besides validating the property)
        this.declaredParameter?.property?.let { declaredProperty ->
            if (!property.equalsProperty(declaredProperty)) {
                val message = "Expected to be declaring `${declaredProperty.name}`, but got `${property.name}`"
                throw ParameterizeBreak(parameterizeState, ParameterizeException(message))
            }
            return
        }

        val iterator = arguments.iterator()
        if (!iterator.hasNext()) {
            throw parameterizeState.parameterizeContinue // Before changing any state
        }

        this.declaredParameter = DeclaredParameter(property, iterator.next())
        this.arguments = arguments
        this.argumentIterator = iterator.takeIf { it.hasNext() }
    }

    /**
     * Get the current [DeclaredParameter].
     *
     * @throws IllegalStateException if the argument has not been declared yet.
     */
    fun getDeclaredParameter(): DeclaredParameter<*> {
        val declaredParameter = checkNotNull(this.declaredParameter) {
            "Cannot get declared parameter before it's been declared"
        }

        return declaredParameter
    }

    /**
     * Iterates the parameter argument.
     *
     * @throws IllegalStateException if the argument has not been declared yet.
     */
    fun nextArgument() {
        val declaredParameter = checkNotNull(this.declaredParameter) {
            "Cannot iterate arguments before parameter has been declared"
        }

        val arguments = checkNotNull(arguments) {
            "Expected arguments to be non-null since parameter has been declared"
        }

        val iterator = argumentIterator ?: arguments.iterator()

        this.declaredParameter = DeclaredParameter(declaredParameter.property, iterator.next())
        argumentIterator = iterator.takeIf { it.hasNext() }
    }
}
