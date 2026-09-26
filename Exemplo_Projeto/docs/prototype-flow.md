# Fluxo implementado

O diagrama original em `first_diagram.md` está representado na consulta por perguntas
condicionais. Os blocos de estado inicial, condutor, veículo, ambiente, trajetória,
ultrapassagem, faixa, cruzamentos, emergência e risco alimentam a mesma base comum.

```mermaid
flowchart TD
    A[Estado inicial do veículo] --> A2[Condutor e ocupantes]
    A2 --> A3[Veículo, energia e pneus]
    A3 --> A4[Ambiente e estrada]
    A4 --> A5[Trajetória, obstáculos e peões]
    A5 --> A6[Ultrapassagem, faixa e cruzamentos]
    A6 --> A7[Emergência e risco global]
    A7 --> B[Validar e normalizar dados]
    B --> C[Carregar base de conhecimento JSON]
    C --> P[Executar adaptador SWI-Prolog]
    C --> D[Executar adaptador Drools]
    P --> RP[Regras ativas e why-not]
    D --> RD[Regras ativas e why-not]
    RP --> X[Resolver prioridades e conflitos]
    RD --> X
    X --> Q{Resultados equivalentes?}
    Q -- Sim --> S[Mostrar concordância]
    Q -- Não --> N[Assinalar divergência]
    S --> O[Decisão, motivo e regras]
    N --> O
```
