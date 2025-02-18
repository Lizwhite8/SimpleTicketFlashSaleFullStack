package Com.SimpleFlashSaleBackend.SimpleFlashSale.Exception;

import Com.SimpleFlashSaleBackend.SimpleFlashSale.Response.Response;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // ✅ Handle generic exceptions
    @ExceptionHandler(Exception.class)
    public Response<Object> handleGlobalException(Exception ex, WebRequest request) {
        return new Response<>(500, "Internal Server Error: " + ex.getMessage(), null);
    }

    // ✅ Handle resource not found exceptions
    @ExceptionHandler(ResourceNotFoundException.class)
    public Response<Object> handleResourceNotFoundException(ResourceNotFoundException ex) {
        return new Response<>(404, ex.getMessage(), null);
    }

    // ✅ Handle invalid requests (e.g., validation errors)
    @ExceptionHandler(IllegalArgumentException.class)
    public Response<Object> handleIllegalArgumentException(IllegalArgumentException ex) {
        return new Response<>(400, "Bad Request: " + ex.getMessage(), null);
    }
}