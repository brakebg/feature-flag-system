package com.example.featureflags;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.featureflags.archfixture.LeakyController;
import com.example.featureflags.archfixture.evaluation.LeakyEvaluation;
import com.example.featureflags.cyclea.A;
import com.example.featureflags.cycleb.B;
import com.example.featureflags.group.FlagGroup;
import com.example.featureflags.group.FlagGroupRepository;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/** Gate 3: each architecture rule fails on a class that breaks it. */
class ArchitectureRulesCanFailTest {

  private static final JavaClasses FIXTURES =
      new ClassFileImporter()
          .importClasses(
              LeakyController.class,
              LeakyEvaluation.class,
              A.class,
              B.class,
              FlagGroup.class,
              FlagGroupRepository.class);

  @Test
  void controllerUsingARepositoryFails() {
    assertThatThrownBy(() -> ArchitectureTest.controllersDoNotUseRepositories.check(FIXTURES))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void entityInAControllerSignatureFails() {
    assertThatThrownBy(() -> ArchitectureTest.noEntityInControllerSignatures.check(FIXTURES))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void evaluationReadingTheDatabaseOutsideLoadersFails() {
    assertThatThrownBy(
            () ->
                ArchitectureTest.evaluationReadsDatabaseOnlyInLoadersOrReconciliation.check(
                    FIXTURES))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void packageCycleFails() {
    assertThatThrownBy(() -> ArchitectureTest.noPackageCycles.check(FIXTURES))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void fieldInjectionFails() {
    assertThatThrownBy(() -> ArchitectureTest.noFieldInjection.check(FIXTURES))
        .isInstanceOf(AssertionError.class);
  }
}
