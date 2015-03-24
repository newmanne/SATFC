package ca.ubc.cs.beta.stationpacking.webapp.rest;

import ca.ubc.cs.beta.stationpacking.webapp.model.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractController {
  private static final Logger log = LoggerFactory.getLogger(AbstractController.class);
  
  protected final static String JSON_CONTENT = "application/json";

  @ExceptionHandler
  @RequestMapping(produces = JSON_CONTENT)
  @ResponseBody
  ErrorResponse handleDefaultException(HttpServletResponse response, Exception ex) {
    log.error("Unexpected error while performing REST action.", ex);

    log.debug("Default exception handling - setting SC_BAD_REQUEST");
    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);

    final List<String> errors = new ArrayList<String>();
    handleException(ex, errors);
    return new ErrorResponse(23423l, errors);
  }

  private void handleException(Throwable ex, List<String> errors) {
    if (ex.getCause() != null) {
      handleException(ex.getCause(), errors);
    }

    errors.add(ex.getMessage());
  }
}