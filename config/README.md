Config
======
Extends [config4k](https://github.com/config4k/config4k)'s 
[HOCON](https://github.com/lightbend/config) wrapper with environmental overrides
and the ability to read [AWS KMS](https://aws.amazon.com/kms/) encrypted values.

Generally, a maven module will provide a Config object that encapsulates usage of the 
config module and exposes the raw configuration values needed.  For example, see the 
`db` module's
[DbConfig](https://github.com/trib3/leakycauldron/blob/master/db/src/main/kotlin/com/trib3/db/config/DbConfig.kt).

Environmental Overrides
-----------------------
Configuration files loaded by 
[`ConfigLoader`](https://github.com/trib3/leakycauldron/blob/master/config/src/main/kotlin/com/trib3/config/ConfigLoader.kt)
support environmental overrides.  Files will first be loaded through standard means 
(`application.conf` will be loaded with fallback to `reference.conf`), and then the value 
of the `ENV` environment variable (or `env` config value) will be read to specify a comma
delimited list of environments to apply.  Any config blocks that match the active environments
will be applied, in order, to override the config.  In addition, any `overrides` block that 
exists will be applied to override all other configuration.

Example config:
```hocon
    abc: def
    ghi: klm

    staging {
        abc: stagingdef
    }
    overrides: {
        ghi: ${?GHI_ENV_VAR}
    }
```
 The above config will:
 * for `abc`: return "stagingdef" when run in staging, else will return "def"
 * for `ghi`: return "klm" unless the GHI_ENV_VAR is set, in which case it will return its value


AWS KMS Encrypted Values
------------------------
Configuration string values read by 
[`Config.extract(path: String)`](https://github.com/trib3/leakycauldron/blob/master/config/src/main/kotlin/com/trib3/config/Extension.kt) 
support reading AWS KMS-encrypted strings.  Any string encoded as `KMS(ciphertext)` will be
decoded through KMS before being returned.  Note that in order to successfully decrypt a 
KMS-encrypted value, the String itself must be `extract()`ed directly, and not nested within
an object being `extract()`ed.

Example config:
```hocon
    secretKey: KMS(ciphertext)
```
Example extract code:
```kotlin
    val config = configLoader.load()
    val plaintextKey = config.extract<String>("secretKey")
```
