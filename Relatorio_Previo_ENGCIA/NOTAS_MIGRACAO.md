# Migração do Relatório Prévio para LaTeX — resumo das alterações

O relatório que estava em Word foi migrado para LaTeX na branch `feat/relatorio-migracao-latex`, com um commit por capítulo. O PDF compilado está em `build/relatorio.pdf` (40 páginas). A secção «Para decidir antes da entrega», no fim, é a parte mais importante para a reunião.

## Como foi feita a migração

- **O texto foi mantido igual ao do Word.** Só foi alterado para corrigir erros e gralhas (concordância, regência, pontuação, palavras em falta ou repetidas, números com unidades), para uniformizar termos que apareciam escritos de formas diferentes e para não afirmar no presente coisas que o protótipo ainda não faz. Todas as outras escolhas de escrita da equipa ficaram como estavam.
- **A estrutura foi reorganizada** para o relatório ter um fio condutor mais claro: primeiro o problema e de onde vem o conhecimento, depois como é representado, usado e explicado, e só no fim a implementação e a validação.
- **As figuras feitas com formas do Word** (Figuras 1, 2, 4 e 5 do docx) foram redesenhadas em LaTeX (TikZ) com o mesmo conteúdo. A figura da arquitetura foi mantida tal como estava.
- **Todas as tabelas** passaram a ter o mesmo estilo, legenda numerada e referência no texto.

## Estrutura nova

| Capítulo (LaTeX)                               | Págs.   | Origem no docx                                                                                          |
| ----------------------------------------------- | -------- | ------------------------------------------------------------------------------------------------------- |
| Terminologia específica (início, sem número) | vi–viii | Terminologia específica                                                                                |
| 1 Introdução                                  | 1–3     | Introdução + Problemática (passou a secção 1.2) + secção nova 1.3 Estrutura do relatório        |
| 2 Objetivos                                     | 4        | Objetivos                                                                                               |
| 3 Aquisição de conhecimento                   | 5–7     | Aquisição de Conhecimento                                                                             |
| 4 Sensores e dados do veículo                  | 8–10    | Sensores e dados de Veículos Autónomos                                                                |
| 5 Representação do conhecimento               | 11–13   | Representação do Conhecimento + secção nova 5.3 Regras representativas                              |
| 6 Raciocínio e tomada de decisão              | 14–15   | Tomada de Decisão (7.1 e 7.2)                                                                          |
| 7 Explicação da tomada de decisão            | 16–17   | Tomada de Decisão (7.3 Why, How, Why Not), agora capítulo próprio                                    |
| 8 Arquitetura e implementação                 | 18–24   | Arquitetura e Funcionamento + Implementação do Sistema + secção nova 8.5 Estado atual do protótipo |
| 9 Cenários e critérios de aceitação         | 25–26   | Cenários e Critérios de Aceitação                                                                   |
| 10 Limitações e trabalho futuro               | 27–28   | Limitações e Trabalho Futuro                                                                          |
| 11 Conclusão                                   | 29       | Conclusão                                                                                              |
| Anexo A Perguntas e respostas ao perito         | 31       | Anexo A                                                                                                 |

Foram retirados os capítulos e anexos vazios do esqueleto (Delimitação, Enquadramento teórico, Plano de validação, Guião de entrevista, Ficha de validação e Catálogo de regras). O Resumo e a Terminologia específica não aparecem no índice.

## Decisões tomadas durante a migração

**Termos uniformizados em todo o relatório**

- «memória de trabalho» (com *working memory* só na primeira ocorrência), em vez de «Working Memory».
- «tempo até à colisão (TTC)», com a sigla definida uma única vez, na Introdução.
- «travagem automática de emergência», como no glossário.
- «via» em vez de «faixa» (mudança de via, via adjacente, saída de via), como no Código da Estrada.
- «saliência (*salience*)», como no glossário.
- *Why*, *How* e *Why Not* em itálico, sempre com o N maiúsculo.

