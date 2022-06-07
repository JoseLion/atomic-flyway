package io.github.joselion.atomicflyway;

import com.github.joselion.maybe.Maybe;

import org.flywaydb.core.Flyway;

import picocli.CommandLine;
import picocli.CommandLine.Option;
import reactor.core.publisher.Mono;

public class AtomicFlyway {

  @Option(
    names = {"--migrate"},
    description = "Run migrations"
  )
  private boolean migrate;

  @Option(
    names = {"--undo-migration", "--undo"},
    description = "Reverts the last applied migration using its down script"
  )
  private boolean undoMigration;

  @Option(
    names = {"-url"},
    description = "The URL for the database connection",
    defaultValue = "${user:-}"
  )
  private String url;

  @Option(
    names = {"-user"},
    description = "The user of the database connection",
    defaultValue = "${user:-}"
  )
  private String user;

  @Option(
    names = {"-password"},
    description = "The password of the database connection",
    defaultValue = ""
  )
  private String password;

  public static void attach(final String... args) {
    final var atomicFlyway = new AtomicFlyway();
    new CommandLine(atomicFlyway).parseArgs(args);

    final var flywayMono = Mono.<Flyway>create(sink ->
      Maybe.fromResolver(() -> Flyway.configure()
        .dataSource(atomicFlyway.url, atomicFlyway.user, atomicFlyway.password)
        .load()
      )
      .doOnSuccess(sink::success)
      .doOnError(sink::error)
    );

    if (atomicFlyway.migrate) {
      flywayMono.flatMap(flyway -> Mono.<Integer>create(sink ->
        Maybe.fromEffect(flyway::migrate)
          .doOnSuccess(() -> sink.success(CommandLine.ExitCode.OK))
          .doOnError(sink::error)
      ))
      .subscribe(
        System::exit,
        error -> System.exit(CommandLine.ExitCode.USAGE)
      );

      return;
    }

    if (atomicFlyway.undoMigration) {
      flywayMono.flatMap(UndoMigration::undoLastMigration)
        .subscribe(
          System::exit,
          error -> System.exit(CommandLine.ExitCode.USAGE)
        );
    }
  }
}
