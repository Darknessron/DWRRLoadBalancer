package ron.dwrrlb.advice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
@Slf4j
public class ExceptionAdvice {

  @ExceptionHandler(Exception.class)
  public void handleUncaughtException(Throwable e) {
    log.error(e.getMessage(), e);
  }

}
