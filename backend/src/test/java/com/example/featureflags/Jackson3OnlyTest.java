package com.example.featureflags;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.featureflags.archfixture.Jackson2User;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Spec 002 section 5 item 2: the backend uses Jackson 3 only (the annotations stay 2.x). */
@Tag("AC-UPG-2")
class Jackson3OnlyTest {

  @Test
  void noProductionClassImportsJackson2CoreOrDatabind() {
    JavaClasses production =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.example.featureflags");
    assertThatCode(() -> ArchitectureTest.noJackson2CoreOrDatabind.check(production))
        .doesNotThrowAnyException();
  }

  @Test
  void noTestClassImportsJackson2CoreOrDatabindExceptTheFailingFixture() {
    JavaClasses tests =
        new ClassFileImporter()
            .withImportOption(location -> !location.contains("/archfixture/"))
            .importPackages("com.example.featureflags");
    assertThatCode(() -> ArchitectureTest.noJackson2CoreOrDatabind.check(tests))
        .doesNotThrowAnyException();
  }

  @Test
  void aClassImportingJackson2DatabindFails() {
    JavaClasses fixture = new ClassFileImporter().importClasses(Jackson2User.class);
    assertThatThrownBy(() -> ArchitectureTest.noJackson2CoreOrDatabind.check(fixture))
        .isInstanceOf(AssertionError.class);
  }
}
