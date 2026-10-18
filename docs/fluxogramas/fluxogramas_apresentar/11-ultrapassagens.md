# 11 — Ultrapassagens e regresso à direita

**Objetivo:** ultrapassar pela esquerda um veículo mais lento, sem nunca passar alguém pela direita, e voltar à direita logo que possível (Código da Estrada, art. 13.º, 36.º, 38.º e 41.º). Se as faixas à frente estiverem todas lentas, desistir em segurança.

## Começar a ultrapassagem

```mermaid
flowchart TD
    S(["Veículo mais lento à frente,<br/>na mesma faixa"]) --> Q1{"Em que faixa está o carro?"}
    Q1 -- "esquerda (LEFT)" --> A1["Não há faixa para ultrapassar:<br/>acompanha-o (09)"]
    Q1 -- "direita ou centro" --> Q2{"A linha à esquerda é contínua?"}
    Q2 -- "Sim" --> A1
    Q2 -- "Não" --> Q3{"O sensor lateral diz que<br/>o lado esquerdo está ocupado?"}
    Q3 -- "Sim" --> A1
    Q3 -- "Não" --> Q4{"Na faixa da esquerda, o veículo seguinte<br/>está mais de 20 m para lá<br/>do que vai ultrapassar?"}
    Q4 -- "Não" --> A1
    Q4 -- "Sim, ou não há nenhum" --> Q5{"Há um peão no caminho?"}
    Q5 -- "Sim" --> A1
    Q5 -- "Não" --> Q6{"Há um semáforo que não esteja verde<br/>a 125 m ou menos?"}
    Q6 -- "Sim" --> A1
    Q6 -- "Não" --> Q7{"Como está o veículo da frente?"}
    Q7 -- "a aproximar-se: a mais de 0,5 m/s de diferença,<br/>a mais de 15 m e até máx(50 m; 5,5 s × diferença + 10 m)" --> A2["Muda para a esquerda"]
    Q7 -- "à mesma velocidade, a 6–30 m,<br/>pelo menos 10 km/h abaixo do desejado" --> A2
    Q7 -- "parado a 6–20 m,<br/>com o carro também parado" --> A2
    Q7 -- "outro caso" --> A1

    classDef dec fill:#fff3cd,stroke:#b58900,color:#000
    classDef acao fill:#d4edda,stroke:#2e7d32,color:#000
    class Q1,Q2,Q3,Q4,Q5,Q6,Q7 dec
    class A1,A2 acao
```

Se o veículo de que se aproxima está parado e perto demais para o contornar em andamento (a menos de 5,5 s à velocidade de aproximação), o carro pára atrás dele (09) e contorna-o depois de parado.

## Veículo mais lento na faixa da esquerda

```mermaid
flowchart TD
    S(["O radar vê um veículo à frente<br/>na faixa da esquerda"]) --> Q1{"Esse veículo está em movimento?<br/>(1,4 m/s ou mais)"}
    Q1 -- "Não" --> A0["Não é uma ultrapassagem: nada muda"]
    Q1 -- "Sim" --> Q2{"É pelo menos 1,4 m/s mais lento<br/>do que a velocidade que o carro pretende?"}
    Q2 -- "Não" --> A0
    Q2 -- "Sim" --> Q3{"O carro está na faixa da direita<br/>e o centro pode ser ocupado?"}
    Q3 -- "Sim" --> Q4{"A faixa mais à esquerda está livre<br/>mais de 20 m para lá desse veículo?"}
    Q4 -- "Sim" --> A1["Passa para o centro, atrás dele,<br/>e depois ultrapassa-o pela esquerda"]
    Q4 -- "Não" --> A2
    Q3 -- "Não" --> A2["Não o passa pela direita:<br/>abranda para ficar 35 m atrás dele"]
    S2(["O radar vê um veículo mais lento<br/>duas faixas à esquerda<br/>(o carro na direita, ele na esquerda)"]) --> A3["Também não o passa pela direita:<br/>abranda para ficar 35 m atrás dele<br/>e nunca acelera até à velocidade dele"]

    classDef dec fill:#fff3cd,stroke:#b58900,color:#000
    classDef acao fill:#d4edda,stroke:#2e7d32,color:#000
    class Q1,Q2,Q3,Q4 dec
    class A0,A1,A2,A3 acao
```

## Voltar à direita

