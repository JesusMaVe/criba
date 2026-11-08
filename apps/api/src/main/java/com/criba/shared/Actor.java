package com.criba.shared;

/**
 * Quién provocó un hecho.
 *
 * <p>Hoy siempre hay un actor de tipo {@code USER} fijo, pero la columna existe desde el primer
 * evento: cuando llegue la autenticación, la historia ya está guardada con la forma correcta y no
 * hay que reescribirla.
 */
public record Actor(String type, String id) {

    public static final String USER = "USER";
    public static final String AGENT = "AGENT";

    public static Actor user(String id) {
        return new Actor(USER, id);
    }

    public static Actor agent(String id) {
        return new Actor(AGENT, id);
    }
}
