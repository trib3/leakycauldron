package com.trib3.server.coroutine

import net.bytebuddy.ByteBuddy
import net.bytebuddy.description.modifier.Visibility
import net.bytebuddy.implementation.InvocationHandlerAdapter
import org.glassfish.jersey.internal.inject.InjectionManager
import org.glassfish.jersey.server.AsyncContext
import org.glassfish.jersey.server.model.ModelProcessor
import org.glassfish.jersey.server.model.Resource
import org.glassfish.jersey.server.model.ResourceModel
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
                val injectedInstance = method.invocable.handler.getInstance(injectionManager)
                val fakeClass = ByteBuddy()
                    .subclass(Object::class.java)
                    .annotateType(method.invocable.handler.handlerClass.annotations.toList())
                    .defineMethod(
                        method.invocable.handlingMethod.name,
                        method.invocable.rawResponseType,
                        Visibility.PUBLIC
                    )
                    .withParameters(
                        method.invocable.parameters
                            .slice(0 until method.invocable.parameters.size - 1)
                            .map { it.rawType }
                    )
                    .intercept(
                        InvocationHandlerAdapter.of(
                            CoroutineInvocationHandler(
                                asyncContextProvider,
                                injectedInstance,
                                method.invocable.handlingMethod
                            )
                        )
                    )
                    .annotateMethod(method.invocable.handlingMethod.annotations.toList())
                    // copy the annotations for each parameter in the invocable method
                    // (except for the last/Continuation param)
                    .let { fakeMethod ->
                        method.invocable.handlingMethod.parameterAnnotations
                            .slice(0 until method.invocable.handlingMethod.parameterAnnotations.size - 1)
                            .foldIndexed(fakeMethod) { index, annotatedMethod, parameter ->
                                annotatedMethod.annotateParameter(index, parameter.toList())
                            }
                    }
                    .make()
                    .load(this::class.java.classLoader)
                    .loaded

                val proxyInstance = fakeClass.getDeclaredConstructor().newInstance()
                val handlingMethod =
                    proxyInstance::class.java.methods.first { it.name == method.invocable.handlingMethod.name }
                val replacedMethod = resourceBuilder.updateMethod(method)
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
            val resourceBuilder = Resource.builder(resource)
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
