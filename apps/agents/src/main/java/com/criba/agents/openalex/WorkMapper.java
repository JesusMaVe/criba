package com.criba.agents.openalex;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Traduce un trabajo de OpenAlex a la forma normalizada que entiende Criba. */
final class WorkMapper {

    private static final Pattern DOI_PREFIX =
            Pattern.compile("^(?:https?://(?:dx\\.)?doi\\.org/|doi:)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOI = Pattern.compile("^10\\.\\d+/.+");
    private static final Pattern OPENALEX_WORK_ID = Pattern.compile("^W\\d+$");

    private WorkMapper() {
    }

    /** Vacío si no se puede crear un nodo con él: la API exige título y deduplica por identidad. */
    static Optional<OpenAlexWork> toWork(OpenAlexResponse.Work work) {
        String id = shortId(work.id());
        if (id == null || work.title() == null || work.title().isBlank()) {
            return Optional.empty();
        }
        OpenAlexResponse.Location location = work.primaryLocation();
        return Optional.of(new OpenAlexWork(
                id,
                normalizeDoi(work.doi()),
                location == null ? null : normalizeUrl(location.landingPageUrl()),
                work.title().trim(),
                rebuildAbstract(work.abstractInvertedIndex()),
                work.publicationYear(),
                location == null || location.source() == null ? null : location.source().displayName()));
    }

    /** DOI desnudo y en minúsculas, o {@code null} si no lo es. */
    static String normalizeDoi(String raw) {
        if (raw == null) {
            return null;
        }
        String doi = DOI_PREFIX.matcher(raw.trim()).replaceFirst("").toLowerCase(Locale.ROOT);
        return DOI.matcher(doi).matches() ? doi : null;
    }

    /**
     * URL http(s) sin fragmento y con esquema y host en minúsculas.
     *
     * <p>Conserva la query, a diferencia de la clave natural: aquí la URL tiene que seguir abriendo
     * la página, y hay sitios donde la query identifica el documento.
     */
    static String normalizeUrl(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            URI uri = new URI(raw.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null) {
                return null;
            }
            return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT)
                    + (uri.getPort() == -1 ? "" : ":" + uri.getPort())
                    + (uri.getRawPath() == null ? "" : uri.getRawPath())
                    + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        }
        catch (URISyntaxException e) {
            return null;
        }
    }

    private static String shortId(String raw) {
        if (raw == null) {
            return null;
        }
        String id = raw.substring(raw.lastIndexOf('/') + 1);
        return OPENALEX_WORK_ID.matcher(id).matches() ? id : null;
    }

    private static String rebuildAbstract(Map<String, List<Integer>> invertedIndex) {
        if (invertedIndex == null || invertedIndex.isEmpty()) {
            return null;
        }
        TreeMap<Integer, String> words = new TreeMap<>();
        invertedIndex.forEach((word, positions) -> positions.forEach(position -> words.put(position, word)));
        return words.values().stream().collect(Collectors.joining(" "));
    }
}
