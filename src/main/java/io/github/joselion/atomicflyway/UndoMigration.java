package io.github.joselion.atomicflyway;

import java.sql.PreparedStatement;
import java.util.Optional;

import javax.sql.DataSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;

import io.github.joselion.atomicflyway.exceptions.UndoMigrationException;
import io.github.joselion.maybe.Maybe;
import picocli.CommandLine;
import reactor.core.publisher.Mono;

/**
 * Internal use only. This class handles the proccess of reverting migrationsa.
 * 
 * @author Jose Luis Leon
 * @since v1.0.0
 */
public record UndoMigration() {

  private static final Logger log = LogManager.getLogger(UndoMigration.class);

  private static final String DELETE_TEMPLATE = """
    BEGIN;
      %s;
      DELETE FROM "flyway_schema_history" WHERE "script"='%s';
    COMMIT;
    """;

  /**
   * Static method that given a {@link Flyway} instance reverts the lastest
   * {@link AtomicMigration} using the down script.
   *
   * @param flyway the current Flyway instance
   * @return a {@link Mono} publisher wrapping the expected exit code
   */
  public static Mono<Integer> undoLastMigration(final Flyway flyway) {
    return Mono.<String>create(sink ->
      Optional.of(flyway.info())
        .map(MigrationInfoService::current)
        .map(MigrationInfo::getScript)
        .ifPresentOrElse(
          sink::success,
          () -> sink.error(UndoMigrationException.of("⚠️  No migrations left to undo!"))
        )
    )
    .doOnNext(script -> log.info("🔍 Last migration script: {}", script))
    .zipWhen(script ->
      Mono.<String>create(sink ->
        Maybe.just(script)
          .resolve(x -> flyway.getConfiguration().getClassLoader().loadClass(x))
          .doOnError(sink::error)
          .resolve(loaded ->  loaded.getDeclaredConstructor().newInstance())
          .map(migration -> {
            if (migration instanceof final AtomicMigration atomicMigration) {
              log.info("💣 Reverting last migration...");
              return atomicMigration.down();
            }

            throw UndoMigrationException.of("💢 The migration is not an AtomicMigration instance!");
          })
          .doOnSuccess(sink::success)
          .doOnError(sink::error)
      )
    )
    .map(tuple -> {
      final var script = tuple.getT1();
      final var downSql = tuple.getT2();

      return DELETE_TEMPLATE.formatted(downSql, script);
    })
    .flatMap(statement ->
      Mono.<Boolean>create(sink ->
        Maybe.just(flyway.getConfiguration().getDataSource())
          .resolve(DataSource::getConnection)
          .resolve(connection -> connection.prepareStatement(statement))
          .resolve(PreparedStatement::execute)
          .doOnSuccess(sink::success)
          .doOnError(sink::error)
      )
    )
    .map(result -> CommandLine.ExitCode.OK)
    .doOnSuccess(exitCode -> log.info("🎉 Last migration undone!"));
  }
}
