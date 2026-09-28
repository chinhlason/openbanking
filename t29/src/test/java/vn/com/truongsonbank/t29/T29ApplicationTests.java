package vn.com.truongsonbank.t29;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class T29ApplicationTests {
    @Autowired
    private MockMvc mvc;

    @Test
    void contextLoads() {
    }

    @Test
    void opensAccountsAndTransfersMoney() throws Exception {
        String fromAccount = openAccount("001");
        String toAccount = openAccount("002");

        mvc.perform(post("/accounts/{accountNumber}/deposit", fromAccount)
                .contentType("application/json")
                .content("{\"amount\":100000}"))
                .andExpect(status().isOk());

        mvc.perform(post("/transfers")
                .contentType("application/json")
                .content("""
                        {"fromAccountNumber":"%s","toAccountNumber":"%s","amount":25000}
                        """.formatted(fromAccount, toAccount)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromBalance", is(75000)));

        mvc.perform(get("/accounts/{accountNumber}/balance", toAccount))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(25000)));
    }

    private String openAccount(String cccd) throws Exception {
        return mvc.perform(post("/accounts")
                .contentType("application/json")
                .content("{\"cccd\":\"" + cccd + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll("\\D", "");
    }

}
