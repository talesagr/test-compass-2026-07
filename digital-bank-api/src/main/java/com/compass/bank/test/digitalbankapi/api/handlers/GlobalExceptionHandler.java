package com.compass.bank.test.digitalbankapi.api.handlers;

import com.compass.bank.test.digitalbankapi.application.exception.AccountNotFoundException;
import com.compass.bank.test.digitalbankapi.application.exception.BadRequestException;
import com.compass.bank.test.digitalbankapi.application.exception.IdempotencyKeyConflictException;
import com.compass.bank.test.digitalbankapi.application.exception.IdempotencyLockTimeoutException;
import com.compass.bank.test.digitalbankapi.application.exception.InsufficientBalanceException;
import com.compass.bank.test.digitalbankapi.application.exception.SameAccountTransferException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ProblemDetail> handle(MethodArgumentNotValidException ex) {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
		problem.setTitle("Validation failed");
		problem.setType(URI.create("about:blank"));
		Map<String, String> errors = new HashMap<>();
		ex.getBindingResult().getFieldErrors()
				.forEach(fe -> errors.put(fe.getField(), fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage()));
		problem.setProperty("errors", errors);
		return ResponseEntity.badRequest().body(problem);
	}

	@ExceptionHandler(AccountNotFoundException.class)
	public ResponseEntity<ProblemDetail> handle(AccountNotFoundException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		problem.setTitle("Account not found");
		problem.setProperty("accountId", ex.getAccountId().toString());
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
	}

	@ExceptionHandler(InsufficientBalanceException.class)
	public ResponseEntity<ProblemDetail> handle(InsufficientBalanceException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
		problem.setTitle("Insufficient balance");
		return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
	}

	@ExceptionHandler(SameAccountTransferException.class)
	public ResponseEntity<ProblemDetail> handle(SameAccountTransferException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
		problem.setTitle("Invalid transfer");
		return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problem);
	}

	@ExceptionHandler(BadRequestException.class)
	public ResponseEntity<ProblemDetail> handle(BadRequestException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
		problem.setTitle("Bad request");
		return ResponseEntity.badRequest().body(problem);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ProblemDetail> handle(IllegalArgumentException ex) {
		return handleIllegalArgumentFromThirdParty(ex);
	}

	private ResponseEntity<ProblemDetail> handleIllegalArgumentFromThirdParty(IllegalArgumentException ex) {
		log.warn("IllegalArgumentException treated as third-party failure", ex);
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_GATEWAY);
		problem.setTitle("Third-party failure");
		problem.setDetail("A dependency reported an invalid argument. This response is not a client validation error.");
		return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(problem);
	}

	@ExceptionHandler(CompletionException.class)
	public ResponseEntity<ProblemDetail> handle(CompletionException ex) {
		return dispatchFromAsyncCause(unwrapCompletionChain(ex));
	}

	@ExceptionHandler(IdempotencyKeyConflictException.class)
	public ResponseEntity<ProblemDetail> handle(IdempotencyKeyConflictException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
		problem.setTitle("Idempotency key conflict");
		return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
	}

	@ExceptionHandler(IdempotencyLockTimeoutException.class)
	public ResponseEntity<ProblemDetail> handle(IdempotencyLockTimeoutException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
		problem.setTitle("Idempotent transfer not ready");
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
	}

	@ExceptionHandler(PessimisticLockingFailureException.class)
	public ResponseEntity<ProblemDetail> handle(PessimisticLockingFailureException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
		problem.setTitle("Could not acquire account row lock");
		return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(problem);
	}

	private ResponseEntity<ProblemDetail> dispatchFromAsyncCause(Throwable cause) {
		return switch (cause) {
			case AccountNotFoundException e -> handle(e);
			case InsufficientBalanceException e -> handle(e);
			case SameAccountTransferException e -> handle(e);
			case BadRequestException e -> handle(e);
			case IdempotencyKeyConflictException e -> handle(e);
			case IdempotencyLockTimeoutException e -> handle(e);
			case PessimisticLockingFailureException e -> handle(e);
			case IllegalArgumentException e -> handleIllegalArgumentFromThirdParty(e);
			default -> handleUnexpected(cause);
		};
	}

	private ResponseEntity<ProblemDetail> handleUnexpected(Throwable cause) {
		log.error("Unhandled async or server failure", cause);
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
		problem.setTitle("Internal error");
		problem.setDetail("An unexpected error occurred.");
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
	}

	private static Throwable unwrapCompletionChain(Throwable ex) {
		Throwable t = ex;
		while (t instanceof CompletionException ce && ce.getCause() != null) {
			t = ce.getCause();
		}
		return t;
	}
}
