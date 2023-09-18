package io.github.joselion.atomicflyway;

import java.util.Optional;
import java.util.function.UnaryOperator;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;

import io.github.joselion.maybe.Maybe;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import reactor.core.publisher.Mono;

/**
 * This is the public API that allows attaching the atomic Flyway migrations to
 * your application. It also provides a way for customizating your Flyway
 * configuration programmatically. The attachment is expoected in the main
 * method so the execution arguments can be handled.
 *
 * <pre>
 * public class MyAwesomeApp {
 *
 *   public static void main(String[] args) {
 *     AtomicFlyway.configure(config ->
 *       config
 *         .baselineOnMigrate(true)
 *         .batch(true)
 *         .cleanDisabled(System.getProperty("production", false))
 *     )
 *     .attach(args);
 * 
 *     // start your app here!
 *   }
 * }
 * </pre>
 * 
 * @author Jose Luis Leon
 * @since v1.0.0
 */
@Slf4j
public class AtomicFlyway {

  @Option(
    names = {"--migrate"},
    description = "Run migrations"
  )
  private boolean migrate;

  @Option(
    names = {"--undo-migration", "--undo"},
    description =
      "Reverts the last N applied migration(s) using their down script. Use an " +
      "integer parameter to specify the number of migrations to undo. Defaults " +
      "to 1 if the parameter is ommited.",
    arity = "0..1",
    fallbackValue = "1"
  )
  private Optional<Integer> undoMigration;

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
    this.undoMigration = Optional.empty();
    this.url = Optional.empty();
    this.user = "";
    this.flywayConfig = flywayConfig;
  }

  /**
   * Factory method to create an AtomicFlyway instance. It allows to customize
   * the Flyway onfiguration using the function parameter, which receives the
   * default configuration and expect to return a customized configuration.
   *
   * @param configurer a function that receives and returns a Flyway configurer
   * @return a new AtomicFlyway instance
   */
  public static AtomicFlyway configure(final UnaryOperator<FluentConfiguration> configurer) {
    final var initialConfig = Flyway.configure().envVars();
    final var config = configurer.apply(initialConfig);

    return new AtomicFlyway(config);
  }

  /**
   * Factory method to create an AtomicFlyway instance using the default
   * Flyway configuration.
   *
   * @return a new AtomicFlyway instance.
   */
  public static AtomicFlyway configure() {
    return AtomicFlyway.configure(UnaryOperator.identity());
  }

  /**
   * Binds the AtomicFlyway CLI to the application. This method is expected to
   * be called in your main program method so it can receive the execution
   * arguments to be parsed.
   * 
   * <pre>
   * public class MyAwesomeApp {
   *
   *   public static void main(String[] args) {
   *     AtomicFlyway.configure().attach(args);
   * 
   *     // start your app here!
   *   }
   * }
   * </pre>
   *
   * @param args the {@code main} method arguments
   */
  public void attach(final String... args) {
    new CommandLine(this)
      .setStopAtUnmatched(false)
      .setUnmatchedArgumentsAllowed(true)
      .parseArgs(args);

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
        this::handleError
      );

      return;
    }

    if (this.undoMigration.isPresent()) {
      flywayMono
        .flatMap(UndoMigration::undoLastMigration)
        .repeat(this.undoMigration.get() - 1L)
        .reduce((acc, exitCode) ->
          exitCode != CommandLine.ExitCode.OK
            ? exitCode
            : acc
        )
        .subscribe(
          System::exit,
          this::handleError
        );
    }
  }

  /**
   * Returns the Flyway configuration used to create the instance.
   *
   * @return the instance Flyeway configurations
   */
  public FluentConfiguration getFlywayConfig() {
    return this.flywayConfig;
  }

  private <E extends Throwable> void handleError(final E error) {
    log.error("❌ Operation failed to complete!");
    log.error(error.getMessage(), error);
    System.exit(CommandLine.ExitCode.USAGE);
  }
}
