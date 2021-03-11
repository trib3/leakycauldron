package com.trib3.testing

import org.easymock.Capture
import org.easymock.EasyMock
import org.easymock.EasyMockSupport
import org.easymock.internal.LastControl

/**
 * Extension function to add a `mock<T>()` method to [EasyMockSupport]
 * for less repetition when creating mocks.
 */
inline fun <reified T> EasyMockSupport.mock(name: String? = null): T {
    return mock(name, T::class.java)
}

/**
 * Extension function to add a `niceMock<T>()` method to [EasyMockSupport]
 * for less repetition when creating nice mocks.
 */
inline fun <reified T> EasyMockSupport.niceMock(name: String? = null): T {
    return niceMock(name, T::class.java)
}

/**
 * Collection of static methods to use with EasyMock for a more
 * usable kotlin experience.  Provides type-inference friendly mock()
 * methods as well as matchers that return non-null values for passing
 * to non-nullable methods.  Can be intermixed with [EasyMock] methods
 * when the [LeakyMock] implementation is missing or not desired.
 *
 * Note that any matchers that return a mock must be used on open/non-final
 * classes, as EasyMock can't create mock instances of closed/final classes.
 * In those cases, the EasyMock version of the matcher can be used if the
 * method being called accepts null arguments.
 */
@Suppress("TooManyFunctions")
class LeakyMock private constructor() {
    companion object {
        /**
         * Create a mock object of the specified type
         */
        inline fun <reified T> mock(name: String? = null): T {
            return EasyMock.mock(name, T::class.java)
        }

        /**
         * Create a mock object of the specified type
         */
        fun <T> mock(clazz: Class<T>): T {
            return EasyMock.mock(clazz)
        }

        /**
         * Create a named mock object of the specified type
         */
        fun <T> mock(name: String, clazz: Class<T>): T {
            return EasyMock.mock(name, clazz)
        }

        /**
         * Create a nice mock object of the specified type
         */
        inline fun <reified T> niceMock(name: String? = null): T {
            return EasyMock.niceMock(name, T::class.java)
        }

        /**
         * Create a nice mock object of the specified type
         */
        fun <T> niceMock(clazz: Class<T>): T {
            return EasyMock.niceMock(clazz)
        }

        /**
         * Create a named nice mock object of the specified type
         */
        fun <T> niceMock(name: String, clazz: Class<T>): T {
            return EasyMock.niceMock(name, clazz)
        }

        /**
         * Expect any object of the specified type and return a mock
         */
        inline fun <reified T> anyObject(): T {
            EasyMock.anyObject<T>()
            return mock()
        }

        /**
         * Expect any object of the specified type and return a mock
         */
        inline fun <reified T> anyObject(clazz: Class<T>): T {
            EasyMock.anyObject(clazz)
            return mock()
        }

        /**
         * Expect any string and return an empty string
         */
        fun anyString(): String {
            EasyMock.anyString()
            return ""
        }

        /**
         * Expect an instance of the specified type and return a mock
         */
        inline fun <reified T> isA(): T {
            EasyMock.isA(T::class.java)
            return mock()
        }

        /**
         * Expect an instance of the specified type and return a mock
         */
        fun <T> isA(clazz: Class<T>): T {
            EasyMock.isA(clazz)
            return mock(clazz)
        }

        /**
         * Expect a string that contains [substring] and return an empty string
         */
        fun contains(substring: String): String {
            EasyMock.contains(substring)
            return ""
        }

        /**
         * Expect an object that matches all given expectations, and return the first one
         */
        fun <T> and(vararg args: T): T {
            check(args.isNotEmpty()) { "Must pass at least one argument to LeakyMock.and()" }
            LastControl.reportAnd(args.size)
            return args.first()
        }

        /**
         * Expect an object that matches any of the given expectations, and return the first one
         */
        fun <T> or(vararg args: T): T {
            check(args.isNotEmpty()) { "Must pass at least one argument to LeakyMock.or()" }
            LastControl.reportOr(args.size)
            return args.first()
        }

        /**
         * Expect an object that does not match the given expectation, and return the expectation
         */
        fun <T> not(first: T): T {
            EasyMock.not(first)
            return first
        }

        // skip isNull() and notNull() matchers -- if using them the method likely accepts nullable args

        /**
         * Expect a string that contains a substring that matches [regex] and return an empty string
         */
        fun find(regex: String): String {
            EasyMock.find(regex)
            return ""
        }

        /**
         * Expect a string that matches [regex] and return an empty string
         */
        fun matches(regex: String): String {
            EasyMock.matches(regex)
            return ""
        }

        /**
         * Expect a string that starts with [prefix] and return an empty string
         */
        fun startsWtih(prefix: String): String {
            EasyMock.startsWith(prefix)
            return ""
        }

        /**
         * Expect a string that ends with [suffix] and return an empty string
         */
        fun endsWith(suffix: String): String {
            EasyMock.endsWith(suffix)
            return ""
        }

        /**
         * Expect any object, but capture it for later use, and return a mock
         */
        inline fun <reified T> capture(captured: Capture<T>): T {
            EasyMock.capture(captured)
            return mock()
        }
    }
}
