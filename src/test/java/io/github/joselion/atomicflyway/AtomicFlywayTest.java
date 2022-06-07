package io.github.joselion.atomicflyway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.h2.jdbc.JdbcSQLSyntaxErrorException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.joselion.testing.annotations.UnitTest;
import io.github.joselion.testing.migrations.V001CreateAccountTable;
import io.github.joselion.testing.migrations.V002AddCreatedAtToAccount;
import picocli.CommandLine;
import uk.org.webcompere.systemstubs.SystemStubs;

@UnitTest class AtomicFlywayTest {

  @Nested class attach {
    @Nested class migrate {
      @Nested class when_the_migrate_option_is_passed {
        @Test void runs_flyway_migrations() throws Exception {
          final var flyway = Flyway.configure()
            .dataSource("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "sa", "")
            .load();
          final var connection = flyway.getConfiguration()
            .getDataSource()
            .getConnection();

          flyway.clean();

          assertThatThrownBy(() ->
            connection
              .createStatement()
              .executeQuery("SELECT * FROM \"flyway_schema_history\";")
          )
          .isExactlyInstanceOf(JdbcSQLSyntaxErrorException.class)
          .hasMessageStartingWith("Table \"flyway_schema_history\" not found (this database is empty)");

          final var exitCode = SystemStubs.catchSystemExit(() ->
            AtomicFlyway.attach(
              "--migrate",
              "-url", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
              "-user", "sa",
              "-password", ""
            )
          );

          final var result = connection
            .createStatement()
            .executeQuery("SELECT * FROM \"flyway_schema_history\";");

          result.last();

          assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);
          assertThat(result.getInt("installed_rank")).isEqualTo(-1);
          assertThat(result.getString("version")).isNull();
          assertThat(result.getBigDecimal("checksum")).isNull();
        }
      }

      @Nested class when_the_migration_options_is_not_passed {
        @Test void does_not_run_flyway_migrations() throws Exception {
          final var flyway = Flyway.configure()
            .dataSource("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "sa", "")
            .load();
          final var connection = flyway.getConfiguration()
            .getDataSource()
            .getConnection();

          flyway.clean();

          AtomicFlyway.attach(
            "-url", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
            "-user", "sa",
            "-password", ""
          );

          assertThatThrownBy(() ->
            connection
              .createStatement()
              .executeQuery("SELECT * FROM \"flyway_schema_history\";")
          )
          .isExactlyInstanceOf(JdbcSQLSyntaxErrorException.class)
          .hasMessageStartingWith("Table \"flyway_schema_history\" not found (this database is empty)");
        }
      }
    }

    @Nested class undoMigration {
      @Nested class when_the_undo_migration_option_is_passed {
        @Test void runs_undoLastMigration() throws Exception {
          final var flyway = Flyway.configure()
            .dataSource("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "sa", "")
            .javaMigrations(new V001CreateAccountTable(), new V002AddCreatedAtToAccount())
            .load();

          flyway.clean();
          flyway.migrate();

          final var exitCode = SystemStubs.catchSystemExit(() ->
            AtomicFlyway.attach(
              "--undo-migration",
              "-url", "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
              "-user", "sa",
              "-password", ""
            )
          );

          assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);
        }
      }
    }
  }
}
