package pl.pz.supplyblockchain.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class FlywayMigrationIT {

    @Autowired
    private DataSource dataSource;

    @Test
    void migrationsApplyAndTablesExist() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        int commands = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'commands'",
                Integer.class);
        int challenges = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'auth_challenges'",
                Integer.class);
        int sessions = jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = 'auth_sessions'",
                Integer.class);

        assertThat(commands).isEqualTo(1);
        assertThat(challenges).isEqualTo(1);
        assertThat(sessions).isEqualTo(1);
    }
}
