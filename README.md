[![CircleCI](https://circleci.com/gh/trib3/klibs.svg?style=svg&circle-token=75d8c0fddf399e7d6393730422d42be35ef4f3b2)](https://circleci.com/gh/trib3/klibs)
[![codecov](https://codecov.io/gh/trib3/klibs/branch/master/graph/badge.svg?token=MmCucLTttM)](https://codecov.io/gh/trib3/klibs)

trib3 klibs
=======

Description
-----------
The trib3 klibs are a collection of libraries used for building kotlin services.

Getting Started
---------------
#### Install jdk and maven
* Install openjdk 11 or newer
* Install maven 3.5 or newer
#### Build and run tests
* Building with maven will run tests
```
mvn clean install
```
* View test coverage reports for each module at ${module}/target/site/jacoco/index.html

* To use the trib3 klibs in a service of your own, see [this example](https://github.com/trib3/example-service)
  for a recommended project skeleton

Project Layout
--------------
Note that most modules make use of [Guice](https://github.com/google/guice) for 
dependency injection of configured instances.

* /build-resources

This contains resources for use during a maven build.  If depended upon, it should only be
brought into test scope.

* /parent-pom

This contains the parent pom to be used for all maven projects at Tribe.

* /json

This contains classes and [Guice](https://github.com/google/guice) modules for configuring 
[Jackson](https://github.com/FasterXML/jackson) objects to work with Tribe codebases.

* /config

This contains classes for parsing [HOCON](https://github.com/lightbend/config) application.conf
files with support for environmental overrides and AWS KMS-based encrypted values.

* /db

This contains classes for injecting configured [jOOQ](https://www.jooq.org) `DSLContext`s and 
JDBC `DataSource`s into application data access code.

* /server

This contains classes for setting up a configurable [Dropwizard](https://dropwizard.io) based 
application for running locally that can also be deployed to 
[AWS Lambda](https://aws.amazon.com/lambda/).
