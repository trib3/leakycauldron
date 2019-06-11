build-resources
===============
Provides the following resources for use during the build process:
* `logback-test.xml`:  A logback config file for formatting log output during the running 
  of unit tests, included in `test` scope by default.
* `detekt-config.yml`: Configuration for the [detekt](https://arturbosch.github.io/detekt/)
  static analysis tool run during the maven `verify` build phase.
* `assemblies/elasticbeanstalk.xml` / `.ebextensions`:  Configuration for creating an 
  assembly for running a Leaky Cauldron based project in 
  [AWS ElasticBeanstalk](https://aws.amazon.com/elasticbeanstalk/), with increased 
  timeout settings and ELB health check configuration.
  
