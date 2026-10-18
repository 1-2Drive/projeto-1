# 13 — Obstáculos na estrada

**Objetivo:** contornar um obstáculo na faixa do carro; enquanto não sai da faixa, ser sempre capaz de parar 10 m antes dele; se estiver perigosamente perto, travar de emergência.

São duas decisões ao mesmo tempo: a do volante (contornar) e a dos pedais (a que velocidade se aproxima). Por isso o carro pode travar e começar a desviar-se no mesmo instante.

```mermaid
flowchart TD
    S(["A câmara vê um obstáculo à frente,<br/>na faixa do carro (alcance: 120 m)"]) --> Q0{"O obstáculo é grave e está a 8 m ou menos,<br/>na faixa onde o carro está mesmo agora?"}
    Q0 -- "Sim" --> A0["Travagem de emergência"]
    Q0 -- "Não" --> Q1{"Já começou a sair da faixa?"}
    Q1 -- "Sim" --> A9["Termina o desvio (16)"]
    Q1 -- "Não" --> Q2{"Distância vs. distância para parar 10 m antes<br/>com travagem confortável<br/>(0,5 s de reação + travagem a 2,8 m/s² + 10 m;<br/>nunca menos de 60 m)"}
    Q2 -- "> essa distância" --> A1["Segue normalmente"]
    Q2 -- "≤ essa distância" --> Q3{"Para parar 10 m antes, bastaria<br/>travar a 3,1 m/s² ou menos?"}
    Q3 -- "Não" --> A2["Trava a fundo"]
    Q3 -- "Sim" --> Q4{"A velocidade é maior do que a que deixa<br/>parar 10 m antes, travando suavemente (2,0 m/s²)?"}
    Q4 -- "Sim" --> A3["Trava, seguindo essa curva<br/>(pára 10 m antes se não puder desviar)"]
    Q4 -- "Não" --> A4["Mantém a velocidade,<br/>sem ganhar velocidade para o obstáculo"]
    A3 --> Q5
    A4 --> Q5{"Distância ao obstáculo"}
    Q5 -- "> 50 m" --> A5["Ainda não decide o desvio"]
    Q5 -- "≤ 50 m" --> Q6{"Posso mudar para um lado? (ver 00)<br/>no centro, primeiro a esquerda"}
    Q6 -- "Sim" --> A6["Muda de faixa para o contornar"]
    Q6 -- "Não" --> A7["Fica na faixa e pára 10 m antes,<br/>à espera que uma faixa abra"]
    A6 --> A8["Depois volta à direita (17)"]

    classDef dec fill:#fff3cd,stroke:#b58900,color:#000
    classDef acao fill:#d4edda,stroke:#2e7d32,color:#000
    classDef forte fill:#f8d7da,stroke:#b02a37,color:#000
    class Q0,Q1,Q2,Q3,Q4,Q5,Q6 dec
    class A1,A3,A4,A5,A6,A7,A8,A9 acao
    class A0,A2 forte
```

O desvio só se decide a 50 m ou menos porque o sensor lateral só vê os perigos das faixas ao lado até 60 m: um lado visto livre fica livre durante toda a manobra. A travagem começa antes, para o carro chegar aos 50 m a uma velocidade da qual ainda consegue parar.

## Em resumo

| Situação | O que o carro faz |
|---|---|
| A 60 km/h, obstáculo a 100 m na faixa | A cerca de 68 m começa a travar suavemente; a 50 m desvia-se, sem travagem de emergência |
| Obstáculo no centro, esquerda livre | Contorna pela esquerda |
| Esquerda ocupada, direita livre | Contorna pela direita |
| Nenhuma faixa livre, ou linha contínua | Pára 10 m antes do obstáculo e explica que a linha contínua impede a manobra |
| Obstáculo grave a 8 m ou menos | Travagem de emergência |
| A meio do desvio, já fora da faixa do obstáculo | Não trava de emergência: a regra olha para a faixa onde o carro está mesmo |
| No centro, esquerda livre mas com um carro demasiado perto para o seguir, direita segura | Contorna pela direita; depois não passa pela direita um carro mais lento na faixa da esquerda (11) |

A pergunta "posso mudar para este lado?" é a mesma em todas as mudanças de faixa (ver 00): sem outra manobra em curso, sem linha contínua a cruzar, com o sensor lateral a dizer que esse lado está livre e com espaço para seguir o carro da frente nessa faixa.
