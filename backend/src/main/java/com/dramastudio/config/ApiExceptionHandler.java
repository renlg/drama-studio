package com.dramastudio.config;
import java.time.Instant; import java.util.*; import org.springframework.http.*; import org.springframework.orm.ObjectOptimisticLockingFailureException; import org.springframework.web.bind.MethodArgumentNotValidException; import org.springframework.web.bind.annotation.*; import org.springframework.web.server.ResponseStatusException;
@RestControllerAdvice public class ApiExceptionHandler {
 @ExceptionHandler(ResponseStatusException.class) ResponseEntity<?> status(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("code",e.getStatusCode().toString(),"message",e.getReason()==null?"Request failed":e.getReason(),"timestamp",Instant.now()));}
 @ExceptionHandler(ObjectOptimisticLockingFailureException.class) ResponseEntity<?> conflict(){return ResponseEntity.status(409).body(Map.of("code","REVISION_CONFLICT","message","工作流已被其他会话更新，请刷新后重试"));}
 @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<?> validation(MethodArgumentNotValidException e){return ResponseEntity.badRequest().body(Map.of("code","VALIDATION_FAILED","errors",e.getBindingResult().getFieldErrors().stream().map(x->Map.of("field",x.getField(),"message",Objects.toString(x.getDefaultMessage()))).toList()));}
}
