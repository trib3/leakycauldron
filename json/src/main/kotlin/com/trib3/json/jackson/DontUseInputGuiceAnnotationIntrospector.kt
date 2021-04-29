package com.trib3.json.jackson

import com.fasterxml.jackson.annotation.JacksonInject
import com.fasterxml.jackson.databind.introspect.AnnotatedMember
import com.fasterxml.jackson.module.guice.GuiceAnnotationIntrospector

/**
 * GuiceAnnotationInspector extension that doesn't ignore [JacksonInject.useInput]
 * if set, and defaults to false if not set.  This means that by default, values
 * will be injected instead of read from json if present in both.
 */
class DontUseInputGuiceAnnotationIntrospector : GuiceAnnotationIntrospector() {
    override fun findInjectableValue(m: AnnotatedMember): JacksonInject.Value? {
        val injectId = super.findInjectableValue(m)
        return injectId?.let {
            JacksonInject.Value.construct(
                it.id,
                m.getAnnotation(JacksonInject::class.java)?.useInput?.asBoolean() ?: false
            )
        }
    }
}
