# 04 — Arrancar depois de parar

**Objetivo:** quando o carro está parado e já nada o obriga a ficar parado, arrancar com suavidade.

```mermaid
flowchart TD
    S(["O carro está parado ou quase<br/>(menos de 3 km/h)"]) --> Q1{"Está a meio de uma<br/>mudança de faixa?"}
    Q1 -- "Sim" --> A1["Ainda não arranca"]
    Q1 -- "Não" --> Q2{"Alguma regra o obriga a ficar parado?<br/>(vermelho, STOP, peão, obstáculo,<br/>barreira, carro parado à frente, emergência)"}
    Q2 -- "Sim" --> A1
    Q2 -- "Não" --> Q3{"Há um pedido externo de<br/>mudança de faixa por atender?"}
    Q3 -- "Sim" --> A2["Atende primeiro o pedido (15)"]
    Q3 -- "Não" --> A3["Arranca com suavidade até 30 km/h<br/>(ou até ao limite, se for mais baixo)"]
    A3 --> Q4{"Alguma regra voltou<br/>a obrigar a parar?"}
    Q4 -- "Sim" --> A4["Termina o arranque"]
    Q4 -- "Não" --> Q5{"Velocidade vs. velocidade de arranque"}
    Q5 -- "a 1 km/h ou menos dela" --> A4
    Q5 -- "mais de 1 km/h abaixo" --> A3

    classDef dec fill:#fff3cd,stroke:#b58900,color:#000
    classDef acao fill:#d4edda,stroke:#2e7d32,color:#000
    class Q1,Q2,Q3,Q4,Q5 dec
    class A1,A2,A3,A4 acao
```

Depois de terminado o arranque, as outras regras voltam a decidir a velocidade (01, 03).

## Em resumo

| Situação | O que o carro faz |
|---|---|
| O semáforo ficou verde | Arranca |
| O peão acabou de atravessar | Arranca |
| Abriu-se uma faixa ao lado de um obstáculo | Contorna o obstáculo e arranca ao mesmo tempo |
| Ainda está vermelho | Fica parado |
