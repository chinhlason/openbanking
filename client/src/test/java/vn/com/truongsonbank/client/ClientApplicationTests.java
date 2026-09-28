package vn.com.truongsonbank.client;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClientApplicationTests {
    @Autowired
    private MockMvc mvc;

    @Test
    void contextLoads() {
    }

    @Test
    void importsResponseWrapperFromSharedpackage() throws Exception {
        mvc.perform(get("/shared-test/wrapped")
                        .header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "4bf92f3577b34da6a3ce929d0e0e4736"))
                .andExpect(jsonPath("$.traceId", is("4bf92f3577b34da6a3ce929d0e0e4736")))
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.code", is("SUCCESS")))
                .andExpect(jsonPath("$.message", is("Success")))
                .andExpect(jsonPath("$.duration", endsWith("ms")))
                .andExpect(jsonPath("$.timestamp", notNullValue()))
                .andExpect(jsonPath("$.data.service", is("client")));
    }

    @Test
    void importsErrorHandlerAndMessagesFromSharedpackage() throws Exception {
        mvc.perform(get("/shared-test/error")
                        .header("X-Language", "vi")
                        .header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.traceId", is("4bf92f3577b34da6a3ce929d0e0e4736")))
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("CLIENT_001")))
                .andExpect(jsonPath("$.message", is("Client test loi")))
                .andExpect(jsonPath("$.duration", endsWith("ms")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    void importsInputValidatorFromSharedpackage() throws Exception {
        mvc.perform(post("/shared-test/validate")
                        .contentType("application/json")
                        .header("X-Language", "vi")
                        .header("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01")
                        .content("{\"username\":\"too-long-username\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("COMMON_VALIDATION_MAX")))
                .andExpect(jsonPath("$.message", is("Vui long nhap username toi da 10 ky tu")));
    }

    @Test
    void importsInputValidatorMinFromSharedpackage() throws Exception {
        mvc.perform(post("/shared-test/validate")
                        .contentType("application/json")
                        .header("X-Language", "vi")
                        .content("{\"username\":\"ab\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.code", is("COMMON_VALIDATION_MIN")))
                .andExpect(jsonPath("$.message", is("Vui long nhap username toi thieu 3 ky tu")));
    }

    @Test
    void acceptsValidInputValidatorRequest() throws Exception {
        mvc.perform(post("/shared-test/validate")
                        .contentType("application/json")
                        .content("{\"username\":\"son_123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.username", is("son_123")));
    }

    @Test
    void importsCacheAnnotationsFromSharedpackage() throws Exception {
        mvc.perform(post("/shared-test/cache/abc/evict"))
                .andExpect(status().isOk());

        mvc.perform(get("/shared-test/cache/abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value", is("abc-1")))
                .andExpect(jsonPath("$.data.calls", is(1)));

        mvc.perform(get("/shared-test/cache/abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.value", is("abc-1")))
                .andExpect(jsonPath("$.data.calls", is(1)));
    }

}
