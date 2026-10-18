# 01 — Limites de velocidade

**Objetivo:** circular sempre dentro do limite legal e abrandar a tempo quando se aproxima de um limite mais baixo.

O sinal de limite vale para todas as faixas. A câmara lê-o ao longe, mas, tal como no Código da Estrada, o novo limite **só começa no sinal**. Até lá o carro prepara-se: se o novo limite for mais baixo, abranda gradualmente para chegar ao sinal já à velocidade certa.

```mermaid
flowchart TD
    S(["O carro circula"]) --> Q1{"A câmara vê um sinal de limite?<br/>(alcance: 120 m, menos com pouca visibilidade)"}
    Q1 -- "Não" --> Q5
    Q1 -- "Sim" --> A0["Guarda o novo limite<br/>para quando chegar ao sinal"]
    A0 --> Q2{"A velocidade atual já é maior do que a que<br/>deixa chegar ao novo limite 8 m antes do sinal,<br/>travando suavemente (2,0 m/s²)?"}
    Q2 -- "Sim" --> A1["Abranda aos poucos,<br/>seguindo essa curva"]
    Q2 -- "Não" --> Q3
    A1 --> Q3{"Já chegou ao sinal?"}
    Q3 -- "Sim" --> A3["O novo limite passa a valer"]
    Q3 -- "Não" --> Q4{"Há quanto tempo a câmara<br/>não vê o sinal?"}
    Q4 -- "> 1 s" --> A4["Esquece o sinal;<br/>o limite atual mantém-se"]
    Q4 -- "≤ 1 s" --> Q5
    A3 --> Q5{"Velocidade vs. limite em vigor"}
    A4 --> Q5
    Q5 -- "acima" --> A5["Abranda até ao limite"]
    Q5 -- "igual ou abaixo" --> Q6{"O limite em vigor<br/>veio de um sinal?"}
    Q6 -- "Sim" --> A6["Circula no limite<br/>(sobe até ele se for abaixo)"]
    Q6 -- "Não: ainda nenhum sinal passado" --> A7["Segue a velocidade de cruzeiro (03)"]

    classDef dec fill:#fff3cd,stroke:#b58900,color:#000
    classDef acao fill:#d4edda,stroke:#2e7d32,color:#000
    class Q1,Q2,Q3,Q4,Q5,Q6 dec
    class A0,A1,A3,A4,A5,A6,A7 acao
```

## Em resumo

| Situação | O que o carro faz |
|---|---|
| Vai a 90 km/h e vê um sinal de 50 | Começa a abrandar a cerca de 116 m do sinal e chega lá a 50 km/h |
| Vê um sinal de 90 e vai a 60 km/h | Quando passa o sinal, acelera até 90 km/h |
| O sinal deixa de ser visto antes de lá chegar (por exemplo com chuva) | Esquece-o ao fim de 1 segundo; o limite atual mantém-se |
| Ainda não passou nenhum sinal | O limite é 120 km/h e o carro segue a velocidade de cruzeiro |

O limite de velocidade nunca é ultrapassado por causa de outras regras: perigos, tempo ou trânsito só podem baixar a velocidade.
