package com.company.integration_catalog.security;

import com.company.integration_catalog.ai.*;
import com.company.integration_catalog.controller.AiMappingController;
import com.company.integration_catalog.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AiMappingController.class)
@Import({SecurityConfig.class,CredentialAccess.class,AccountDetailsService.class})
class AiMappingWebTests {
    private static final String BASE="/api/v1/integrations/1/mappings";
    @Autowired MockMvc mvc;
    @MockitoBean AiMappingService service;
    @MockitoBean UserAccountRepository users;
    private static final String REQUEST="{\"sourceSchemaId\":10,\"targetSchemaId\":20,\"expectedContextRevision\":\"revision\"}";

    @ParameterizedTest @ValueSource(strings={"ADMIN","EDITOR"})
    void authorizedEditorsCanCreateJobsWithCsrfAndIdempotencyKey(String role) throws Exception {
        when(service.create(eq(1L),eq("test"),eq("key"),any())).thenReturn(
                new AiMappingDtos.JobView("job","QUEUED",null,null,null,null,null,2000));
        mvc.perform(post(BASE+"/ai/jobs").with(user("test").roles(role)).with(csrf()).header("Idempotency-Key","key")
                .contentType(MediaType.APPLICATION_JSON).content(REQUEST)).andExpect(status().isAccepted())
                .andExpect(header().string("Location",BASE+"/ai/jobs/job"));
    }
    @Test void viewersCannotWriteAndMissingCsrfOrAuthenticationCannotCreateJobs() throws Exception {
        mvc.perform(post(BASE+"/ai/jobs").with(user("viewer").roles("VIEWER")).with(csrf())
                .header("Idempotency-Key","key").contentType(MediaType.APPLICATION_JSON).content(REQUEST)).andExpect(status().isForbidden());
        mvc.perform(post(BASE+"/ai/jobs").with(user("editor").roles("EDITOR"))
                .header("Idempotency-Key","key").contentType(MediaType.APPLICATION_JSON).content(REQUEST)).andExpect(status().isForbidden());
        mvc.perform(get(BASE+"/context")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings={"ADMIN","EDITOR","VIEWER"})
    void readRolesCanInspectContextCapabilitiesStatusAndResults(String role) throws Exception {
        for(String suffix:new String[]{"/context","/ai/capabilities","/ai/jobs/job","/ai/jobs/job/result"})
            mvc.perform(get(BASE+suffix).with(user("test").roles(role))).andExpect(status().isOk());
    }
    @Test void emptyAppendAndMissingIdempotencyKeyAreRejected() throws Exception {
        mvc.perform(post(BASE+"/fields/append").with(user("editor").roles("EDITOR")).with(csrf()).header("Idempotency-Key","key")
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedContextRevision\":\"revision\",\"mappings\":[]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(BASE+"/ai/jobs").with(user("editor").roles("EDITOR")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(REQUEST)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
