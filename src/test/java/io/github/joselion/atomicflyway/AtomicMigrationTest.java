package io.github.joselion.atomicflyway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.joselion.testing.annotations.UnitTest;
import mockit.Expectations;
import mockit.Mocked;
import mockit.Verifications;

@UnitTest class AtomicMigrationTest {

  @Nested class canExecuteInTransaction {
    @Test void returns_true_by_default() {
      final var migration = new V001TestMigration();

      assertThat(migration.canExecuteInTransaction()).isTrue();
    }
  }

  @Nested class getChecksum {
    @Test void returns_the_hash_of_the_up_migration() {
      final var migration = new V001TestMigration();

      assertThat(migration.getChecksum()).isEqualTo("Migration up".hashCode());
    }
  }

  @Nested class getDescription {
    @Test void returns_the_name_of_the_migrations_without_the_version_prefix() {
      final var migration = new V001TestMigration();

      assertThat(migration.getDescription()).isEqualTo("TestMigration");
    }
  }

  @Nested class getVersion {
    @Nested class when_the_migration_is_not_repeatable {
      @Test void returns_the_version_part_of_the_prefix() {
        final var migration = new V001TestMigration();
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
      final var migration = new V001TestMigration();

      assertThat(migration.isUndo()).isFalse();
    }
  }

  @Nested class isBaselineMigration {
    @Test void returns_false_by_default() {
      final var migration = new V001TestMigration();

      assertThat(migration.isBaselineMigration()).isFalse();
    }
  }

  @Nested class migrate {
    @Nested class when_the_SQL_statement_can_be_executed {
      @Test void executes_the_statement_with_the_up_migration(
        final @Mocked Connection connection,
        final @Mocked PreparedStatement preparedStatement,
        final @Mocked Context context
      ) throws Exception {
        new Expectations() {{
          preparedStatement.execute(); result = true;
          connection.prepareStatement(anyString); result = preparedStatement;
          context.getConnection(); result = connection;
        }};

        final var migration = new V001TestMigration();
        migration.migrate(context);

        new Verifications() {{
          connection.prepareStatement("Migration up"); times = 1;
        }};
      }
    }

    @Nested class and_the_sql_statement_is_cannot_be_executed {
      @Test void raises_a_SQLException(
        final @Mocked Connection connection,
        final @Mocked PreparedStatement preparedStatement,
        final @Mocked Context context
      ) throws Exception {
        new Expectations() {{
          preparedStatement.execute(); result = new SQLException("Bad SQL statement!");
          connection.prepareStatement(anyString); result = preparedStatement;
          context.getConnection(); result = connection;
        }};

        final var migration = new V001TestMigration();

        assertThatThrownBy(() -> migration.migrate(context))
          .isExactlyInstanceOf(SQLException.class)
          .hasMessage("Bad SQL statement!");
      }
    }
  }

  @Nested class nameMatcher {
    @Nested class when_the_name_matches_a_versioned_migration {
      @Test void returns_the_versioned_pattern_matcher() {
        final var migration = new V001TestMigration();
        final var matcher = migration.nameMatcher();

        assertThat(matcher.groupCount()).isEqualTo(3);
        assertThat(matcher.group(1)).isEqualTo("V");
        assertThat(matcher.group(2)).isEqualTo("001");
        assertThat(matcher.group(3)).isEqualTo("TestMigration");
      }
    }

    @Nested class when_the_name_matches_a_repeatable_migration {
      @Test void returns_the_repeatable_pattern_matcher() {
        final var migration = new R001TestSeed();
        final var matcher = migration.nameMatcher();

        assertThat(matcher.groupCount()).isEqualTo(3);
        assertThat(matcher.group(1)).isEqualTo("R");
        assertThat(matcher.group(2)).isEqualTo("001");
        assertThat(matcher.group(3)).isEqualTo("TestSeed");
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

  /* --- Test Migrations --- */

  private record V001TestMigration() implements AtomicMigration {

    @Override
    public String up() {
      return "Migration up";
    }

    @Override
    public String down() {
      return "Migration down";
    }
  }

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
