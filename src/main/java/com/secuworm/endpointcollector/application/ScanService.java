package com.secuworm.endpointcollector.application;

import com.secuworm.endpointcollector.burpadapter.HistoryItemPayload;
import com.secuworm.endpointcollector.domain.EndpointCandidate;
import com.secuworm.endpointcollector.domain.EndpointExtractor;
import com.secuworm.endpointcollector.domain.EndpointNormalizer;
import com.secuworm.endpointcollector.domain.EndpointRecord;
import com.secuworm.endpointcollector.domain.EndpointRepository;
import com.secuworm.endpointcollector.domain.ScanResult;
import com.secuworm.endpointcollector.infra.AppConfig;
import com.secuworm.endpointcollector.infra.ExtensionLogger;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public class ScanService {
    private final EndpointExtractor extractor;
    private final EndpointNormalizer normalizer;
    private final EndpointRepository repository;
    private final ExtensionLogger logger;

    public ScanService(ExtensionLogger logger) {
        this(new EndpointExtractor(), new EndpointNormalizer(), new EndpointRepository(), logger);
    }

    public ScanService(
        EndpointExtractor extractor,
        EndpointNormalizer normalizer,
        EndpointRepository repository,
        ExtensionLogger logger
    ) {
        this.extractor = extractor == null ? new EndpointExtractor() : extractor;
        this.normalizer = normalizer == null ? new EndpointNormalizer() : normalizer;
        this.repository = repository == null ? new EndpointRepository() : repository;
        this.logger = logger;
    }

    public ScanResult scan(
        List<HistoryItemPayload> historyItems,
        ProgressCallback progressCallback,
        BooleanSupplier shouldStop
    ) {
        long startedAt = System.currentTimeMillis();
        List<HistoryItemPayload> items = historyItems == null ? new ArrayList<>() : new ArrayList<>(historyItems);

        repository.clear();
        int totalItems = items.size();
        int processedItems = 0;
        int totalCandidates = 0;
        int errorCount = 0;

        for (int start = 0; start < totalItems; start += AppConfig.SCAN_BATCH_SIZE) {
            if (shouldStopRequested(shouldStop)) {
                break;
            }
            int end = Math.min(start + AppConfig.SCAN_BATCH_SIZE, totalItems);
            for (int i = start; i < end; i++) {
                if (shouldStopRequested(shouldStop)) {
                    break;
                }

                HistoryItemPayload payload = items.get(i);
                try {
                    if (payload == null) {
                        continue;
                    }
                    String responseText = payload.getResponseText() == null ? "" : payload.getResponseText();
                    String contentType = payload.getContentType() == null ? "" : payload.getContentType();
                    String sourceUrl = payload.getSourceUrl() == null ? "" : payload.getSourceUrl();
                    String sourceRequestMethod = payload.getSourceRequestMethod();

                    List<EndpointCandidate> candidates = extractor.extract(responseText, contentType, sourceUrl);
                    totalCandidates += candidates.size();
                    for (EndpointCandidate candidate : candidates) {
                        String endpointUrl = normalizer.normalize(candidate, sourceUrl);
                        if (endpointUrl == null) {
                            continue;
                        }
                        repository.upsert(
                            endpointUrl,
                            extractHost(endpointUrl),
                            sourceUrl,
                            sourceRequestMethod,
                            payload.getSourceRequestHeaders()
                        );
                    }
                } catch (Exception ex) {
                    errorCount += 1;
                    logError("scan item failed: " + ex.getMessage());
                } finally {
                    processedItems += 1;
                }
            }

            if (progressCallback != null) {
                try {
                    progressCallback.onProgress(totalItems, processedItems, errorCount, repository.size());
                } catch (Exception ex) {
                    logError("progress callback failed: " + ex.getMessage());
                }
            }
        }

        long durationMs = System.currentTimeMillis() - startedAt;
        return new ScanResult(
            totalItems,
            processedItems,
            totalCandidates,
            repository.size(),
            errorCount,
            durationMs
        );
    }

    public List<EndpointRecord> getRecords() {
        return repository.getAll();
    }

    private String extractHost(String endpointUrl) {
        try {
            URI uri = new URI(endpointUrl);
            if (uri.getHost() == null) {
                return "";
            }
            int port = uri.getPort();
            if (port > 0) {
                return uri.getHost() + ":" + port;
            }
            return uri.getHost();
        } catch (URISyntaxException ex) {
            return "";
        }
    }

    private boolean shouldStopRequested(BooleanSupplier shouldStop) {
        if (shouldStop == null) {
            return false;
        }
        try {
            return shouldStop.getAsBoolean();
        } catch (Exception ex) {
            logError("stop callback failed: " + ex.getMessage());
            return false;
        }
    }

    private void logError(String message) {
        if (logger != null) {
            logger.error(message);
        }
    }

    public interface ProgressCallback {
        void onProgress(int totalItems, int processedItems, int errorCount, int uniqueEndpoints);
    }
}
