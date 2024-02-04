package io.github.joselion.atomicflyway;

import java.sql.PreparedStatement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.Nullable;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.Context;
import org.flywaydb.core.api.migration.JavaMigration;

import io.github.joselion.maybe.Maybe;

/**
 * The AtomicMigration contract allows the creating of Flyway Java-based atomic
 * migrations by implementing the {@code up()} and {@code down()} methods.
 *
 * @author Jose Luis Leon
 * @since v1.0.0
 */
public interface AtomicMigration extends JavaMigration {

  /**
   * Versioned migrations class name pattern.
   */
  Pattern VERSIONED_PATTERN = Pattern.compile("^(V)(\\d+)(__)?([A-Z]\\w*)$");

  /**
   * Repeatable migrations class name pattern.
   */
  Pattern REPEATABLE_PATTERN = Pattern.compile("^(R)(\\d*)(__)?([A-Z]\\w*)$");

  /**
   * The {@code up} migration that will run whith the {@code migrate} command.
   *
   * @return a raw string of the SQL to run during upon migration
   * @apiNote by default, the SQL is executed within a transaction, unless the
   *          {@link JavaMigration#canExecuteInTransaction()} method is overriden.
   */
  String up();

  /**
   * The {@code down} migration that will run with the {@code undo-migration}
   * command. Typically, this scripts should effectively revert everything in
   * the {@code up} migration.
   *
   * @return a raw string of the SQL to run upon reverting the migration
   * @apiNote by default, the SQL is executed within a transaction, unless the
   *          {@link JavaMigration#canExecuteInTransaction()} method is overriden.
   */
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
    final var className = getClass().getSimpleName();
    final var repeatableMatcher = REPEATABLE_PATTERN.matcher(className);
    final var baseDescription = this.nameMatcher().group(4);

    return repeatableMatcher.matches()
      ? this.nameMatcher().group(2).concat(baseDescription)
      : baseDescription;
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
  default void migrate(final Context context) throws Exception {
    final var connection = context.getConnection();
    final var statement = connection.prepareStatement(this.up());

    Maybe
      .withResource(statement)
      .effect(PreparedStatement::execute)
      .orThrow();
  }

  /**
   * Helper method that allows matching on the Java-based migration class names
   * to extract their parts. You can override this method if you're having
   * trouble with the migrations class naming convention. The regular
   * expression matcher should consist of 4 groups:
   *
   * <ol>
   *   <li>The prefix {@code V} for versioned, or {@code R} for repeatable</li>
   *   <li>The version number</li>
   *   <li>An optional separator (default is {@code __})</li>
   *   <li>The script name</li>
   * </ol>
   *
   * @return a regex matcher for the migration class name
   */
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
