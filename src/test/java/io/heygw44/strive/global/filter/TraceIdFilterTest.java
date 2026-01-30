package io.heygw44.strive.global.filter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TraceIdFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void generatesTraceIdWhenMissing() throws Exception {
        mockMvc.perform(get("/test/ping"))
                .andExpect(status().isOk())
                .andExpect(header().exists(TraceIdFilter.TRACE_ID_HEADER))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void echoesProvidedTraceId() throws Exception {
        String traceId = "test-trace-id";
        mockMvc.perform(get("/test/ping")
                        .header(TraceIdFilter.TRACE_ID_HEADER, traceId))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdFilter.TRACE_ID_HEADER, traceId))
                .andExpect(jsonPath("$.traceId").value(traceId));
    }
}
