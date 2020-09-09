Graphql
======
Provides the application infrastructure for adding [GraphQL](https://graphql.org) support 
to a [server](https://github.com/trib3/leakycauldron/blob/HEAD/server) application.
* GraphQL endpoints:
  * UUID, java8 time, and threeten-extra [Scalars](https://github.com/trib3/leakycauldron/blob/HEAD/graphql/src/main/kotlin/com/trib3/graphql/execution/LeakyCauldronHooks.kt)
  * [Request Ids](https://github.com/trib3/leakycauldron/blob/HEAD/graphql/src/main/kotlin/com/trib3/graphql/execution/RequestIdInstrumentation.kt) 
    attached to the GraphQL response extensions
  * Any guice bound Query, Subscription or Mutation Resolvers
  * Supports websockets using the [Apollo Protocol](https://github.com/apollographql/subscriptions-transport-ws/blob/HEAD/PROTOCOL.md)
  * Supports subscriptions via [coroutine](https://github.com/kotlin/kotlinx.coroutines/) Flows 
    for any Resolvers that return a `Publisher<T>`
  * Supports [Dropwizard Authentication](https://www.dropwizard.io/en/latest/manual/auth.html) Principals
    passed through to Resolvers via [GraphQLContext](https://github.com/ExpediaGroup/graphql-kotlin/blob/HEAD/graphql-kotlin-schema-generator/src/main/kotlin/com/expediagroup/graphql/execution/GraphQLContext.kt)
  * Supports [coroutine](https://github.com/kotlin/kotlinx.coroutines/) structured concurrency and cancellation
    of POSTed GraphQL queries for Resolvers implemented as `suspend` functions
* Admin:
  * [GraphiQL](https://github.com/graphql/graphiql) available at `/admin/graphiql`

#### Configuration
Configuration is done primarily though Guice.  [`GraphQLApplicationModule`](https://github.com/trib3/leakycauldron/blob/HEAD/graphql/src/main/kotlin/com/trib3/graphql/modules/GraphQLApplicationModule.kt)
exposes binders for commonly bound objects.  

##### GraphQL Resolvers
[`GraphQLApplicationModule`](https://github.com/trib3/leakycauldron/blob/HEAD/graphql/src/main/kotlin/com/trib3/graphql/modules/GraphQLApplicationModule.kt)
provides methods that expose multi-binders for configuring GraphQL resolvers.  Any model
classes must be added to the `graphQLPackagesBinder()` to allow [GraphQL Kotlin](https://github.com/ExpediaDotCom/graphql-kotlin/)
to use them.  Query Resolver implementations can be added to the `graphQLQueriesBinder()`, 
Subscriptions to the `graphQLSubscriptionsBinder()`, and Mutations to the `graphQLMutationsBinder()` 

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

#### Auth
If Dropwizard Authentication is setup per the [server README](https://github.com/trib3/leakycauldron/blob/HEAD/server/README.md#auth),
GraphQL resolver methods can receive the principal inside the GraphQL context of type
`GraphQLResourceContext`.  Resolver methods can write to the `cookie` field of the context 
object they receive in order to set cookies on the client (useful when, for example, using
a `CookieTokenAuthFilter` for auth).

```kotlin
class ExampleLoginMutations : GraphQLQueryResolver {
    fun login(context: GraphQLResourceContext, email: String, pass: String): Boolean {
        if (context.principal == null) {
            // log in!
            val userSession = authenticate(email, pass)
            if (userSession == null) {
                return false
            }
            // cookie will be set in response
            context.cookie = NewCookie("example-app-session-id", userSession.id)
        } else {
            // already logged in
        }
        return true
    }

    fun logout(context: GraphQLResourceContext): Boolean {
        if (context.principal != null) {
            deleteSession(context.principal)
            context.cookie =
                NewCookie(
                    Cookie("example-app-session-id", ""),
                    null,
                    -1,
                    Date(0), // 1970
                    false,
                    false
                )
        }
        return true
    }
}
```

#### GraphQLResourceContext CoroutineScope
`GraphQLResourceContext` implements `CoroutineScope`.  GraphQL resolver methods
implemented as `suspend` functions will be run in this scope.  A `DELETE` call to 
`/app/graphql?id=${requestId}` will cancel the scope of a running query. 

```kotlin
class ExampleSuspendQuery : GraphQLQueryResolver {
    suspend fun coroutineMethod(): String {
        return coroutineScope {
            // new scope whose parent scope is the GraphQLResourceContext object
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