package io.github.joselion.atomicflyway;

import java.util.Optional;
import java.util.function.UnaryOperator;

import com.github.joselion.maybe.Maybe;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;

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
    description = "The URL for the database connection"
  )
  private Optional<String> url;

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

  private final FluentConfiguration flywayConfig;

  private AtomicFlyway(final FluentConfiguration flywayConfig) {
    this.migrate = false;
    this.password = "";
    this.undoMigration = false;
    this.url = Optional.empty();
    this.user = "";
    this.flywayConfig = flywayConfig;
  }

  public static AtomicFlyway configure(final UnaryOperator<FluentConfiguration> configurer) {
    final var initialConfig = Flyway.configure().envVars();
    final var config = configurer.apply(initialConfig);

    return new AtomicFlyway(config);
  }

  public static AtomicFlyway configure() {
    return AtomicFlyway.configure(UnaryOperator.identity());
  }

  public void attach(final String... args) {
    new CommandLine(this).parseArgs(args);

    final var flywayMono = Mono.<Flyway>create(sink ->
      Maybe.fromResolver(() ->
        this.url
          .map(dbUrl -> flywayConfig.dataSource(dbUrl, this.user, this.password))
          .orElse(flywayConfig)
          .load()
      )
      .doOnSuccess(sink::success)
      .doOnError(sink::error)
    );

    if (this.migrate) {
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

    if (this.undoMigration) {
      flywayMono.flatMap(UndoMigration::undoLastMigration)
        .subscribe(
          System::exit,
          error -> System.exit(CommandLine.ExitCode.USAGE)
        );
    }
  }

  public FluentConfiguration getFlywayConfig() {
    return this.flywayConfig;
  }
}
