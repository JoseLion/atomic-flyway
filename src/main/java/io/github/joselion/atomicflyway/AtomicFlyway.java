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

  private final Optional<Runnable> onMigrate;

  private final Optional<Runnable> onUndone;

  private final Optional<Runnable> onComplete;

  private AtomicFlyway(final FluentConfiguration flywayConfig) {
    this.migrate = false;
    this.password = "";
    this.undoMigration = false;
    this.url = Optional.empty();
    this.user = "";
    this.flywayConfig = flywayConfig;
    this.onMigrate = Optional.empty();
    this.onUndone = Optional.empty();
    this.onComplete = Optional.empty();
  }

  private AtomicFlyway(
    final FluentConfiguration flywayConfig,
    final Optional<Runnable> onMigrate,
    final Optional<Runnable> onUndone,
    final Optional<Runnable> onComplete
  ) {
    this.migrate = false;
    this.password = "";
    this.undoMigration = false;
    this.url = Optional.empty();
    this.user = "";
    this.flywayConfig = flywayConfig;
    this.onMigrate = onMigrate;
    this.onUndone = onUndone;
    this.onComplete = onComplete;
  }

  public static AtomicFlyway configure(final UnaryOperator<FluentConfiguration> configurer) {
    final var initialConfig = Flyway.configure().envVars();
    final var config = configurer.apply(initialConfig);

    return new AtomicFlyway(config);
  }

  public static AtomicFlyway configure() {
    return AtomicFlyway.configure(UnaryOperator.identity());
  }

  public AtomicFlyway onMigrate(final Runnable callback) {
    return new AtomicFlyway(
      this.flywayConfig,
      Optional.of(callback),
      this.onUndone,
      this.onComplete
    );
  }

  public AtomicFlyway onUndone(final Runnable callback) {
    return new AtomicFlyway(
      this.flywayConfig,
      this.onMigrate,
      Optional.of(callback),
      this.onComplete
    );
  }

  public AtomicFlyway onComplete(final Runnable callback) {
    return new AtomicFlyway(
      this.flywayConfig,
      this.onMigrate,
      this.onUndone,
      Optional.of(callback)
    );
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
      .doOnNext(exitCode -> this.onMigrate.ifPresent(Runnable::run))
      .subscribe(
        exitCode -> {
          this.onComplete.ifPresent(Runnable::run);
          System.exit(exitCode);
        },
        error -> System.exit(CommandLine.ExitCode.USAGE)
      );

      return;
    }

    if (this.undoMigration) {
      flywayMono.flatMap(UndoMigration::undoLastMigration)
        .doOnNext(exitCode -> this.onUndone.ifPresent(Runnable::run))
        .subscribe(
          exitCode -> {
            this.onComplete.ifPresent(Runnable::run);
            System.exit(exitCode);
          },
          error -> System.exit(CommandLine.ExitCode.USAGE)
        );
    }
  }

  public FluentConfiguration getFlywayConfig() {
    return this.flywayConfig;
  }
}
