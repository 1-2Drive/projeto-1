# 10 — Risco de colisão

**Objetivo:** travar de emergência quando o choque com o carro da frente está iminente, e travar mais cedo quando chove.

O radar mede o **tempo até colisão** (TTC): a distância ao carro da frente a dividir pela velocidade a que o carro se aproxima dele. Mede dez vezes por segundo.

```mermaid
flowchart TD
    S(["O radar mede o TTC"]) --> Q1{"TTC na última medição<br/>(últimos 0,2 s)"}
    Q1 -- "< 1,3 s" --> A1["Travagem de emergência imediata"]
    Q1 -- "≥ 1,3 s" --> Q2{"Quantas medições com TTC < 2,5 s<br/>nos últimos 2 s?"}
    Q2 -- "3 ou mais" --> A1
    Q2 -- "menos de 3" --> Q3{"Está a chover?"}
    Q3 -- "Não" --> A3["Sem risco: segue as outras regras"]
    Q3 -- "Sim" --> Q4{"Quantas medições com TTC < 3 s<br/>feitas com chuva, nos últimos 2 s?"}
    Q4 -- "2 ou mais" --> A2["Trava com firmeza até 15 km/h"]
    Q4 -- "menos de 2" --> A3

    classDef dec fill:#fff3cd,stroke:#b58900,color:#000
    classDef acao fill:#d4edda,stroke:#2e7d32,color:#000
    classDef forte fill:#f8d7da,stroke:#b02a37,color:#000
    class Q1,Q2,Q3,Q4 dec
    class A2,A3 acao
    class A1 forte
```

## Em resumo

| Situação | O que o carro faz |
|---|---|
| Risco iminente de colisão (TTC < 1,3 s) | Travagem de emergência, com luzes de emergência; nenhuma outra regra se sobrepõe |
| Risco persistente (3 medições abaixo de 2,5 s em 2 s) | Travagem de emergência |
| Chuva com risco moderado (2 medições abaixo de 3 s em 2 s) | Trava mais cedo do que com tempo seco. Só contam as medições feitas com chuva: uma leitura de chuva a menos de 0,4 segundos de cada medição (operador de Allen "coincide com", `coincides`) |
| Uma medição isolada entre 1,3 e 2,5 s | Não reage por uma só medição, para evitar travagens falsas |