```mermaid
flowchart TD
    S(["A meio de uma ultrapassagem"]) --> Q1{"Já passou o veículo?<br/>(o radar deixou de o ver à frente)"}
    Q1 -- "Não" --> A1["Continua na faixa da ultrapassagem"]
    Q1 -- "Sim" --> Q2{"A linha à direita é contínua?"}
    Q2 -- "Sim" --> A1
    Q2 -- "Não" --> Q3{"O sensor lateral, numa leitura feita depois<br/>da última mudança de faixa, diz que<br/>a direita está livre?"}
    Q3 -- "Não" --> A1
    Q3 -- "Sim" --> Q4{"Há algum veículo à frente na faixa<br/>da direita a 80 m ou menos?"}
    Q4 -- "Sim" --> A1
    Q4 -- "Não" --> Q5{"Há um pedido externo de<br/>mudança de faixa ativo?"}
    Q5 -- "Sim" --> A1
    Q5 -- "Não" --> A2["Volta à direita, uma faixa de cada vez"]

    classDef dec fill:#fff3cd,stroke:#b58900,color:#000
    classDef acao fill:#d4edda,stroke:#2e7d32,color:#000
    class Q1,Q2,Q3,Q4,Q5 dec
    class A1,A2 acao
```

## Quando a ultrapassagem é impossível

```mermaid
flowchart TD
    S(["Fora da faixa da direita, com um veículo<br/>a 30 m ou menos, pelo menos 10 km/h<br/>abaixo da velocidade desejada"]) --> Q1{"Pode continuar para a esquerda?<br/>(não está em LEFT, sem linha contínua,<br/>esquerda livre e mais livre em 20 m)"}
    Q1 -- "Sim" --> A0["Continua a ultrapassagem"]
    Q1 -- "Não" --> Q2{"Há quanto tempo está preso assim?"}
    Q2 -- "< 3 s" --> A5["Segue o carro da frente (09)"]
    Q2 -- "≥ 3 s" --> Q3{"O sensor lateral diz que<br/>a direita está livre ao lado?"}
    Q3 -- "Não" --> A2["Abranda até 3 km/h abaixo do carro da frente,<br/>para abrir espaço"]
    Q3 -- "Sim" --> Q4{"Espaço livre à frente na direita<br/>≥ máx(30 m; 2 s à velocidade atual)?<br/>(3 s com chuva ou aderência < 0,85)"}
    Q4 -- "Não" --> A2
    Q4 -- "Sim" --> Q5{"Há trânsito na faixa da direita<br/>a 80 m ou menos?"}
    Q5 -- "Não" --> A6["Fica atrás dele no centro:<br/>ir para a direita seria<br/>ultrapassá-lo pela direita"]
    Q5 -- "Sim" --> A3["Desiste e volta à direita"]
    A2 --> Q3
    A3 --> A4["Fica na direita a seguir o carro da frente<br/>enquanto as faixas continuarem bloqueadas"]

    classDef dec fill:#fff3cd,stroke:#b58900,color:#000
    classDef acao fill:#d4edda,stroke:#2e7d32,color:#000
    class Q1,Q2,Q3,Q4,Q5 dec
    class A0,A2,A3,A4,A5,A6 acao
```

## Em resumo

| Situação | O que o carro faz |
|---|---|
| Carro lento à frente, esquerda livre | Ultrapassa pela esquerda e volta à direita |
| Carro parado à frente | Pára atrás e contorna-o quando a esquerda fica livre |
| Carro lento na faixa da esquerda, faixa seguinte livre | Põe-se atrás dele e ultrapassa-o pela esquerda |
| Carro lento na faixa da esquerda, faixa seguinte também lenta | Fica na direita, abranda e não o passa pela direita |
| Trânsito lento em todas as faixas, o carro na direita | Fica na direita e segue o carro da frente |
| Trânsito lento em todas as faixas, o carro já fora da direita | Desiste, volta à direita com distância segura (maior com chuva) e segue o trânsito |
| Acabou de mudar de faixa | Só decide voltar à direita com uma leitura nova do sensor lateral; a anterior ainda podia ser de antes da manobra |
| Faixa da direita vazia mas carro lento no centro | Fica atrás dele no centro: ir para a direita seria ultrapassar pela direita |
| Contornou um obstáculo pela direita e há um carro mais lento na faixa da esquerda | Não o passa pela direita (art. 36.º): fica atrás dele |
