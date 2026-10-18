# 09 — Seguir o carro da frente

**Objetivo:** manter sempre uma distância segura do carro da frente (Código da Estrada, art. 18.º e 24.º), acompanhá-lo sem pára-arranca e parar atrás dele se estiver parado.

A distância de referência é uma folga de **12 m** atrás do carro da frente. As várias verificações são independentes; quando mais do que uma pede para abrandar, vale a velocidade mais baixa.

```mermaid
flowchart TD
    S(["O radar vê um carro à frente,<br/>na mesma faixa (alcance: 130 m)"]) --> Q1{"O carro da frente está parado?<br/>(menos de 0,5 m/s)"}
    Q1 -- "Sim" --> Q2{"Distância"}
    Q2 -- "≤ 14 m" --> A1["Pára e fica parado,<br/>cerca de 12 m atrás"]
    Q2 -- "> 14 m" --> Q3{"A velocidade é maior do que a que deixa<br/>parar 12 m atrás, travando suavemente (2,0 m/s²)?"}
    Q3 -- "Sim" --> A2["Trava aos poucos, seguindo essa curva"]
    Q3 -- "Não" --> A0["Ainda não precisa de travar"]
    Q1 -- "Não, está a andar" --> Q4{"Distância"}
    Q4 -- "≤ 12 m" --> Q4b{"O carro da frente está a afastar-se?"}
    Q4b -- "Não" --> A3["Abranda até 3 km/h abaixo da velocidade dele,<br/>para voltar aos 12 m"]
    Q4b -- "Sim" --> A7
    Q4 -- "12 m a 20 m<br/>(até 40 m com pouca aderência)" --> A4["Acompanha a velocidade dele<br/>(1 km/h abaixo se estiver a menos de 16 m)"]
    Q4 -- "> 20 m<br/>(> 40 m com pouca aderência)" --> Q5{"A velocidade é maior do que a que deixa<br/>igualar a dele 12 m atrás,<br/>travando suavemente (2,0 m/s²)?"}
    Q5 -- "Sim" --> A5["Abranda, seguindo essa curva"]
    Q5 -- "Não" --> Q6{"A que velocidade se aproxima dele?<br/>(conta só a 14–35 m)"}
    Q6 -- "> 0,8 m/s" --> A6["Abranda para 1,2 km/h por metro<br/>de distância (no mínimo 20 km/h)"]
    Q6 -- "≤ 0,8 m/s, ou fora dos 14–35 m" --> A7["Segue normalmente"]
    A4 --> Q7{"O carro da frente vai pelo menos 10 km/h<br/>abaixo da velocidade desejada?"}
    Q7 -- "Sim" --> A8["Tenta ultrapassar (11)"]
    Q7 -- "Não" --> A9["Continua a acompanhá-lo"]

    classDef dec fill:#fff3cd,stroke:#b58900,color:#000
    classDef acao fill:#d4edda,stroke:#2e7d32,color:#000
    class Q1,Q2,Q3,Q4,Q4b,Q5,Q6,Q7 dec
    class A0,A1,A2,A3,A4,A5,A6,A7,A8,A9 acao
```

Além disto, se a distância encolher mais de 4 m em 2 segundos (a menos de 50 m), o carro abranda para 35 km/h, ou não acelera se já for abaixo disso. A menos de 14 m e a aproximar-se, trava até 3 km/h abaixo da velocidade do carro da frente.

## Em resumo

| Situação | O que o carro faz |
|---|---|
| Carro parado à frente, a 60 km/h | Trava aos poucos e pára cerca de 12 m atrás dele |
| Carro lento à frente (por exemplo a 5 km/h) | Acompanha-o suavemente, sem travar e acelerar repetidamente |
| Chuva, piso molhado | Acompanha a uma distância maior (a janela de 20 m cresce até 40 m) |
| Carro da frente afasta-se | Volta a acelerar até ao ritmo normal |
| Carro da frente muito mais lento e a faixa da esquerda livre | Ultrapassa (11) |
