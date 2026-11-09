package com.criba.agents.openalex;

/** OpenAlex no pudo responder. El mensaje nunca incluye la API key. */
public class OpenAlexException extends RuntimeException {

    OpenAlexException(String message) {
        super(message);
    }

    OpenAlexException(String message, Throwable cause) {
        super(message, cause);
    }
}
