# Arquitetura do protótipo

## Diagrama de componentes

```mermaid
flowchart LR
    UI[Interface HTML/CSS/JS] -->|JSON comum| API[API Spring Boot]
    API --> ORQ[Serviço de avaliação]
    KB[(knowledge-base.json)] --> ORQ
    ORQ --> PA[Adaptador Prolog]
    ORQ --> DA[Adaptador Drools]
    PA --> SWI[SWI-Prolog]
    DA --> KIE[Sessão KIE/Drools]
    SWI --> CMP[Comparador]
    KIE --> CMP
    CMP --> UI
```

O ficheiro `knowledge-base.json` é a única fonte das dezoito regras de domínio. O
Drools recebe cada definição como facto e aplica meta-regras DRL. O Prolog recebe
o mesmo JSON pela entrada padrão e interpreta as condições declarativas.

## Sequência de uma avaliação

```mermaid
sequenceDiagram
    actor U as Utilizador
    participant F as Frontend
    participant S as Spring Boot
    participant P as SWI-Prolog
    participant D as Drools
    U->>F: Responde às perguntas dos sensores
    F->>S: POST /api/evaluate
    par Inferência independente
        S->>P: Sensores + base comum
        P-->>S: Regras ativas/inativas
    and
        S->>D: Sensores + base comum
        D-->>S: Regras ativas/inativas
    end
    S->>S: Prioridades, conflitos e equivalência
    S-->>F: Decisões e explicações
    F-->>U: Comparação lado a lado
```

## Sensores simulados

| Dispositivo | Variável lógica | Unidade/tipo |
|---|---|---|
| Câmara frontal | `obstaculoNaFaixa` | booleano |
| Câmara frontal | `faixaEsquerdaLivre`, `faixaDireitaLivre`, `chuvaIntensa`, `visibilidadeReduzida` | booleano |
| Câmara e mapa | `numeroFaixas`, `faixaAtual` | inteiro (1 a 6) |
| Câmara e radar | `peaoNaTrajetoria`, `distanciaPeao` | booleano e metros |
| Radar | `distanciaObstaculo` | metros |
| GPS | `limiteVelocidade` | km/h |
| IMU | `pisoInstavel` | booleano |
| Sensores das rodas | `velocidade` | km/h |
| Câmara do condutor | `condutorDistraido`, `nivelFadiga` | booleano e nível |
| TPMS | `estadoPressaoPneus` | `NORMAL`, `BAIXA` ou `CRITICA` |
| Autodiagnóstico | `sensoresOperacionais`, `falhaSensorCritico` | booleano |
| Bancos | `cintosColocados` | booleano |

## Política de decisão

Todas as condições de uma regra formam uma conjunção lógica. Podem existir
várias regras ativas; vence a de prioridade numérica superior. Num empate vence
o menor identificador, tornando o resultado determinístico. Decisões incompatíveis
continuam visíveis como conflito, mesmo depois de escolhida a vencedora.
