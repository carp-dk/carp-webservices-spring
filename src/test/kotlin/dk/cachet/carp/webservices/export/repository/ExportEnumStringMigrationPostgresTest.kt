package dk.cachet.carp.webservices.export.repository

import dk.cachet.carp.webservices.export.domain.ExportStatus
import dk.cachet.carp.webservices.export.domain.ExportType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import javax.sql.DataSource
import kotlin.test.assertEquals

/**
 * Verifies V14, which converts the enum ordinals Hibernate used to store in `exports.status` and `exports.type`
 * into the constant names that the `EnumType.STRING` mapping now reads and writes.
 */
@Testcontainers(disabledWithoutDocker = true)
class ExportEnumStringMigrationPostgresTest {
    private lateinit var jdbc: JdbcTemplate
    private lateinit var dataSource: DataSource

    @BeforeEach
    fun setup() {
        dataSource =
            DriverManagerDataSource().apply {
                setDriverClassName(postgres.driverClassName)
                url = postgres.jdbcUrl
                username = postgres.username
                password = postgres.password
            }
        jdbc = JdbcTemplate(dataSource)
        jdbc.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public")
        // The two columns as V1 (status) and V4 (type) created them.
        jdbc.execute(
            "CREATE TABLE exports (id VARCHAR(255) PRIMARY KEY, status VARCHAR(255), type VARCHAR(255))",
        )
    }

    private fun runMigration() {
        dataSource.connection.use { c ->
            ScriptUtils.executeSqlScript(c, ClassPathResource("db/migration/V14__store_export_enums_as_strings.sql"))
        }
    }

    private fun insert(
        id: String,
        status: String?,
        type: String?,
    ) = jdbc.update("INSERT INTO exports (id, status, type) VALUES (?, ?, ?)", id, status, type)

    private fun statusOf(id: String) = columnOf("status", id)

    private fun typeOf(id: String) = columnOf("type", id)

    private fun columnOf(
        column: String,
        id: String,
    ) = jdbc.queryForObject("SELECT $column FROM exports WHERE id = ?", String::class.java, id)

    @Test
    fun `converts every status ordinal to its name`() {
        STATUS_ORDINALS.keys.forEach { insert("status-$it", it, "0") }

        runMigration()

        STATUS_ORDINALS.forEach { (ordinal, status) -> assertEquals(status.name, statusOf("status-$ordinal")) }
    }

    @Test
    fun `converts every type ordinal to its name`() {
        TYPE_ORDINALS.keys.forEach { insert("type-$it", "0", it) }

        runMigration()

        TYPE_ORDINALS.forEach { (ordinal, type) -> assertEquals(type.name, typeOf("type-$ordinal")) }
    }

    @Test
    fun `sets missing values to UNKNOWN`() {
        insert("legacy", null, null)

        runMigration()

        assertEquals(ExportStatus.UNKNOWN.name, statusOf("legacy"))
        assertEquals(ExportType.UNKNOWN.name, typeOf("legacy"))
    }

    @Test
    fun `leaves values that are already names unchanged`() {
        insert("named", ExportStatus.AVAILABLE.name, ExportType.ANONYMOUS_PARTICIPANTS.name)

        runMigration()

        assertEquals(ExportStatus.AVAILABLE.name, statusOf("named"))
        assertEquals(ExportType.ANONYMOUS_PARTICIPANTS.name, typeOf("named"))
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17-alpine")

        // The ordinals as they were stored before V14. Spelled out rather than derived from the enums, so that
        // reordering a constant later (safe now that names are stored) doesn't break this historical mapping.
        private val STATUS_ORDINALS =
            mapOf(
                "0" to ExportStatus.UNKNOWN,
                "1" to ExportStatus.IN_PROGRESS,
                "2" to ExportStatus.AVAILABLE,
                "3" to ExportStatus.ERROR,
                "4" to ExportStatus.EXPIRED,
            )
        private val TYPE_ORDINALS =
            mapOf(
                "0" to ExportType.UNKNOWN,
                "1" to ExportType.STUDY_DATA,
                "2" to ExportType.DEPLOYMENT_DATA,
                "3" to ExportType.ANONYMOUS_PARTICIPANTS,
            )
    }
}
