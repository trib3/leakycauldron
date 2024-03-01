package com.trib3.server

/**
 * Conditionally calls the specified function [block] with `this` value and returns its result,
 * or returns `this` value if the [condition] is false.
 *
 * Useful for interacting with builder/fluent APIs.
 */
fun <T> T.runIf(
    condition: Boolean,
    block: T.() -> T,
): T {
    return if (condition) {
        block()
    } else {
        this
    }
}
