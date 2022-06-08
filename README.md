

# Atomic Flyway

A wrapper around Flyway to make your migration atomic. That is to say, it provides a revert mechanism that is set in place by design every time you write a migration.

## Why?

Database schema migrations can come with a pain point or two, and we could say that is especially true during development, or even worse, when rolling back in production environments. Schema migrations allow us to move databases from one state to another while keeping track of the changes we add incrementally. Still, it's common to ignore how we can safely revert those changes in the same incremental fashion.

In this context, making a migration "atomic" means it contains the script that adds an incremental change, but it also knows how to revert that change. So if you're writing a schema migration and something didn't go as expected, you don't have to wipe clean your local database. You can undo that last migration instead.

This problem becomes critical when undergoing a rollback on production environments. Usually, production rollbacks are time-sensitive, something in the application is broken, or we just don't want users to interact with the current state. So we don't have time to write scripts to revert the changes at that point. Making your migrations "atomic" means you can revert them as soon as required in production environments.

## How is this different from Flyway Undo?

Flyway already comes with an undo command available in the `Teams` edition. However, it only allows the creation of decoupled "Undo Migrations" which, once executed, are recorded in the `flyway_schema_history` and leave the affected migration in a pending state. At this point, you can change the migration and apply it again.

Atomic Migrations keep the migration and undo scripts (called `up` and `down`) in the same file, so you cannot write migrations without its undo script. Once a migration is reverted, it is also removed from the `flyway_schema_history` table, as if it was never applied.

## Requirements

As a wrapper around Flyway, the library expects to have `flyway-core` in the classpath. Also, at the moment of writing the library, Java's latest version was Java 18, which is the version used to write the library and the minimum required.

- flyway-core v8.+
- Java 18+

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
    AtomicFlyway.configure(config ->
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

### Class naming convention

The class naming for migrations and seeds follows Flyway's basic convention with a slight improvement to fit also on JDK coding standards. In terms of a regular expression, the name convention goes like this:

```regex
^(V|R)([0-9]*)(__)?([A-Z]\w*)$
```

- **^(V|R)** - The migration type prefix. Start the name with a`V` for versioned migrations or an `R` for repeatable migrations (seeds).
- **([0-9]*)** - The version number of the migration. Any number of any size. It's a good idea to keep it clean and ordered. Repeatable migrations don't need this version number.
- **(__)?** - Optional separator. Splits the migration type and version from the script name. This portion is optional to fit JDK's class naming standards more properly, as underscores are not usually part of Java class names.
- **([A-Z]\w*)$** - The script name. End the name with any word. The only condition is that it must start with an uppercase to keep up with the "PascalCase" for class names convention.

Some examples of valid names are:
- V001CreateAccountTable.java
- V1CreateAccountTable.java
- V1__CreateAccountTable.java
- RPopulateAccounts.java
- R__PopulateAccounts.java
- R001PopulateAccounts.java (you can number seeds t

# Atomic Flyway

A wrapper around Flyway to make your migration atomic. That is to say, it provides a revert mechanism that is set in place by design every time you write a migration.

## Why?

Database schema migrations can come with a pain point or two, and we could say that is especially true during development, or even worse, when rolling back in production environments. Schema migrations allow us to move databases from one state to another while keeping track of the changes we add incrementally. Still, it's common to ignore how we can safely revert those changes in the same incremental fashion.

In this context, making a migration "atomic" means it contains the script that adds an incremental change, but it also knows how to revert that change. So if you're writing a schema migration and something didn't go as expected, you don't have to wipe clean your local database. You can undo that last migration instead.

This problem becomes critical when undergoing a rollback on production environments. Usually, production rollbacks are time-sensitive, something in the application is broken, or we just don't want users to interact with the current state. So we don't have time to write scripts to revert the changes at that point. Making your migrations "atomic" means you can revert them as soon as required in production environments.

## How is this different from Flyway Undo?

Flyway already comes with an undo command available in the `Teams` edition. However, it only allows the creation of decoupled "Undo Migrations" which, once executed, are recorded in the `flyway_schema_history` and leave the affected migration in a pending state. At this point, you can change the migration and apply it again.

Atomic Migrations keep the migration and undo scripts (called `up` and `down`) in the same file, so you cannot write migrations without its undo script. Once a migration is reverted, it is also removed from the `flyway_schema_history` table, as if it was never applied.

## Requirements

As a wrapper around Flyway, the library expects to have `flyway-core` in the classpath. Also, at the moment of writing the library, Java's latest version was Java 18, which is the version used to write the library and the minimum required.

- flyway-core v8.+
- Java 18+

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
    AtomicFlyway.configure(config ->
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

## Something's missing?

Suggestions are always welcome! Please create an [issue](https://github.com/JoseLion/atomic-flyway/issues/new) describing the request, feature, or bug. I'll try to look into it as soon as possible 🙂

## Contributions

Contributions are very welcome! Please fork this repository and open a Pull Request to the `main` branch to do so.

## License

[Apache License 2.0](https://github.com/JoseLion/atomic-flyway/blob/main/LICENSE)
oo if you like)

## Something's missing?

Suggestions are always welcome! Please create an [issue](https://github.com/JoseLion/atomic-flyway/issues/new) describing the request, feature, or bug. I'll try to look into it as soon as possible 🙂

## Contributions

Contributions are very welcome! Please fork this repository and open a Pull Request to the `main` branch to do so.

## License

[Apache License 2.0](https://github.com/JoseLion/atomic-flyway/blob/main/LICENSE)
