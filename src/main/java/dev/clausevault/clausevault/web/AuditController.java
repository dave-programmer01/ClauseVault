package dev.clausevault.clausevault.web;

import static java.nio.charset.StandardCharsets.UTF_8;

import dev.clausevault.clausevault.exception.AuditException;
import dev.clausevault.clausevault.model.AuditResult;
import dev.clausevault.clausevault.model.AuditRun;
import dev.clausevault.clausevault.model.ReviewRequest;
import dev.clausevault.clausevault.service.AuditService;
import dev.clausevault.clausevault.service.PdfTextService;
import java.io.IOException;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final PdfTextService pdfTextService;
    private final AuditService auditService;

    public AuditController(PdfTextService pdfTextService, AuditService auditService) {
        this.pdfTextService = pdfTextService;
        this.auditService = auditService;
    }

    /** Synchronous: runs both stages and returns clauses, risks and one TEE receipt per stage. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AuditResult audit(@RequestParam("file") MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename() == null || file.getOriginalFilename().isBlank()
            ? "upload" : file.getOriginalFilename();
        byte[] bytes = file.getBytes();
        String text;
        if (isPdf(filename, file.getContentType(), bytes)) {
            text = pdfTextService.extract(bytes);
        } else if (isText(filename, file.getContentType())) {
            text = pdfTextService.validate(new String(bytes, UTF_8));
        } else {
            throw new AuditException(HttpStatus.BAD_REQUEST, "Upload a PDF or a .txt file");
        }
        return auditService.audit(filename, text);
    }

    /** Human review of one risk. Returns the full updated result so the UI replaces its state from one response. */
    @PostMapping(path = "/{id}/review", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AuditResult review(@PathVariable String id, @RequestBody ReviewRequest request) {
        return auditService.review(id, request);
    }

    @GetMapping("/{id}")
    public AuditResult get(@PathVariable String id) {
        return auditService.get(id)
            .map(AuditRun::result)
            .orElseThrow(() -> new AuditException(HttpStatus.NOT_FOUND, "No audit with id " + id));
    }

    private static boolean isPdf(String filename, String contentType, byte[] bytes) {
        return filename.toLowerCase(Locale.ROOT).endsWith(".pdf")
            || MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(contentType)
            || (bytes.length >= 4 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F');
    }

    private static boolean isText(String filename, String contentType) {
        return filename.toLowerCase(Locale.ROOT).endsWith(".txt")
            || (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("text/plain"));
    }
}
