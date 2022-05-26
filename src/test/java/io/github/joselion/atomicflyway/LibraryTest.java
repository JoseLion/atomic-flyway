package io.github.joselion.atomicflyway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.github.joselion.testing.annotations.UnitTest;

@UnitTest class LibraryTest {

  @Test void some_library_method_returns_true() {
    Library classUnderTest = new Library();
    assertThat(classUnderTest.someLibraryMethod()).isTrue();
  }
}
