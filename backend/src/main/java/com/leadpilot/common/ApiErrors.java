package com.leadpilot.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiErrors extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

    public static ProblemDetail problem(HttpStatus status, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        String requestPath = request.getRequestURI();
        // Match Spring's Problem Details convention without trusting malformed URL characters.
        try {
            problem.setInstance(URI.create(requestPath));
        } catch (IllegalArgumentException ignored) {
            problem.setInstance(URI.create("/"));
        }
        return problem;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> apiException(ApiException error, HttpServletRequest request) {
        return response(error.status(), error.getMessage(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> authentication(HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "The email address or password is incorrect.", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> authorization(HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "You do not have permission to perform this action.", request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> invalidParameter(ConstraintViolationException exception, HttpServletRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getConstraintViolations().forEach(violation -> {
            String path = violation.getPropertyPath().toString();
            fields.putIfAbsent(path.substring(path.lastIndexOf('.') + 1), violation.getMessage());
        });
        ProblemDetail error = problem(HttpStatus.BAD_REQUEST, "Check the highlighted fields.", request);
        error.setProperty("fieldErrors", fields);
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> optimisticLock(HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "This record changed. Refresh the page and retry.", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> dataIntegrity(HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "The change conflicts with an existing record.", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception error, HttpServletRequest request) {
        log.error("Unhandled request error in {}: {}", request.getMethod(), error.getClass().getSimpleName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError field : exception.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(field.getField(), field.getDefaultMessage());
        }
        ProblemDetail error = problem(HttpStatus.BAD_REQUEST, "Check the highlighted fields.", servletRequest(request));
        error.setProperty("fieldErrors", fields);
        return handleExceptionInternal(exception, error, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (exception.isForReturnValue()) {
            return super.handleHandlerMethodValidationException(exception, headers, status, request);
        }
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error -> fields.putIfAbsent(
                    error instanceof FieldError field ? field.getField() : name, error.getDefaultMessage()));
        });
        ProblemDetail error = problem(HttpStatus.BAD_REQUEST, "Check the highlighted fields.", servletRequest(request));
        error.setProperty("fieldErrors", fields);
        return handleExceptionInternal(exception, error, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detail = exception.getCause() instanceof JsonProcessingException
                ? "The request contains malformed JSON or an invalid field."
                : "The request body is missing or unreadable.";
        return handleExceptionInternal(exception, problem(HttpStatus.BAD_REQUEST, detail, servletRequest(request)),
                headers, status, request);
    }

    private HttpServletRequest servletRequest(WebRequest request) {
        return ((ServletWebRequest) request).getRequest();
    }

    private ResponseEntity<ProblemDetail> response(HttpStatus status, String detail, HttpServletRequest request) {
        return ResponseEntity.status(status).body(problem(status, detail, request));
    }
}
