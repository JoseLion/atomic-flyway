[![CI](https://github.com/JoseLion/atomic-flyway/actions/workflows/ci.yml/badge.svg)](https://github.com/JoseLion/atomic-flyway/actions/workflows/ci.yml)
[![CodeQL](https://github.com/JoseLion/atomic-flyway/actions/workflows/codeql.yml/badge.svg)](https://github.com/JoseLion/atomic-flyway/actions/workflows/codeql.yml)
[![Release](https://github.com/JoseLion/atomic-flyway/actions/workflows/release.yml/badge.svg)](https://github.com/JoseLion/atomic-flyway/actions/workflows/release.yml)
[![Pages](https://github.com/JoseLion/atomic-flyway/actions/workflows/pages.yml/badge.svg)](https://github.com/JoseLion/atomic-flyway/actions/workflows/pages.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.joselion/atomic-flyway?logo=sonatype)](https://central.sonatype.com/artifact/io.github.joselion/atomic-flyway)
[![javadoc](https://javadoc.io/badge2/io.github.joselion/atomic-flyway/javadoc.svg)](https://javadoc.io/doc/io.github.joselion/atomic-flyway)
[![License](https://img.shields.io/github/license/JoseLion/atomic-flyway)](https://github.com/JoseLion/atomic-flyway/blob/main/LICENSE)
[![Known Vulnerabilities](https://snyk.io/test/github/JoseLion/atomic-flyway/badge.svg)](https://snyk.io/test/github/JoseLion/atomic-flyway)

# Atomic Flyway

A wrapper around Flyway to make your migration atomic. That is to say, it provides a revert mechanism that is set in place by design every time you write a migration.

## Why?

Database schema migrations can come with a pain point or two, and we could say that is especially true during development, or even worse, when rolling back in production environments. Schema migrations allow us to move databases from one state to another while keeping track of the changes we add incrementally. Still, it's common to ignore how we can safely revert those changes in the same incremental fashion.

In this context, making a migration "atomic" means it contains the script that adds an incremental change, but it also knows how to revert that change. So if you're writing a schema migration and something didn't go as expected, you don't have to wipe clean your local database. You can undo that last migration instead.

This problem becomes critical when undergoing a rollback on production environments. Usually, production rollbacks are time-sensitive, something in the application is broken, or we just don't want users to interact with the current state. So we don't have time to write scripts to revert the changes at that point. Making your migrations "atomic" means you can revert them as soon as required in production environments.

### How is this different from Flyway Undo?

Flyway already comes with an undo command available in the `Teams` edition. However, it only allows the creation of decoupled "Undo Migrations" which, once executed, are recorded in the `flyway_schema_history` and leave the affected migration in a pending state. At this point, you can change the migration and apply it again.

Atomic Migrations keep the migration and undo scripts (called `up` and `down`) in the same file, so you cannot write migrations without its undo script. Once a migration is reverted, it is also removed from the `flyway_schema_history` table, as if it was never applied.

## Requirements

Because the library works with Flyway, it needs to have [flyway-core]() in the classpath. Also, as of version `v1.3.0` the minimum required JDK version is JDK16. Previous versions were built with a higher level (JDK18+) so the should still work on newer JDKs.

- Java 16+
- flyway-core v8.+

## Install

[![Maven Central](https://img.shields.io/maven-central/v/io.github.joselion/atomic-flyway?logo=sonatype)](https://central.sonatype.com/artifact/io.github.joselion/atomic-flyway)

Atomic Flyway is available in [Maven Central](https://central.sonatype.com/artifact/io.github.joselion/atomic-flyway). You can checkout the latest version with the badge above.

**Gradle**

```gradle
implementation('io.github.joselion:atomic-flyway:x.y.z')
```

**Maven**

```xml
<dependency>
  <groupId>io.github.joselion</groupId>
  <artifactId>atomic-flyway</artifactId>
  <version>x.y.z</version>
</dependency>
```

## Usage

Atomic Flyway relies on Flyway's [Java-based migrations](https://flywaydb.org/documentation/concepts/migrations#java-based-migrations) to allow the `up` and `down` scripts. Because of this, the general approach is also to execute the migration commands from your application's `main` method, which gives us two main advantages:
- You have all your application context in the migration scripts
- You can execute the migration commands right from the jar/war artifact (which is most useful in production environments, where build tools are not present anymore)

To attach `AtomicFlyway` to your application, create an instance with the `AtomicFlyway.configure()` factory method. Then pass your main method's args to `.attach(..)`:

```java
public class MyAwesomeApplication {

  public static void main(final String[] args) {
    AtomicFlyway.configure().attach(args);

    // start your app here!
  }
}
```

And that's it! Now you can use the application's executable artifact to run the migrations.

```bash
java -jar myawesomeapp.jar \
  --migrate \
  -url http://localhost:5432/mydb \
  -user admin \
  -password securepass
```

And to undo the latest applied migration, you can use `--undo-migration` or the convenient alias `--undo`:

```bash
java -jar myawesomeapp.jar \
  --undo-migration \
  -url http://localhost:5432/mydb \
  -user admin \
  -password securepass
```

Whenever you want to run your application normally, just omit the `--migrate`, `--undo-migration`, or `--undo` options. Keep in mind that `-url`, `-user`, and `-password` are also optional, as you can configure Flyway's data source with any other supported method (see below).

### Custom Flyway configuration

Given that the library is a wrapper of Flyway, you can use everything that Flyway supports for configuring itself, like the `FLYWAY_*` environment variables or configuration files. However, for more refined and more programmatic control over the Flyway configuration used by Atomic Flyway, you can use the `.configure(..)` overload, which uses a "configurer" function to allow customization over the default configurations:

> **Note:** Keep in mind that if you use the `-url` option, the command line values (`-url`, `-user`, and `-password`) take precedence over any other configuration to create Flyway's data source.

```java
public class MyAwesomeApplication {

  public static void main(final String[] args) {
    AtomicFlyway
      .configure(config ->
        config
          .dataSource(Secrets.DB_URL, Secrets.DB_USER, Secrets.DB_PASS)
          .baselineOnMigrate(true)
          .batch(true)
          .cleanDisabled(System.getProperty("production", false))
      )
      .attach(args);

    // start your app here!
  }
}
```

### Writing atomic migrations

Now that you have AtomicFlyway attached to your application, you must write the schema migrations as "atomic migrations." To do so, create Java-based migration classes as usual (for instance, inside the `classpath:db/migrations` path), and make them implement the `AtomicMigration` interface. The contract expects you to implement two methods: **up()** and **down()**. The `up` method should return the script for the migration, while the `down` method expects the script to revert or undo the migration. The contract also works for seeds or repeatable migrations. You need only to use the `R` prefix instead of `V` in the name of the class.

```java
public class V001CreateAccountTable implements AtomicMigration {

  @Override
  public String up() {
    return """
      CREATE TABLE account (
        id INT NOT NULL,
        username VARCHAR(50) NOT NULL,
        password VARCHAR(50)
      );
      """;
  }

  @Override
  public String down() {
    return """
      DROP TABLE account;
      """;
  }
}
```

## Class naming convention

The class naming for migrations and seeds follows Flyway's basic convention with a slight improvement to fit also on JDK coding standards. In terms of a regular expression, the name convention goes like this:

```regex
^(V|R)([0-9]*)(__)?([A-Z]\w*)$
```

1. **^(V|R)** - The migration type prefix. Start the name with a`V` for versioned migrations or an `R` for repeatable migrations (seeds).
2. **([0-9]*)** - The version number of the migration. Any number of any size. It's a good idea to keep it clean and ordered. Repeatable migrations don't need this version number.
3. **(__)?** - Optional separator. Splits the migration type and version from the script name. This portion is optional to fit JDK's class naming standards more properly, as underscores are not usually part of Java class names.
4. **([A-Z]\w*)$** - The script name. End the name with any word. The only condition is that it must start with an uppercase to keep up with the "PascalCase" for class names convention.

Some examples of valid names are:
- V001CreateAccountTable.java
- V1CreateAccountTable.java
- V1__CreateAccountTable.java
- RPopulateAccounts.java
- R__PopulateAccounts.java
- R001PopulateAccounts.java (you can number seeds too if you like)

## Usage with Gradle

For the time being, there's no Gradle plugin for Atomic Flyway, but implementing a task to run it should be pretty straightforward.

```gradle
task migrate(type: JavaExec) {
  classpath = sourceSets.main.runtimeClasspath
  mainClass = 'com.example.myawesomeapp.MyAwesomeApp'

  args(
    project.hasProperty('undo') ? '--undo-migration' : '--migrate',
    '-url', System.getenv('DB_URL'),
    '-user', System.getenv('DB_USER'),
    '-password', System.getenv('DB_PASSWORD')
  )
}
```

Now executing the migrations is as simple as running the task with Gradle:

```bash
./gradlew migrate
```

And if you want to undo the last migration, pass the `undo` project property to the task (with the `-P` prefix):

```bash
./gradlew migrate -Pundo
```

> **Note:** As a good practice, try using a [12-Factor Config](https://12factor.net/config) approach instead of environment variables on the database URL and credentials. Using a `.env` file, for instance, will make your project setup much more portable and flexible. Check [dotenv-gradle](https://github.com/uzzu/dotenv-gradle) or [gradle-dotenv-plugin](https://github.com/otkmnb2783/gradle-dotenv-plugin) if you're looking for a good `.env` option.

## Something's missing?

Suggestions are always welcome! Please create an [issue](https://github.com/JoseLion/atomic-flyway/issues/new) describing the request, feature, or bug. I'll try to look into it as soon as possible 🙂

## Contributions

Contributions are very welcome! Please fork this repository and open a Pull Request to the `main` branch.

## License

[Apache License 2.0](https://github.com/JoseLion/atomic-flyway/blob/main/LICENSE)
