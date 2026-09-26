# Sistema Especialista para Condução Automatizada

Protótipo académico que compara SWI-Prolog e Drools sobre os mesmos dados e uma
única base de conhecimento. Não se destina ao controlo de veículos reais.

## Pré-requisitos

- Java 17 ou superior;
- SWI-Prolog disponível no comando `swipl`.

## Executar

```bash
./mvnw spring-boot:run
```

Abrir `http://localhost:8080` e escolher Prolog, Drools ou Ambos. A aplicação
faz uma pergunta de cada vez: os estados dos sensores são respondidos com
**Sim/Não** ou opções semânticas e apenas velocidade e distâncias usam campos numéricos.
Perguntas que deixem de ser relevantes são omitidas automaticamente.
Uma resposta que ative uma regra terminal de segurança encerra imediatamente a
consulta; as perguntas restantes não são apresentadas.

A consulta inclui o número de faixas e a faixa atual (a faixa 1 é a mais à
direita), autodiagnóstico, fadiga, cintos, pressão dos pneus em três níveis,
piso, chuva, visibilidade e deteção de peões/ciclistas.

A versão completa acrescenta estado do veículo e estacionamento, álcool,
medicação, avarias, energia, distância frontal, ultrapassagem, permanência na
faixa, cruzamentos, prioridade, sinalização, veículos de emergência e risco global.

O painel **Cenário atual** representa a via vista de cima e acompanha as
respostas, posicionando o veículo, obstáculos e peões/ciclistas nas faixas e
aplicando chuva ou visibilidade reduzida quando indicadas.

No resultado, abrir **Why** para consultar a conclusão, o motivo e as regras
ativas. Abrir **Why not** para ver as regras que não foram aplicadas e as
condições que falharam.

Para demonstrar um conflito, indicar um obstáculo entre 8 e 25 metros e responder
que as faixas esquerda e direita estão livres. As duas regras ficam ativas e a
prioridade da base de conhecimento resolve qual é a decisão selecionada.

## Testar

```bash
./mvnw test
```

## Estrutura

```text
src/main/java/       API Spring Boot, serviços e adaptadores
src/main/prolog/     Meta-intérprete SWI-Prolog
src/main/resources/  Base comum, meta-regras Drools e frontend
src/test/            Testes de equivalência e API
docs/                Relatório e diagramas
```

Documentação principal: [relatório](docs/relatorio.md) e
[arquitetura](docs/architecture.md). Para preparar outro computador, consultar o
[guia de instalação e execução](docs/Guia_Instalacao_Execucao_Sistema_Especialista.docx).

## API

- `POST /api/evaluate`: executa `both`, `prolog` ou `drools`;
- `GET /api/knowledge-base`: mostra a base comum carregada;
- `GET /api/health`: confirma que a aplicação está disponível.
