package io.github.joselion.testing.migrations;

import io.github.joselion.atomicflyway.AtomicMigration;

public record V002AddCreatedAtToAccount() implements AtomicMigration {

  @Override
  public String up() {
    return """
      ALTER TABLE account
        ADD COLUMN created_at DATE;
      """;
  }

  @Override
  public String down() {
    return """
      ALTER TABLE account
        DROP COLUMN created_at;
      """;
  }
}
