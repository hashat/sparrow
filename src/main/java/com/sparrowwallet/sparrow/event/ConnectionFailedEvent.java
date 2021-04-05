package com.sparrowwallet.sparrow.event;

import com.sparrowwallet.sparrow.net.TlsServerException;

public class ConnectionFailedEvent {
    private final Throwable exception;

    public ConnectionFailedEvent(Throwable exception) {
        this.exception = exception;
    }

    public Throwable getException() {
        return exception;
    }

    public String getMessage() {
        if(exception instanceof TlsServerException) {
            return exception.getMessage();
        }

        Throwable cause = (exception.getCause() != null ? exception.getCause() : exception);
        cause = (cause.getCause() != null ? cause.getCause() : cause);
        String message = splitCamelCase(cause.getClass().getSimpleName().replace("Exception", "Error"));
        return message + " (" + cause.getMessage() + ")";
    }

    static String splitCamelCase(String s) {
        return s.replaceAll(
                String.format("%s|%s|%s",
                        "(?<=[A-Z])(?=[A-Z][a-z])",
                        "(?<=[^A-Z])(?=[A-Z])",
                        "(?<=[A-Za-z])(?=[^A-Za-z])"
                ),
                " "
        );
    }
}
