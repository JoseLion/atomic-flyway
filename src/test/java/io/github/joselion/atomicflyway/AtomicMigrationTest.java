package io.github.joselion.atomicflyway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.Context;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.github.joselion.testing.annotations.UnitTest;

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
      @Test void executes_the_statement_with_the_up_migration() throws Exception {
        final var connection = mock(Connection.class);
        final var preparedStatement = mock(PreparedStatement.class);
        final var context = mock(Context.class);
        final var migration = new V001TestMigration();

        when(preparedStatement.execute()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(context.getConnection()).thenReturn(connection);

        migration.migrate(context);

        verify(connection).prepareStatement("Migration up");
      }
    }

    @Nested class and_the_sql_statement_is_cannot_be_executed {
      @Test void raises_a_SQLException() throws Exception {
        final var preparedStatement = mock(PreparedStatement.class);
        final var connection = mock(Connection.class);
        final var context = mock(Context.class);
        final var migration = new V001TestMigration();

        when(preparedStatement.execute()).thenThrow(new SQLException("Bad SQL statement!"));
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(context.getConnection()).thenReturn(connection);

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
