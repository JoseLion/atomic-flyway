package io.github.joselion.atomicflyway;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.joselion.testing.annotations.UnitTest;
import mockit.Expectations;
import mockit.Invocation;
import mockit.Mock;
import mockit.MockUp;
import mockit.Mocked;
import mockit.Verifications;
import picocli.CommandLine;

@UnitTest class AtomicFlywayTest {

  @Nested class attach {
    @Nested class migrate {
      @Nested class when_the_migrate_option_is_passed {
        @Test void runs_flyway_migrations(
          final @Mocked FluentConfiguration config,
          final @Mocked Flyway flyway,
          final @Mocked MigrateResult migrateResult
        ) {
          new Expectations() {{
            config.load(); result = flyway;
            flyway.migrate(); result = migrateResult;
          }};

          new MockUp<System>() {
            @Mock
            public static void exit(final Invocation invocation, final int status) {
              assertThat(status).isEqualTo(CommandLine.ExitCode.OK);
              assertThat(invocation.getInvocationCount()).isEqualTo(1);
            }
          };

          AtomicFlyway.attach(
            "--migrate",
            "-url", "http://localhost:5432/testdb",
            "-user", "test",
            "-password", "1234"
          );

          new Verifications() {{
            config.dataSource("http://localhost:5432/testdb", "test", "1234"); times = 1;
            flyway.migrate(); times = 1;
          }};
        }
      }

      @Nested class when_the_migration_options_is_not_passed {
        @Test void does_not_run_flyway_migrations(
          final @Mocked FluentConfiguration config,
          final @Mocked Flyway flyway
        ) {
          AtomicFlyway.attach(
            "-url", "http://localhost:5432/testdb",
            "-user", "test",
            "-password", "1234"
          );

          new Verifications() {{
            config.dataSource("http://localhost:5432/testdb", "test", "1234"); times = 0;
            flyway.migrate(); times = 0;
          }};
        }
      }
    }
  }
}
