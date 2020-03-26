Testing
=======
Classes that enhance writing unit tests.

### TestBase classes

#### DAOTestBase
Base class for writing DAO tests.  Uses [OpenTable Embedded PostgreSQL](https://github.com/opentable/otj-pg-embedded/)
to set up a [jOOQ](https://www.jooq.org) `DSLContext` and a jdbc `DataSource` that can 
be used by tests, and runs any [Flyway](https://flywaydb.org) migrations found on the 
classpath.

#### ResourceTestBase
Base class for writing Resource tests.  Implement `getResource()` to return the
JAX-RS resource being tested.  Uses an `InMemoryTestContainer` by default, but can be 
overriden by implementing `getContainerFactory()`.  If additional jersey resources need
to be added to the container, override `buildAdditionalResources()` to configure them.


### Utility classes

#### JettyWebTestContainerFactory
A `TestContainerFactory` that supports servlet features (eg, injection of 
`@Context HttpServletRequest` params, etc) on top of a [Jetty](https://www.eclipse.org/jetty/) 
web server.

#### LeakyMock
Enhancements to [EasyMock](http://easymock.org/) for a more usable kotlin experience.  Makes mock
creation more concise, and provides a number of matchers that return non-null values to work better
with kotlin's nullability checks.

Create mocks with type inference (Avoid this [issue](https://github.com/easymock/easymock/issues/239)):
```kotlin
// all these options also work for niceMock
val mock = LeakyMock.mock<ClassToMock>() // mock will be inferred to be of type ClassToMock
val mock2 = LeakyMock.mock(ClassToMock::class.java) // mock2 will also be of type ClassToMock
val mock3: ClassToMock = LeakyMock.mock() // mock3 will also be of type ClassToMock
val support = EasyMockSupport()
val mock4 = support.mock<ClassToMock>() // mock 4 is a ClassToMock, and tracked by support
// compare this with:
val nonLeakyMock = EasyMock.mock<ClassToMock>(ClassToMock::class.java)
```
Match on objects without violating kotlin null checks (Avoid `EasyMock.anyObject() must not be null`):
```kotlin
EasyMock.expect(mock.doSomethingWithNonNullable(LeakyMock.anyObject())).and ...
// compare this with:
EasyMock.expect(mock.doSomethingWithNonNullable(EasyMock.anyObject() ?: instance)).and ...
```