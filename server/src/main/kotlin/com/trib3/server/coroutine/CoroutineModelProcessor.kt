package com.trib3.server.coroutine

import net.bytebuddy.ByteBuddy
import net.bytebuddy.description.modifier.Visibility
import net.bytebuddy.implementation.InvocationHandlerAdapter
import org.glassfish.jersey.internal.inject.InjectionManager
import org.glassfish.jersey.server.AsyncContext
import org.glassfish.jersey.server.model.ModelProcessor
import org.glassfish.jersey.server.model.Resource
import org.glassfish.jersey.server.model.ResourceMethod
import org.glassfish.jersey.server.model.ResourceModel
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import javax.inject.Inject
import javax.ws.rs.core.Configuration
import javax.ws.rs.ext.Provider
import kotlin.coroutines.Continuation

/**
 * Jersey [ModelProcessor] that finds resource methods that are implemented
 * with a suspend function, and redefines them with a dynamically created
 * non-suspend function.  That dynamically created implementation will then
 * use the [CoroutineInvocationHandler] to invoke the suspend function.
 */
@Provider
class CoroutineModelProcessor @Inject constructor(
    private val injectionManager: InjectionManager,
    private val asyncContextProvider: javax.inject.Provider<AsyncContext>
) : ModelProcessor {

    /**
     * Extract the type parameter from the continuation Type to be used
     * as the return value for the generated function.  Special case Unit->void
     * conversion to correctly generate a void method instead of an Object/Unit
     * returning method
     */
    private fun getContinuationTypeParameter(continuationType: Type): Type {
        val typeArgument = (continuationType as ParameterizedType).actualTypeArguments[0]
        val lowerBound = if (typeArgument is WildcardType) {
            typeArgument.lowerBounds[0]
        } else {
            typeArgument
        }
        return if (lowerBound == Unit::class.java) {
            Void.TYPE
        } else {
            lowerBound
        }
    }

    private fun createClassWrapper(method: ResourceMethod): Class<*> {
        return ByteBuddy()
            .subclass(Object::class.java)
            .annotateType(method.invocable.definitionMethod.declaringClass.annotations.toList())
            .defineMethod(
                method.invocable.definitionMethod.name,
                getContinuationTypeParameter(method.invocable.parameters.last().type),
                Visibility.PUBLIC
            )
            .withParameters(
                method.invocable.parameters
                    .slice(0 until method.invocable.parameters.size - 1)
                    .map { it.type }
            )
            .intercept(
                InvocationHandlerAdapter.of(
                    CoroutineInvocationHandler(
                        asyncContextProvider,
                        { method.invocable.handler.getInstance(injectionManager) },
                        method.invocable,
                        // Invoker should ignore the method's return value if the actual value will be
                        // provided asynchronously.  eg, processing SSE or providing return value
                        // explicitly through a @Suspended AsyncReturn.resume() callback.
                        // See org.glassfish.jersey.server.model.ResourceMethodInvoker.apply() for details.
                        method.isSse || method.isSuspendDeclared
                    )
                )
            )
            .annotateMethod(method.invocable.definitionMethod.annotations.toList())
            // copy the annotations for each parameter in the invocable method
            // (except for the last/Continuation param)
            .let { fakeMethod ->
                method.invocable.definitionMethod.parameterAnnotations
                    .slice(0 until method.invocable.definitionMethod.parameterAnnotations.size - 1)
                    .foldIndexed(fakeMethod) { index, annotatedMethod, parameter ->
                        annotatedMethod.annotateParameter(index, parameter.toList())
                    }
            }
            .make()
            .load(this::class.java.classLoader)
            .loaded
    }

    /**
     * Replace any suspend function (ie, function whose last param is a Continuation)
     * with a dynamically created non-suspend implementation
     */
    private fun buildCoroutineReplacedResource(resource: Resource): Resource {
        val resourceBuilder = Resource.builder(resource)
        for (method in resource.resourceMethods) {
            if (
                method.invocable.parameters.isNotEmpty() &&
                method.invocable.parameters.last().rawType == Continuation::class.java
            ) {
                val fakeClass = createClassWrapper(method)

                val proxyInstance = fakeClass.getDeclaredConstructor().newInstance()
                val handlingMethod =
                    proxyInstance::class.java.methods.first { it.name == method.invocable.definitionMethod.name }
                val replacedMethod = resourceBuilder.updateMethod(method)
                if (method.isSse) {
                    replacedMethod.sse()
                }
                if (method.isSuspendDeclared) {
                    replacedMethod.suspended(method.suspendTimeout, method.suspendTimeoutUnit)
                }
                if (method.isManagedAsyncDeclared) {
                    replacedMethod.managedAsync()
                }
                replacedMethod.handledBy(
                    proxyInstance,
                    handlingMethod
                )
                replacedMethod.handlingMethod(handlingMethod)
            }
        }
        return resourceBuilder.build()
    }

    /**
     * Go though every child resource in the resourceModel and replace any coroutine resource methods found
     */
    override fun processResourceModel(resourceModel: ResourceModel, configuration: Configuration): ResourceModel {
        val resourceModelBuilder = ResourceModel.Builder(false)
        for (resource in resourceModel.resources) {
            val resourceBuilder = Resource.builder(buildCoroutineReplacedResource(resource))
            for (child in resource.childResources) {
                resourceBuilder.replaceChildResource(child, buildCoroutineReplacedResource(child))
            }
            resourceModelBuilder.addResource(resourceBuilder.build())
        }
        return resourceModelBuilder.build()
    }

    /**
     * Go though every child resource in the subResourceModel and replace any coroutine resource methods found
     */
    override fun processSubResource(subResourceModel: ResourceModel, configuration: Configuration): ResourceModel {
        return processResourceModel(subResourceModel, configuration)
    }
}
