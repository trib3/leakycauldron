Db
==
Allows injection of configured [jOOQ](https://www.jooq.org) `DSLContext` or jdbc `DataSource`
instances into application code.  Also provides [jOOQ](https://www.jooq.org) `Converter` 
implementations for `java.time.YearMonth` and `org.threeten.extra.YearQuarter`.

DbModule
--------
Configures a `DataSource` and a `DSLContext` from the `db` section of config, using
[HikariCP](https://github.com/brettwooldridge/HikariCP) as a connection pool implementation.

Example config:
```hocon
    db {
        dialect: POSTGRES
        subprotocol: postgresql
        host: localhost
        port: 5432
        schema: localdb
        user: dbuser
        password: dbpass
        driverClassName: org.postgresql.Driver
        autoCommit: false
    }
```

Example application code injection:
```kotlin
class DAO
@Inject constructor(val ctx: DSLContext) {
   // ...
}
```

The following environment variables can be used to specify config without adding a `db`
section to `application.conf` explicitly:
* `DB_DIALECT`:  jOOQ dialect to use, defaults to `POSTGRES`
* `DB_SUBPROTOCOL`:  jdbc subprotocol to use, defaults to `postgresql`
* `DB_HOST`:  database hostname, defaults to `localhost`
* `DB_PORT`:  database port, defaults to `5432` (default postgres port)
* `DB_SCHEMA`:  database schema name, defaults to `""`
* `DB_USER`:  database user name, defaults to `tribe`
* `DB_PASSWORD`:  database password, defaults to no password
* `DB_DRIVER_CLASS_NAME`:  jdbc driver, defaults to `org.postgresql.Driver`
* `DB_AUTOCOMMTI`: connection autocommit status, defaults to `false`
* `DB_CONNECT_TIMEOUT`: connection timeout, defaults to `30000` 
* `DB_IDLE_TIMEOUT`: connection idle timeout, defaults to `600000`
* `DB_MAX_LIFETIME`: connection max lifetime, defaults to `1800000`
* `DB_CONNECTION_TEST_QUERY`: connection timeout, defaults to `null`
* `DB_MINIMUM_IDLE`: min number of idle connections, defaults to `10`
* `DB_MAXIMUM_POOL_SIZE`: max number of total connections, defaults to `10`

NamedDbModule
-------------
If an application needs to connect to multiple databases, `NamedDbModule` can be used
instead of `DbModule` to specify different configuration for each database.

Example config:
```hocon
    postgres {
        dialect: POSTGRES
        subprotocol: postgresql
        host: localhost
        port: 5432
        schema: localpostgresdb
        user: dbuser
        password: dbpass
        driverClassName: org.postgresql.Driver
    }
    mysql {
        dialect: MYSQL_8_0
        subprotocol: mysql
        host: localhost
        port: 3306
        schema: localmysqldb
        user: dbuser
        password: dbpass
        driverClassName: com.mysql.jdbc.Driver
    }
```

Example application code injection:
```kotlin
class PostgresDAO
@Inject constructor(@Named("postgres") val ctx: DSLContext) {
   // ...
}

class MysqlDAO
@Inject constructor(@Named("mysql") val ctx: DSLContext) {
   // ...
}
```
