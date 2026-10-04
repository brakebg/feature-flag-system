package com.example.featureflags;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.annotation.Resource;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.repository.Repository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

/** Gate 3 (spec 11.3): architecture rules. */
@AnalyzeClasses(
    packages = "com.example.featureflags",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  @ArchTest
  static final ArchRule controllersDoNotUseRepositories =
      noClasses()
          .that()
          .areAnnotatedWith(RestController.class)
          .or()
          .areAnnotatedWith(Controller.class)
          .should()
          .dependOnClassesThat()
          .areAssignableTo(Repository.class)
          .because("controllers never touch repositories (spec 9.5)");

  @ArchTest
  static final ArchRule noEntityInControllerSignatures =
      methods()
          .that()
          .areDeclaredInClassesThat()
          .areAnnotatedWith(RestController.class)
          .or()
          .areDeclaredInClassesThat()
          .areAnnotatedWith(Controller.class)
          .should(notExposeEntities())
          .because("entities never leave the service layer (spec 9.5)");

  /**
   * Evaluation API code reads the database only in the {@code FlagCacheService} loaders (methods
   * named {@code load*} or {@code reloadAll}) or in the reconciliation job.
   */
  @ArchTest
  static final ArchRule evaluationReadsDatabaseOnlyInLoadersOrReconciliation =
      classes()
          .that()
          .resideInAPackage("..evaluation..")
          .should(onlyAccessDatabaseInLoaders())
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule noPackageCycles =
      slices().matching("com.example.featureflags.(*)..").should().beFreeOfCycles();

  @ArchTest
  static final ArchRule noFieldInjection =
      noFields()
          .should()
          .beAnnotatedWith(Autowired.class)
          .orShould()
          .beAnnotatedWith(Value.class)
          .orShould()
          .beAnnotatedWith(Resource.class)
          .because("use constructor injection");

  private static ArchCondition<JavaMethod> notExposeEntities() {
    return new ArchCondition<>("not have an @Entity in its signature") {
      @Override
      public void check(JavaMethod method, ConditionEvents events) {
        List<JavaType> types = new ArrayList<>();
        types.add(method.getReturnType());
        types.addAll(method.getParameterTypes());
        for (JavaType type : types) {
          for (JavaClass c : type.getAllInvolvedRawTypes()) {
            if (c.isAnnotatedWith(Entity.class)) {
              events.add(
                  SimpleConditionEvent.violated(
                      method, method.getFullName() + " exposes entity " + c.getName()));
            }
          }
        }
      }
    };
  }

  private static ArchCondition<JavaClass> onlyAccessDatabaseInLoaders() {
    return new ArchCondition<>("access the database only in the cache loaders") {
      @Override
      public void check(JavaClass clazz, ConditionEvents events) {
        for (JavaAccess<?> access : clazz.getAccessesFromSelf()) {
          JavaClass target = access.getTargetOwner();
          boolean db =
              target.isAssignableTo(Repository.class)
                  || target.isAssignableTo(EntityManager.class)
                  || target.isAssignableTo(JdbcTemplate.class)
                  || target.isAssignableTo(NamedParameterJdbcTemplate.class)
                  || target.isAssignableTo(DataSource.class)
                  || target.isAssignableTo(Connection.class);
          if (!db) {
            continue;
          }
          String owner = clazz.getSimpleName();
          String method = access.getOrigin().getName();
          boolean allowed =
              owner.equals("CacheReconciliationJob")
                  || (owner.startsWith("FlagCacheService")
                      && (method.startsWith("load") || method.equals("reloadAll")));
          if (!allowed) {
            events.add(SimpleConditionEvent.violated(access, access.getDescription()));
          }
        }
      }
    };
  }
}
