# 15 — Mudança de faixa pedida externamente

**Objetivo:** atender um pedido para mudar de faixa, mas só se for seguro.

O pedido não vem de um condutor dentro do carro: vem do operador (pela API ou pelos botões da aplicação desktop) ou de uma ação programada do cenário. A interface web deixou de ter os botões LANE LEFT e LANE RIGHT.

```mermaid
flowchart TD
    S(["Chega um pedido para mudar<br/>para a esquerda ou para a direita"]) --> Q1{"Idade do pedido"}
    Q1 -- "> 1,5 s" --> A0["O pedido expira"]
    Q1 -- "≤ 1,5 s" --> Q2{"O sensor lateral diz que<br/>o lado pedido está ocupado?"}
    Q2 -- "Sim" --> A2["Recusa e mantém a faixa,<br/>explicando que está ocupada"]
    Q2 -- "Não" --> Q3{"Há outra mudança de faixa em curso?"}
    Q3 -- "Sim" --> A3["Não muda; o painel mostra<br/>a condição que falhou"]
    Q3 -- "Não" --> Q4{"A linha que vai cruzar é contínua?"}
    Q4 -- "Sim" --> A3
    Q4 -- "Não" --> Q5{"Depois de mudar (3 s), ainda consegue<br/>baixar à velocidade do carro da frente<br/>nessa faixa e ficar 12 m atrás dele,<br/>travando suavemente (2,0 m/s²)?"}
    Q5 -- "Não" --> A3
    Q5 -- "Sim, ou não há carro" --> Q6{"Há uma paragem obrigatória ou emergência<br/>a travar o carro para o parar?"}
    Q6 -- "Sim" --> A4["A segurança prevalece:<br/>não muda de faixa"]
    Q6 -- "Não" --> A1["Muda de faixa"]
    A1 --> A5["Fica na faixa nova pelo menos 5 s<br/>antes de voltar à direita (17)"]

    classDef dec fill:#fff3cd,stroke:#b58900,color:#000
    classDef acao fill:#d4edda,stroke:#2e7d32,color:#000
    class Q1,Q2,Q3,Q4,Q5,Q6 dec
    class A0,A1,A2,A3,A4,A5 acao
```

## Em resumo

| Situação | O que o carro faz |
|---|---|
| Faixa pedida livre, sem outra manobra nem linha contínua | Muda de faixa |
| Outra mudança de faixa ainda em curso | Não muda; o painel mostra "a lane change is already in progress" |
| Faixa pedida ocupada | Mantém a faixa e informa |
| Carro lento pouco à frente na faixa pedida | Não muda: depois da manobra já não o conseguiria seguir a distância segura |
| Há um peão ou uma paragem obrigatória a travar o carro | A segurança prevalece: não muda de faixa |
| O carro estava a arrancar | Atende primeiro o pedido |
| Mudou de faixa a pedido | Fica lá pelo menos 5 segundos; depois, se nada o impedir, volta à direita (17) |

Antes de executar, o carro confirma mais uma vez que a faixa está livre e que não há linha contínua.
