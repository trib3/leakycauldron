package com.palominolabs.metrics.guice.annotation;

import com.codahale.metrics.annotation.Counted;
import com.codahale.metrics.annotation.Metered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.Lists;
import java.lang.reflect.Method;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ListAnnotationResolverTest {

    @Test
    public void testMixedAnnotations() throws Exception {
        ListAnnotationResolver annotationProvider = new ListAnnotationResolver(
                Lists.newArrayList(
                        new MethodAnnotationResolver(),
                        new ClassAnnotationResolver()
                )
        );

        Class<MixedAnnotatedClass> klass = MixedAnnotatedClass.class;
        Method publicMethod = klass.getDeclaredMethod("publicMethod");
        Method protectedMethod = klass.getDeclaredMethod("protectedMethod");
        Method packagePrivateMethod = klass.getDeclaredMethod("packagePrivateMethod");

        Timed classTimed = annotationProvider.findAnnotation(Timed.class, publicMethod);
        assertNotNull(classTimed);
        assertFalse(classTimed.absolute());
        assertNull(annotationProvider.findAnnotation(Metered.class, publicMethod));
        assertNull(annotationProvider.findAnnotation(Counted.class, publicMethod));

        assertNotNull(annotationProvider.findAnnotation(Timed.class, protectedMethod));
        assertNotNull(annotationProvider.findAnnotation(Metered.class, protectedMethod));
        assertNull(annotationProvider.findAnnotation(Counted.class, protectedMethod));

        Timed methodTimed = annotationProvider.findAnnotation(Timed.class, packagePrivateMethod);
        assertNotNull(methodTimed);
        assertTrue(methodTimed.absolute());
        assertNull(annotationProvider.findAnnotation(Metered.class, packagePrivateMethod));
        assertNull(annotationProvider.findAnnotation(Counted.class, packagePrivateMethod));
    }

    @Timed
    private static class MixedAnnotatedClass {
        public void publicMethod() {
        }

        @Metered
        protected void protectedMethod() {
        }

        @Timed(absolute = true)
        void packagePrivateMethod() {
        }
    }
}
