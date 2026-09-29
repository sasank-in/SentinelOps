package io.sentinelops.chaos;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for failure injection.
 *
 * <pre>
 * GET    /failure          list active failures
 * POST   /failure/{type}   start a failure: cpu, memory, database, latency, error-rate, crash
 * DELETE /failure/{type}   stop one failure
 * DELETE /failure          stop all failures
 * </pre>
 */
@RestController
@RequestMapping("/failure")
public class ChaosController {

	private final ChaosEngine engine;

	public ChaosController(ChaosEngine engine) {
		this.engine = engine;
	}

	@GetMapping
	public Map<String, List<ActiveFailure>> active() {
		return Map.of("active", this.engine.active());
	}

	@PostMapping("/{type}")
	public ResponseEntity<ActiveFailure> start(@PathVariable String type,
			@RequestBody(required = false) FailureRequest request) {
		ActiveFailure failure = this.engine.start(FailureType.fromPath(type),
				(request != null) ? request : FailureRequest.defaults());
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(failure);
	}

	@DeleteMapping("/{type}")
	public ResponseEntity<Void> stop(@PathVariable String type) {
		return this.engine.stop(FailureType.fromPath(type)) ? ResponseEntity.noContent().build()
				: ResponseEntity.notFound().build();
	}

	@DeleteMapping
	public ResponseEntity<Void> stopAll() {
		this.engine.stopAll();
		return ResponseEntity.noContent().build();
	}

	@ExceptionHandler(IllegalArgumentException.class)
	ProblemDetail badRequest(IllegalArgumentException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	@ExceptionHandler(IllegalStateException.class)
	ProblemDetail conflict(IllegalStateException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
	}

}
