package no.vegvesen.nvdb.tnits.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.vegvesen.nvdb.tnits.database.DirtyVeglenkesekvenser
import no.vegvesen.nvdb.tnits.database.KeyValue
import no.vegvesen.nvdb.tnits.database.Stedfestinger
import no.vegvesen.nvdb.tnits.database.Vegobjekter
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

const val FETCH_SIZE = 1_000

fun configureDatabase(config: AppConfig) {
    val databaseConfig = config.database

    val hikariConfig =
        HikariConfig().apply {
            jdbcUrl = databaseConfig.url
            username = databaseConfig.user
            password = databaseConfig.password

            // Fix for Oracle JDBC JSON handling - ORA-18722 error
            if (jdbcUrl.contains("oracle")) {
                addDataSourceProperty("oracle.jdbc.jsonDefaultGetObjectType", "java.lang.String")
            }
        }

    val dataSource = HikariDataSource(hikariConfig)
    Database.connect(
        dataSource,
        databaseConfig =
        DatabaseConfig {
            defaultFetchSize = FETCH_SIZE
        },
    )

    transaction {
        SchemaUtils.create(Vegobjekter, KeyValue, Stedfestinger, DirtyVeglenkesekvenser)
    }
}
