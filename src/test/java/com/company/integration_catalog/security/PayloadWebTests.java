package com.company.integration_catalog.security;

import com.company.integration_catalog.controller.MappingController;
import com.company.integration_catalog.exception.PayloadValidationException;
import com.company.integration_catalog.repository.UserAccountRepository;
import com.company.integration_catalog.service.MappingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MappingController.class)
@Import({SecurityConfig.class, CredentialAccess.class, AccountDetailsService.class})
class PayloadWebTests {
    private static final String BASE = "/api/v1/integrations/1/mappings/schemas";
    @Autowired MockMvc mvc;
    @MockitoBean MappingService mappings;
    @MockitoBean UserAccountRepository users;

    private MockMultipartFile metadata() {
        return new MockMultipartFile("metadata", "", "application/json",
                "{\"schemaType\":\"SOURCE_TO_DEST_REQUEST\",\"format\":\"CSV\"}".getBytes());
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "orders.csv", "text/csv", "id\n42".getBytes());
    }

    @ParameterizedTest @ValueSource(strings = {"EDITOR", "ADMIN"})
    void editorsAndAdminsCanUploadWithCsrf(String role) throws Exception {
        mvc.perform(multipart(BASE + "/upload").file(metadata()).file(file())
                        .with(user("test").roles(role)).with(csrf()))
                .andExpect(status().isOk());
        verify(mappings).uploadSchema(eq(1L), argThat(dto -> dto.getFormat().name().equals("CSV")), any());
    }

    @Test
    void viewersCannotUploadAndCsrfIsRequired() throws Exception {
        mvc.perform(multipart(BASE + "/upload").file(metadata()).file(file())
                        .with(user("viewer").roles("VIEWER")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(multipart(BASE + "/upload").file(metadata()).file(file())
                        .with(user("editor").roles("EDITOR")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(mappings);
    }

    @ParameterizedTest @ValueSource(strings = {"VIEWER", "EDITOR", "ADMIN"})
    void downloadsReturnOriginalBytesAsAttachmentForAllReadRoles(String role) throws Exception {
        when(mappings.getSampleFile(1L, 10L)).thenReturn(new MappingService.SampleFile("orders.csv", "text/csv", "id\r\n42".getBytes()));
        mvc.perform(get(BASE + "/10/sample").with(user("test").roles(role)))
                .andExpect(status().isOk()).andExpect(content().bytes("id\r\n42".getBytes()))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment;")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void missingPartsReturnBadRequestAndDownloadsRequireAuthentication() throws Exception {
        mvc.perform(multipart(BASE + "/upload").file(metadata()).with(user("test").roles("EDITOR")).with(csrf()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MISSING_UPLOAD_PART"));
        mvc.perform(get(BASE + "/10/sample")).andExpect(status().isUnauthorized());
        verifyNoInteractions(mappings);
    }

    @Test
    void formatErrorsUseStandardErrorEnvelopeAndMachineReadableCode() throws Exception {
        when(mappings.saveSchema(eq(1L), any())).thenThrow(new PayloadValidationException("INVALID_XML", "Malformed XML at line 1, column 8"));
        mvc.perform(post(BASE).with(user("test").roles("EDITOR")).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schemaType\":\"SOURCE_TO_DEST_REQUEST\",\"format\":\"XML\",\"rawContent\":\"<root>\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_XML"))
                .andExpect(jsonPath("$.path").value(BASE)).andExpect(jsonPath("$.timestamp").exists());
    }
}
