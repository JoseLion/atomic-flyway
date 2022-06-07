package io.github.joselion.testing.migrations;

import io.github.joselion.atomicflyway.AtomicMigration;

public record V001CreateAccountTable() implements AtomicMigration {

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
