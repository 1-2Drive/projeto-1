# 07 — Sinal STOP

**Objetivo:** parar por completo na linha de paragem, esperar um momento e só depois avançar (sinal B2, Código da Estrada).

O sinal STOP vale apenas para a faixa onde está. A linha de paragem fica 15 m antes do sinal; o carro procura parar 2 m antes da linha. As distâncias abaixo são à linha.

```mermaid
flowchart TD
    S(["Sinal STOP na faixa do carro"]) --> Q1{"Distância à linha"}
    Q1 -- "> 125 m" --> A1["Ainda não reage"]
    Q1 -- "4 m a 125 m" --> Q2{"A velocidade já é maior do que a que deixa<br/>parar 2 m antes da linha travando<br/>suavemente (2,0 m/s²)?"}
    Q2 -- "Não" --> A1b["Segue normalmente"]
    Q2 -- "Sim" --> Q3{"Com travagem confortável (2,8 m/s²),<br/>quanto passaria do ponto de paragem?"}
    Q3 -- "> 3 m (> 1 m se já travava a fundo)" --> A3["Trava a fundo"]
    Q3 -- "≤ 3 m" --> Q4{"A velocidade é maior do que a da curva<br/>de travagem confortável?"}
    Q4 -- "Sim" --> A2["Trava com conforto"]
    Q4 -- "Não" --> A2b["Mantém a velocidade"]
    Q1 -- "≤ 4 m, ou a linha já ficou para trás" --> Q5{"Com travagem confortável,<br/>quanto passaria do ponto de paragem?"}
    Q5 -- "> 3 m (> 1 m se já travava a fundo)" --> A3
    Q5 -- "≤ 3 m" --> A4["Pára por completo"]
    A3 --> Q1
    A2 --> Q1
    A2b --> Q1
    A4 --> Q6{"Há quanto tempo está parado?<br/>(menos de 0,6 km/h)"}
    Q6 -- "< 1,2 s" --> A4
    Q6 -- "≥ 1,2 s" --> A5["Avança a 25 km/h e arranca (04)"]

    classDef dec fill:#fff3cd,stroke:#b58900,color:#000
    classDef acao fill:#d4edda,stroke:#2e7d32,color:#000
    classDef forte fill:#f8d7da,stroke:#b02a37,color:#000
    class Q1,Q2,Q3,Q4,Q5,Q6 dec
    class A1,A1b,A2,A2b,A4,A5 acao
    class A3 forte
```

## Em resumo

| Situação | O que o carro faz |
|---|---|
| STOP à frente, a 50 km/h | A cerca de 50 m da linha começa a abrandar de forma progressiva e pára na linha |
| STOP visto já perto, depressa demais | Trava a fundo, numa só travagem contínua |
| STOP visto só depois de a linha ter ficado para trás (a 60 km/h, a 8 m do sinal) | Trava a fundo, pára assim que consegue (cerca de 6 m depois da linha), cumpre a paragem e só então segue |
| Parou na linha | Espera 1,2 segundos e avança a 25 km/h |
| STOP noutra faixa | Não se aplica ao carro |

**Como conta os 1,2 segundos.** Quando o carro para na linha, o sistema regista esse instante como um acontecimento ("parei aqui"). A paragem fica cumprida quando chega uma leitura do sensor de velocidade pelo menos 1,2 segundos depois desse acontecimento. É raciocínio temporal com um operador de Allen ("depois de", `after`), em vez de um cronómetro feito à mão.

**Não esquece o STOP.** A câmara deixa de ver o sinal quando o carro passa por ele. Enquanto a paragem estiver por fazer, o carro não esquece o sinal: só o esquece depois de ter parado.
