server
======
Provides the application infrastructure for running a [Dropwizard](https://dropwizard.io)
based application. The application will be
a [simple server](https://dropwizard.readthedocs.io/en/stable/manual/configuration.html#simple), and have:

* [HOCON](https://github.com/trib3/leakycauldron/blob/HEAD/server/src/main/kotlin/com/trib3/server/config/dropwizard/HoconConfigurationFactory.kt)
  based configuration instead of the default .yaml based configuration
* Health checks:
  * [Ping](https://github.com/trib3/leakycauldron/blob/HEAD/server/src/main/kotlin/com/trib3/server/healthchecks/PingHealthCheck.kt):
    A simple health check that will always return healthy
  * [Version](https://github.com/trib3/leakycauldron/blob/HEAD/server/src/main/kotlin/com/trib3/server/healthchecks/VersionHealthCheck.kt):
    A health check that will return the version of the running application
  * Database: if a [DbModule](https://github.com/trib3/leakycauldron/blob/HEAD/db#dbmodule) is installed,
    [HikariCP](https://github.com/brettwooldridge/HikariCP/wiki/Dropwizard-HealthChecks)
    will report on the db health
* REST endpoints:
  * [Ping](https://github.com/trib3/leakycauldron/blob/HEAD/server/src/main/kotlin/com/trib3/server/resources/PingResource.kt):
    A simple REST GET endpoint that will always return `pong`
  * [Request Ids](https://github.com/trib3/leakycauldron/blob/HEAD/server/src/main/kotlin/com/trib3/server/filters/RequestIdFilter.kt)
    attached to HTTP headers
  * Any guice bound JAX-RS Resources
  * JAX-RS Resource methods implementable
    via [suspend functions/coroutines](https://github.com/trib3/leakycauldron/blob/HEAD/server/src/main/kotlin/com/trib3/server/coroutine/CoroutineInvocationHandler.kt)
* Admin:
  * The dropwizard `/admin` servlet will
    be [password protected](https://github.com/trib3/leakycauldron/blob/HEAD/server/src/main/kotlin/com/trib3/server/filters/AdminAuthFilter.kt)
    with a password set from the `application.adminAuthToken` configuration variable
    (or `ADMIN_AUTH_TOKEN` environment variable)
  * [Swagger UI](https://github.com/swagger-api/swagger-ui) available at `/admin/swagger`

#### Configuration

Configuration is done primarily though
Guice.  [`TribeApplicationModule`](https://github.com/trib3/leakycauldron/blob/HEAD/server/src/main/kotlin/com/trib3/server/modules/TribeApplicationModule.kt)
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

[`TribeApplicationModule`](https://github.com/trib3/leakycauldron/blob/HEAD/server/src/main/kotlin/com/trib3/server/modules/TribeApplicationModule.kt)
provides a method that exposes a multi-binder for JAX-RS Resources. Any Resource classes should be bound using this
binder.

```kotlin
class ExampleApplicationModule : TribeApplicationModule() {
  override fun configure() {
    // ...
    resourceBinder().addBinding().to<com.example.server.resources.ExampleResource>()
    // ...
  }
}
```

##### Cloudwatch Metrics Reporter

[`CloudWatchReporterFactory`](https://github.com/trib3/leakycauldron/blob/HEAD/server/src/main/kotlin/com/trib3/server/config/dropwizard/CloudWatchReporterFactory.kt)
allows for configuration based specification of a
[CloudWatch Reporter](https://github.com/azagniotov/codahale-aggregated-metrics-cloudwatch-reporter). In addition to the
below example config, an application using the `CloudWatchReporterFactory` must make a `CloudWatchAsyncClient`
injectable via guice, for example:

```kotlin
bind<CloudWatchAsyncClient>()
  .toInstance(
    CloudWatchAsyncClient.builder().region(Region.US_EAST_1).build()
  )
```

Example config:

```hocon
metrics: {
    frequency: 10 seconds
    reporters: [{
        includes: [] # use to control which metrics to report on
        excludes: [] # use to control which metrics to report on
        type: cloudwatch
        namespace: null # defaults to TribeApplicationConfig.env
        globalDimensions: ['dim1=val1', 'dim2=val2'] # adds Application=TribeApplicationConfig.appName,Hostname=InetAddress.getLocalHost().hostname
        percentiles: [P75, P95, P999]
        dryRun: false
        meanRate: false # applies to Meters and Timers
        oneMinuteMeanRate: false # applies to Meters and Timers
        fiveMinuteMeanRate: false # applies to Meters and Timers
        fifteenMinuteMeanRate: false # applies to Meters and Timers
        arithmeticMean: false # applies to Histograms and Timers
        stdDev: false # applies to Histograms and Timers
        statisticSet: false # applies to Histograms and Timers
        zeroValueSubmission: false
        highResolution: false
        reportRawCountValue: false
        jvmMetrics: false
        meterUnit: null # defaults to durationUnit
    }]
}
```

#### Auth

Authentication and authorization can be implemented
via [Dropwizard Authentication](https://www.dropwizard.io/en/latest/manual/auth.html)
by binding an `AuthFilter` with an `Authenticator` (and optionally an `Authorizer`)
in the Guice injector. Binding and registration of `AuthDynamicFeature`, `RolesAllowedDynamicFeature`
and `AuthValueFactoryProvider.Binder(Principal::class.java)` are done by default, so `@Auth`
annotations on resource method parameterss are supported once the `AuthFilter` is registered.

In addition, a `CookieTokenAuthFilter` implementation is provided for reading a session token out of a configured cookie
value.

```kotlin
class ExampleCookieAuthedApplicationModule : TribeApplicationModule() {
  override fun configure() {
    // ...
    bind<Authenticator<String?, Principal>>().to<com.example.server.auth.ExampleSessionAuthenticator>()
    bind<Authorizer<Principal>>().to<com.example.server.auth.ExampleUserAuthorizer>()

    // can also use a ChainedAuthFilter<Any, Principal> to add BasicAuthFilter/OAuthCredentialAuthFilter/etc
    authFilterBinder().setBinding().toInstance(
      CookieTokenAuthFilter.Builder<Principal>("example-app-session-id")
        .setAuthenticator(authenticator)
        .setAuthorizer(authorizer)
        .buildAuthFilter()
    )
    // ...
  }
}
```

##### GraphQL Resolvers

To add [GraphQL](https://graphql.org) support, see [graphql](https://github.com/trib3/leakycauldron/blob/HEAD/graphql)

#### Execution

Running a downstream application is most easily done by using a shaded `.jar`:

```bash
$ java -jar server/target/server-1.0-SNAPSHOT.jar
``` 

or by using the maven exec plugin:

```bash
$ mvn exec:java -Dexec.mainClass=com.trib3.server.TribeApplicationKt 
```

When running in [AWS Elastic Beanstalk](https://aws.amazon.com/elasticbeanstalk/), the assembly configuration
from [build-resources](https://github.com/trib3/leakycauldron/blob/HEAD/build-resources)
can be used to create a `.zip` distribution that configures health checks and timeout values. With the
preconfigured `Java 8` Elastic Beanstalk platform, the `PORT` environment property should be set to `5000` in the
environment's software configuration.

For running in [AWS Lambda](https://aws.amazon.com/lambda/),
see [lambda](https://github.com/trib3/leakycauldron/blob/HEAD/lambda)
