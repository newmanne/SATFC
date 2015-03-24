package ca.ubc.cs.beta.stationpacking.webapp.model;

import java.io.Serializable;
import java.util.List;

public class ErrorResponse implements Serializable {
  private static final long serialVersionUID = 7707914283100541061L;
  
  private Long errorId;
  private List<String> errors;
  
  public ErrorResponse() {
    // empty
  }
  
  public ErrorResponse(Long errorId, List<String> errors) {
    this.errorId = errorId;
    this.errors = errors;
  }

  public Long getErrorId() {
    return errorId;
  }

  public void setErrorId(Long errorId) {
    this.errorId = errorId;
  }

  public List<String> getErrors() {
    return errors;
  }

  public void setErrors(List<String> errors) {
    this.errors = errors;
  }
}