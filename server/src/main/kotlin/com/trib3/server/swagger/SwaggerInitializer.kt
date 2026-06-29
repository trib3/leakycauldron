package com.trib3.server.swagger

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.trib3.server.config.TribeApplicationConfig
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.core.jackson.ModelResolver
import io.swagger.v3.jaxrs2.Reader
import io.swagger.v3.jaxrs2.integration.JaxrsApplicationScanner
import io.swagger.v3.jaxrs2.integration.JaxrsOpenApiContextBuilder
import io.swagger.v3.jaxrs2.integration.OpenApiServlet
import io.swagger.v3.oas.integration.SwaggerConfiguration
import io.swagger.v3.oas.integration.api.OpenApiContext
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.ComposedSchema
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.servers.Server
import jakarta.inject.Inject
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.core.Application
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.UriBuilder
import io.swagger.v3.oas.annotations.media.Schema as SchemaAnnotation

// JaxrsOpenApiContextBuilder<T> needs to be subclassed in order to instantiate with a <T>
private class SwaggerContextBuilder : JaxrsOpenApiContextBuilder<SwaggerContextBuilder>()

/**
 * Defaults `application/json` for content whose declaring resource has no
 * `@Consumes`/`@Produces`.  swagger-jaxrs2's default is the wildcard, but
 * dropwizard sets jackson/jersey up to default to `application/json`.
 */
class JsonDefaultMediaTypeReader : Reader() {
    override fun processContent(
        content: Content?,
        schema: Schema<*>?,
        methodConsumes: Consumes?,
        classConsumes: Consumes?,
    ): Content {
        val result = super.processContent(content, schema, methodConsumes, classConsumes)
        if (methodConsumes == null && classConsumes == null) {
            result[MediaType.APPLICATION_JSON] = result.remove(DEFAULT_MEDIA_TYPE_VALUE)
        }
        return result
    }
}

/**
 * Schemas sealed kotlin parents as `oneOf` of subclass `$ref`s, and synthesizes
 * named array schemas for subclasses whose JSON shape is `@JsonValue` on a system
 * collection (e.g. `List<X>`).  swagger-core's default leaves the parent shape
 * empty (`type: object` + `allOf`-on-children), and crashes resolving the latter.
 */
internal class SealedKotlinSchemaConverter(
    private val mapper: ObjectMapper,
) : ModelConverter {
    override fun resolve(
        type: AnnotatedType,
        context: ModelConverterContext,
        chain: MutableIterator<ModelConverter>,
    ): Schema<*>? {
        val javaType = mapper.constructType(type.type)
        return resolveSealedParent(type, javaType, context)
            ?: chain.takeIf { it.hasNext() }?.next()?.resolve(type, context, chain)
            ?: resolveJsonValueContainer(type, javaType, context)
    }

    /**
     * Returns a `$ref` to a `oneOf`-of-subclasses schema for a sealed kotlin
     * parent, or null otherwise.
     */
    private fun resolveSealedParent(
        type: AnnotatedType,
        javaType: JavaType,
        context: ModelConverterContext,
    ): Schema<*>? {
        val rawClass = javaType.rawClass
        val sealedSubclasses = rawClass.kotlin.sealedSubclasses
        val name = rawClass.simpleName
        return if (name.isEmpty() || type.isSubtype || sealedSubclasses.isEmpty()) {
            null
        } else {
            val composed = ComposedSchema().name(name)
            for (subclass in sealedSubclasses) {
                val resolved = context.resolve(AnnotatedType().type(subclass.java))
                val refName = resolved?.name ?: subclass.java.simpleName
                composed.addOneOfItem(Schema<Any>().`$ref`("#/components/schemas/$refName"))
            }
            context.defineModel(name, composed, type, null)
            // $ref so containers emit a $ref at each use site instead of inlining
            // the oneOf (their handler only $ref-ifies ObjectSchema items).
            Schema<Any>().`$ref`("#/components/schemas/$name")
        }
    }

    /**
     * Returns a named array schema for a class whose JSON shape is `@JsonValue` on
     * a system collection, or null otherwise.
     */
    private fun resolveJsonValueContainer(
        type: AnnotatedType,
        javaType: JavaType,
        context: ModelConverterContext,
    ): Schema<*>? {
        val jsonValueType =
            mapper.serializationConfig
                .introspect(javaType)
                .findJsonValueAccessor()
                ?.type
                ?.takeIf { it.isContainerType }
        val contentType = jsonValueType?.contentType
        val rawClass = javaType.rawClass
        val name = rawClass.swaggerSchemaName()
        return if (contentType == null || name == null) {
            null
        } else {
            val resolvedItem = context.resolve(AnnotatedType().type(contentType))
            val itemName = resolvedItem?.name ?: contentType.rawClass.simpleName
            val itemRef = Schema<Any>().`$ref`("#/components/schemas/$itemName")
            ComposedSchema()
                .type("array")
                .items(itemRef)
                .name(name)
                .description(rawClass.swaggerDescription())
                .also { context.defineModel(name, it, type, null) }
        }
    }

    /**
     * Returns the explicit `@Schema(name = ...)` if non-empty, else the class's
     * simple name, or null for anonymous classes.
     */
    private fun Class<*>.swaggerSchemaName(): String? {
        val annotation = getAnnotation(SchemaAnnotation::class.java)
        return if (annotation != null && annotation.name.isNotEmpty()) {
            annotation.name
        } else {
            simpleName.ifEmpty { null }
        }
    }

    /**
     * Returns the class's `@Schema(description = ...)` if non-empty, or null.
     */
    private fun Class<*>.swaggerDescription(): String? =
        getAnnotation(SchemaAnnotation::class.java)?.description?.ifEmpty { null }
}

// TODO: find a better place for this interface
interface JaxrsAppProcessor {
    fun process(application: Application)
}

/**
 * Registers an [OpenApiContext] for the application's Jersey resources under
 * [contextId].  For compatibility with [OpenApiServlet] does the same contextId
 * generation as [io.swagger.v3.jaxrs2.integration.ServletConfigContextUtils]
 * in the @Inject constructor.
 */
class SwaggerInitializer(
    private val contextId: String,
    private val appConfig: TribeApplicationConfig,
    private val objectMapper: ObjectMapper,
) : JaxrsAppProcessor {
    @Inject
    constructor(
        appConfig: TribeApplicationConfig,
        objectMapper: ObjectMapper,
    ) : this(
        OpenApiContext.OPENAPI_CONTEXT_ID_PREFIX + "servlet." +
            OpenApiServlet::class.simpleName,
        appConfig,
        objectMapper,
    )

    override fun process(application: Application) {
        ModelConverters.getInstance().addConverter(ModelResolver(objectMapper))
        ModelConverters.getInstance().addConverter(SealedKotlinSchemaConverter(objectMapper))
        val hostAndPath = UriBuilder.newInstance().host(appConfig.corsDomains[0]).path(appConfig.appContextPath)
        val baseUrls =
            listOf(
                hostAndPath.clone().scheme("https"),
                hostAndPath.clone().scheme("http"),
                hostAndPath.clone().scheme("http").port(appConfig.appPort),
            )
        SwaggerContextBuilder()
            .openApiConfiguration(
                SwaggerConfiguration()
                    .openAPI(
                        OpenAPI().servers(
                            baseUrls.map { Server().url(it.build().toString()) },
                        ),
                    ).scannerClass(JaxrsApplicationScanner::class.qualifiedName)
                    .readerClass(JsonDefaultMediaTypeReader::class.qualifiedName),
            ).application(application)
            .ctxId(contextId)
            .buildContext(true)
    }
}
