package no.vegvesen.nvdb.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import no.vegvesen.nvdb.database.RoadnetTable
import no.vegvesen.nvdb.database.SpeedLimitTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun Application.configureDatabase() {
    val databaseUrl =
        environment.config.propertyOrNull("database.url")?.getString()
            ?: "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
    val databaseUser = environment.config.propertyOrNull("database.user")?.getString() ?: "sa"
    val databasePassword = environment.config.propertyOrNull("database.password")?.getString() ?: ""

    val hikariConfig =
        HikariConfig().apply {
            jdbcUrl = databaseUrl
            username = databaseUser
            password = databasePassword
            driverClassName =
                when {
                    databaseUrl.contains("postgresql") -> "org.postgresql.Driver"
                    databaseUrl.contains("h2") -> "org.h2.Driver"
                    else -> "org.h2.Driver"
                }
            maximumPoolSize = 10
        }

    val dataSource = HikariDataSource(hikariConfig)
    Database.connect(dataSource)

    transaction {
        SchemaUtils.create(RoadnetTable, SpeedLimitTable)
    }
}
