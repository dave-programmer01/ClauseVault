package dev.clausevault.clausevault.service;

import dev.clausevault.clausevault.exception.AuditException;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PdfTextService {

    /** Stage 1 echoes every clause verbatim, so longer contracts blow the demo latency budget. */
    public static final int MAX_CHARS = 12_000;

    public String extract(byte[] pdf) {
        String text;
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            text = new PDFTextStripper().getText(doc);
        } catch (IOException e) {
            throw new AuditException(HttpStatus.BAD_REQUEST, "Could not read PDF: " + e.getMessage());
        }
        return validate(text);
    }

    /** Applies the same limits to text from any source (PDF or .txt upload). Returns the trimmed text. */
    public String validate(String text) {
        if (text == null || text.isBlank()) {
            throw new AuditException(HttpStatus.UNPROCESSABLE_CONTENT, "No extractable text (scanned PDF?)");
        }
        String trimmed = text.strip();
        if (trimmed.length() > MAX_CHARS) {
            throw new AuditException(HttpStatus.PAYLOAD_TOO_LARGE,
                "Contract has " + trimmed.length() + " characters; the limit is " + MAX_CHARS);
        }
        return trimmed;
    }
}
