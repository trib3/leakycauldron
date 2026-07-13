package com.trib3.server.swagger

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import com.trib3.config.ConfigLoader
import com.trib3.json.ObjectMapperProvider
import com.trib3.server.config.TribeApplicationConfig
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverterContextImpl
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.integration.OpenApiContextLocator
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.Application
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import org.testng.annotations.Test
import io.swagger.v3.oas.models.media.Schema as ModelSchema

@JvmInline
value class InlineId(
    val value: String,
)

data class ModelWithInlineClass(
    val id: InlineId,
    val name: String,
)

sealed interface Parent {
    @Schema(name = "ParentA", description = "Blah blah blah")
    data class A(
        @Schema(description = "A property")
        val onA: String,
    ) : Parent

    @Schema(name = "ParentBatch", description = "Hey hi hello")
    data class Batch
        @JsonCreator
        constructor(
            @get:JsonValue val items: List<A>,
        ) : Parent
}

data class ParentListWrapper(
    val parents: List<Parent>,
)

/**
 * Sealed hierarchy whose subclasses carry no `@Schema` annotation, so the synthesized
 * array schema for the `@JsonValue`-on-collection subclass falls back to the raw class
 * simple name and a null description.
 */
sealed interface BareParent {
    data class Single(
        val name: String,
    ) : BareParent

    data class Batch
        @JsonCreator
        constructor(
            @get:JsonValue val items: List<Single>,
        ) : BareParent
}

@Schema(name = "JsonValueRenamed")
data class JsonValueWithNameOnly
    @JsonCreator
    constructor(
        @get:JsonValue val items: List<Parent.A>,
    )

data class JsonValueScalar(
    @get:JsonValue val name: String,
)

data class JsonValueOnPrimitiveList
    @JsonCreator
    constructor(
        @get:JsonValue val items: List<String>,
    )

@Schema(description = "Has only a description")
data class JsonValueWithDescriptionOnly
    @JsonCreator
    constructor(
        @get:JsonValue val items: List<Parent.A>,
    )

@Path("/no-consumes")
@Produces(MediaType.APPLICATION_JSON)
class NoConsumesTestResource {
    @POST
    fun post(body: Parent.A): Response = Response.ok(body).build()
}

@Path("/explicit-consumes")
@Produces(MediaType.APPLICATION_JSON)
class ExplicitConsumesTestResource {
    @POST
    @Consumes(MediaType.APPLICATION_XML)
    fun post(body: Parent.A): Response = Response.ok(body).build()
}

@Path("/class-consumes")
@Consumes(MediaType.APPLICATION_XML)
@Produces(MediaType.APPLICATION_JSON)
class ClassConsumesTestResource {
    @POST
    fun post(body: Parent.A): Response = Response.ok(body).build()
}

class SwaggerInitializerTest {
    private fun newInitializer(
        contextId: String,
        configLoader: ConfigLoader = ConfigLoader(),
    ) = SwaggerInitializer(contextId, TribeApplicationConfig(configLoader), ObjectMapperProvider().get())

    /** Resolves [type] against an isolated converter chain (no downstream `ModelResolver`). */
    private fun resolveDirectly(
        type: Class<*>,
        asSubtype: Boolean = false,
    ): Pair<ModelSchema<*>?, ModelConverterContextImpl> {
        val context = ModelConverterContextImpl(SealedKotlinSchemaConverter(ObjectMapperProvider().get()))
        val annotated = AnnotatedType().type(type)
        if (asSubtype) annotated.subtype(true)
        return context.resolve(annotated) to context
    }

    /** Returns the request body content keys for [resource]'s POST at [path]. */
    private fun postBodyContentKeys(
        contextId: String,
        path: String,
        resource: Class<*>,
    ): Set<String>? {
        newInitializer(contextId).process(
            object : Application() {
                override fun getClasses(): Set<Class<*>> = setOf(resource)
            },
        )
        return OpenApiContextLocator
            .getInstance()
            .getOpenApiContext(contextId)
            .read()
            .paths[path]
            ?.post
            ?.requestBody
            ?.content
            ?.keys
    }

    @Test
    fun testInlineClassPropertyNames() {
        newInitializer("testInlineClassPropertyNames").process(object : Application() {})
        val schema = ModelConverters.getInstance().read(ModelWithInlineClass::class.java)["ModelWithInlineClass"]
        assertThat(schema).isNotNull()
        assertThat(schema!!.properties.keys).containsExactlyInAnyOrder("id", "name")
    }

    @Test
    fun testSealedHierarchySchema() {
        newInitializer("testSealedHierarchySchema").process(object : Application() {})
        val schemas = ModelConverters.getInstance().readAll(Parent::class.java)

        val parent = schemas["Parent"]
        assertThat(parent).isNotNull()
        assertThat(parent!!.oneOf?.map { it.`$ref` })
            .isNotNull()
            .containsExactlyInAnyOrder(
                "#/components/schemas/ParentA",
                "#/components/schemas/ParentBatch",
            )

        val parentA = schemas["ParentA"]
        assertThat(parentA).isNotNull()
        assertThat(parentA!!.type).isEqualTo("object")
        assertThat(parentA.description).isEqualTo("Blah blah blah")
        assertThat(parentA.properties.keys).containsExactlyInAnyOrder("onA")
        assertThat(parentA.properties["onA"]?.description).isEqualTo("A property")

        val parentBatch = schemas["ParentBatch"]
        assertThat(parentBatch).isNotNull()
        assertThat(parentBatch!!.type).isEqualTo("array")
        assertThat(parentBatch.description).isEqualTo("Hey hi hello")
        assertThat(parentBatch.items?.`$ref`).isEqualTo("#/components/schemas/ParentA")
    }

