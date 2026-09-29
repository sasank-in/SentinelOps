package io.sentinelops.order;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps failures to RFC 9457 problem responses. Unexpected errors are logged with their
 * stack trace: these log lines are the evidence the incident agent reads later.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(PaymentUnavailableException.class)
	ProblemDetail paymentUnavailable(PaymentUnavailableException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
				"Payment service unavailable");
		problem.setProperty("orderId", ex.getOrderId());
		return problem;
	}

	@ExceptionHandler({ CannotCreateTransactionException.class, DataAccessResourceFailureException.class })
	ProblemDetail databaseUnavailable(Exception ex, HttpServletRequest request) {
		logger.error("Database unavailable while handling {} {}", request.getMethod(), request.getRequestURI(), ex);
		return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Database unavailable");
	}

	@ExceptionHandler(Exception.class)
	ProblemDetail unexpected(Exception ex, HttpServletRequest request) {
		logger.error("Unhandled error while handling {} {}", request.getMethod(), request.getRequestURI(), ex);
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
	}

}
