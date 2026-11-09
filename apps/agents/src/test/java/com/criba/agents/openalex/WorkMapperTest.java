package com.criba.agents.openalex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class WorkMapperTest {

    private static OpenAlexResponse.Work work(String id, String title) {
        return new OpenAlexResponse.Work(id, null, title, null, null, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://doi.org/10.1186/ABC-1",
            "http://dx.doi.org/10.1186/abc-1",
            "  HTTPS://DOI.ORG/10.1186/abc-1 ",
            "doi:10.1186/ABC-1",
            "10.1186/abc-1"})
    @DisplayName("un DOI queda desnudo y en minúsculas, como lo espera NaturalKey")
    void normalizaElDoi(String raw) {
        assertEquals("10.1186/abc-1", WorkMapper.normalizeDoi(raw));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "no-es-un-doi", "11.1000/xyz", "10.1000"})
    @DisplayName("lo que no es un DOI no se inventa")
    void rechazaDoisInvalidos(String raw) {
        assertNull(WorkMapper.normalizeDoi(raw));
    }

    @Test
    @DisplayName("una URL conserva ruta y query, y pierde el fragmento y las mayúsculas del host")
    void normalizaLaUrl() {
        assertEquals("https://ejemplo.org:8443/Paper?id=7",
                WorkMapper.normalizeUrl("  HTTPS://Ejemplo.ORG:8443/Paper?id=7#seccion "));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "javascript:alert(1)", "ftp://ejemplo.org/a", "/ruta/relativa", "http://ej emplo.org"})
    @DisplayName("solo pasan URLs http o https bien formadas: terminan en un enlace de la interfaz")
    void rechazaUrlsInseguras(String raw) {
        assertNull(WorkMapper.normalizeUrl(raw));
    }

    @Test
    @DisplayName("el abstract se reconstruye en orden desde el índice invertido")
    void reconstruyeElAbstract() {
        OpenAlexResponse.Work raw = new OpenAlexResponse.Work("https://openalex.org/W1", null, "T", null, null,
                Map.of("revisión", List.of(1), "La", List.of(0), "la", List.of(3), "automatiza", List.of(2), "criba", List.of(4)));

        assertEquals("La revisión automatiza la criba", WorkMapper.toWork(raw).orElseThrow().abstractText());
    }

    @Test
    @DisplayName("convierte el trabajo completo")
    void convierteElTrabajo() {
        OpenAlexResponse.Work raw = new OpenAlexResponse.Work(
                "https://openalex.org/W2095601341",
                "https://doi.org/10.1186/2046-4053-3-74",
                "  Systematic review automation technologies ",
                2014,
                new OpenAlexResponse.Location("https://doi.org/10.1186/2046-4053-3-74",
                        new OpenAlexResponse.Source("Systematic Reviews")),
                null);

        assertEquals(new OpenAlexWork("W2095601341", "10.1186/2046-4053-3-74",
                        "https://doi.org/10.1186/2046-4053-3-74", "Systematic review automation technologies",
                        null, 2014, "Systematic Reviews"),
                WorkMapper.toWork(raw).orElseThrow());
    }

    @Test
    @DisplayName("tolera ubicación y fuente ausentes")
    void toleraCamposAusentes() {
        OpenAlexWork sinUbicacion = WorkMapper.toWork(work("https://openalex.org/W1", "T")).orElseThrow();
        assertNull(sinUbicacion.url());
        assertNull(sinUbicacion.venue());
        assertNull(sinUbicacion.abstractText());

        OpenAlexResponse.Work sinFuente = new OpenAlexResponse.Work("https://openalex.org/W1", null, "T", null,
                new OpenAlexResponse.Location(null, null), Map.of());
        assertNull(WorkMapper.toWork(sinFuente).orElseThrow().venue());
        assertNull(WorkMapper.toWork(sinFuente).orElseThrow().abstractText());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    @DisplayName("sin título no hay nodo: la API lo exige")
    void descartaSinTitulo(String title) {
        assertTrue(WorkMapper.toWork(work("https://openalex.org/W1", title)).isEmpty());
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "https://openalex.org/A123", "W12a"})
    @DisplayName("sin identificador válido no hay con qué deduplicar")
    void descartaSinIdentificador(String id) {
        assertTrue(WorkMapper.toWork(work(id, "T")).isEmpty());
    }

    @Test
    @DisplayName("la identidad prioriza el DOI y usa OpenAlex como respaldo")
    void identidad() {
        assertEquals("doi:10.1/a", new OpenAlexWork("W1", "10.1/a", null, "T", null, null, null).naturalKey());
        assertEquals("openalex:W1", new OpenAlexWork("W1", null, null, "T", null, null, null).naturalKey());
    }
}
