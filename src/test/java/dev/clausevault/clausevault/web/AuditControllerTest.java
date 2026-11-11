package dev.clausevault.clausevault.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.clausevault.clausevault.exception.AuditException;
import dev.clausevault.clausevault.exception.StageException;
import dev.clausevault.clausevault.model.AuditResult;
import dev.clausevault.clausevault.model.ReviewAction;
import dev.clausevault.clausevault.model.ReviewRequest;
import dev.clausevault.clausevault.model.ReviewStatus;
import dev.clausevault.clausevault.model.Risk;
import dev.clausevault.clausevault.model.Severity;
import dev.clausevault.clausevault.service.AuditService;
import dev.clausevault.clausevault.service.PdfTextService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuditController.class)
class AuditControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    AuditService auditService;

    @MockitoBean
    PdfTextService pdfTextService;

    @Test
    void txtUploadSkipsPdfBox() throws Exception {
        when(pdfTextService.validate("hello contract")).thenReturn("hello contract");
        when(auditService.audit(eq("nda.txt"), eq("hello contract")))
            .thenReturn(new AuditResult("id1", "nda.txt", List.of(), List.of(), List.of()));

        mvc.perform(multipart("/api/audit")
                .file(new MockMultipartFile("file", "nda.txt", "text/plain", "hello contract".getBytes())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("id1"))
            .andExpect(jsonPath("$.filename").value("nda.txt"));
    }

    @Test
    void unsupportedFile_is400() throws Exception {
        mvc.perform(multipart("/api/audit")
                .file(new MockMultipartFile("file", "nda.docx", "application/octet-stream", new byte[] {1, 2, 3})))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Upload a PDF or a .txt file"));
    }

    @Test
    void stageFailure_is502() throws Exception {
        when(pdfTextService.validate(any())).thenReturn("t");
        when(auditService.audit(any(), any())).thenThrow(new StageException("clause-extraction: NEAR AI call failed"));

        mvc.perform(multipart("/api/audit")
                .file(new MockMultipartFile("file", "a.txt", "text/plain", "t".getBytes())))
            .andExpect(status().isBadGateway());
    }

    @Test
    void review_happyPath_returnsUpdatedResult() throws Exception {
        Risk reviewed = new Risk("r1", "c2", "UNLIMITED_LIABILITY", Severity.MEDIUM, "Capped in practice.",
            "without limit", ReviewStatus.ACCEPTED, true);
        when(auditService.review(eq("id1"), eq(new ReviewRequest("r1", ReviewAction.EDIT, Severity.MEDIUM, "Capped in practice."))))
            .thenReturn(new AuditResult("id1", "nda.txt", List.of(), List.of(reviewed), List.of()));

        mvc.perform(post("/api/audit/id1/review").contentType(MediaType.APPLICATION_JSON)
                .content("{\"riskId\":\"r1\",\"action\":\"EDIT\",\"severity\":\"MEDIUM\",\"explanation\":\"Capped in practice.\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.risks[0].status").value("ACCEPTED"))
            .andExpect(jsonPath("$.risks[0].edited").value(true))
            .andExpect(jsonPath("$.risks[0].severity").value("MEDIUM"));
    }

    @Test
    void review_unknownAudit_is404() throws Exception {
        when(auditService.review(eq("nope"), any()))
            .thenThrow(new AuditException(HttpStatus.NOT_FOUND, "No audit with id nope"));

        mvc.perform(post("/api/audit/nope/review").contentType(MediaType.APPLICATION_JSON)
                .content("{\"riskId\":\"r1\",\"action\":\"ACCEPT\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("No audit with id nope"));
    }

    @Test
    void review_unknownRisk_is404() throws Exception {
        when(auditService.review(eq("id1"), eq(new ReviewRequest("r99", ReviewAction.DISMISS, null, null))))
            .thenThrow(new AuditException(HttpStatus.NOT_FOUND, "No risk r99 in audit id1"));

        mvc.perform(post("/api/audit/id1/review").contentType(MediaType.APPLICATION_JSON)
                .content("{\"riskId\":\"r99\",\"action\":\"DISMISS\"}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void review_emptyEdit_is400() throws Exception {
        when(auditService.review(eq("id1"), eq(new ReviewRequest("r1", ReviewAction.EDIT, null, null))))
            .thenThrow(new AuditException(HttpStatus.BAD_REQUEST, "EDIT needs a severity and/or an explanation"));

        mvc.perform(post("/api/audit/id1/review").contentType(MediaType.APPLICATION_JSON)
                .content("{\"riskId\":\"r1\",\"action\":\"EDIT\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("EDIT needs a severity and/or an explanation"));
    }

    @Test
    void review_invalidAction_is400() throws Exception {
        mvc.perform(post("/api/audit/id1/review").contentType(MediaType.APPLICATION_JSON)
                .content("{\"riskId\":\"r1\",\"action\":\"MAYBE\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void unknownId_is404() throws Exception {
        when(auditService.get("nope")).thenReturn(java.util.Optional.empty());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/audit/nope"))
            .andExpect(status().isNotFound());
    }
}
