# 05 — Semáforo vermelho

**Objetivo:** parar sempre antes da linha de paragem e esperar pelo verde.

O semáforo vale para todas as faixas. A linha de paragem fica 18 m antes do semáforo. O carro calcula a distância de que precisa para parar a partir da velocidade atual (0,5 s de reação + a travagem + os 18 m até à linha) e começa a agir a tempo. Quando vê o vermelho, decide logo que vai parar e não muda de ideias.

```mermaid
flowchart TD
    S(["Semáforo vermelho à frente"]) --> Q1{"Distância ao semáforo"}
    Q1 -- "> 125 m" --> A1["Ainda não reage"]
    Q1 -- "≤ 125 m" --> Q2{"Já está na linha de paragem?<br/>(a 21 m ou menos do semáforo:<br/>linha a 18 m + 3 m de tolerância)"}
    Q2 -- "Não (> 21 m)" --> Q3{"Distância ≤ distância para parar<br/>travando suavemente (2,0 m/s²)?"}
    Q3 -- "Não" --> A1b["Segue normalmente"]
    Q3 -- "Sim" --> Q4{"Distância ≤ distância para parar<br/>com travagem confortável (2,8 m/s²)?"}
    Q4 -- "Não" --> A2["Deixa de acelerar<br/>(mantém a velocidade)"]
    Q4 -- "Sim" --> Q5{"Com travagem confortável,<br/>passaria a linha em mais de 2 m?"}
    Q5 -- "Não" --> A3["Trava com conforto"]
    Q5 -- "Sim" --> A4["Trava a fundo<br/>(travagem de emergência)"]
    Q2 -- "Sim (≤ 21 m)" --> Q6{"Uma travagem confortável ainda o pára a tempo?<br/>(no máximo 2 m depois da linha;<br/>com a linha já passada, 3 m antes do semáforo)"}
    Q6 -- "Sim" --> A5["Pára e fica parado"]
    Q6 -- "Não" --> A4
    A2 --> Q1
    A3 --> Q1
    A4 --> Q1
    A5 --> Q7{"O semáforo ficou verde?<br/>(muda 5 s depois de o carro parar)"}
    Q7 -- "Não" --> A5
    Q7 -- "Sim" --> A6["Arranca até 30 km/h (04)"]

    classDef dec fill:#fff3cd,stroke:#b58900,color:#000
    classDef acao fill:#d4edda,stroke:#2e7d32,color:#000
    classDef forte fill:#f8d7da,stroke:#b02a37,color:#000
    class Q1,Q2,Q3,Q4,Q5,Q6,Q7 dec
    class A1,A1b,A2,A3,A5,A6 acao
    class A4 forte
```

## Em resumo

| Situação | O que o carro faz |
|---|---|
| A 60 km/h, vê o vermelho a 100 m | Segue até cerca de 96 m, deixa de acelerar, a cerca de 76 m trava com conforto e pára antes da linha |
| O vermelho aparece já muito perto | Trava a fundo para perder o máximo de velocidade |
| Está parado na linha | Espera; o semáforo passa a verde 5 segundos depois de o carro parar |
| Há um carro mais lento à frente durante a aproximação | Vale a velocidade mais baixa: o semáforo decide que tem de parar e o carro da frente pode obrigar a ir ainda mais devagar |
