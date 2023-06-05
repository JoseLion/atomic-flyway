package io.github.joselion.atomicflyway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.h2.jdbc.JdbcSQLSyntaxErrorException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.joselion.testing.annotations.UnitTest;
import io.github.joselion.testing.migrations.V001CreateAccountTable;
import io.github.joselion.testing.migrations.V002AddCreatedAtToAccount;
import picocli.CommandLine;
import uk.org.webcompere.systemstubs.SystemStubs;

@UnitTest class AtomicFlywayTest {

  private static final String H2_URL = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1";

  private static final String H2_USER = "sa";

  private static final String H2_PASSWORD = "";

  @Nested class configure {
    @Nested class when_the_configurer_is_provided {
      @Test void create_an_instance_with_the_provided_configuration() {
        final var atomicFlyway = AtomicFlyway.configure(config -> config.baselineOnMigrate(true));

        assertThat(atomicFlyway.getFlywayConfig()).isNotNull();
        assertThat(atomicFlyway.getFlywayConfig().isBaselineOnMigrate()).isTrue();
      }
    }

    @Nested class when_the_configurer_is_not_provided {
      @Test void creates_an_instance_with_the_default_configuration() {
        final var atomicFlyway = AtomicFlyway.configure();

        assertThat(atomicFlyway.getFlywayConfig()).isNotNull();
        assertThat(atomicFlyway.getFlywayConfig().isBaselineOnMigrate()).isFalse();
      }
    }
  }

  @Nested class attach {

    private final Flyway flyway = Flyway.configure()
      .dataSource(H2_URL, H2_USER, H2_PASSWORD)
      .cleanDisabled(false)
      .load();

    @BeforeEach void cleanup() {
      flyway.clean();
    }

    @Nested class when_the_url_option_is_not_used {
      @Test void uses_the_configurer_datasource() throws Exception {
        final var exitCode = SystemStubs.catchSystemExit(() ->
          AtomicFlyway.configure(config -> config.dataSource(H2_URL, H2_USER, H2_PASSWORD))
            .attach("--migrate")
        );

        assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);
      }
    }

    @Nested class when_the_url_option_is_used {
      @Test void overrides_the_datasource_connection_with_the_passed_options() throws Exception {
        final var exitCode = SystemStubs.catchSystemExit(() ->
          AtomicFlyway.configure(config -> config.dataSource(H2_URL, H2_USER, H2_PASSWORD))
            .attach(
              "--migrate",
              "-url", "bad_url",
              "-user", "bad_user",
              "-password", "bad_password"
            )
        );

        assertThat(exitCode).isEqualTo(CommandLine.ExitCode.USAGE);
      }
    }

    @Nested class when_unmatched_options_are_used {
      @Test void ignores_the_unmatched_options() throws Exception {
        final var exitCode = SystemStubs.catchSystemExit(() ->
          AtomicFlyway.configure(config -> config.dataSource(H2_URL, H2_USER, H2_PASSWORD))
            .attach(
              "--migrate",
              "-Dspring.profiles.active=dev"
            )
        );

        assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);
      }
    }

    @Nested class migrate {
      @Nested class when_the_migrate_option_is_passed {
        @Test void runs_flyway_migrations() throws Exception {
          final var connection = flyway.getConfiguration()
            .getDataSource()
            .getConnection();

          assertThatThrownBy(() ->
            connection
              .createStatement()
              .executeQuery("SELECT * FROM \"flyway_schema_history\";")
          )
          .isExactlyInstanceOf(JdbcSQLSyntaxErrorException.class)
          .hasMessageStartingWith("Table \"flyway_schema_history\" not found (this database is empty)");

          final var exitCode = SystemStubs.catchSystemExit(() ->
            AtomicFlyway.configure()
              .attach(
                "--migrate",
                "-url", H2_URL,
                "-user", H2_USER,
                "-password", H2_PASSWORD
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
          final var connection = flyway.getConfiguration()
            .getDataSource()
            .getConnection();

          AtomicFlyway.configure()
            .attach(
              "-url", H2_URL,
              "-user", H2_USER,
              "-password", H2_PASSWORD
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

      private final Flyway flyway = Flyway.configure()
        .dataSource(H2_URL, H2_USER, H2_PASSWORD)
        .javaMigrations(new V001CreateAccountTable(), new V002AddCreatedAtToAccount())
        .cleanDisabled(false)
        .load();

      @BeforeEach void cleanup() {
        flyway.clean();
      }

      @Nested class when_the_undo_migration_option_is_passed {
        @Nested class and_no_parameter_is_specified {
          @Test void runs_undoLastMigration_on_the_last_migration_only() throws Exception {
            flyway.migrate();

            final var exitCode = SystemStubs.catchSystemExit(() ->
              AtomicFlyway.configure()
                .attach(
                  "--undo-migration",
                  "-url", H2_URL,
                  "-user", H2_USER,
                  "-password", H2_PASSWORD
                )
            );

            assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);

            final var result = flyway.getConfiguration()
              .getDataSource()
              .getConnection()
              .createStatement()
              .executeQuery("SELECT count(*) FROM \"flyway_schema_history\";");

            result.next();

            assertThat(result.getInt("COUNT(*)")).isEqualTo(2);
          }
        }

        @Nested class and_a_parameter_is_specified {
          @Test void runs_undoLastMigration_on_the_number_of_migrations_passed() throws Exception {
            flyway.migrate();

            final var exitCode = SystemStubs.catchSystemExit(() ->
              AtomicFlyway.configure()
                .attach(
                  "--undo=2",
                  "-url", H2_URL,
                  "-user", H2_USER,
                  "-password", H2_PASSWORD
                )
            );

            assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);

            final var result = flyway.getConfiguration()
              .getDataSource()
              .getConnection()
              .createStatement()
              .executeQuery("SELECT count(*) FROM \"flyway_schema_history\";");

            result.next();

            assertThat(result.getInt("COUNT(*)")).isEqualTo(1);
          }
        }
      }
    }
  }
}
