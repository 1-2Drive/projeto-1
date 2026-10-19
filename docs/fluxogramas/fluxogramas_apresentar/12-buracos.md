# 12 — Buracos na estrada

**Objetivo:** contornar um buraco na faixa do carro quando há uma faixa para onde ir; se não houver, passar devagar. Um buraco noutra faixa não obriga a abrandar (Código da Estrada, art. 24.º: a velocidade adapta-se aos perigos da via por onde se circula).

```mermaid
flowchart TD
    S(["A câmara vê um buraco à frente<br/>(alcance: 120 m)"]) --> Q0{"Está na faixa do carro?"}
    Q0 -- "Não" --> A0["Nada muda"]
    Q0 -- "Sim" --> Q1{"Distância ao buraco"}
    Q1 -- "> 50 m" --> A0
    Q1 -- "≤ 50 m" --> Q2{"Posso mudar para um lado? (ver 00)<br/>no centro, primeiro a direita"}
    Q2 -- "Sim" --> A1["Muda de faixa para o contornar"]
    Q2 -- "Não" --> Q3{"Distância ao buraco"}
    Q3 -- "40 m a 50 m" --> A0b["Ainda não abranda"]
    Q3 -- "15 m a 40 m" --> A2["Abranda para 30 km/h"]
    Q3 -- "< 15 m" --> A3["Abranda para 20 km/h"]
    A1 --> A4["Depois volta à direita (17)"]

    classDef dec fill:#fff3cd,stroke:#b58900,color:#000
    classDef acao fill:#d4edda,stroke:#2e7d32,color:#000
    class Q0,Q1,Q2,Q3 dec
    class A0,A0b,A1,A2,A3,A4 acao
```

## Em resumo

| Situação | O que o carro faz |
|---|---|
| Buraco no centro, direita livre | Desvia para a direita |
| Buraco no centro, direita ocupada, esquerda livre | Desvia para a esquerda |
| Sem lado seguro (ocupado, ou livre mas sem espaço para seguir o carro da frente) | Passa devagar (30 km/h, 20 km/h nos últimos 15 m) |
| Buraco noutra faixa | Mantém a velocidade |
| Com chuva | Desvia na mesma, e abranda por causa do tempo |

A pergunta "posso mudar para este lado?" é a mesma em todas as mudanças de faixa (ver 00): sem outra manobra em curso, sem linha contínua a cruzar, com o sensor lateral a dizer que esse lado está livre e com espaço para seguir o carro da frente nessa faixa.
