package com.neuropattern.app;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** The selected export payload; never changes application records. */
final class ExportDocument {
    final String id, name, mime, text;
    ExportDocument(String id, String name, String mime, String text) {
        if (id == null || !id.matches("export-[0-9]+-[0-9]+"))
            throw new IllegalArgumentException("Invalid export request.");
        boolean valid = "application/json".equals(mime) && "NeuroPattern-backup.json".equals(name)
                || "text/csv".equals(mime) && "NeuroPattern-behaviour.csv".equals(name)
                || "text/plain".equals(mime) && name != null
                && name.matches("NeuroPattern_[A-Za-z0-9_]{0,80}_[0-9]{1,3}day_report\\.txt");
        if (!valid || text == null) throw new IllegalArgumentException("Invalid export filename or file type.");
        this.id = id; this.name = name; this.mime = mime; this.text = text;
    }
    void write(OutputStream stream) throws IOException {
        if (stream == null) throw new IOException("The selected location could not be opened.");
        // Report success only after write, flush AND close succeed.
        try (OutputStream out = stream) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }
}
