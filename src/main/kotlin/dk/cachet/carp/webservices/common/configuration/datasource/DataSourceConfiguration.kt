package dk.cachet.carp.webservices.common.configuration.datasource

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.*
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@Configuration
@ComponentScan(basePackages = ["dk.cachet.carp.webservices"])
@PropertySources(PropertySource(value = ["classpath:config/application.yml"]))
@ConfigurationProperties(prefix = "spring.datasource")
@Suppress("LongParameterList")
class DataSourceConfiguration(
    @param:Value("\${spring.datasource.url}") private val datasourceUrl: String,
    @param:Value("\${spring.datasource.driver-class-name}") private val dbDriverClassName: String,
    @param:Value("\${spring.datasource.username}") private val dbUsername: String,
    @param:Value("\${spring.datasource.password}") private val dbPassword: String,
    @param:Value("\${spring.datasource.hikari.pool-name}") private val carpHikariPool: String,
    @param:Value("\${spring.datasource.hikari.maximum-pool-size}") private val maxPoolSize: Number,
    @param:Value("\${spring.datasource.hikari.max-lifetime}") private val maxLifetime: Number,
    @param:Value("\${spring.datasource.hikari.minimum-idle}") private val minimumIdle: Number,
    @param:Value("\${spring.datasource.hikari.connection-timeout}") private val connectionTimeout: Number,
    @param:Value("\${spring.datasource.hikari.leak-detection-threshold}") private val leakDetectionThreshold: Number,
    @param:Value("\${spring.datasource.hikari.idle-timeout}") private val idleTimeout: Number,
) : HikariConfig() {
    companion object DefaultDataSourceProperties {
        private const val SOCKET_TIMEOUT_MINUTES: Long = 15
        private const val CHARACTER_ENCODING = "utf-8"
        private const val AUTO_RECONNECT = true
        private const val USE_UNICODE = true
    }

    @Bean
    @Qualifier("dataSource")
    fun dataSource(): DataSource {
        val hikariConfig = HikariConfig()
        hikariConfig.driverClassName = dbDriverClassName
        hikariConfig.jdbcUrl = datasourceUrl
        hikariConfig.username = dbUsername
        hikariConfig.password = dbPassword
        hikariConfig.poolName = carpHikariPool

        hikariConfig.maximumPoolSize = maxPoolSize.toInt()
        hikariConfig.idleTimeout = idleTimeout.toLong()
        hikariConfig.maxLifetime = maxLifetime.toLong()
        hikariConfig.minimumIdle = minimumIdle.toInt()
        hikariConfig.connectionTimeout = connectionTimeout.toLong()
        hikariConfig.leakDetectionThreshold = leakDetectionThreshold.toLong()

        hikariConfig.addDataSourceProperty("useUnicode", USE_UNICODE)
        hikariConfig.addDataSourceProperty("characterEncoding", CHARACTER_ENCODING)
        hikariConfig.addDataSourceProperty("autoReconnect", AUTO_RECONNECT)
        hikariConfig.addDataSourceProperty(
            "socketTimeout",
            TimeUnit.SECONDS.convert(SOCKET_TIMEOUT_MINUTES, TimeUnit.MINUTES).toInt(),
        )

        hikariConfig.connectionTestQuery = "select 1"

        return HikariDataSource(hikariConfig)
    }
}
