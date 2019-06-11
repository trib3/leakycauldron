server
======
Provides the application infrastructure for running a [Dropwizard](https://dropwizard.io)
based application.  The application will be a [simple server](https://dropwizard.readthedocs.io/en/stable/manual/configuration.html#simple),
and have:
* [HOCON](https://github.com/trib3/klibs/blob/master/server/src/main/kotlin/com/trib3/server/config/dropwizard/HoconConfigurationFactory.kt) 
  based configuration instead of the default .yaml based configuration
* Health checks:
  * [Ping](https://github.com/trib3/klibs/blob/master/server/src/main/kotlin/com/trib3/server/healthchecks/PingHealthCheck.kt):
    A simple health check that will always return healthy
  * [Version](https://github.com/trib3/klibs/blob/master/server/src/main/kotlin/com/trib3/server/healthchecks/VersionHealthCheck.kt):
    A health check that will return the version of the running application
  * Database: if a database is configured, [HikariCP](https://github.com/brettwooldridge/HikariCP/wiki/Dropwizard-HealthChecks)
    will report on the db health
* REST endpoints:
  * [Ping](https://github.com/trib3/klibs/blob/master/server/src/main/kotlin/com/trib3/server/resources/PingResource.kt):
    A simple REST GET endpoint that will always return `pong`
  * [Request Ids](https://github.com/trib3/klibs/blob/master/server/src/main/kotlin/com/trib3/server/filters/RequestIdFilter.kt) 
    attached to HTTP headers
  * Any guice bound JAX-RS Resources
* GraphQL endpoints:
  * java8 time and threeten-extra [Scalars](https://github.com/trib3/klibs/blob/master/server/src/main/kotlin/com/trib3/server/graphql/DateTimeHooks.kt)
  * [Request Ids](https://github.com/trib3/klibs/blob/master/server/src/main/kotlin/com/trib3/server/graphql/RequestIdInstrumentation.kt) 
    attached to the GraphQL response extensions
  * Any guice bound Query or Mutation Resolvers
* Admin:
  * The dropwizard `/admin` servlet will be [password protected](https://github.com/trib3/klibs/blob/master/server/src/main/kotlin/com/trib3/server/filters/AdminAuthFilter.kt)
    with a password set from the `application.adminAuthToken` configuration variable 
    (or `ADMIN_AUTH_TOKEN` environment variable)
  * [Swagger UI](https://github.com/swagger-api/swagger-ui) available at `/admin/swagger`
  * [GraphiQL](https://github.com/graphql/graphiql) available at `/admin/graphiql`

#### Configuration
Configuration is done primarily though Guice.  [`TribeApplicationModule`](https://github.com/trib3/klibs/blob/master/server/src/main/kotlin/com/trib3/server/modules/TribeApplicationModule.kt)
exposes binders for commonly bound objects.  
  
##### Application Guice Modules
The modules to create the guice injector are specified in `application.conf` 
under the `application.modules`configuration key.  

Example config:
```hocon
{
    application {
        modules: [
            "com.example.ExampleApplicationModule",
            "com.example.ExamplePersistenceModule"
        ]
    }
}
```

##### JAX-RS Resources
[`TribeApplicationModule`](https://github.com/trib3/klibs/blob/master/server/src/main/kotlin/com/trib3/server/modules/TribeApplicationModule.kt)
provides a method that exposes a multi-binder for JAX-RS Resources.  Any Resource classes
should be bound using this binder.

```kotlin
class ExampleApplicationModule : TribeApplicationModule() {
    override fun configure() {
        // ...
        resourceBinder().addBinding().to<com.example.server.resources.ExampleResource>()
        // ...
    }
}
```

##### GraphQL Resolvers
[`TribeApplicationModule`](https://github.com/trib3/klibs/blob/master/server/src/main/kotlin/com/trib3/server/modules/TribeApplicationModule.kt)
provides methods that expose multi-binders for configuring GraphQL resolvers.  Any model
classes must be added to the `graphqlPackagesBinder()` to allow [GraphQL Kotlin](https://github.com/ExpediaDotCom/graphql-kotlin/)
to use them.  Query Resolver implementations can be added to the `graphqlQueriesBinder()`
and Mutations to the `graphqlMutationsBinder()` 

```kotlin
class ExampleApplicationModule : TribeApplicationModule() {
    override fun configure() {
        // ...
        graphqlPackagesBinder().addBinding().toInstance("com.example.api")
        graphqlPackagesBinder().addBinding().toInstance("com.example.server.graphql")

        graphqlQueriesBinder().addBinding().to<com.example.server.graphql.Query>()
        graphqlMutationsBinder().addBinding().to<com.example.server.graphql.Mutation>()
        // ...
    }
}
```

#### Execution
Running a downstream application is most easily done by using a shaded `.jar`:
```bash
$ java -jar server/target/server-1.0-SNAPSHOT.jar
``` 
or by using the maven exec plugin:
```bash
$ mvn exec:java -Dexec.mainClass=com.trib3.server.TribeApplicationKt 
```
The shaded `.jar` can also be uploaded to [AWS Lambda](https://aws.amazon.com/lambda/) to
execute behind the Amazon API Gateway.  If doing so, it is recommended to add `lambda`
to the active environment list.

When running in [AWS Elastic Beanstalk](https://aws.amazon.com/elasticbeanstalk/), the
assembly configuration from [build-resources](https://github.com/trib3/klibs/blob/master/build-resources)
can be used to create a `.zip` distribution that configures health checks and timeout
values.