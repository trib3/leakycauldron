package com.trib3.server.coroutine

/**
 * Annotation that allows explicitly setting the dispatcher for coroutine async jersey resource methods.
 * [dispatcher] can be any of: "Default", "IO", "Unconfined"
 * ("Main" supported if a main dispatcher artifact is imported, but generally doesn't apply to a server)
 */
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class AsyncDispatcher(val dispatcher: String)
