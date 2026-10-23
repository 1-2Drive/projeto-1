# 12 — Buracos na estrada

**Objetivo:** contornar um buraco na faixa do carro quando há uma faixa para onde ir; se não houver, passar devagar (30 km/h). Um buraco noutra faixa não obriga a abrandar (Código da Estrada, art. 24.º: a velocidade adapta-se aos perigos da via por onde se circula).

```mermaid
flowchart TD
    S(["A câmara vê um buraco à frente<br/>(alcance: 120 m)"]) --> Q0{"Está no caminho do carro?<br/>(a sua faixa, ou a faixa<br/>para onde está a mudar)"}
    Q0 -- "Não" --> A0["Nada muda"]
    Q0 -- "Sim" --> Q1{"Distância ao buraco"}
    Q1 -- "≤ 50 m" --> Q2{"Posso mudar para um lado? (ver 00)<br/>no centro, primeiro a direita"}
    Q2 -- "Sim" --> Q2b{"Ainda sai da faixa antes do buraco?<br/>(precisa de 2,5 s à velocidade atual + 5 m)"}
    Q2b -- "Sim" --> A1["Muda de faixa para o contornar"]
    Q2b -- "Não" --> Q3
    Q2 -- "Não" --> Q3
    Q1 -- "> 50 m: o desvio ainda não se decide" --> Q3{"Já está perto o bastante para ter de abrandar?<br/>(a distância para baixar a 30 km/h<br/>com travagem confortável; nunca menos de 40 m)"}
    Q3 -- "Não" --> A0b["Ainda não abranda"]
    Q3 -- "Sim" --> Q4{"O buraco é grave e a travagem confortável<br/>já não chega para estar a 30 km/h nele?"}
    Q4 -- "Sim" --> A5["Trava a fundo"]
    Q4 -- "Não" --> Q5{"Distância ao buraco"}
    Q5 -- "> 40 m" --> A2a["Abranda aos poucos (2,0 m/s²)<br/>para chegar ao buraco a 30 km/h"]
    Q5 -- "15 m a 40 m" --> A2["Abranda para 30 km/h"]
    Q5 -- "< 15 m" --> A3["Abranda para 20 km/h"]
    A1 --> A4["Depois volta à direita (17)"]

    classDef dec fill:#fff3cd,stroke:#b58900,color:#000
    classDef acao fill:#d4edda,stroke:#2e7d32,color:#000
    classDef forte fill:#f8d7da,stroke:#b02a37,color:#000
    class Q0,Q1,Q2,Q2b,Q3,Q4,Q5 dec
    class A0,A0b,A1,A2,A2a,A3,A4 acao
    class A5 forte
```

## Em resumo

| Situação | O que o carro faz |
|---|---|
| Buraco no centro, direita livre | Desvia para a direita |
| Buraco no centro, direita ocupada, esquerda livre | Desvia para a esquerda |
| Sem lado seguro (ocupado, ou livre mas sem espaço para seguir o carro da frente) | Passa devagar (30 km/h, 20 km/h nos últimos 15 m) |
| Buraco já perto demais para sair da faixa antes dele | Passa devagar, em vez de se desviar por cima dele |
| A 90 km/h, com um buraco à frente na faixa | Começa a abrandar aos poucos a cerca de 110 m dele; a 50 m desvia-se se houver um lado seguro |
| Buraco grave visto tarde | Trava a fundo para chegar a ele a 30 km/h |
| Buraco noutra faixa | Mantém a velocidade |
| Com chuva | Desvia na mesma, e abranda por causa do tempo |

A pergunta "posso mudar para este lado?" é a mesma em todas as mudanças de faixa (ver 00): sem outra manobra em curso, sem linha contínua a cruzar, com o sensor lateral a dizer que esse lado está livre e com espaço para seguir o carro da frente nessa faixa.
