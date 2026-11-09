package com.criba.agents.openalex;

import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OpenAlexConnector {

    @Autowired
    OpenAlexConnector(OpenAlexProperties properties, JsonMapper json) {
        this(properties, json, System::nanoTime, duration -> Thread.sleep(duration));
    }

    OpenAlexConnector(OpenAlexProperties properties, JsonMapper json, LongSupplier nanoTime,
            RequestPacer.Sleeper sleeper) {
    }

    public OpenAlexSearch search(String query, int maxResults) {
        throw new UnsupportedOperationException("por implementar");
    }
}
