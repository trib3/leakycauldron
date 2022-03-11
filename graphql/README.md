Graphql
======
Provides the application infrastructure for adding [GraphQL](https://graphql.org) support to
a [server](https://github.com/trib3/leakycauldron/blob/HEAD/server) application.

* GraphQL endpoint at `/app/graphql`:
    * UUID, java8 time, and
      threeten-extra [Scalars](https://github.com/trib3/leakycauldron/blob/HEAD/graphql/src/main/kotlin/com/trib3/graphql/execution/LeakyCauldronHooks.kt)
    * [Request Ids](https://github.com/trib3/leakycauldron/blob/HEAD/graphql/src/main/kotlin/com/trib3/graphql/execution/RequestIdInstrumentation.kt)
      attached to the GraphQL response extensions
    * Any guice bound Query, Subscription or Mutation Resolvers
    * Supports websockets using
      the [Apollo Protocol](https://github.com/apollographql/subscriptions-transport-ws/blob/HEAD/PROTOCOL.md)
      or the new [GraphQL over WebSocket Protocol](https://github.com/enisdenjo/graphql-ws/blob/master/PROTOCOL.md)
    * Supports the
      [GraphQL over Server-Sent Events Protocol](https://github.com/enisdenjo/graphql-sse/blob/master/PROTOCOL.md)
      at `/app/graphql/stream` in both Single Connection Mode and Distinct Connections Mode
    * Supports subscriptions via [coroutine](https://github.com/kotlin/kotlinx.coroutines/) Flows for any Resolvers that
      return a `Flow<T>` or `Publisher<T>` (`Flow` preferred)
    * Supports [Dropwizard Authentication](https://www.dropwizard.io/en/latest/manual/auth.html) Principals passed
      through to Resolvers
      via [GraphQLContext](https://github.com/ExpediaGroup/graphql-kotlin/blob/HEAD/graphql-kotlin-schema-generator/src/main/kotlin/com/expediagroup/graphql/execution/GraphQLContext.kt)
    * Supports [coroutine](https://github.com/kotlin/kotlinx.coroutines/) structured concurrency and cancellation of
      POSTed GraphQL queries for Resolvers implemented as `suspend` functions
* Admin:
    * [GraphiQL](https://github.com/graphql/graphiql) available at `/admin/graphiql`

### Configuration

Configuration is done primarily though Guice.
[`GraphQLApplicationModule`](https://github.com/trib3/leakycauldron/blob/HEAD/graphql/src/main/kotlin/com/trib3/graphql/modules/GraphQLApplicationModule.kt)
exposes binders for commonly bound objects. Additionally, some parameters are set though the HOCON config:

Default config:

```hocon
graphql {
  keepAliveIntervalSeconds: 15  # interval to send keepalive messages over websocket/sse protocols in seconds
  idleTimeout: null  # allows override of jetty websocket policy idleTimeout
  maxBinaryMessageSize: null  # allows override of jetty websocket policy maxBinaryMessageSize
  maxTextMessageSize: null  # allows override of jetty websocket policy maxTextMessageSize
  checkAuthorization: false  # whether to kick unauthenticated clients off websocket/sse sessions (allow by default)
  connectionInitWaitTimeout: 15  # when using graphql-ws protocol, must receive the connection_init message within this many seconds
}
```

### GraphQL Resolvers

[`GraphQLApplicationModule`](https://github.com/trib3/leakycauldron/blob/HEAD/graphql/src/main/kotlin/com/trib3/graphql/modules/GraphQLApplicationModule.kt)
provides methods that expose multi-binders for configuring GraphQL resolvers. Any model classes must be added to
the `graphQLPackagesBinder()` to allow [GraphQL Kotlin](https://github.com/ExpediaDotCom/graphql-kotlin/)
to use them. Query Resolver implementations can be added to the `graphQLQueriesBinder()`, Subscriptions to
the `graphQLSubscriptionsBinder()`, and Mutations to the `graphQLMutationsBinder()`

```kotlin
class ExampleApplicationModule : GraphQLApplicationModule() {
    override fun configureApplication() {
        // ...
        graphQLPackagesBinder().addBinding().toInstance("com.example.api")
        graphQLPackagesBinder().addBinding().toInstance("com.example.server.graphql")

        graphQLQueriesBinder().addBinding().to<com.example.server.graphql.Query>()
        graphQLMutationsBinder().addBinding().to<com.example.server.graphql.Mutation>()
        graphQLSubscriptionsBinder().addBinding().to<com.example.server.graphql.Subscription>()
        // ...
    }
}
```

### Auth

#### Auth Context

If Dropwizard Authentication is setup and an `AuthFilter<*, *>` binding is provided per
the [server README](https://github.com/trib3/leakycauldron/blob/HEAD/server/README.md#auth), GraphQL resolver methods
can receive the principal inside the GraphQL context map received from the `DataFetchingEnvironment` using the
provided `getInstance` extension method. Resolver methods can write to the `cookie` field of the context map using
the `setInstance` extension method in order to set cookies on the client (useful when, for example, using
a `CookieTokenAuthFilter` for auth).

```kotlin
class ExampleLoginMutations : GraphQLQueryResolver {
    fun login(dfe: DataFetchingEnvironment, email: String, pass: String): Boolean {
        if (dfe.graphQlContext.getInstance<Principal> == null) {
            // log in!
            val userSession = authenticate(email, pass)
            if (userSession == null) {
                return false
            }
            // cookie will be set in response
            dfe.graphQlContext.setInstance(NewCookie("example-app-session-id", userSession.id))
        } else {
            // already logged in
        }
        return true
    }

    fun logout(dfe: DataFetchingEnvironment): Boolean {
        if (dfe.graphQlContext.getInstance<Principal> != null) {
            deleteSession(dfe.graphQlContext.getInstance<Principal>)
            dfe.graphQlContext.setInstance(
                NewCookie(
                    Cookie("example-app-session-id", ""),
                    null,
                    -1,
                    Date(0), // 1970
                    false,
                    false
                )
            )
        }
        return true
    }
}
```

When using the WebSocket transport, credentials can be provided in HTTP headers/cookies of the upgrade request, or
provided in the payload of the `connection_init` message.

When using the Server-Sent Events transport's Single Connection Mode, credentials provided in the HTTP headers/cookies
of the reservation request are carried over to any requests made with the returned stream token.

#### Auth Schema Directive

A [`@GraphQLAuth`](https://github.com/trib3/leakycauldron/blob/HEAD/graphql/src/main/kotlin/com/trib3/graphql/execution/GraphQLAuthDirectiveWiring.kt)
schema directive annotation is also provided to allow role based protection of GraphQL exposed fields. Any field in the
GraphQL schema that is annotated with `@GraphQLAuth` will be restricted to being fetched by authenticated users. If an
`Authorizer<Principal>` binding is provided per the
[server README](https://github.com/trib3/leakycauldron/blob/HEAD/server/README.md#auth), then the `@GraphQLAuth`
directive allows for role based restriction of fields.

Note that any field annotated with `@GraphQLAuth` will return null if auth fails, so must return a nullable type. Failed
auth will also result in Unauthorized/Forbidden errors in the GraphQL result.

```kotlin
class ExampleAuthedQuery : GraphQLQueryResolver {
    fun openField(): String {
        return "anyone can access this"
    }

    @GraphQLAuth
    fun protectedField(): String? {
        return "only logged in users can access this"
    }

    @GraphQLAuth(roles = ["ADMIN", "SPECIAL"])
    fun protectedField(): String? {
        return "only logged in ADMIN and SPECIAL users can access this, assuming an Authorizer binding is provided"
    }
}
```

### GraphQLContext CoroutineScope

The GraphQLContext map also contains a `CoroutineScope`. GraphQL resolver methods implemented as `suspend` functions
will be run in this scope. A `DELETE` call to
`/app/graphql?id=${requestId}` will cancel the scope of a running query.

```kotlin
class ExampleSuspendQuery : GraphQLQueryResolver {
    suspend fun coroutineMethod(): String {
        return coroutineScope {
            // new scope whose parent scope is the in GraphQLContext map
            val job1 = async {
                // do stuff asynchronously
                "value1"
            }
            val job2 = async {
                // do more stuff asynchronously, concurrently
                "value2"
            }
            "${job1.await()}:${job2.await()}"
        }
    }
}
```