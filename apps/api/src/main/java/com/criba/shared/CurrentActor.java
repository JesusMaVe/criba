package com.criba.shared;

/**
 * Quién está haciendo la petición.
 *
 * <p>Hoy devuelve siempre el mismo actor local. Existe como interfaz porque el día que haya login
 * solo cambia la implementación: ni el servicio ni los eventos se enteran.
 */
public interface CurrentActor {

    Actor actor();
}
