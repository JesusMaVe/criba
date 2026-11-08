package com.criba.board;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NaturalKeyTest {

    @Test
    @DisplayName("el DOI manda y se normaliza a minúsculas")
    void elDoiManda() {
        assertEquals("doi:10.1000/xyz", NaturalKey.of("  10.1000/XYZ ", "W123", "https://ejemplo.org/a", "T", 2020));
    }

    @Test
    @DisplayName("sin DOI se usa el identificador de OpenAlex")
    void sinDoiSeUsaOpenAlex() {
        assertEquals("openalex:W2741809807", NaturalKey.of(null, "W2741809807", "https://ejemplo.org/a", "T", 2020));
    }

    @Test
    @DisplayName("la URL canónica ignora query, fragmento, mayúsculas y barra final")
    void laUrlSeCanoniza() {
        String conRuido = NaturalKey.of(null, null, "HTTPS://Ejemplo.ORG/Paper/?utm_source=x#seccion", "T", 2020);
        String limpia = NaturalKey.of(null, null, "https://ejemplo.org/Paper", "T", 2020);
        assertEquals(limpia, conRuido);
    }

    @Test
    @DisplayName("dos papers distintos con el mismo título y año son el mismo nodo")
    void sinIdentificadoresSeUsaTituloYAño() {
        String a = NaturalKey.of(null, null, null, "Atención es todo lo que necesitas", 2017);
        String b = NaturalKey.of(null, null, null, "atencion es todo lo que necesitas", 2017);
        assertEquals(a, b);
        assertNotEquals(a, NaturalKey.of(null, null, null, "Atención es todo lo que necesitas", 2018));
    }

    @Test
    @DisplayName("títulos con acentos y signos no colisionan con otro distinto")
    void elSlugQuitaAcentos() {
        assertEquals("title:atencion-es-todo-lo-que-necesitas:2017",
                NaturalKey.of(null, null, null, "¡Atención, es todo lo que necesitas!", 2017));
    }
}
