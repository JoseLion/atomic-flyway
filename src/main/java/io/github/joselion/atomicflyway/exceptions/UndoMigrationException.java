package io.github.joselion.atomicflyway.exceptions;

/**
 * Spetialized exception used on the {@code undo-migration} command.
 *
 * @author Jose Luis Leon
 * @since v1.0.0
 */
public final class UndoMigrationException extends RuntimeException {

  private UndoMigrationException(final String message) {
    super(message);
  }

  /**
   * Factory method that creates a new {@code UndoMigrationException} using the
   * provided message.
   *
   * @param message the description of the exception
   * @return a new {@code UndoMigrationException} instance
   */
  public static UndoMigrationException of(final String message) {
    return new UndoMigrationException(message);
  }
}
