package dev.clausevault.clausevault.audit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
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
    void unknownId_is404() throws Exception {
        when(auditService.get("nope")).thenReturn(java.util.Optional.empty());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/audit/nope"))
            .andExpect(status().isNotFound());
    }
}
