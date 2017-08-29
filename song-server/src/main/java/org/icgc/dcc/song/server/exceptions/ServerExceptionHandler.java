package org.icgc.dcc.song.server.exceptions;

import lombok.val;
import org.icgc.dcc.song.core.exceptions.ServerException;
import org.icgc.dcc.song.core.exceptions.SongError;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.icgc.dcc.song.core.exceptions.ServerErrors.UNKNOWN_ERROR;

@ControllerAdvice
public class ServerExceptionHandler {

  @ExceptionHandler(ServerException.class)
  public ResponseEntity<String> handleServerException(HttpServletRequest request, HttpServletResponse response, ServerException ex){
    val requestUrl = request.getRequestURL().toString();
    val songError = ex.getSongError();
    songError.setRequestUrl(requestUrl);
    return songError.getResponseEntity();
  }

  @ExceptionHandler(Throwable.class)
  public ResponseEntity<String> handleThrowable(HttpServletRequest request, HttpServletResponse response, Throwable ex){
    val requestUrl = request.getRequestURL().toString();
    val error = new SongError();
    error.setRequestUrl(requestUrl);
    error.setTimestamp(System.currentTimeMillis());
    error.setHttpStatus(UNKNOWN_ERROR.getHttpStatus());
    error.setErrorId(UNKNOWN_ERROR.getErrorId());
    error.setMessage(ex.getMessage());
    error.setStackTraceElementArray(ex.getStackTrace());
    return error.getResponseEntity();
  }

}
