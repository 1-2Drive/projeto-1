# 18 — Chuva, visibilidade e aderência

**Objetivo:** adaptar a velocidade ao tempo de forma gradual, como faria um bom condutor.

Visibilidade e aderência não são "sim ou não": vão de 0 (péssimas) a 1 (perfeitas), e cada valor pertence, em maior ou menor grau, a uma de quatro categorias. O carro usa **lógica difusa**: avalia em que medida as condições são boas ou más e combina essas avaliações numa velocidade, sem mudanças bruscas.

```mermaid
flowchart TD
    S(["O sensor do tempo mede chuva,<br/>visibilidade (0 a 1) e aderência (0 a 1)"]) --> P1["Classifica cada valor, com graus:<br/>• má: até 0,35 (deixa de o ser aos 0,5)<br/>• reduzida: 0,35 a 0,7 (máximo aos 0,5)<br/>• razoável: 0,5 a 0,85 (máximo aos 0,7)<br/>• boa: a partir de 0,7 (totalmente aos 0,85)"]
    P1 --> P2["Aplica regras simples, usando a pior das duas:<br/>• má → muito devagar (≈ 25 km/h)<br/>• reduzida → devagar (≈ 40 km/h)<br/>• razoável → moderado (≈ 55 km/h)<br/>• boa com chuva → prudente (≈ 65 km/h)<br/>• boa sem chuva → normal (≈ 75 km/h)"]
    P2 --> Q1{"Alguma regra que não seja<br/>'boa sem chuva' tem grau acima de 0?<br/>(chuva, ou visibilidade ou aderência < 0,85)"}
    Q1 -- "Não" --> A2["Sem restrição pelo tempo"]
    Q1 -- "Sim" --> P3["Combina as regras numa única<br/>velocidade recomendada (centro de gravidade)"]
    P3 --> A1["Não ultrapassa essa velocidade;<br/>outros limites mais baixos continuam a valer"]

    classDef dec fill:#fff3cd,stroke:#b58900,color:#000
    classDef acao fill:#d4edda,stroke:#2e7d32,color:#000
    classDef passo fill:#d6e4ff,stroke:#1a56db,color:#000
    class Q1 dec
    class A1,A2 acao
    class P1,P2,P3 passo
```

## Em resumo

| Condições | Velocidade máxima recomendada |
|---|---|
| Bom tempo (visibilidade e aderência ≥ 0,85, sem chuva) | Sem restrição |
| Só chuva, boa visibilidade e bom piso | Cerca de 65 km/h |
| Chuva com visibilidade e piso reduzidos (botão "Rain") | Cerca de 51 km/h |
| Visibilidade a 0,5 | Cerca de 40 km/h |
| Visibilidade ou piso muito maus | Cerca de 25 km/h |

O mau tempo só pode baixar a velocidade, nunca a faz ultrapassar o limite legal. Com chuva o carro também mantém mais distância do carro da frente (09), trava mais cedo perante um risco de colisão medido com chuva (10) e exige mais espaço para voltar à direita (11).
