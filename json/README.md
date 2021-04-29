Json
====
Provides an injectable `ObjectMapper` that is configured to support:

* Dropwizard object mapper [defaults](https://github.com/dropwizard/dropwizard/blob/HEAD/dropwizard-jackson/src/main/java/io/dropwizard/jackson/Jackson.java)
* kotlin data classes via the [jackson kotlin module](https://github.com/FasterXML/jackson-module-kotlin)
* java8 time classes (and [threeten-extra](https://github.com/ThreeTen/threeten-extra) `YearQuarter`s)
* permissiveness on unknown properties (`FAIL_ON_UNKNOWN_PROPERTIES` set to false)
* @JacksonInject [delegation to guice bindings](https://github.com/FasterXML/jackson-modules-base/tree/master/guice/)

##### ThreeTenExtraModule

A jackson module that provides support for serializing and deserializing
[threeten-extra](https://github.com/ThreeTen/threeten-extra) `YearQuarter`s. 
