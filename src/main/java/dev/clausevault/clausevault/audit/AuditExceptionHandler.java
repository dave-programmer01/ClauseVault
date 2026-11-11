package dev.clausevault.clausevault.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Slf4j
@RestControllerAdvice
class AuditExceptionHandler {

    @ExceptionHandler(AuditException.class)
    ProblemDetail input(AuditException e) {
        return ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail tooLarge(MaxUploadSizeExceededException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the 10MB upload limit");
    }

    @ExceptionHandler(StageException.class)
    ProblemDetail stage(StageException e) {
        log.error("Pipeline stage failed", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
    }
}
