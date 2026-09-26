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
                .andExpect(jsonPath("$.rules.length()").value(49));
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

    @Test
    void rejectsUnknownTyreState() throws Exception {
        mvc.perform(post("/api/evaluate").contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("both").replace("\"NORMAL\"", "\"DESCONHECIDA\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("estadoPressaoPneus")));
    }

    @Test
    void rejectsLaneThatDoesNotExist() throws Exception {
        mvc.perform(post("/api/evaluate").contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("both").replace("\"faixaAtual\":1", "\"faixaAtual\":3")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("faixa atual")));
    }

    @Test
    void rejectsUnknownEngine() throws Exception {
        mvc.perform(post("/api/evaluate").contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("motor-inexistente")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Motor invalido")));
    }

    @Test
    void healthEndpointReportsOk() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    private String validBody(String engine) {
        return """
                {"engine":"%s","sensors":{"velocidade":40,"limiteVelocidade":90,
                "distanciaObstaculo":1000,"obstaculoNaFaixa":false,
                "faixaEsquerdaLivre":false,"faixaDireitaLivre":false,
                "numeroFaixas":2,"faixaAtual":1,"estadoPressaoPneus":"NORMAL",
                "pisoInstavel":false,"condutorDistraido":false,"sensoresOperacionais":true,
                "falhaSensorCritico":false,"nivelFadiga":"NENHUMA","cintosColocados":true,
                "chuvaIntensa":false,"visibilidadeReduzida":false,
                "peaoNaTrajetoria":false,"distanciaPeao":1000}}
                """.formatted(engine);
    }
}
