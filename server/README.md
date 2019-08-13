server
======
Provides the application infrastructure for running a [Dropwizard](https://dropwizard.io)
based application.  The application will be a [simple server](https://dropwizard.readthedocs.io/en/stable/manual/configuration.html#simple),
and have:
* [HOCON](https://github.com/trib3/leakycauldron/blob/master/server/src/main/kotlin/com/trib3/server/config/dropwizard/HoconConfigurationFactory.kt) 
  based configuration instead of the default .yaml based configuration
* Health checks:
  * [Ping](https://github.com/trib3/leakycauldron/blob/master/server/src/main/kotlin/com/trib3/server/healthchecks/PingHealthCheck.kt):
    A simple health check that will always return healthy
  * [Version](https://github.com/trib3/leakycauldron/blob/master/server/src/main/kotlin/com/trib3/server/healthchecks/VersionHealthCheck.kt):
    A health check that will return the version of the running application
  * Database: if a [DbModule](https://github.com/trib3/leakycauldron/blob/master/db#dbmodule) is installed, 
    [HikariCP](https://github.com/brettwooldridge/HikariCP/wiki/Dropwizard-HealthChecks)
    will report on the db health
* REST endpoints:
  * [Ping](https://github.com/trib3/leakycauldron/blob/master/server/src/main/kotlin/com/trib3/server/resources/PingResource.kt):
    A simple REST GET endpoint that will always return `pong`
  * [Request Ids](https://github.com/trib3/leakycauldron/blob/master/server/src/main/kotlin/com/trib3/server/filters/RequestIdFilter.kt) 
    attached to HTTP headers
  * Any guice bound JAX-RS Resources
* Admin:
  * The dropwizard `/admin` servlet will be [password protected](https://github.com/trib3/leakycauldron/blob/master/server/src/main/kotlin/com/trib3/server/filters/AdminAuthFilter.kt)
    with a password set from the `application.adminAuthToken` configuration variable 
    (or `ADMIN_AUTH_TOKEN` environment variable)
  * [Swagger UI](https://github.com/swagger-api/swagger-ui) available at `/admin/swagger`

#### Configuration
Configuration is done primarily though Guice.  [`TribeApplicationModule`](https://github.com/trib3/leakycauldron/blob/master/server/src/main/kotlin/com/trib3/server/modules/TribeApplicationModule.kt)
exposes binders for commonly bound objects.  
  
##### Application Guice Modules
The modules to create the guice injector are specified in `application.conf` 
under the `application.modules`configuration key.  

Example config:
```hocon
    application {
        modules: [
            "com.example.ExampleApplicationModule",
            "com.example.ExamplePersistenceModule"
        ]
    }
```

##### JAX-RS Resources
[`TribeApplicationModule`](https://github.com/trib3/leakycauldron/blob/master/server/src/main/kotlin/com/trib3/server/modules/TribeApplicationModule.kt)
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
To add [GraphQL](https://graphql.org) support, see [graphql](https://github.com/trib3/leakycauldron/blob/master/graphql)

#### Execution
Running a downstream application is most easily done by using a shaded `.jar`:
```bash
$ java -jar server/target/server-1.0-SNAPSHOT.jar
``` 
or by using the maven exec plugin:
```bash
$ mvn exec:java -Dexec.mainClass=com.trib3.server.TribeApplicationKt 
```

When running in [AWS Elastic Beanstalk](https://aws.amazon.com/elasticbeanstalk/), the
assembly configuration from [build-resources](https://github.com/trib3/leakycauldron/blob/master/build-resources)
can be used to create a `.zip` distribution that configures health checks and timeout
values.  With the preconfigured `Java 8` Elastic Beanstalk platform, the `PORT` environment 
property should be set to `5000` in the environment's software configuration.

For running in [AWS Lambda](https://aws.amazon.com/lambda/), see [lambda](https://github.com/trib3/leakycauldron/blob/master/lambda)