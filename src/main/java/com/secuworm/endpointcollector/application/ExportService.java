package com.secuworm.endpointcollector.application;

import com.secuworm.endpointcollector.domain.EndpointRecord;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class ExportService {
    public static final String[] HEADER = new String[]{"Endpoint", "Host", "SourceURL", "Count"};

    public ExportResult exportCsv(String filePath, List<EndpointRecord> records) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return new ExportResult(false, "File path is empty.");
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            writer.write(toCsvLine(HEADER));
            writer.newLine();
            if (records != null) {
                for (EndpointRecord record : records) {
                    if (record == null) {
                        continue;
                    }
                    String[] row = new String[]{
                        safe(record.getEndpointUrl()),
                        safe(record.getHost()),
                        safe(record.getSourceUrl()),
                        String.valueOf(record.getCount())
                    };
                    writer.write(toCsvLine(row));
                    writer.newLine();
                }
            }
            return new ExportResult(true, "");
        } catch (IOException ex) {
            return new ExportResult(false, ex.getMessage());
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String toCsvLine(String[] values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(escape(values[i]));
        }
        return builder.toString();
    }

    private String escape(String value) {
        String text = value == null ? "" : value;
        boolean needsQuote = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");
        String escaped = text.replace("\"", "\"\"");
        if (needsQuote) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    public static class ExportResult {
        private final boolean success;
        private final String errorMessage;

        public ExportResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
