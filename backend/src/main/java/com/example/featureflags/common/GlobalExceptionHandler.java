package com.example.featureflags.common;

import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Spec 9.1: every error as an RFC 9457 problem detail, implemented once. */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private static final String UNIQUE_VIOLATION = "23505";

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ProblemDetail> invalidBody(
      MethodArgumentNotValidException e, HttpServletRequest req) {
    List<Map<String, String>> errors = new ArrayList<>();
    for (FieldError fe : e.getBindingResult().getFieldErrors()) {
      errors.add(error(rootField(fe.getField()), fe.getDefaultMessage()));
    }
    e.getBindingResult()
        .getGlobalErrors()
        .forEach(ge -> errors.add(error(ge.getObjectName(), ge.getDefaultMessage())));
    return Problems.response(Problems.validation(errors, req));
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  ResponseEntity<ProblemDetail> invalidParameter(
      HandlerMethodValidationException e, HttpServletRequest req) {
    List<Map<String, String>> errors = new ArrayList<>();
    e.getParameterValidationResults()
        .forEach(
            r ->
                r.getResolvableErrors()
                    .forEach(
                        err ->
                            errors.add(
                                error(
                                    r.getMethodParameter().getParameterName(),
                                    err.getDefaultMessage()))));
    return Problems.response(Problems.validation(errors, req));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ProblemDetail> constraintViolation(
      ConstraintViolationException e, HttpServletRequest req) {
    List<Map<String, String>> errors = new ArrayList<>();
    e.getConstraintViolations()
        .forEach(v -> errors.add(error(lastNode(v.getPropertyPath()), v.getMessage())));
    return Problems.response(Problems.validation(errors, req));
  }

  @ExceptionHandler(FieldValidationException.class)
  ResponseEntity<ProblemDetail> fieldValidation(
      FieldValidationException e, HttpServletRequest req) {
    return Problems.response(Problems.validation(List.of(error(e.field(), e.getMessage())), req));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ProblemDetail> unreadable(
      HttpMessageNotReadableException e, HttpServletRequest req) {
    if (NestedExceptionUtils.getMostSpecificCause(e) instanceof PayloadTooLargeException tooLarge) {
      return tooLarge(tooLarge, req);
    }
    return malformed("Request body is not valid JSON or has a wrong type", req);
  }

  @ExceptionHandler({
    HttpMediaTypeNotSupportedException.class,
    MethodArgumentTypeMismatchException.class,
    ServletRequestBindingException.class
  })
  ResponseEntity<ProblemDetail> badRequestShape(Exception e, HttpServletRequest req) {
    return malformed("Request is malformed", req);
  }

  @ExceptionHandler(MalformedRequestException.class)
  ResponseEntity<ProblemDetail> malformedRequest(
      MalformedRequestException e, HttpServletRequest req) {
    return malformed(e.getMessage(), req);
  }

  @ExceptionHandler({
    NotFoundException.class,
    NoHandlerFoundException.class,
    NoResourceFoundException.class,
    HttpRequestMethodNotSupportedException.class
  })
  ResponseEntity<ProblemDetail> notFound(Exception e, HttpServletRequest req) {
    String detail = e instanceof NotFoundException ? e.getMessage() : "No such resource";
    return Problems.response(
        Problems.of(HttpStatus.NOT_FOUND, "not-found", "Not found", detail, req));
  }

  @ExceptionHandler(DuplicateKeyException.class)
  ResponseEntity<ProblemDetail> duplicate(DuplicateKeyException e, HttpServletRequest req) {
    return Problems.response(
        Problems.of(HttpStatus.CONFLICT, "duplicate-key", "Duplicate key", e.getMessage(), req));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ProblemDetail> integrity(
      DataIntegrityViolationException e, HttpServletRequest req) {
    if (NestedExceptionUtils.getMostSpecificCause(e) instanceof SQLException sql
        && UNIQUE_VIOLATION.equals(sql.getSQLState())) {
      return Problems.response(
          Problems.of(
              HttpStatus.CONFLICT, "duplicate-key", "Duplicate key", "Key already exists", req));
    }
    return internalError(e, req);
  }

  @ExceptionHandler({
    VersionConflictException.class,
    ObjectOptimisticLockingFailureException.class,
    OptimisticLockException.class
  })
  ResponseEntity<ProblemDetail> versionConflict(Exception e, HttpServletRequest req) {
    return Problems.response(
        Problems.of(
            HttpStatus.CONFLICT,
            "version-conflict",
            "Version conflict",
            "The item was changed by someone else",
            req));
  }

  @ExceptionHandler(LimitReachedException.class)
  ResponseEntity<ProblemDetail> limit(LimitReachedException e, HttpServletRequest req) {
    return Problems.response(
        Problems.of(HttpStatus.CONFLICT, "limit-reached", "Limit reached", e.getMessage(), req));
  }

  @ExceptionHandler(PayloadTooLargeException.class)
  ResponseEntity<ProblemDetail> tooLarge(PayloadTooLargeException e, HttpServletRequest req) {
    return Problems.response(
        Problems.of(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "payload-too-large",
            "Payload too large",
            e.getMessage(),
            req));
  }

  /**
   * Framework exceptions that carry their own status ({@code ResponseStatusException}, 406, ...).
   * 404 stays not-found, 413 payload-too-large, other client errors are malformed requests, the
   * rest is unexpected.
   */
  private ResponseEntity<ProblemDetail> frameworkError(
      Exception e, ErrorResponse error, HttpServletRequest req) {
    int status = error.getStatusCode().value();
    if (status == HttpStatus.NOT_FOUND.value()) {
      return notFound(e, req);
    }
    if (status == HttpStatus.PAYLOAD_TOO_LARGE.value()) {
      return tooLarge(new PayloadTooLargeException(), req);
    }
    if (status >= 400 && status < 500) {
      return malformed("Request is malformed", req);
    }
    return internalError(e, req);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> unexpected(Exception e, HttpServletRequest req) {
    if (e instanceof ErrorResponse error) {
      return frameworkError(e, error, req);
    }
    return internalError(e, req);
  }

  private ResponseEntity<ProblemDetail> internalError(Exception e, HttpServletRequest req) {
    String requestId = MDC.get(RequestIds.MDC_KEY);
    log.error(
        "Unexpected error on {} {} (requestId {})",
        req.getMethod(),
        req.getRequestURI(),
        requestId,
        e);
    String detail = "Unexpected error" + (requestId == null ? "" : "; correlation id " + requestId);
    return Problems.response(
        Problems.of(
            HttpStatus.INTERNAL_SERVER_ERROR, "internal", "Internal server error", detail, req));
  }

  private ResponseEntity<ProblemDetail> malformed(String detail, HttpServletRequest req) {
    return Problems.response(
        Problems.of(HttpStatus.BAD_REQUEST, "malformed-request", "Malformed request", detail, req));
  }

  private static Map<String, String> error(String field, String message) {
    return Map.of("field", field, "message", message == null ? "invalid" : message);
  }

  /**
   * {@code name.<map value>} or {@code name[0]} (Optional / container elements) to {@code name}.
   */
  private static String rootField(String path) {
    int cut = path.length();
    for (char c : new char[] {'.', '['}) {
      int i = path.indexOf(c);
      if (i > 0 && i < cut) {
        cut = i;
      }
    }
    return path.substring(0, cut);
  }

  private static String lastNode(Path path) {
    String name = null;
    for (Path.Node node : path) {
      if (node.getName() != null) {
        name = node.getName();
      }
    }
    return name == null ? path.toString() : name;
  }
}
