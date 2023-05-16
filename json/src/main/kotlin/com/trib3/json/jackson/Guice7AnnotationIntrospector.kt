package com.trib3.json.jackson

import com.fasterxml.jackson.annotation.JacksonInject
import com.fasterxml.jackson.databind.introspect.AnnotatedField
import com.fasterxml.jackson.databind.introspect.AnnotatedMember
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod
import com.fasterxml.jackson.databind.introspect.AnnotatedParameter
import com.fasterxml.jackson.module.guice.GuiceAnnotationIntrospector
import com.google.inject.BindingAnnotation
import com.google.inject.Key
import java.lang.reflect.Type
import kotlin.reflect.full.hasAnnotation

class Guice7AnnotationIntrospector : GuiceAnnotationIntrospector() {
    override fun findInjectableValue(m: AnnotatedMember): JacksonInject.Value? {
        return findGuiceInjectId(m)?.let {
            JacksonInject.Value.forId(it)
        }
    }

    @Deprecated("Deprecated in superclass", ReplaceWith("findInjectableValue(m)"))
    override fun findInjectableValueId(m: AnnotatedMember): Any? {
        return findGuiceInjectId(m)
    }

    @Suppress("ReturnCount")
    private fun findGuiceInjectId(m: AnnotatedMember): Any? {
        /*
         * We check on three kinds of annotations: @JacksonInject for types
         * that were actually created for Jackson, and @Inject (both Guice's
         * and jakarta.inject) for types that (for example) extend already
         * annotated objects.
         *
         * Postel's law: http://en.wikipedia.org/wiki/Robustness_principle
         */
        // 19-Apr-2017, tatu: Actually this is something that should not be done;
        //   instead, pair of AnnotationIntrospector should be used... Leaving in
        //   for now, however.
        if (m.getAnnotation(JacksonInject::class.java) == null &&
            m.getAnnotation(jakarta.inject.Inject::class.java) == null &&
            m.getAnnotation(com.google.inject.Inject::class.java) == null
        ) {
            return null
        }

        val guiceMember: AnnotatedMember
        val guiceAnnotation: Annotation?

        if (m is AnnotatedField || m is AnnotatedParameter) {
            /* On fields and parameters the @Qualifier annotation and type to
             * inject are the member itself, so, nothing to do here...
             */
            guiceMember = m
            val anns = m.allAnnotations
            guiceAnnotation = findBindingAnnotation(anns.annotations())
        } else if (m is AnnotatedMethod) {
            /* For method injection, the @Qualifier and type to inject are
             * specified on the parameter. Here, we only consider methods with
             * a single parameter.
             */
            if (m.parameterCount != 1) {
                return null
            }

            /* Jackson does not *YET* give us parameter annotations on methods,
             * only on constructors, henceforth we have to do a bit of work
             * ourselves!
             */
            guiceMember = m.getParameter(0)
            val annotations = m.member.parameterAnnotations[0]
            guiceAnnotation = findBindingAnnotation(listOf(*annotations))
        } else {
            // Ignore constructors
            return null
        }

        /* Depending on whether we have an annotation (or not) return the
         * correct Guice key that Jackson will use to query the Injector.
         */

        /* Depending on whether we have an annotation (or not) return the
         * correct Guice key that Jackson will use to query the Injector.
         */
        return if (guiceAnnotation == null) {
            // 19-Sep-2016, tatu: Used to pass `getGenericType()`, but that is now deprecated.
            //    Looking at code in Guice Key, I don't think it does particularly good job
            //    in resolving generic types, so this is probably safe...
            //            return Key.get(guiceMember.getGenericType());
            Key.get(guiceMember.rawType as Type)
        } else Key.get(guiceMember.rawType as Type, guiceAnnotation)
//        return Key.get(guiceMember.getGenericType(), guiceAnnotation);
        //        return Key.get(guiceMember.getGenericType(), guiceAnnotation);
    }
    private fun findBindingAnnotation(annotations: Iterable<Annotation>): Annotation? {
        for (annotation in annotations) {
            // Check on guice (BindingAnnotation) & javax (Qualifier) based injections
            if (annotation.annotationClass.hasAnnotation<BindingAnnotation>() ||
                annotation.annotationClass.hasAnnotation<jakarta.inject.Qualifier>()
            ) {
                return annotation
            }
        }
        return null
    }
}
