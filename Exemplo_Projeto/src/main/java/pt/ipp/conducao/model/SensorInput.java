package pt.ipp.conducao.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;

import java.util.Map;
import java.util.LinkedHashMap;

/** Leitura normalizada dos sensores simulados enviada igualmente aos dois motores. */
public record SensorInput(
        @DecimalMin("0") @DecimalMax("300") double velocidade,
        @DecimalMin("10") @DecimalMax("150") double limiteVelocidade,
        @DecimalMin("0") @DecimalMax("1000") double distanciaObstaculo,
        boolean obstaculoNaFaixa,
        boolean faixaEsquerdaLivre,
        boolean faixaDireitaLivre,
        @Min(1) @Max(6) int numeroFaixas,
        @Min(1) @Max(6) int faixaAtual,
        @NotBlank @Pattern(regexp = "NORMAL|BAIXA|CRITICA") String estadoPressaoPneus,
        boolean pisoInstavel,
        boolean condutorDistraido,
        boolean sensoresOperacionais,
        boolean falhaSensorCritico,
        @NotBlank @Pattern(regexp = "NENHUMA|MODERADA|ELEVADA") String nivelFadiga,
        boolean cintosColocados,
        boolean chuvaIntensa,
        boolean visibilidadeReduzida,
        boolean peaoNaTrajetoria,
        @DecimalMin("0") @DecimalMax("1000") double distanciaPeao,
        Map<String, Object> contexto
) {
    /** Mantem os cenarios academicos antigos legiveis nos testes. */
    public SensorInput(double velocidade, double limiteVelocidade, double distanciaObstaculo,
                       boolean obstaculoNaFaixa, boolean faixaEsquerdaLivre,
                       boolean faixaDireitaLivre, boolean pressaoPneusBaixa,
                       boolean pisoInstavel, boolean condutorDistraido) {
        this(velocidade, limiteVelocidade, distanciaObstaculo, obstaculoNaFaixa,
                faixaEsquerdaLivre, faixaDireitaLivre, 3, 2,
                pressaoPneusBaixa ? "BAIXA" : "NORMAL", pisoInstavel,
                condutorDistraido, true, false, "NENHUMA", true,
                false, false, false, 1000, Map.of());
    }

    @AssertTrue(message = "A faixa atual tem de existir no numero de faixas indicado")
    public boolean isFaixaAtualValida() {
        return faixaAtual <= numeroFaixas;
    }

    public Object valorContexto(String nome) {
        Object value = contexto == null ? null : contexto.get(nome);
        return value != null ? value : contextoPadrao().get(nome);
    }

    public static Map<String, Object> contextoPadrao() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("veiculoLigado", true);
        values.put("veiculoParado", false);
        values.put("estacionadoCorretamente", true);
        values.put("localSeguroDisponivel", true);
        values.put("pretendeIniciarViagem", true);
        values.put("consumiuAlcool", false);
        values.put("taxaAlcoolConhecida", false);
        values.put("taxaAlcoolAcimaPermitido", false);
        values.put("tomouMedicacao", false);
        values.put("efeitosMedicacao", false);
        values.put("avariaCritica", false);
        values.put("energiaSuficiente", true);
        values.put("autonomiaLocalSeguro", true);
        values.put("velocidadeAdequada", true);
        values.put("distanciaFrenteSegura", true);
        values.put("tempoColisaoCritico", false);
        values.put("estaChovendo", false);
        values.put("obstaculoDetetado", false);
        values.put("obstaculoNaTrajetoria", false);
        values.put("espacoSeguroDesvio", false);
        values.put("peaoDetetado", false);
        values.put("peaoPodeEntrarTrajetoria", false);
        values.put("emUltrapassagem", false);
        values.put("ultrapassagemPermitida", true);
        values.put("visibilidadeEspacoUltrapassagem", true);
        values.put("veiculoSentidoContrario", false);
        values.put("tempoConcluirUltrapassagem", true);
        values.put("mantemDentroFaixa", true);
        values.put("piscaAtivado", false);
        values.put("mudancaFaixaSegura", true);
        values.put("aproximaCruzamento", false);
        values.put("temPrioridade", true);
        values.put("sinalPermiteAvancar", true);
        values.put("veiculoEmergencia", false);
        values.put("nivelRiscoGlobal", "BAIXO");
        return values;
    }
}
