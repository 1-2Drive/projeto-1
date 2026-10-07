# Alterações a fazer no código

O relatório foi revisto para resolver as divergências com o perito (ver `NOTAS_MIGRACAO.md`, ponto 2). Em dois casos, o relatório passou a descrever um comportamento que o código ainda não tem. Esta lista diz o que falta mudar para que o código e o relatório coincidam.

Referência: branch `origin/test` (o `DecisionContext` e as regras são iguais em `origin/feat/drools-realtime-driving-expert-system`).

| # | Alteração | Esforço | Estado no relatório |
|---|---|---|---|
| 1 | TTC persistente: 3 → 2 leituras | Uma linha | Descrito como implementado |
| 2 | Paragem perante falha de sensor (R8) | Funcionalidade nova | Descrito como previsto |

---

## 1. TTC persistente com 2 leituras

**Porquê:** o perito indicou uma leitura numa situação crítica e duas numa situação normal. A regra de 1,3 s já age à primeira leitura. Com 2 leituras na regra de 2,5 s, o sistema passa a seguir esse esquema, e a R3 (chuva) já usa 2.

**O quê:** em `src/main/resources/rules/emergency.drl`, regra «Emergency brake when persistent TTC is critical»:

```diff
-        $count >= 3
+        $count >= 2
```

**Verificar:**
- Correr os testes. `SimulationEngineE2ETest` (linha 117) verifica que a regra dispara, o que com 2 leituras deve acontecer mais cedo.
- Confirmar no cenário de TTC crítico que a travagem de emergência continua a acontecer.

**No relatório:** secção 6.1, secção 8.x (CEP) e tabela da secção 3.3.

---

## 2. Paragem perante a falha de um sensor (R8)

**Porquê:** perante uma falha do veículo, o perito respondeu «STOP imediato, sem dúvida». O relatório apresenta esta funcionalidade como trabalho futuro (secção 10.2), e a R8 está na Tabela 5.x como «prevista».

**Problema atual:** as perceções expiram ao fim de 600 ms sem atualização (`SimulationEngine.purgeExpiredDetections`). Se um sensor deixar de responder, o sistema lê isso como ausência de objetos, ou seja, estrada livre, quando o estado devia ser *desconhecido*.

**O quê, segundo a R8 e a secção 4.x:**
- Representar explicitamente o estado de cada sensor (por exemplo, um facto `SensorStatusFact` com `OK`, `SEM_DADOS` e `FALHA`). O `SensorManager` já guarda `lastUpdateMillis` por sensor, o que pode servir de ponto de partida.
- Distinguir uma ausência temporária de observações de uma falha efetiva, por exemplo com um limiar de tempo sem leituras acima do período normal do sensor.
- Criar uma regra de paragem controlada com risco `CRITICAL` e prioridade `EMERGENCY_SAFETY` (70). A paragem deve ser controlada e não instantânea (secção 4.x).
- Incluir na evidência da decisão o sensor em falha, para a explicação (*Why*).

**Verificar:**
- Um cenário em que um sensor deixa de enviar dados e o veículo para de forma controlada.
- Um cenário com uma falha curta, abaixo do limiar, que não deve provocar paragem.

**No relatório, quando estiver implementado:**
- R8 na Tabela 5.x: «prevista» → «implementada».
- Tabela 8.5, «Paragem perante a falha de um sensor»: «Previsto» → «Implementado».
- Tabela da secção 3.3, linha «Falha do veículo»: «Previsto» → «Implementado».
- Secção 4.x: passar os verbos do futuro para o presente.
- Secção 10.2: retirar o parágrafo e a menção na frase de fecho.
