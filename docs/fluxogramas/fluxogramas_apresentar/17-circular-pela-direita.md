# 17 — Circular pela direita

**Objetivo:** voltar à faixa da direita sempre que não houver motivo para estar noutra, como manda o Código da Estrada (art. 13.º).

Cada pergunta abaixo é um motivo diferente para o carro ficar onde está. Só volta à direita quando nenhum se aplica.

```mermaid
flowchart TD
    S(["O carro está fora da faixa da direita"]) --> Q1{"Está a mudar de faixa?"}
    Q1 -- "Sim" --> A0["Fica na faixa atual"]
    Q1 -- "Não" --> Q2{"Tempo desde o início da simulação"}
    Q2 -- "< 4 s" --> A0
    Q2 -- "≥ 4 s" --> Q3{"A linha à direita é contínua?"}
    Q3 -- "Sim" --> A0
    Q3 -- "Não" --> Q4{"Há um perigo fixo na faixa da direita<br/>a 150 m ou menos?"}
    Q4 -- "Sim" --> A0
    Q4 -- "Não" --> Q5{"O sensor lateral, numa leitura feita depois<br/>da última mudança de faixa, diz que<br/>a direita está livre ao lado?"}
    Q5 -- "Não" --> A0
    Q5 -- "Sim" --> Q6{"Há um carro à frente na faixa da direita<br/>a 80 m ou menos?"}
    Q6 -- "Sim" --> A0
    Q6 -- "Não" --> Q7{"Há um carro à frente na própria faixa<br/>a 80 m ou menos?"}
    Q7 -- "Sim" --> A0
    Q7 -- "Não" --> Q8{"Há obstáculo, buraco, barreira fechada<br/>ou peão em conflito à frente?"}
    Q8 -- "Sim" --> A0
    Q8 -- "Não" --> Q9{"Está a arrancar depois de uma paragem?"}
    Q9 -- "Sim" --> A0
    Q9 -- "Não" --> Q10{"Está a meio de uma ultrapassagem?"}
    Q10 -- "Sim" --> A0
    Q10 -- "Não" --> Q11{"Há um pedido externo de<br/>mudança de faixa ativo?"}
    Q11 -- "Sim" --> A0
    Q11 -- "Não" --> Q12{"Tempo desde que terminou uma mudança<br/>de faixa pedida externamente"}
    Q12 -- "< 5 s" --> A0
    Q12 -- "≥ 5 s, ou não houve pedido" --> A1["Volta à direita,<br/>uma faixa de cada vez"]

    classDef dec fill:#fff3cd,stroke:#b58900,color:#000
    classDef acao fill:#d4edda,stroke:#2e7d32,color:#000
    class Q1,Q2,Q3,Q4,Q5,Q6,Q7,Q8,Q9,Q10,Q11,Q12 dec
    class A0,A1 acao
```

## Em resumo

| Situação | O que o carro faz |
|---|---|
| Acabou de contornar um obstáculo | Volta à direita quando a faixa está livre |
| Está na faixa da esquerda | Passa para o centro e depois para a direita |
| Há um carro na faixa da direita ao lado | Espera que fique livre |
| Mudou de faixa a pedido (operador ou cenário) | Fica lá 5 segundos antes de voltar à direita |
| Ultrapassagem impossível com trânsito lento | Volta à direita pelas regras da ultrapassagem (11) |
| Acabou de mudar de faixa | Espera por uma leitura nova do sensor lateral antes de decidir voltar: a anterior ainda podia ser de antes da manobra |
