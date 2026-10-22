# 14 — Barreiras

**Objetivo:** contornar uma barreira fechada quando há uma faixa segura; se não houver, parar antes dela; com a barreira aberta, passar devagar.

Para contornar a barreira e para decidir parar, o carro faz a mesma pergunta: "posso mudar para este lado?" (ver 00). Por isso faz sempre uma das duas coisas: ou contorna, ou pára. Nunca segue contra a barreira (Código da Estrada, art. 35.º: só se muda de faixa sem perigo).

Enquanto a barreira estiver no caminho do carro (a sua faixa, ou a faixa para onde está a mudar), o carro trava como para um obstáculo, de modo a conseguir parar 10 m antes, mesmo quando vai contorná-la: só deixa de travar quando a mudança de faixa o tira do caminho. Assim nunca chega à barreira depressa demais, se o lado deixar de estar livre.

```mermaid
flowchart TD
    S(["A câmara vê uma barreira à frente<br/>(alcance: 120 m)"]) --> Q0{"A barreira está aberta?"}
    Q0 -- "Sim" --> Q1{"Distância à barreira"}
    Q1 -- "≤ 35 m" --> A0["Passa devagar (até 35 km/h)"]
    Q1 -- "> 35 m" --> A9["Segue normalmente"]
    Q0 -- "Não" --> Q2{"Está no caminho do carro?<br/>(a sua faixa, ou a faixa para onde está a mudar)"}
    Q2 -- "Não" --> A9
    Q2 -- "Sim" --> Q6{"Distância vs. distância para parar<br/>com travagem confortável<br/>(0,5 s de reação + travagem a 2,8 m/s² + 10 m;<br/>nunca menos de 55 m)"}
    Q6 -- "> essa distância" --> A9
    Q6 -- "≤ essa distância" --> A7["Trava para conseguir parar 10 m antes<br/>(a fundo se precisar de mais de 3,1 m/s²)"]
    A7 --> Q3{"Está a meio de uma mudança de faixa?"}
    Q3 -- "Sim" --> A8["Termina a manobra (16)"]
    Q3 -- "Não" --> Q4{"Posso mudar para algum lado? (ver 00)<br/>no centro, primeiro a esquerda"}
    Q4 -- "Sim" --> Q5{"Distância à barreira"}
    Q5 -- "≤ 55 m" --> Q9{"Ainda sai da faixa antes da barreira?<br/>(precisa de 2,5 s à velocidade atual + 5 m)"}
    Q9 -- "Sim" --> A1["Muda de faixa para a contornar"]
    Q9 -- "Não" --> A7
    Q5 -- "> 55 m" --> A7
    Q4 -- "Não" --> Q7{"Para parar antes da barreira, bastaria<br/>travar a 3,1 m/s² ou menos?"}
    Q7 -- "Sim" --> A2["Pára antes da barreira"]
    Q7 -- "Não" --> A3["Trava a fundo"]
    A2 --> Q8{"Abriu-se a barreira,<br/>ou um lado passou a ser seguro?"}
    Q8 -- "Sim" --> A4["Arranca ou contorna (04)"]
    Q8 -- "Não" --> A2

    classDef dec fill:#fff3cd,stroke:#b58900,color:#000
    classDef acao fill:#d4edda,stroke:#2e7d32,color:#000
    classDef forte fill:#f8d7da,stroke:#b02a37,color:#000
    class Q0,Q1,Q2,Q3,Q4,Q5,Q6,Q7,Q8,Q9 dec
    class A0,A1,A2,A4,A7,A8,A9 acao
    class A3 forte
```

## Em resumo

| Situação | O que o carro faz |
|---|---|
| Barreira fechada, faixa ao lado segura | Trava enquanto ela está no caminho e contorna-a |
| Barreira fechada, sem alternativa | Pára e espera |
| Faixa ao lado livre, mas com um carro à frente demasiado perto para ser seguido | Trava pela barreira; quando já vai devagar o suficiente para o seguir, contorna-a |
| Barreira fechada vista tarde, a 60 km/h a 22 m | Trava a fundo |
| Barreira aberta | Passa devagar |

O carro decide pelo que **vê**: a faixa ao lado conta como livre quando o sensor lateral o diz, como um condutor que só se pode certificar do que observa (Código da Estrada, art. 35.º e 38.º).
