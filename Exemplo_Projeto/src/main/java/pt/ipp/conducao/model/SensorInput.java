package pt.ipp.conducao.model;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;

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
        @NotBlank String estadoPressaoPneus,
        boolean pisoInstavel,
        boolean condutorDistraido,
        boolean sensoresOperacionais,
        boolean falhaSensorCritico,
        @NotBlank String nivelFadiga,
        boolean cintosColocados,
        boolean chuvaIntensa,
        boolean visibilidadeReduzida,
        boolean peaoNaTrajetoria,
        @DecimalMin("0") @DecimalMax("1000") double distanciaPeao
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
                false, false, false, 1000);
    }

    @AssertTrue(message = "A faixa atual tem de existir no numero de faixas indicado")
    public boolean isFaixaAtualValida() {
        return faixaAtual <= numeroFaixas;
    }
}
