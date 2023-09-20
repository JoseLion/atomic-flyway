package io.github.joselion.atomicflyway;

import static reactor.function.TupleUtils.function;

import java.lang.reflect.Constructor;
import java.sql.PreparedStatement;
import java.util.Optional;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;

import io.github.joselion.atomicflyway.exceptions.UndoMigrationException;
import io.github.joselion.maybe.Maybe;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import reactor.core.publisher.Mono;

/**
 * Internal use only. This class handles the proccess of reverting migrationsa.
 * 
 * @author Jose Luis Leon
 * @since v1.0.0
 */
@Slf4j
@UtilityClass
class UndoMigration {

  static Mono<Integer> undoLastMigration(final Flyway flyway) {
    return Mono
      .<String>create(sink ->
        Optional
          .of(flyway.info())
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
          Maybe
            .just(script)
            .resolve(flyway.getConfiguration().getClassLoader()::loadClass)
            .doOnError(sink::error)
            .resolve(Class::getDeclaredConstructor)
            .resolve(Constructor::newInstance)
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
      .map(function(UndoMigration::deleteStatement))
      .flatMap(statement ->
        Mono.<Boolean>create(sink ->
          Maybe
            .just(flyway.getConfiguration().getDataSource())
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

  private static String deleteStatement(final String script, final String downSql) {
    return """
      BEGIN;
        %s;
        DELETE FROM "flyway_schema_history" WHERE "script"='%s';
      COMMIT;
      """
      .formatted(downSql, script);
  }
}
