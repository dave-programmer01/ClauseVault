package dev.clausevault.clausevault.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class PdfTextServiceTest {

    private final PdfTextService service = new PdfTextService();

    @Test
    void extractsTextFromSamplePdf() throws IOException {
        String sample = Files.readString(Path.of("samples/nda-vendor.txt"));
        String text = service.extract(TestPdfs.fromText(sample));

        String flat = text.replaceAll("\\s+", " ");
        assertTrue(flat.contains("whether or not derived from Confidential Information"), flat);
        assertTrue(flat.contains("without limit as to amount"), flat);
        assertTrue(flat.contains("10. Entire Agreement."), flat);
    }

    @Test
    void blankPdf_is422() throws IOException {
        AuditException e = assertThrows(AuditException.class, () -> service.extract(TestPdfs.fromText("")));
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, e.status());
    }

    @Test
    void garbage_is400() {
        AuditException e = assertThrows(AuditException.class, () -> service.extract("not a pdf".getBytes()));
        assertEquals(HttpStatus.BAD_REQUEST, e.status());
    }

    @Test
    void tooLong_is413() {
        AuditException e = assertThrows(AuditException.class,
            () -> service.validate("x".repeat(PdfTextService.MAX_CHARS + 1)));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, e.status());
        assertEquals("x".repeat(PdfTextService.MAX_CHARS), service.validate(" " + "x".repeat(PdfTextService.MAX_CHARS) + "\n"));
    }
}
