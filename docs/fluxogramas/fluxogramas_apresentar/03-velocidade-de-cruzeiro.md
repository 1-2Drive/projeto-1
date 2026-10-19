# 03 — Velocidade de cruzeiro e aceleração

**Objetivo:** depois de abrandar ou parar, voltar à velocidade normal da estrada; e ter sempre uma decisão, mesmo quando nada de especial acontece.

Cada cenário tem uma **velocidade de cruzeiro** (a velocidade inicial). Enquanto nenhum sinal de limite tiver sido passado, é a ela que o carro regressa. Um pedido de aceleração não vem de um condutor: vem de uma ação programada do cenário ou de um botão da aplicação desktop.

```mermaid
flowchart TD
    S(["Estrada sem nada de especial"]) --> Q1{"Há um pedido de aceleração<br/>(operador ou cenário)?"}
    Q1 -- "Sim, feito há < 1,5 s" --> A3["Acelera até à velocidade pedida,<br/>se nenhum limite ou perigo pedir menos"]
    Q1 -- "Não, ou feito há ≥ 1,5 s" --> Q2{"Já passou algum<br/>sinal de limite?"}
    Q2 -- "Sim" --> A1["Segue o limite do sinal (01)"]
    Q2 -- "Não" --> Q3{"Velocidade vs. velocidade de cruzeiro<br/>(ou o limite, se for mais baixo)"}
    Q3 -- "mais de 0,5 km/h abaixo" --> A2["Volta a acelerar até ela"]
    Q3 -- "a 0,5 km/h ou menos" --> A4["Mantém a velocidade"]

    classDef dec fill:#fff3cd,stroke:#b58900,color:#000
    classDef acao fill:#d4edda,stroke:#2e7d32,color:#000
    class Q1,Q2,Q3 dec
    class A1,A2,A3,A4 acao
```

## Em resumo

| Situação | O que o carro faz |
|---|---|
| Travou por causa de um obstáculo e a estrada ficou livre | Volta à velocidade de cruzeiro |
| Há um carro lento à frente, chuva ou um perigo | A velocidade de cruzeiro cede: o carro respeita sempre o limite mais baixo |
| Um cenário pede para acelerar | Acelera, desde que nada mais importante o impeça |
| Nenhuma regra tem nada a dizer | Mantém a velocidade atual |
