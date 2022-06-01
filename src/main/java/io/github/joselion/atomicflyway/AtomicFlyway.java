package io.github.joselion.atomicflyway;

import org.flywaydb.core.Flyway;

import picocli.CommandLine;
import picocli.CommandLine.Option;

public class AtomicFlyway {

  @Option(
    names = {"--migrate"},
    description = "Run migrations"
  )
  private boolean migrate;

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

    if (atomicFlyway.migrate) {
      final var flyway = Flyway.configure()
        .dataSource(atomicFlyway.url, atomicFlyway.user, atomicFlyway.password)
        .load();

      flyway.migrate();
      System.exit(CommandLine.ExitCode.OK);
    }
  }
}
