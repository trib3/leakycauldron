testing
=======
Classes that enhance writing unit tests.

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