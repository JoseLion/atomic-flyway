package io.github.joselion.atomicflyway.exceptions;

public class UndoMigrationException extends RuntimeException {

  private UndoMigrationException(final String message) {
    super(message);
  }

  public static UndoMigrationException of(final String message) {
    return new UndoMigrationException(message);
  }
}
