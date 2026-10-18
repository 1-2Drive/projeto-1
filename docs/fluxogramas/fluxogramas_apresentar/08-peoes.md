# 08 — Peões

**Objetivo:** dar sempre prioridade a um peão que está no caminho do carro ou a atravessar a estrada, e só voltar a andar quando a travessia terminar.

Um peão conta quando está na faixa do carro (ou a 3,5 m ou menos da sua trajetória) ou quando está a atravessar a estrada, em qualquer faixa. Um peão parado noutra faixa ou no passeio é só vigiado.

```mermaid
flowchart TD
    S(["A câmara vê um peão à frente<br/>(alcance: 120 m)"]) --> Q0{"O peão já terminou a travessia?"}
    Q0 -- "Sim" --> A5["Já não o segura: arranca (04)"]
    Q0 -- "Não" --> Q1{"Está no caminho do carro?"}
    Q1 -- "Sim" --> Q3
    Q1 -- "Não" --> Q2{"Está a atravessar a estrada?"}
    Q2 -- "Não" --> A0["Apenas o vigia"]
    Q2 -- "Sim" --> Q3{"Distância ao peão"}
    Q3 -- "≤ 6 m" --> A3["Travagem de emergência"]
    Q3 -- "6 m a 32 m" --> A2["Pára antes do peão"]
    Q3 -- "> 32 m" --> Q4{"O carro está parado (menos de 1 km/h)<br/>e o peão a atravessar a 45 m ou menos?"}
    Q4 -- "Sim" --> A4["Fica parado à espera"]
    Q4 -- "Não" --> Q5{"A velocidade é maior do que a que deixa parar<br/>18 m antes do peão, travando<br/>suavemente (2,0 m/s²)?"}
    Q5 -- "Sim" --> A1["Trava (até 20 km/h)"]
    Q5 -- "Não" --> Q6{"Fase da aproximação a este peão"}
    Q6 -- "a travar" --> Q7{"A velocidade já desceu abaixo da curva<br/>mais suave (1,4 m/s²)?"}
    Q7 -- "Não" --> A1
    Q7 -- "Sim" --> A1b["Deixa de travar e mantém a velocidade,<br/>sem voltar a acelerar para o peão"]
    Q6 -- "a manter a velocidade" --> A1b
    Q6 -- "ainda não reagiu" --> Q8{"A velocidade é maior do que a da<br/>curva mais suave (1,4 m/s²)?"}
    Q8 -- "Sim" --> A1b
    Q8 -- "Não" --> A6["Ainda há espaço: nada muda"]

    classDef dec fill:#fff3cd,stroke:#b58900,color:#000
    classDef acao fill:#d4edda,stroke:#2e7d32,color:#000
    classDef forte fill:#f8d7da,stroke:#b02a37,color:#000
    class Q0,Q1,Q2,Q3,Q4,Q5,Q6,Q7,Q8 dec
    class A0,A1,A1b,A2,A4,A5,A6 acao
    class A3 forte
```

O carro lembra-se de que já começou a travar por um peão: trava até ficar abaixo da curva mais suave e, a partir daí, mantém a velocidade até a curva de 2,0 m/s² o voltar a apanhar. Assim não alterna entre travar e manter em cada instante.

## Em resumo

| Situação | O que o carro faz |
|---|---|
| Peão parado na faixa do carro, a 80 m, a 60 km/h | Abranda e pára a cerca de 20 m do peão |
| Peão a atravessar noutra faixa, em direção ao carro | Abranda e cede passagem, mesmo antes de o peão chegar à sua faixa |
| Peão que saiu da faixa do carro mas ainda está a atravessar | Continua parado até ele chegar ao passeio |
| Peão parado no passeio ou noutra faixa, sem atravessar | Apenas o vigia |
| Peão a 6 m ou menos | Travagem de emergência |
| Peão a atravessar à frente de outro carro | O outro carro também pára e espera; depois retoma (comportamento dos outros veículos na simulação) |

O peão tem prioridade sobre o semáforo verde e sobre a aproximação a um semáforo ou STOP.
