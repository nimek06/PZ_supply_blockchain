package pl.pz.supplyblockchain.api;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import pl.pz.supplyblockchain.domain.DomainException;
import pl.pz.supplyblockchain.domain.ErrorCode;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    ResponseEntity<ProblemDetail> handleDomain(
            DomainException exception, HttpServletRequest request) {
        HttpStatus status = statusFor(exception.code());
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            LOG.error("Domain error {}", exception.code(), exception);
        }
        ProblemDetail problem = problem(status, exception.getMessage(), exception.code().name(), request);
        if (exception.retryAllowedAfter() != null) {
            problem.setProperty("retryAllowedAfter", exception.retryAllowedAfter());
        }
        return ResponseEntity.status(status).body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> handleUnreadable(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(problem(
                HttpStatus.BAD_REQUEST,
                "request body is malformed or contains unknown fields",
                ErrorCode.SCHEMA_VIOLATION.name(),
                request));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(problem(
                HttpStatus.BAD_REQUEST,
                "path or query parameter has an invalid type",
                ErrorCode.INVALID_REQUEST.name(),
                request));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ProblemDetail> handleNoResource(
            NoResourceFoundException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem(
                HttpStatus.NOT_FOUND, "resource not found", ErrorCode.NOT_FOUND.name(), request));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
        String correlationId = correlationId(request);
        LOG.error("Unhandled request error correlationId={}", correlationId, exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal server error",
                ErrorCode.INTERNAL_ERROR.name(),
                request));
    }

    private static ProblemDetail problem(
            HttpStatus status, String detail, String code, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("about:blank"));
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        problem.setProperty("correlationId", correlationId(request));
        return problem;
    }

    private static String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.ATTRIBUTE);
        return value == null ? UUID.randomUUID().toString() : value.toString();
    }

    private static HttpStatus statusFor(ErrorCode code) {
        return switch (code) {
            case INVALID_REQUEST, SCHEMA_VIOLATION, DEADLINE_EXPIRED, DEADLINE_TOO_FAR,
                    UNEXPECTED_STAGE -> HttpStatus.BAD_REQUEST;
            case PROFILE_VIOLATION -> HttpStatus.UNPROCESSABLE_ENTITY;
            case INVALID_SIGNATURE, SIGNER_MISMATCH, FORBIDDEN, ROLE_MISSING -> HttpStatus.FORBIDDEN;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case STAGE_ALREADY_PENDING, STAGE_ALREADY_COMPLETED, CHALLENGE_USED_OR_EXPIRED,
                    IDEMPOTENCY_CONFLICT -> HttpStatus.CONFLICT;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DEPENDENCY_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
