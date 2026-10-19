# 06 — Semáforo amarelo e verde

**Objetivo:** no amarelo, parar se ainda for seguro; se já não for, prosseguir sem travar a meio. No verde, seguir ou arrancar.

Segue o Regulamento de Sinalização do Trânsito (art. 69.º): no amarelo o condutor pára, a menos que já esteja tão perto que não consiga parar em segurança. A decisão é tomada **uma única vez**, quando o carro vê o amarelo, e não muda depois.

```mermaid
flowchart TD
    S(["Semáforo à frente"]) --> Q0{"Que cor?"}
    Q0 -- "Vermelho" --> A6["Ver 05 — Semáforo vermelho"]
    Q0 -- "Amarelo" --> Q1{"Distância ao semáforo vs. distância<br/>para parar com travagem confortável<br/>(0,5 s de reação + travagem a 2,8 m/s²<br/>+ 18 m até à linha)"}
    Q1 -- "≥" --> A1["Decide parar"]
    Q1 -- "<" --> A2["Decide prosseguir:<br/>o semáforo não o trava"]
    A1 --> Q2{"Já está na linha?<br/>(a 21 m ou menos do semáforo)"}
    Q2 -- "Sim" --> A7["Pára"]
    Q2 -- "Não" --> Q3{"Distância ≤ distância para parar<br/>travando suavemente (2,0 m/s²)?"}
    Q3 -- "Não" --> A8["Segue normalmente, por agora"]
    Q3 -- "Sim" --> Q4{"Distância ≤ distância para parar<br/>com travagem confortável (2,8 m/s²)?"}
    Q4 -- "Não" --> A9["Deixa de acelerar"]
    Q4 -- "Sim" --> A10["Trava com conforto"]
    Q0 -- "Verde" --> Q5{"Distância ao semáforo"}
    Q5 -- "> 40 m" --> A5["Segue normalmente"]
    Q5 -- "≤ 40 m" --> Q6{"O carro está parado?<br/>(menos de 1 km/h)"}
    Q6 -- "Sim" --> A4["Arranca até 30 km/h"]
    Q6 -- "Não" --> A11["Segue em frente<br/>(até 50 km/h, se nada pedir menos)"]

    classDef dec fill:#fff3cd,stroke:#b58900,color:#000
    classDef acao fill:#d4edda,stroke:#2e7d32,color:#000
    class Q0,Q1,Q2,Q3,Q4,Q5,Q6 dec
    class A1,A2,A4,A5,A6,A7,A8,A9,A10,A11 acao
```

Se o amarelo passar a vermelho, a decisão mantém-se: quem decidiu parar continua a parar (05); quem decidiu prosseguir passa.

## Em resumo

| Situação | O que o carro faz |
|---|---|
| Amarelo visto a 90 m, a 60 km/h (precisa de ~76 m) | Decide parar; abranda e pára antes da linha |
| Amarelo visto a 50 m, a 60 km/h | Decide prosseguir; passa sem travar, mesmo que fique vermelho entretanto |
| Verde com o carro parado | Arranca |
| Decidiu prosseguir mas aparece um peão | Pára pelo peão: a decisão do semáforo nunca se sobrepõe à segurança |

O semáforo muda sozinho: o amarelo passa a vermelho ao fim de 3 segundos e o vermelho passa a verde 5 segundos depois de o carro parar.
