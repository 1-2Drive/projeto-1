# Sistema Especialista para Apoio à Condução Automatizada

## Resumo

Este projeto implementa um protótipo académico de apoio à decisão em condução
automatizada. O mesmo cenário sensorial e a mesma base de conhecimento são
avaliados por dois motores de inferência distintos: SWI-Prolog e Drools. A
aplicação apresenta a decisão escolhida, as regras ativadas, a justificação da
decisão, as razões pelas quais outras regras não foram ativadas e eventuais
conflitos. O sistema destina-se exclusivamente ao ensino e não controla veículos.

## 1. Problema e objetivos

Um sistema especialista representa conhecimento de domínio através de factos,
regras e um mecanismo de inferência. Neste protótipo, os factos são leituras
simuladas de sensores e as regras traduzem critérios simples de segurança. O
objetivo principal não é criar um condutor autónomo completo, mas comparar duas
formas de raciocínio simbólico sob condições controladas e reproduzíveis.

Objetivos específicos:

1. recolher leituras simuladas numa interface web;
2. manter uma única base de conhecimento independente do motor;
3. executar os mesmos factos em SWI-Prolog e Drools;
4. aplicar prioridades e detetar decisões incompatíveis;
5. produzir explicações `why` e `why not`;
6. testar automaticamente a equivalência dos resultados.

## 2. Fundamentação teórica

### 2.1 Sistemas especialistas

Um sistema especialista separa o conhecimento do domínio do mecanismo que o
aplica. Esta separação melhora a rastreabilidade: uma conclusão pode ser ligada
a uma regra concreta e aos factos que a satisfizeram. Ao contrário de um modelo
estatístico, as regras deste protótipo são legíveis e podem ser auditadas.

### 2.2 Prolog

Prolog é uma linguagem lógica baseada em factos, regras e resolução. O programa
descreve relações e o motor procura soluções que as satisfaçam. Neste projeto,
um meta-intérprete Prolog percorre as condições JSON de cada regra. A abordagem
é declarativa e permite observar diretamente quais condições foram verdadeiras.

### 2.3 Drools

Drools é um sistema de gestão de regras para Java. A memória de trabalho contém
factos e a agenda controla a ativação de regras. Aqui, cada definição da base
comum é inserida como facto numa sessão KIE; duas meta-regras classificam-na como
ativa ou inativa. Desta forma, Drools participa efetivamente na inferência sem
duplicar as dezoito regras de domínio em DRL.

### 2.4 Explicabilidade

Uma explicação `why` identifica a regra vencedora, a decisão, a prioridade e o
motivo associado. Uma explicação `why not` lista, para cada regra inativa, as
condições que não foram satisfeitas. Isto torna a inferência examinável e ajuda a
detetar erros de modelação.

## 3. Base de conhecimento comum

A base encontra-se em `src/main/resources/knowledge/knowledge-base.json`. Cada
regra tem identificador, nome, prioridade, decisão, motivo e uma lista de
condições. Os operadores suportados são igualdade e comparações numéricas, além
de comparações entre dois campos, como velocidade atual superior ao limite GPS.

As dezoito regras cobrem cinco grupos: integridade dos sensores, condutor e
ocupantes, estado dos pneus e piso, ambiente, e trajetória. Entre as decisões
estão travagem de emergência, bloqueio da condução autónoma, paragem segura,
redução de velocidade, preparação de travagem, mudança de faixa e condução normal.

| Prioridade | Regra | Decisão |
|---:|---|---|
| 100 | R01 Travagem crítica | Travar de emergência |
| 99 | Falha de sensor crítico | Impedir condução autónoma |
| 98 | Peão/ciclista a distância crítica | Travar de emergência |
| 95 | Pressão crítica | Parar e verificar pneus |
| 92 | Fadiga elevada | Parar para descansar |
| 90 | Condutor distraído | Solicitar intervenção humana |
| 88 | Cinto em falta | Parar em segurança |
| 84 | Pressão baixa | Reduzir velocidade |
| 82 | Falha de sensor não crítica | Ativar modo degradado |
| 80 | Piso instável | Reduzir velocidade |
| 78 | Peão/ciclista próximo | Preparar travagem |
| 75 | Desvio pela esquerda | Mudar para a esquerda |
| 70 | Desvio pela direita | Mudar para a direita |
| 68 | Chuva intensa | Reduzir velocidade |
| 66 | Visibilidade reduzida | Reduzir velocidade |
| 64 | Fadiga moderada | Recomendar pausa |
| 60 | Excesso de velocidade | Reduzir velocidade |
| 10 | Condução normal | Manter velocidade |

## 4. Arquitetura e implementação

O frontend envia um pedido JSON para a API Spring Boot. O serviço de avaliação
carrega a base comum e chama um ou ambos os adaptadores. O adaptador Prolog inicia
o executável `swipl` e comunica pela entrada e saída padrão. O adaptador Drools
cria uma sessão KIE e insere os sensores e as definições das regras como factos.
Uma política comum ordena as regras ativas, escolhe a maior prioridade e assinala
conflitos. Finalmente, o comparador verifica decisão, regras e explicações.

Esta política comum não substitui os motores: os motores determinam quais regras
estão ativas. A política apenas torna explícito e idêntico o critério de desempate,
evitando que diferenças acidentais de agenda prejudiquem a comparação.

O questionário também distingue regras terminais. Falha de sensor crítico,
fadiga elevada, cinto em falta, pressão crítica, peão a distância crítica e
colisão iminente encerram a recolha imediatamente. No `why not`, hipóteses que
exigiriam perguntas não realizadas são assinaladas como não investigadas.

## 5. Conflitos e prioridades

O cenário com obstáculo entre 8 e 25 metros e ambas as faixas livres ativa R11 e
R12. As decisões são incompatíveis. O sistema mostra as duas regras e o conflito,
mas seleciona R11 porque a prioridade 75 é superior a 70. Esta opção demonstra
resolução de conflitos sem esconder conhecimento potencialmente contraditório.

## 6. Testes

Os testes verificam a existência de dezoito regras, a travagem num cenário crítico,
a equivalência entre motores, a deteção do conflito entre mudanças de faixa, a
distinção entre pressão baixa e crítica, os limites laterais da estrada, a
produção de `why not` e a validação da API. Novos casos devem ser adicionados
sempre que uma regra ou operador for introduzido.

## 7. Limitações

As entradas são discretas ou escalares e não incluem incerteza, fusão temporal,
qualidade do sensor, dinâmica do veículo ou requisitos de tempo real. As regras
são deliberadamente pequenas e não foram certificadas. A aplicação não deve ser
ligada a atuadores nem utilizada para decisões reais de segurança.

## 8. Evolução futura

O desenho permite acrescentar regras ao JSON sem alterar os adaptadores, desde
que usem operadores existentes. Evoluções naturais incluem graus de confiança,
histórico temporal, simulação de falhas, importação de cenários, métricas de
desempenho, persistência de avaliações e geração automática de matrizes de teste.

## 9. Conclusão

O protótipo demonstra que motores com paradigmas diferentes podem partilhar
factos e conhecimento declarativo. A comparação explícita transforma uma eventual
divergência num resultado observável, enquanto as explicações tornam a decisão
adequada para análise e apresentação académica.
