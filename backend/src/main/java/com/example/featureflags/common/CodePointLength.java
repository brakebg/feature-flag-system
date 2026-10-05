package com.example.featureflags.common;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Spec 4.2 item 2: lengths count Unicode code points. {@code null} is valid. */
@Target({
  ElementType.FIELD,
  ElementType.PARAMETER,
  ElementType.TYPE_USE,
  ElementType.RECORD_COMPONENT
})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CodePointLength.Validator.class)
public @interface CodePointLength {

  int min() default 0;

  int max();

  String message() default "length must be between {min} and {max} characters";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};

  /** Checks the code point count of a string. */
  class Validator implements ConstraintValidator<CodePointLength, String> {
    private int min;
    private int max;

    @Override
    public void initialize(CodePointLength a) {
      this.min = a.min();
      this.max = a.max();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
      if (value == null) {
        return true;
      }
      if (value.indexOf('\u0000') >= 0) {
        // BF-6: PostgreSQL text cannot hold U+0000; reject it as input, not as a 500.
        context.disableDefaultConstraintViolation();
        context
            .buildConstraintViolationWithTemplate("must not contain the character U+0000")
            .addConstraintViolation();
        return false;
      }
      int n = value.codePointCount(0, value.length());
      return n >= min && n <= max;
    }
  }
}
