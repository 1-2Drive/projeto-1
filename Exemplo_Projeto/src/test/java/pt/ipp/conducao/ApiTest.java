package pt.ipp.conducao;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ApiTest {
    @Autowired MockMvc mvc;

    @Test
    void exposesKnowledgeBase() throws Exception {
        mvc.perform(get("/api/knowledge-base"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rules.length()").value(18));
    }

    @Test
    void rejectsInvalidSpeed() throws Exception {
        String body = """
                {"engine":"both","sensors":{"velocidade":400,"limiteVelocidade":90,
                "distanciaObstaculo":20,"obstaculoNaFaixa":false,"faixaEsquerdaLivre":false,
                "faixaDireitaLivre":false,"pressaoPneusBaixa":false,"pisoInstavel":false,
                "condutorDistraido":false}}
                """;
        mvc.perform(post("/api/evaluate").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}
