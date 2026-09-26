# Fluxo focado do protótipo

O diagrama original em `first_diagram.md` representa uma visão funcional extensa.
Este fluxo reduz o âmbito às variáveis efetivamente implementadas na primeira versão.

```mermaid
flowchart TD
    A[Recolher perguntas dos sensores] --> B[Validar e normalizar dados]
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