    @Test
    fun testSealedTypeAsListItemUsesRef() {
        newInitializer("testSealedTypeAsListItemUsesRef").process(object : Application() {})
        val schemas = ModelConverters.getInstance().readAll(ParentListWrapper::class.java)

        val parentsItems = schemas["ParentListWrapper"]?.properties?.get("parents")?.items
        assertThat(parentsItems?.`$ref`).isEqualTo("#/components/schemas/Parent")
        assertThat(schemas["Parent"]?.oneOf?.map { it.`$ref` })
            .isNotNull()
            .containsExactlyInAnyOrder(
                "#/components/schemas/ParentA",
                "#/components/schemas/ParentBatch",
            )
    }

    @Test
    fun testSealedHierarchyWithoutSchemaAnnotations() {
        newInitializer("testSealedHierarchyWithoutSchemaAnnotations").process(object : Application() {})
        val schemas = ModelConverters.getInstance().readAll(BareParent::class.java)

        assertThat(schemas["BareParent"]?.oneOf?.map { it.`$ref` })
            .isNotNull()
            .containsExactlyInAnyOrder(
                "#/components/schemas/Single",
                "#/components/schemas/Batch",
            )

        val batch = schemas["Batch"]
        assertThat(batch).isNotNull()
        assertThat(batch!!.type).isEqualTo("array")
        assertThat(batch.description).isNull()
        assertThat(batch.items?.`$ref`).isEqualTo("#/components/schemas/Single")
    }

    @Test
    fun testJsonValueContainerSchemaAnnotationCombinations() {
        newInitializer("testJsonValueContainerSchemaAnnotationCombinations").process(object : Application() {})

        val nameOnly = ModelConverters.getInstance().readAll(JsonValueWithNameOnly::class.java)
        assertThat(nameOnly["JsonValueRenamed"]).isNotNull()
        assertThat(nameOnly["JsonValueRenamed"]!!.description).isNull()

        val descOnly = ModelConverters.getInstance().readAll(JsonValueWithDescriptionOnly::class.java)
        assertThat(descOnly["JsonValueWithDescriptionOnly"]).isNotNull()
        assertThat(descOnly["JsonValueWithDescriptionOnly"]!!.description).isEqualTo("Has only a description")
    }

    @Test
    fun testJsonDefaultMediaTypeForBodyWithoutConsumes() {
        assertThat(
            postBodyContentKeys(
                "testJsonDefaultMediaTypeForBodyWithoutConsumes",
                "/no-consumes",
                NoConsumesTestResource::class.java,
            ),
        ).isEqualTo(setOf("application/json"))
    }

    @Test
    fun testExplicitConsumesIsRespected() {
        assertThat(
            postBodyContentKeys(
                "testExplicitConsumesIsRespected",
                "/explicit-consumes",
                ExplicitConsumesTestResource::class.java,
            ),
        ).isEqualTo(setOf(MediaType.APPLICATION_XML))
    }

    @Test
    fun testClassLevelConsumesIsRespected() {
        assertThat(
            postBodyContentKeys(
                "testClassLevelConsumesIsRespected",
                "/class-consumes",
                ClassConsumesTestResource::class.java,
            ),
        ).isEqualTo(setOf(MediaType.APPLICATION_XML))
    }

    @Test
    fun testConverterReturnsNullWhenChainExhaustedForUnhandledType() {
        assertThat(resolveDirectly(Parent.A::class.java).first).isNull()
    }

    @Test
    fun testConverterSkipsSealedParentWhenResolvedAsSubtype() {
        assertThat(resolveDirectly(Parent::class.java, asSubtype = true).first).isNull()
    }

    @Test
    fun testConverterFallsBackToSimpleNameWhenSubclassResolvesToNull() {
        // With no downstream ModelResolver, regular sealed subclasses resolve to null;
        // the converter falls back to subclass.java.simpleName for the $ref.
        val (_, context) = resolveDirectly(BareParent::class.java)
        assertThat(context.definedModels["BareParent"]?.oneOf?.map { it.`$ref` })
            .isNotNull()
            .containsExactlyInAnyOrder(
                "#/components/schemas/Single",
                "#/components/schemas/Batch",
            )
    }

    @Test
    fun testConverterReturnsNullForAnonymousClass() {
        // Anonymous classes have `Class.simpleName == ""`.
        val anonymous: Any = object {}
        assertThat(resolveDirectly(anonymous::class.java).first).isNull()
    }

    @Test
    fun testJsonValueOnPrimitiveList() {
        // Items whose resolved schema has no name (e.g. String) fall back to the raw
        // class simpleName for the $ref.
        newInitializer("testJsonValueOnPrimitiveList").process(object : Application() {})
        val schema =
            ModelConverters.getInstance().readAll(
                JsonValueOnPrimitiveList::class.java,
            )["JsonValueOnPrimitiveList"]
        assertThat(schema).isNotNull()
        assertThat(schema!!.type).isEqualTo("array")
        assertThat(schema.items?.`$ref`).isEqualTo("#/components/schemas/String")
    }

    @Test
    fun testConverterDoesNotSynthesizeForJsonValueOnNonContainer() {
        assertThat(resolveDirectly(JsonValueScalar::class.java).first).isNull()
    }

    @Test
    fun testServerUrlsForCustomAppContextPath() {
        newInitializer("serverUrlsForCustomAppContextPath", ConfigLoader("appContextPathTestCase"))
            .process(object : Application() {})
        val context = OpenApiContextLocator.getInstance().getOpenApiContext("serverUrlsForCustomAppContextPath")
        assertThat(context.read().servers.map { it.url }).isEqualTo(
            listOf(
                "https://localhost/custom",
                "http://localhost/custom",
                "http://localhost:9080/custom",
            ),
        )
    }
}
