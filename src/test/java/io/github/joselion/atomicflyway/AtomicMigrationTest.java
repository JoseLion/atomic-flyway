package io.github.joselion.atomicflyway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;

import javax.sql.DataSource;

import com.github.joselion.maybe.Maybe;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.migration.Context;
import org.h2.jdbc.JdbcSQLSyntaxErrorException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.joselion.testing.annotations.UnitTest;
import io.github.joselion.testing.migrations.V001CreateAccountTable;
import io.github.joselion.testing.migrations.V002AddCreatedAtToAccount;

@UnitTest class AtomicMigrationTest {

  @Nested class canExecuteInTransaction {
    @Test void returns_true_by_default() {
      final var migration = new V001CreateAccountTable();

      assertThat(migration.canExecuteInTransaction()).isTrue();
    }
  }

  @Nested class getChecksum {
    @Test void returns_the_hash_of_the_up_migration() {
      final var migration = new V001CreateAccountTable();

      assertThat(migration.getChecksum()).isEqualTo(migration.up().hashCode());
    }
  }

  @Nested class getDescription {
    @Test void returns_the_name_of_the_migrations_without_the_version_prefix() {
      final var migration = new V001CreateAccountTable();

      assertThat(migration.getDescription()).isEqualTo("CreateAccountTable");
    }
  }

  @Nested class getVersion {
    @Nested class when_the_migration_is_not_repeatable {
      @Test void returns_the_version_part_of_the_prefix() {
        final var migration = new V001CreateAccountTable();
        final var expected = MigrationVersion.fromVersion("001");

        assertThat(migration.getVersion()).isEqualByComparingTo(expected);
      }
    }

    @Nested class when_the_migration_is_repeatable {
      @Test void return_null() {
        final var seed = new R001TestSeed();

        assertThat(seed.getVersion()).isNull();
      }
    }
  }

  @Nested class isUndo {
    @Test void returns_false_by_default() {
      final var migration = new V001CreateAccountTable();

      assertThat(migration.isUndo()).isFalse();
    }
  }

  @Nested class isBaselineMigration {
    @Test void returns_false_by_default() {
      final var migration = new V001CreateAccountTable();

      assertThat(migration.isBaselineMigration()).isFalse();
    }
  }

  @Nested class migrate {
    @Nested class when_the_SQL_statement_can_be_executed {
      @Test void executes_the_statement_with_the_up_migration() throws Exception {
        final var flyway = Flyway.configure()
          .dataSource("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "sa", "")
          .javaMigrations(new V001CreateAccountTable())
          .load();
        final var context = getFlywayContext(flyway);
        final var migration = new V002AddCreatedAtToAccount();

        flyway.clean();
        flyway.migrate();
        migration.migrate(context);

        final var result = context.getConnection()
          .createStatement()
          .executeQuery("SELECT * FROM account;");

        assertThat(result.getMetaData().getColumnLabel(4)).isEqualToIgnoringCase("created_at");
      }
    }

    @Nested class and_the_sql_statement_cannot_be_executed {
      @Test void raises_a_SQLException() throws Exception {
        final var flyway = Flyway.configure()
          .dataSource("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", "sa", "")
          .javaMigrations(new V001CreateAccountTable(), new V002AddCreatedAtToAccount())
          .load();
        final var context = getFlywayContext(flyway);
        final var migration = new BadMigration();

        flyway.clean();
        flyway.migrate();
        assertThatThrownBy(() -> migration.migrate(context))
          .isExactlyInstanceOf(JdbcSQLSyntaxErrorException.class)
          .hasMessageStartingWith("Syntax error in SQL statement");
      }
    }
  }

  @Nested class nameMatcher {
    @Nested class when_the_name_matches_a_versioned_migration {
      @Test void returns_the_versioned_pattern_matcher() {
        final var migration = new V001CreateAccountTable();
        final var matcher = migration.nameMatcher();

        assertThat(matcher.groupCount()).isEqualTo(4);
        assertThat(matcher.group(1)).isEqualTo("V");
        assertThat(matcher.group(2)).isEqualTo("001");
        assertThat(matcher.group(3)).isNull();
        assertThat(matcher.group(4)).isEqualTo("CreateAccountTable");
      }
    }

    @Nested class when_the_name_matches_a_repeatable_migration {
      @Test void returns_the_repeatable_pattern_matcher() {
        final var migration = new R001TestSeed();
        final var matcher = migration.nameMatcher();

        assertThat(matcher.groupCount()).isEqualTo(4);
        assertThat(matcher.group(1)).isEqualTo("R");
        assertThat(matcher.group(2)).isEqualTo("001");
        assertThat(matcher.group(3)).isNull();
        assertThat(matcher.group(4)).isEqualTo("TestSeed");
      }
    }

    @Nested class when_the_name_does_not_match_either_a_versioned_nor_a_repeatable_migration {
      @Test void raises_a_FlywayException() {
        final var migration = new BadMigration();

        assertThatThrownBy(() -> migration.nameMatcher())
          .isExactlyInstanceOf(FlywayException.class)
          .hasMessage("[FATAL] Invalid migration class name: BadMigration");
      }
    }
  }

  private Context getFlywayContext(final Flyway flyway) {
    return new Context() {

      @Override
      public Configuration getConfiguration() {
        return flyway.getConfiguration();
      }

      @Override
      public Connection getConnection() {
        return Maybe.just(this.getConfiguration())
          .map(Configuration::getDataSource)
          .resolve(DataSource::getConnection)
          .orThrow(RuntimeException::new);
      }
    };
  }

  /* --- Test Migrations --- */

  private record R001TestSeed() implements AtomicMigration {

    @Override
    public String up() {
      return "Seed up";
    }

    @Override
    public String down() {
      return "Seed down";
    }
  }

  private record BadMigration() implements AtomicMigration {

    @Override
    public String up() {
      return "Unreachable up";
    }

    @Override
    public String down() {
      return "Unreachable down";
    }
  }
}
