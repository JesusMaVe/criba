package com.criba.board;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Identidad normalizada de un nodo, para que dos fuentes distintas no creen dos nodos iguales.
 *
 * <p>El orden importa: el DOI manda, después el identificador de OpenAlex, después la URL
 * canónica, y solo si no hay nada de eso el título con el año.
 */
public final class NaturalKey {

    private NaturalKey() {
    }

    public static String of(String doi, String openalexId, String url, String title, Integer year) {
        if (hasText(doi)) {
            return "doi:" + doi.trim().toLowerCase(Locale.ROOT);
        }
        if (hasText(openalexId)) {
            return "openalex:" + openalexId.trim();
        }
        if (hasText(url)) {
            return "url:" + canonicalUrl(url);
        }
        return "title:" + slug(title) + ":" + (year == null ? "nd" : year);
    }

    /** Baja a minúsculas, quita query y fragmento, y normaliza la barra final. */
    static String canonicalUrl(String raw) {
        String trimmed = raw.trim();
        try {
            URI uri = new URI(trimmed);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            int port = uri.getPort();
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme().toLowerCase(Locale.ROOT);
            return scheme + "://" + host + (port == -1 ? "" : ":" + port) + path;
        }
        catch (URISyntaxException e) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
    }

    static String slug(String raw) {
        if (raw == null) {
            return "sin-titulo";
        }
        String withoutAccents = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
