package com.example.copilot;

import com.example.copilot.query.QueryController;
import com.example.copilot.query.QueryEventRecorder;
import com.example.copilot.query.RetrievalClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@WebMvcTest(QueryController.class)
class ArchitectureTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean RetrievalClient retrieval;
    @MockitoBean QueryEventRecorder events;

    @BeforeEach void stubRetrieval() throws Exception {
        when(retrieval.health()).thenReturn(objectMapper.readTree("{\"status\":\"ready\"}"));
        when(retrieval.search(anyString(), anyInt(), anyInt())).thenAnswer(invocation -> objectMapper.readTree(
                "{\"query\":\"" + invocation.getArgument(0) + "\",\"limit\":" + invocation.getArgument(1)
                        + ",\"offset\":" + invocation.getArgument(2) + ",\"results\":[]}"));
    }

    @Test void healthContractIsStable() throws Exception {
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
    }

    @Test void queryUsesFourResultContractAndRejectsBlank() throws Exception {
        mvc.perform(post("/api/v1/query").contentType(MediaType.APPLICATION_JSON).content("{\"query\":\"锁\",\"limit\":4,\"offset\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.limit").value(4)).andExpect(jsonPath("$.results").isArray());
        mvc.perform(post("/api/v1/query").contentType(MediaType.APPLICATION_JSON).content("{\"query\":\" \" ,\"limit\":4,\"offset\":0}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
