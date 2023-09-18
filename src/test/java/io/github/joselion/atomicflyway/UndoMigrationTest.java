package io.github.joselion.atomicflyway;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.joselion.atomicflyway.exceptions.UndoMigrationException;
import io.github.joselion.testing.annotations.UnitTest;
import io.github.joselion.testing.migrations.V001CreateAccountTable;
import io.github.joselion.testing.migrations.V002AddCreatedAtToAccount;
import picocli.CommandLine;
import reactor.test.StepVerifier;

@UnitTest class UndoMigrationTest {

  private final FluentConfiguration flywayConfig = Flyway.configure()
      .dataSource("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "sa", "")
      .cleanDisabled(false);

  @BeforeEach void cleanup() {
    flywayConfig.load().clean();
  }

  @Nested class undoLastMigration {
    @Nested class when_there_is_no_more_migrations_to_undo {
      @Test void raises_a_UndoMigrationException_error() {
        final var flyway = flywayConfig
          .javaMigrations()
          .load();

        flyway.migrate();

        UndoMigration.undoLastMigration(flyway)
          .as(StepVerifier::create)
          .verifyErrorSatisfies(error -> {
            assertThat(error)
              .isExactlyInstanceOf(UndoMigrationException.class)
              .hasMessage("⚠️  No migrations left to undo!");
          });
      }
    }

    @Nested class when_the_latest_migration_is_not_an_atomic_migration {
      @Test void raises_a_UndoMigrationException_error() {
        final var flyway = flywayConfig
          .javaMigrations(new V001__NonAtomicMigration())
          .load();

        flyway.migrate();

        UndoMigration.undoLastMigration(flyway)
          .as(StepVerifier::create)
          .verifyErrorSatisfies(error ->
            assertThat(error)
              .isExactlyInstanceOf(UndoMigrationException.class)
              .hasMessage("💢 The migration is not an AtomicMigration instance!")
          );
      }
    }

    @Nested class when_there_are_atomic_migrations_applied {
      @Test void undos_the_latest_applied_migration() throws SQLException {
        final var flyway = flywayConfig
          .javaMigrations(new V001CreateAccountTable(), new V002AddCreatedAtToAccount())
          .load();
        final var connection = flyway
          .getConfiguration()
          .getDataSource()
          .getConnection();

        flyway.migrate();

        final var metadata = connection
          .prepareStatement("SELECT * from account;")
          .executeQuery()
          .getMetaData();

        assertThat(metadata.getColumnCount()).isEqualTo(4);
        assertThat(metadata.getColumnLabel(1)).isEqualToIgnoringCase("id");
        assertThat(metadata.getColumnLabel(2)).isEqualToIgnoringCase("username");
        assertThat(metadata.getColumnLabel(3)).isEqualToIgnoringCase("password");
        assertThat(metadata.getColumnLabel(4)).isEqualToIgnoringCase("created_at");

        UndoMigration.undoLastMigration(flyway)
          .as(StepVerifier::create)
          .expectNext(CommandLine.ExitCode.OK)
          .verifyComplete();

        final var undoneMetadata = connection
          .prepareStatement("SELECT * from account;")
          .executeQuery()
          .getMetaData();

        assertThat(undoneMetadata.getColumnCount()).isEqualTo(3);
        assertThat(undoneMetadata.getColumnLabel(1)).isEqualToIgnoringCase("id");
        assertThat(undoneMetadata.getColumnLabel(2)).isEqualToIgnoringCase("username");
        assertThat(undoneMetadata.getColumnLabel(3)).isEqualToIgnoringCase("password");
        assertThat(flyway.info().applied()).hasOnlyOneElementSatisfying(migration -> {
          assertThat(migration.getVersion()).isEqualByComparingTo(MigrationVersion.fromVersion("001"));
          assertThat(migration.getScript()).endsWith("V001CreateAccountTable");
        });
      }
    }
  }

  /* --- Migrations --- */

  static class V001__NonAtomicMigration extends BaseJavaMigration {

    @Override
    public void migrate(final Context context) throws Exception {
      final var statement = context.getConnection().prepareStatement(
        """
        CREATE TABLE account (
          id INT NOT NULL,
          username VARCHAR(50) NOT NULL,
          password VARCHAR(50)
        );
        """
      );

      statement.execute();
      statement.close();
    }
  }
}
