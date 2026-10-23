# 16 — Mudança de faixa em curso

**Objetivo:** fazer cada mudança de faixa de forma suave e completa, sem hesitar nem voltar atrás a meio.

```mermaid
flowchart TD
    S(["As regras decidiram mudar de faixa"]) --> Q1{"Já há uma mudança de faixa em curso?"}
    Q1 -- "Sim" --> A9["Ignora a nova decisão:<br/>termina primeiro a atual"]
    Q1 -- "Não" --> Q2{"Verificação final com o que está na estrada:<br/>a faixa de destino está livre?<br/>(nada ao lado a 30 m nem perigo a 60 m)"}
    Q2 -- "Não" --> A0["Cancela antes de começar e,<br/>se ainda não estiver a travar,<br/>abranda 10 km/h por precaução"]
    Q2 -- "Sim" --> Q3{"Há linha contínua entre as faixas?"}
    Q3 -- "Sim" --> A0
    Q3 -- "Não" --> A1["Liga o pisca e começa a mudar"]
    A1 --> A2["Desloca-se suavemente<br/>(cerca de 3 segundos)"]
    A2 --> Q4{"Chegou ao centro da nova faixa?"}
    Q4 -- "Não" --> A2
    Q4 -- "Sim" --> A3["Termina a manobra e desliga o pisca;<br/>regista o instante em que terminou"]

    classDef dec fill:#fff3cd,stroke:#b58900,color:#000
    classDef acao fill:#d4edda,stroke:#2e7d32,color:#000
    class Q1,Q2,Q3,Q4 dec
    class A0,A1,A2,A3,A9 acao
```

## Em resumo

| Situação | O que o carro faz |
|---|---|
| Mudança de faixa normal | Pisca, desloca-se em cerca de 3 segundos e endireita |
| Carro lento perto demais à frente na faixa de destino | Nem chega a decidir mudar: as regras já o verificam antes ("posso mudar para este lado?", ver 00) |
| Durante a manobra surge outra sugestão | Termina primeiro a manobra atual; não começa outra a meio |
| Surge uma emergência durante a manobra | Trava; o movimento lateral termina |
| Acabou a manobra | As regras de voltar à direita só usam leituras do sensor lateral feitas depois deste instante |
