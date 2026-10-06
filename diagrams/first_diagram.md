# Modelo conceptual do sistema de apoio à decisão

> **Scope note:** this diagram describes the *conceptual* driving decision-support
> model that inspired the project. The implemented academic Expert System covers
> the **road, environment, traffic, and manoeuvre** branches (see
> `diagrams/01-system-architecture.md`). Driver-condition (alcohol, fatigue,
> distraction), vehicle-fault/energy, and oncoming-traffic branches are out of the
> current implementation scope.

```mermaid
flowchart TD
    INICIO([Iniciar avaliação de segurança]) --> LIGADO{O veículo está ligado?}

    LIGADO -- Não --> FIM0([Sistema em espera])
    LIGADO -- Sim --> SENSOR{Os sensores estão operacionais?}

    SENSOR -- Não --> FALHA{A falha afeta sensores críticos?}
    FALHA -- Sim --> BLOQUEAR["Impedir condução autónoma<br/>Solicitar intervenção humana"]
    FALHA -- Não --> DEGRADADO["Ativar modo degradado<br/>Reduzir velocidade máxima"]
    SENSOR -- Sim --> PARADO{O veículo está parado?}
    DEGRADADO --> PARADO

    PARADO -- Sim --> ESTACIONADO{Está corretamente estacionado?}
    ESTACIONADO -- Não --> LOCAL_SEGURO{É possível parar num local seguro?}
    LOCAL_SEGURO -- Sim --> ESTACIONAR["Deslocar lentamente para local seguro"]
    LOCAL_SEGURO -- Não --> ALERTA_PARADO["Ligar quatro piscas<br/>Alertar condutor e assistência"]
    ESTACIONADO -- Sim --> INICIAR{O utilizador pretende iniciar a viagem?}
    INICIAR -- Não --> FIM1([Manter veículo imobilizado])
    INICIAR -- Sim --> ALCOOL
    PARADO -- Não --> ALCOOL

    subgraph CONDUTOR["Avaliação do condutor"]
        ALCOOL{Consumiste álcool?}
        ALCOOL -- Sim --> TESTE{Taxa de álcool conhecida?}
        TESTE -- Não --> NAO_CONDUZIR["Não conduzir<br/>Solicitar teste ou condutor alternativo"]
        TESTE -- Acima do permitido --> BLOQUEAR_CONDUCAO["Bloquear condução manual<br/>Sugerir transporte alternativo"]
        TESTE -- Dentro do permitido --> SONO

        ALCOOL -- Não --> SONO{Tens sono ou fadiga?}
        SONO -- Sim --> FADIGA{O nível de fadiga é elevado?}
        FADIGA -- Sim --> PARAGEM["Recomendar paragem imediata<br/>Procurar área de descanso"]
        FADIGA -- Não --> MONITORIZAR["Monitorizar o condutor<br/>Recomendar uma pausa"]
        SONO -- Não --> MEDICACAO

        MONITORIZAR --> MEDICACAO{Tomaste medicação que afeta a condução?}
        MEDICACAO -- Sim --> EFEITOS{Existem tonturas ou perda de atenção?}
        EFEITOS -- Sim --> BLOQUEAR_CONDUCAO
        EFEITOS -- Não --> CINTO
        MEDICACAO -- Não --> CINTO{Todos usam cinto de segurança?}

        CINTO -- Não --> CINTO_ALERTA["Alertar os ocupantes<br/>Impedir o início da marcha"]
        CINTO -- Sim --> DISTRACAO{O condutor está distraído?}
        DISTRACAO -- Sim --> DISTRACAO_ALERTA["Emitir alerta visual e sonoro<br/>Reforçar assistência à condução"]
        DISTRACAO -- Não --> VEICULO
        DISTRACAO_ALERTA --> VEICULO
    end

    subgraph ESTADO_VEICULO["Estado do veículo"]
        VEICULO{Existe alguma avaria crítica?}
        VEICULO -- Sim --> AVARIA["Reduzir velocidade<br/>Parar em segurança<br/>Contactar assistência"]
        VEICULO -- Não --> ENERGIA{Existe energia ou combustível suficiente?}

        ENERGIA -- Não --> AUTONOMIA{Há autonomia até um local seguro?}
        AUTONOMIA -- Não --> PARAR_ENERGIA["Parar em segurança<br/>Solicitar assistência"]
        AUTONOMIA -- Sim --> ROTA_CARGA["Alterar rota para abastecimento ou carregamento"]

        ENERGIA -- Sim --> PNEUS{Pressão e aderência dos pneus adequadas?}
        ROTA_CARGA --> PNEUS
        PNEUS -- Não --> VELOCIDADE_LIMITADA["Reduzir velocidade<br/>Evitar manobras bruscas"]
        PNEUS -- Sim --> AMBIENTE
        VELOCIDADE_LIMITADA --> AMBIENTE
    end

    subgraph AMBIENTE_E_ESTRADA["Condições ambientais e da estrada"]
        AMBIENTE{Está a chover?}
        AMBIENTE -- Sim --> CHUVA{A chuva é intensa?}
        CHUVA -- Sim --> CHUVA_ACAO["Reduzir significativamente a velocidade<br/>Aumentar distância de segurança"]
        CHUVA -- Não --> PISO
        AMBIENTE -- Não --> PISO{O piso está molhado, com gelo ou óleo?}
        CHUVA_ACAO --> PISO

        PISO -- Sim --> PISO_ACAO["Evitar travagens bruscas<br/>Aumentar distância de segurança"]
        PISO -- Não --> VISIBILIDADE
        PISO_ACAO --> VISIBILIDADE{A visibilidade é reduzida?}

        VISIBILIDADE -- Sim --> VIS_ACAO["Ligar iluminação adequada<br/>Reduzir velocidade"]
        VISIBILIDADE -- Não --> LIMITE
        VIS_ACAO --> LIMITE{Qual é o limite de velocidade da via?}
    end

    LIMITE --> VELOCIDADE{A velocidade atual excede o limite?}
    VELOCIDADE -- Sim --> REDUZIR_LIMITE["Reduzir até ao limite legal"]
    VELOCIDADE -- Não --> ADEQUADA{A velocidade é adequada às condições?}
    REDUZIR_LIMITE --> ADEQUADA

    ADEQUADA -- Não --> REDUZIR_SEGURA["Calcular velocidade segura<br/>Desacelerar progressivamente"]
    ADEQUADA -- Sim --> DISTANCIA
    REDUZIR_SEGURA --> DISTANCIA{A distância para o veículo da frente é segura?}

    DISTANCIA -- Não --> COLISAO{O tempo estimado para colisão é crítico?}
    COLISAO -- Sim --> TRAVAGEM["Executar travagem de emergência<br/>Ativar alertas"]
    COLISAO -- Não --> DESACELERAR["Desacelerar e aumentar distância"]
    DISTANCIA -- Sim --> OBSTACULO

    OBSTACULO{Foi detetado um obstáculo?}
    OBSTACULO -- Sim --> OBST_TRAJ{O obstáculo está na trajetória?}
    OBST_TRAJ -- Não --> MONITOR_OBST["Monitorizar posição do obstáculo"]
    OBST_TRAJ -- Sim --> DESVIO{Existe espaço seguro para desviar?}
    DESVIO -- Sim --> DESVIAR["Sinalizar e executar desvio controlado"]
    DESVIO -- Não --> TRAVAGEM
    OBSTACULO -- Não --> PEAO
    MONITOR_OBST --> PEAO

    PEAO{Foi detetado um peão ou ciclista?}
    PEAO -- Sim --> PEAO_TRAJ{Pode entrar na trajetória?}
    PEAO_TRAJ -- Sim --> PEAO_PERTO{Está a uma distância crítica?}
    PEAO_PERTO -- Sim --> TRAVAGEM
    PEAO_PERTO -- Não --> REDUZIR_PEAO["Reduzir velocidade<br/>Preparar travagem"]
    PEAO_TRAJ -- Não --> VIGIAR_PEAO["Manter vigilância reforçada"]
    PEAO -- Não --> ULTRAPASSAR

    ULTRAPASSAR{Estás a ultrapassar?}
    ULTRAPASSAR -- Não --> FAIXA
    ULTRAPASSAR -- Sim --> PERMITIDO{A ultrapassagem é permitida?}
    PERMITIDO -- Não --> CANCELAR["Cancelar ultrapassagem<br/>Regressar à faixa em segurança"]
    PERMITIDO -- Sim --> VIS_LIVRE{Há visibilidade e espaço suficientes?}
    VIS_LIVRE -- Não --> CANCELAR
    VIS_LIVRE -- Sim --> SENTIDO{Vem algum veículo em sentido contrário?}

    SENTIDO -- Sim --> TEMPO{Há tempo seguro para concluir?}
    TEMPO -- Não --> CANCELAR
    TEMPO -- Sim --> EXECUTAR["Sinalizar<br/>Manter distância lateral<br/>Concluir ultrapassagem"]
    SENTIDO -- Não --> EXECUTAR

    EXECUTAR --> FAIXA{O veículo mantém-se dentro da faixa?}
    CANCELAR --> FAIXA
    REDUZIR_PEAO --> FAIXA
    VIGIAR_PEAO --> FAIXA
    DESACELERAR --> FAIXA
    DESVIAR --> FAIXA

    FAIXA -- Não --> PISCA{O pisca foi ativado?}
    PISCA -- Sim --> MUDANCA{A mudança de faixa é segura?}
    MUDANCA -- Sim --> MUDAR["Executar mudança de faixa"]
    MUDANCA -- Não --> MANTER["Cancelar manobra e manter faixa"]
    PISCA -- Não --> CORRIGIR["Alertar condutor<br/>Corrigir trajetória suavemente"]
    FAIXA -- Sim --> CRUZAMENTO

    MUDAR --> CRUZAMENTO{Aproxima-se um cruzamento?}
    MANTER --> CRUZAMENTO
    CORRIGIR --> CRUZAMENTO

    CRUZAMENTO -- Sim --> PRIORIDADE{O veículo tem prioridade?}
    PRIORIDADE -- Não --> CEDER["Reduzir velocidade<br/>Ceder passagem ou parar"]
    PRIORIDADE -- Sim --> SEMAFORO{O semáforo ou sinal permite avançar?}
    SEMAFORO -- Não --> PARAR_SINAL["Parar antes da linha de segurança"]
    SEMAFORO -- Sim --> ATRAVESSAR["Confirmar zona livre<br/>Avançar com precaução"]
    CRUZAMENTO -- Não --> EMERGENCIA

    CEDER --> EMERGENCIA{Existe algum veículo de emergência?}
    PARAR_SINAL --> EMERGENCIA
    ATRAVESSAR --> EMERGENCIA

    EMERGENCIA -- Sim --> FACILITAR["Reduzir velocidade<br/>Abrir passagem e parar se necessário"]
    EMERGENCIA -- Não --> RISCO

    FACILITAR --> RISCO{Qual é o nível global de risco?}
    RISCO -- Crítico --> TRAVAGEM
    RISCO -- Elevado --> MODO_SEGURO["Reduzir velocidade<br/>Aumentar distâncias<br/>Restringir manobras"]
    RISCO -- Moderado --> ALERTAR["Alertar o condutor<br/>Manter monitorização reforçada"]
    RISCO -- Baixo --> NORMAL["Manter condução normal"]

    TRAVAGEM --> EXPLICAR
    MODO_SEGURO --> EXPLICAR
    ALERTAR --> EXPLICAR
    NORMAL --> EXPLICAR

    EXPLICAR["Explicação Why:<br/>dados observados, regra ativada e ação escolhida"]
    EXPLICAR --> WHYNOT["Explicação Why-not:<br/>motivo pelo qual outras ações não foram escolhidas"]

    WHYNOT --> CONTINUAR{A viagem continua?}
    CONTINUAR -- Sim --> SENSOR
    CONTINUAR -- Não --> FIM2([Terminar avaliação])

    BLOQUEAR --> FIM2
    BLOQUEAR_CONDUCAO --> FIM2
    NAO_CONDUZIR --> FIM2
    PARAGEM --> FIM2
    CINTO_ALERTA --> FIM1
    AVARIA --> FIM2
    PARAR_ENERGIA --> FIM2
    ALERTA_PARADO --> FIM2
    ESTACIONAR --> FIM2

    classDef inicio fill:#dbeafe,stroke:#2563eb,color:#172554
    classDef pergunta fill:#fef3c7,stroke:#d97706,color:#451a03
    classDef segura fill:#dcfce7,stroke:#16a34a,color:#052e16
    classDef alerta fill:#ffedd5,stroke:#ea580c,color:#431407
    classDef perigo fill:#fee2e2,stroke:#dc2626,color:#450a0a
    classDef explicacao fill:#ede9fe,stroke:#7c3aed,color:#2e1065

    class INICIO,FIM0,FIM1,FIM2 inicio
    class LIGADO,SENSOR,FALHA,PARADO,ESTACIONADO,LOCAL_SEGURO,INICIAR,ALCOOL,TESTE,SONO,FADIGA,MEDICACAO,EFEITOS,CINTO,DISTRACAO,VEICULO,ENERGIA,AUTONOMIA,PNEUS,AMBIENTE,CHUVA,PISO,VISIBILIDADE,LIMITE,VELOCIDADE,ADEQUADA,DISTANCIA,COLISAO,OBSTACULO,OBST_TRAJ,DESVIO,PEAO,PEAO_TRAJ,PEAO_PERTO,ULTRAPASSAR,PERMITIDO,VIS_LIVRE,SENTIDO,TEMPO,FAIXA,PISCA,MUDANCA,CRUZAMENTO,PRIORIDADE,SEMAFORO,EMERGENCIA,RISCO,CONTINUAR pergunta
    class NORMAL,ATRAVESSAR,MUDAR,EXECUTAR,VIGIAR_PEAO,MONITOR_OBST segura
    class DEGRADADO,MONITORIZAR,DISTRACAO_ALERTA,ROTA_CARGA,VELOCIDADE_LIMITADA,CHUVA_ACAO,PISO_ACAO,VIS_ACAO,REDUZIR_LIMITE,REDUZIR_SEGURA,DESACELERAR,REDUZIR_PEAO,CANCELAR,MANTER,CORRIGIR,CEDER,FACILITAR,MODO_SEGURO,ALERTAR alerta
    class BLOQUEAR,BLOQUEAR_CONDUCAO,NAO_CONDUZIR,PARAGEM,CINTO_ALERTA,AVARIA,PARAR_ENERGIA,TRAVAGEM,PARAR_SINAL perigo
    class EXPLICAR,WHYNOT explicacao
