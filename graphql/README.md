Graphql
======
Provides the application infrastructure for adding [GraphQL](https://graphql.org) support 
to a [server](https://github.com/trib3/leakycauldron/blob/master/server) application.
* GraphQL endpoints:
  * UUID, java8 time, and threeten-extra [Scalars](https://github.com/trib3/leakycauldron/blob/master/graphql/src/main/kotlin/com/trib3/graphql/execution/LeakyCauldronHooks.kt)
  * [Request Ids](https://github.com/trib3/leakycauldron/blob/master/graphql/src/main/kotlin/com/trib3/graphql/execution/RequestIdInstrumentation.kt) 
    attached to the GraphQL response extensions
  * Any guice bound Query, Subscription or Mutation Resolvers
  * Supports websockets using the [Apollo Protocol](https://github.com/apollographql/subscriptions-transport-ws/blob/master/PROTOCOL.md)
  * Supports subscriptions via [coroutine](https://github.com/kotlin/kotlinx.coroutines/) Flows 
    for any Resolvers that return a `Publisher<T>`
* Admin:
  * [GraphiQL](https://github.com/graphql/graphiql) available at `/admin/graphiql`

#### Configuration
Configuration is done primarily though Guice.  [`GraphQLApplicationModule`](https://github.com/trib3/leakycauldron/blob/master/graphql/src/main/kotlin/com/trib3/graphql/modules/GraphQLApplicationModule.kt)
exposes binders for commonly bound objects.  

##### GraphQL Resolvers
[`GraphQLApplicationModule`](https://github.com/trib3/leakycauldron/blob/master/graphql/src/main/kotlin/com/trib3/graphql/modules/GraphQLApplicationModule.kt)
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