**Conteúdo**

- No capítulo 4, os verbos de tudo o que ainda não está implementado passaram para futuro ou «deverá»: LiDAR, equação da distância de travagem, THW, TLC, nível de confiança, confirmação entre sensores e paragem perante falha.
- Números corrigidos de acordo com o código: 77 regras (e não 75) e 7 testes com Playwright (e não 6).
- Figura 8.1 (arquitetura): a legenda já não diz «(Imagem gerada por AI)».
- Glossário: ficaram as definições do docx para «Confiança da deteção», «Desempate» e «Prioridade». A versão LaTeX anterior tinha outras e a de «Prioridade» confundia prioridade com nível de risco. O exemplo de «Facto» deixou de ser `obstaculo_confirmado`.

**Texto novo** (fácil de retirar, se a equipa não concordar)

- 1.3 Estrutura do relatório: dois parágrafos. Um distingue o modelo de conhecimento do protótipo atual e o outro descreve o percurso do relatório.
- 4.1: uma frase a indicar que nem todos os sensores estão implementados.
- 5.3 Regras representativas: tabela com 9 regras (7 implementadas, 2 previstas), escolhidas por serem claras e coerentes com as indicações do perito.
- 8.5 Estado atual do protótipo: tabela com o que está implementado, parcial ou previsto.
- 3.3 Síntese do conhecimento adquirido: escrita, mas **desativada** (não aparece no PDF) até a equipa a aprovar.
- Glossário: 5 termos novos que eram usados sem definição (CEP, DRL, *Entry point*, THW e TLC).

## Para decidir antes da entrega

**Obrigatório**

1. **Resumo**: ainda não está escrito (aparece «[Resumo por redigir]»).
2. **Divergências com o perito**: o sistema faz algumas coisas de forma diferente do que o perito indicou e o texto não o justifica. Para cada uma, decidir se se altera o sistema ou se se explica a escolha:
   - desempate pela menor velocidade, quando o perito indicou aleatório ou a primeira opção de uma lista ordenada (afeta os caps. 6 e 8, a Tabela 8.4 e o glossário);
   - quatro níveis de risco e sete categorias de prioridade, quando o perito falou em quatro níveis de prioridade;
   - regra de TTC persistente com 3 leituras, quando o perito indicou 2;
   - paragem perante falha de sensor ainda por implementar.
3. **Prolog**: perguntar ao professor se fica no relatório. Se sair, tem de sair dos Objetivos, dos critérios de aceitação (9.2), da secção 8.4, das Limitações e da Conclusão.
4. **Figura 8.1**: manter a imagem atual ou substituí-la por um diagrama próprio (basta trocar `figuras/arquitetura-sistema.png`).

**A validar**
5. **Secção 3.3**: reativar ou não. Está em `seccoes/aquisicao.tex` entre `\iffalse` e `\fi`. Se a reativarem, revejam o estado «em análise» das divergências do ponto 2.
6. **Secção 5.3** (regras representativas) e **secção 8.5** (estado do protótipo): confirmar que concordam com o conteúdo.
7. **Objetivo do A\***: não volta a aparecer no relatório. Retirar ou explicar.
8. **Glossário**: manter ou retirar Rete e Phreak (não são usados no texto) e os 5 termos novos.

**Opcional, se houver tempo**
9. Acrescentar à Tabela 9.1 os cenários sugeridos pelo perito: passadeira com peão e objetos inseridos pelo utilizador (este já existe na aplicação).
10. Ligar os exemplos de *Why* e *Why Not* (cap. 7) às regras da Tabela 5.2.
11. Na Conclusão, suavizar «demonstre de forma clara», porque o *Why Not* e o Prolog ainda não estão completos.
12. Acrescentar citações nos capítulos que não têm nenhuma (por exemplo, Giarratano e Jackson nas definições de sistema pericial).
