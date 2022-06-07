package io.github.joselion.atomicflyway;

import java.sql.PreparedStatement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.github.joselion.maybe.Maybe;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.Context;
import org.flywaydb.core.api.migration.JavaMigration;

public interface AtomicMigration extends JavaMigration {

  Pattern VERSIONED_PATTERN = Pattern.compile("^(V)([0-9]*)(__)?([A-Z]\\w*)$");

  Pattern REPEATABLE_PATTERN = Pattern.compile("^(R)([0-9]*)(__)?([A-Z]\\w*)$");

  String up();

  String down();

  @Override
  default boolean canExecuteInTransaction() {
    return true;
  }

  @Override
  default Integer getChecksum() {
    return this.up().hashCode();
  }

  @Override
  default String getDescription() {
    return this.nameMatcher().group(4);
  }

  @Override
  @Nullable
  default MigrationVersion getVersion() {
    final var className = getClass().getSimpleName();
    final var repeatableMatcher = REPEATABLE_PATTERN.matcher(className);

    return !repeatableMatcher.matches()
      ? MigrationVersion.fromVersion(this.nameMatcher().group(2))
      : null;
  }

  @Override
  default boolean isUndo() {
    return false;
  }

  @Override
  default boolean isBaselineMigration() {
    return false;
  }

  @Override
  default void migrate(final Context context) throws Exception {
    final var connection = context.getConnection();
    final var statement = connection.prepareStatement(this.up());

    Maybe.withResource(statement)
      .runEffectClosing(PreparedStatement::execute)
      .orThrow();
  }

  default Matcher nameMatcher() {
    final var className = getClass().getSimpleName();
    final var versionedMatcher = VERSIONED_PATTERN.matcher(className);
    final var repeatableMatcher = REPEATABLE_PATTERN.matcher(className);

    if (!versionedMatcher.matches() && !repeatableMatcher.matches()) {
      throw new FlywayException("[FATAL] Invalid migration class name: " + className);
    }

    return repeatableMatcher.matches()
      ? repeatableMatcher
      : versionedMatcher;
  }
}
