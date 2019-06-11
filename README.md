[![CircleCI](https://circleci.com/gh/trib3/leakycauldron.svg?style=svg&circle-token=75d8c0fddf399e7d6393730422d42be35ef4f3b2)](https://circleci.com/gh/trib3/leakycauldron)
[![codecov](https://codecov.io/gh/trib3/leakycauldron/branch/master/graph/badge.svg?token=MmCucLTttM)](https://codecov.io/gh/trib3/leakycauldron)

trib3 leaky cauldron
=======

Description
-----------
Leaky Cauldron contains a collection of libraries used for building kotlin services.
It's where [drop]wizards get their guice!

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

* To use the Leaky Cauldron in a service of your own, see [this example](https://github.com/trib3/example-cauldron-service)
  for a recommended project skeleton

Project Layout
--------------
Note that most modules make use of [Guice](https://github.com/google/guice) for 
dependency injection of configured instances.

* [`/build-resources`](https://github.com/trib3/leakycauldron/tree/master/build-resources)

This contains resources for use during a maven build.  If depended upon, it should only be
brought into test scope.

* [`/parent-pom`](https://github.com/trib3/leakycauldron/tree/master/parent-pom)

This contains the parent pom to be used for all maven projects at Tribe.

* [`/json`](https://github.com/trib3/leakycauldron/tree/master/json)

This contains classes and [Guice](https://github.com/google/guice) modules for configuring 
[Jackson](https://github.com/FasterXML/jackson) objects to work with Tribe codebases.

* [`/config`](https://github.com/trib3/leakycauldron/tree/master/config)

This contains classes for parsing [HOCON](https://github.com/lightbend/config) application.conf
files with support for environmental overrides and AWS KMS-based encrypted values.

* [`/db`](https://github.com/trib3/leakycauldron/tree/master/db)

This contains classes for injecting configured [jOOQ](https://www.jooq.org) `DSLContext`s and 
JDBC `DataSource`s into application data access code.

* [`/server`](https://github.com/trib3/leakycauldron/tree/master/server)

This contains classes for setting up a configurable [Dropwizard](https://dropwizard.io) based 
application for running locally that can also be deployed to 
[AWS Lambda](https://aws.amazon.com/lambda/).
