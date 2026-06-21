package net.earelin.mercator.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.db.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.assertj.db.type.AssertDbConnection;
import org.assertj.db.type.AssertDbConnectionFactory;
import org.assertj.db.type.Table;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the canonical {@code role} vocabulary seeded by {@code V1.0.0__baseline.sql} against a
 * real Postgres 18 with the full schema applied by Flyway from this module's {@code db/migration}:
 * the seed is complete and the {@code appointment.role} foreign key rejects any role outside the
 * canonical set.
 */
@Testcontainers
class RoleSeedTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.4");

    private static DataSource dataSource;
    private static AssertDbConnection assertDb;

    @BeforeAll
    static void migrate_schema() {
        PGSimpleDataSource ds = new PGSimpleDataSource();
        ds.setUrl(POSTGRES.getJdbcUrl());
        ds.setUser(POSTGRES.getUsername());
        ds.setPassword(POSTGRES.getPassword());
        dataSource = ds;
        Flyway.configure().dataSource(ds).load().migrate();
        assertDb = AssertDbConnectionFactory.of(ds).create();
    }

    @Test
    void seeds_the_sixteen_canonical_roles() {
        Table table = assertDb.table("role").build();
        assertThat(table).hasNumberOfRows(16);
    }

    @Test
    void contains_representative_canonical_codes() throws SQLException {
        assertThat(roleCodes()).contains("ADM_UNICO", "APODERADO", "SOCIO_UNICO");
    }

    @Test
    void foreign_key_rejects_a_role_outside_the_canonical_set() {
        assertThatThrownBy(() -> insertAppointmentWithRole("NOT_A_ROLE"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("fk_appointment_role");
    }

    @Test
    void foreign_key_accepts_a_canonical_role() throws SQLException {
        insertAppointmentWithRole("ADM_UNICO");

        assertThat(assertDb.table("appointment").build()).hasNumberOfRows(1);
    }

    private static java.util.List<String> roleCodes() throws SQLException {
        java.util.List<String> codes = new java.util.ArrayList<>();
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                java.sql.ResultSet rs = statement.executeQuery("SELECT code FROM role")) {
            while (rs.next()) {
                codes.add(rs.getString("code"));
            }
        }
        return codes;
    }

    /**
     * Inserts a minimal company / person / borme_act graph and then an appointment with the given
     * role, so only the {@code fk_appointment_role} constraint is under test.
     */
    private static void insertAppointmentWithRole(String role) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(
                    "INSERT INTO company (id, raw_name, norm_name, province_code, first_seen, last_seen) "
                            + "VALUES (1, 'Acme SL', 'ACME', '36', DATE '2024-01-01', DATE '2024-01-01') "
                            + "ON CONFLICT DO NOTHING");
            statement.execute(
                    "INSERT INTO person (id, raw_name, norm_name) VALUES (1, 'Jane Doe', 'JANE DOE') "
                            + "ON CONFLICT DO NOTHING");
            statement.execute(
                    "INSERT INTO borme_act (id, borme_id, pub_date, province_code, company_id, act_type, doc_seq, raw_block) "
                            + "VALUES (1, 'BORME-A-2024-1-36', DATE '2024-01-01', '36', 1, 'NOMBRAMIENTOS', 0, 'raw') "
                            + "ON CONFLICT DO NOTHING");
            statement.execute(
                    "INSERT INTO appointment (company_id, person_id, role, event_type, act_id, valid_from) "
                            + "VALUES (1, 1, '" + role + "', 'NOMBRAMIENTO', 1, DATE '2024-01-01')");
        }
    }
}
