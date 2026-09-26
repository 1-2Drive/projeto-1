const form = document.querySelector('#question-form');
const answerControl = document.querySelector('#answer-control');
const questionText = document.querySelector('#question-text');
const questionSource = document.querySelector('#question-source');
const questionHelp = document.querySelector('#question-help');
const questionProgress = document.querySelector('#question-progress');
const progressBar = document.querySelector('#progress-bar');
const answerSummary = document.querySelector('#answer-summary');
const backButton = document.querySelector('#back-button');
const nextButton = document.querySelector('#next-button');
const restartButton = document.querySelector('#restart-button');
const resultsSection = document.querySelector('#results');
const resultsContainer = document.querySelector('#engine-results');
const agreement = document.querySelector('#agreement');
const road = document.querySelector('#road');
const roadScene = document.querySelector('#road-scene');
const sceneCar = document.querySelector('#scene-car');
const sceneObstacle = document.querySelector('#scene-obstacle');
const scenePerson = document.querySelector('#scene-person');
const rainLayer = document.querySelector('#rain-layer');
const sceneCaption = document.querySelector('#scenario-caption');
const sceneLegend = document.querySelector('#scene-legend');

const labels = {
  TRAVAR_EMERGENCIA: 'Travar de emergência',
  SOLICITAR_INTERVENCAO: 'Solicitar intervenção humana',
  PARAR_VERIFICAR_PNEUS: 'Parar e verificar pneus',
  IMPEDIR_CONDUCAO_AUTONOMA: 'Impedir condução autónoma',
  PARAR_DESCANSAR: 'Parar para descansar',
  PARAR_SEGURANCA: 'Parar em segurança',
  PREPARAR_TRAVAGEM: 'Reduzir e preparar travagem',
  RECOMENDAR_PAUSA: 'Recomendar uma pausa',
  REDUZIR_VELOCIDADE: 'Reduzir velocidade',
  MUDAR_ESQUERDA: 'Mudar para a esquerda',
  MUDAR_DIREITA: 'Mudar para a direita',
  MANTER_VELOCIDADE: 'Manter velocidade',
  SEM_DECISAO: 'Sem decisão',
  ERRO: 'Erro no motor'
};

const questions = [
  {
    id: 'sensoresOperacionais', source: 'Autodiagnóstico', type: 'choice',
    text: 'Os sensores estão operacionais?', help: 'Considere câmara, radar, GPS, IMU e sensores das rodas.'
  },
  {
    id: 'falhaSensorCritico', source: 'Autodiagnóstico', type: 'choice',
    text: 'A falha afeta um sensor crítico?', help: 'Radar, travagem, direção e deteção da trajetória são considerados críticos.',
    when: a => a.sensoresOperacionais === false
  },
  {
    id: 'numeroFaixas', source: 'Câmara e mapa', type: 'options',
    text: 'Quantas faixas existem neste sentido?', help: 'Conte apenas as faixas no mesmo sentido de circulação.',
    options: [['1 faixa', 1], ['2 faixas', 2], ['3 faixas', 3],
      ['4 faixas', 4], ['5 faixas', 5], ['6 faixas', 6]]
  },
  {
    id: 'faixaAtual', source: 'Câmara e mapa', type: 'options',
    text: 'Em que faixa circula o veículo?', help: 'A faixa 1 é a faixa mais à direita.',
    options: a => Array.from({length: a.numeroFaixas}, (_, index) => [`Faixa ${index + 1}`, index + 1])
  },
  {
    id: 'obstaculoNaFaixa', source: 'Câmara frontal', type: 'choice',
    text: 'Existe algum obstáculo na faixa de rodagem?', help: 'Considere apenas a faixa onde o veículo circula.'
  },
  {
    id: 'distanciaObstaculo', source: 'Radar', type: 'number',
    text: 'A que distância está o obstáculo?', help: 'Indique a distância estimada em metros.',
    min: 0, max: 1000, step: 0.5, value: 15, unit: 'm', when: a => a.obstaculoNaFaixa === true
  },
  {
    id: 'velocidade', source: 'Sensores das rodas', type: 'number',
    text: 'Qual é a velocidade atual do veículo?', help: 'Indique a velocidade em quilómetros por hora.',
    min: 0, max: 300, step: 1, value: 55, unit: 'km/h'
  },
  {
    id: 'condutorDistraido', source: 'Câmara do condutor', type: 'choice',
    text: 'O condutor apresenta sinais de distração?', help: 'Por exemplo, olhar prolongadamente para fora da estrada.'
  },
  {
    id: 'nivelFadiga', source: 'Câmara do condutor', type: 'options',
    text: 'Qual é o nível de fadiga do condutor?', help: 'Use sinais como bocejos, olhos semicerrados ou reação lenta.',
    options: [['Sem fadiga', 'NENHUMA'], ['Fadiga moderada', 'MODERADA'], ['Fadiga elevada', 'ELEVADA']]
  },
  {
    id: 'cintosColocados', source: 'Sensores dos bancos', type: 'choice',
    text: 'Todos os ocupantes usam cinto de segurança?', help: 'Inclua o condutor e todos os passageiros.'
  },
  {
    id: 'estadoPressaoPneus', source: 'TPMS', type: 'options',
    text: 'Qual é o estado da pressão dos pneus?', help: 'Pressão baixa permite continuar com cautela; pressão crítica exige paragem segura.',
    options: [['Normal', 'NORMAL'], ['Baixa', 'BAIXA'], ['Crítica', 'CRITICA']]
  },
  {
    id: 'pisoInstavel', source: 'IMU', type: 'choice',
    text: 'O veículo apresenta sinais de piso instável?', help: 'Inclui perda de aderência ou oscilações anormais.'
  },
  {
    id: 'chuvaIntensa', source: 'Câmara frontal', type: 'choice',
    text: 'Está a chover intensamente?', help: 'Considere chuva que reduza aderência ou aumente a distância de travagem.'
  },
  {
    id: 'visibilidadeReduzida', source: 'Câmara frontal', type: 'choice',
    text: 'A visibilidade está reduzida?', help: 'Pode dever-se a chuva, nevoeiro, noite ou encandeamento.'
  },
  {
    id: 'peaoNaTrajetoria', source: 'Câmara e radar', type: 'choice',
    text: 'Foi detetado um peão ou ciclista que pode entrar na trajetória?', help: 'Responda Sim quando existir risco de cruzamento da trajetória.'
  },
  {
    id: 'distanciaPeao', source: 'Radar', type: 'number',
    text: 'A que distância está o peão ou ciclista?', help: 'Indique a distância estimada em metros.',
    min: 0, max: 1000, step: 0.5, value: 20, unit: 'm', when: a => a.peaoNaTrajetoria === true
  },
  {
    id: 'faixaEsquerdaLivre', source: 'Câmara frontal', type: 'choice',
    text: 'A faixa da esquerda está livre?', help: 'Esta pergunta só é relevante quando uma mudança de faixa é possível.',
    when: a => obstacleAllowsLaneChange(a) && a.faixaAtual < a.numeroFaixas
  },
  {
    id: 'faixaDireitaLivre', source: 'Câmara frontal', type: 'choice',
    text: 'A faixa da direita está livre?', help: 'As duas faixas podem estar livres; nesse caso o sistema explicará o conflito.',
    when: a => obstacleAllowsLaneChange(a) && a.faixaAtual > 1
  },
  {
    id: 'limiteVelocidade', source: 'GPS e mapa', type: 'number',
    text: 'Qual é o limite de velocidade nesta via?', help: 'Indique o limite legal em quilómetros por hora.',
    min: 10, max: 150, step: 10, value: 90, unit: 'km/h'
  }
];

let answers = {};
let history = [];
let currentQuestion = null;
let evaluating = false;
let earlyConclusion = null;

function obstacleAllowsLaneChange(values) {
  return values.obstaculoNaFaixa === true
    && values.distanciaObstaculo >= 8
    && values.distanciaObstaculo < 25;
}

function applicableQuestions() {
  return questions.filter(question => !question.when || question.when(answers));
}

function nextUnansweredQuestion() {
  return applicableQuestions().find(question => answers[question.id] === undefined);
}

function renderQuestion() {
  renderScenario();
  currentQuestion = nextUnansweredQuestion();
  renderAnswerSummary();

  if (!currentQuestion) {
    evaluateConsultation();
    return;
  }

  const applicable = applicableQuestions();
  const answeredCount = applicable.filter(question => answers[question.id] !== undefined).length;
  questionProgress.textContent = `Pergunta ${answeredCount + 1} de ${applicable.length}`;
  progressBar.style.width = `${(answeredCount / applicable.length) * 100}%`;
  questionSource.textContent = currentQuestion.source;
  questionText.textContent = currentQuestion.text;
  questionHelp.textContent = currentQuestion.help;
  backButton.disabled = history.length === 0;
  nextButton.hidden = currentQuestion.type !== 'number';
  nextButton.disabled = false;
  answerControl.replaceChildren();

  if (currentQuestion.type === 'number') renderNumberInput();
  else renderChoices();
}

function renderChoices() {
  const choices = document.createElement('div');
  choices.className = 'choice-buttons';
  const options = currentQuestion.type === 'choice'
    ? [['Sim', true], ['Não', false]]
    : (typeof currentQuestion.options === 'function'
      ? currentQuestion.options(answers) : currentQuestion.options);
  options.forEach(([label, value]) => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'choice-button';
    button.textContent = label;
    button.addEventListener('click', () => saveAnswer(value));
    choices.append(button);
  });
  answerControl.append(choices);
}

function renderNumberInput() {
  const wrapper = document.createElement('div');
  wrapper.className = 'number-answer';
  const input = document.createElement('input');
  input.type = 'number';
  input.id = 'numeric-answer';
  input.required = true;
  input.min = currentQuestion.min;
  input.max = currentQuestion.max;
  input.step = currentQuestion.step;
  input.value = currentQuestion.value;
  input.setAttribute('aria-label', currentQuestion.text);
  const unit = document.createElement('span');
  unit.textContent = currentQuestion.unit;
  wrapper.append(input, unit);
  answerControl.append(wrapper);
  requestAnimationFrame(() => input.focus());
}

function saveAnswer(value) {
  history.push(currentQuestion.id);
  answers[currentQuestion.id] = value;
  removeAnswersThatBecameIrrelevant();
  renderScenario();
  earlyConclusion = terminalConclusion();
  if (earlyConclusion) {
    renderAnswerSummary();
    evaluateConsultation();
    return;
  }
  renderQuestion();
}

function terminalConclusion() {
  if (answers.sensoresOperacionais === false && answers.falhaSensorCritico === true) {
    return 'Falha num sensor crítico: a condução autónoma não pode continuar.';
  }
  if (answers.nivelFadiga === 'ELEVADA') {
    return 'Fadiga elevada: é necessária uma paragem imediata para descanso.';
  }
  if (answers.cintosColocados === false) {
    return 'Existe um ocupante sem cinto: a marcha não pode continuar.';
  }
  if (answers.estadoPressaoPneus === 'CRITICA') {
    return 'Pressão crítica dos pneus: é necessário parar em segurança.';
  }
  if (answers.peaoNaTrajetoria === true && answers.distanciaPeao < 12) {
    return 'Peão ou ciclista a distância crítica: é necessária travagem de emergência.';
  }
  if (answers.obstaculoNaFaixa === true && answers.distanciaObstaculo < 8
      && answers.velocidade > 60) {
    return 'Colisão iminente: é necessária travagem de emergência.';
  }
  return null;
}

function removeAnswersThatBecameIrrelevant() {
  const applicableIds = new Set(applicableQuestions().map(question => question.id));
  Object.keys(answers).forEach(id => {
    if (!applicableIds.has(id)) delete answers[id];
  });
  history = history.filter(id => answers[id] !== undefined);
}

form.addEventListener('submit', event => {
  event.preventDefault();
  if (!currentQuestion || currentQuestion.type !== 'number') return;
  const input = document.querySelector('#numeric-answer');
  if (!input.reportValidity()) return;
  saveAnswer(Number(input.value));
});

backButton.addEventListener('click', () => {
  if (!history.length || evaluating) return;
  const previousId = history.pop();
  delete answers[previousId];
  resultsSection.hidden = true;
  renderQuestion();
});

restartButton.addEventListener('click', restartConsultation);

function restartConsultation() {
  answers = {};
  history = [];
  currentQuestion = null;
  evaluating = false;
  earlyConclusion = null;
  resultsSection.hidden = true;
  resultsContainer.replaceChildren();
  form.hidden = false;
  renderScenario();
  renderQuestion();
}

function renderScenario() {
  const laneCount = answers.numeroFaixas ?? 2;
  const currentLane = Math.min(answers.faixaAtual ?? 1, laneCount);
  const visualLane = laneCount - currentLane;
  const laneX = `${((visualLane + 0.5) / laneCount) * 100}%`;

  road.style.gridTemplateColumns = `repeat(${laneCount}, 1fr)`;
  road.replaceChildren();
  for (let index = 0; index < laneCount; index += 1) {
    const lane = document.createElement('div');
    lane.className = 'road-lane';
    road.append(lane);
  }

  sceneCar.style.setProperty('--lane-x', laneX);
  sceneObstacle.style.setProperty('--lane-x', laneX);
  scenePerson.style.setProperty('--lane-x', laneX);
  sceneObstacle.style.top = `${distanceToSceneTop(answers.distanciaObstaculo)}px`;
  scenePerson.style.top = `${distanceToSceneTop(answers.distanciaPeao)}px`;
  sceneObstacle.hidden = answers.obstaculoNaFaixa !== true;
  scenePerson.hidden = answers.peaoNaTrajetoria !== true;
  rainLayer.hidden = answers.chuvaIntensa !== true;
  roadScene.classList.toggle('low-visibility', answers.visibilidadeReduzida === true);

  const speedText = answers.velocidade === undefined ? '' : ` · ${answers.velocidade} km/h`;
  sceneCaption.textContent = answers.numeroFaixas
    ? `${laneCount} ${laneCount === 1 ? 'faixa' : 'faixas'} · veículo na faixa ${currentLane}${speedText}`
    : 'À espera dos dados da via';
  const items = [];
  if (answers.obstaculoNaFaixa === true) items.push(`Obstáculo: ${answers.distanciaObstaculo ?? '?'} m`);
  if (answers.peaoNaTrajetoria === true) items.push(`Peão/ciclista: ${answers.distanciaPeao ?? '?'} m`);
  if (answers.chuvaIntensa === true) items.push('Chuva intensa');
  if (answers.visibilidadeReduzida === true) items.push('Visibilidade reduzida');
  if (answers.estadoPressaoPneus) items.push(`Pneus: ${answers.estadoPressaoPneus.toLowerCase()}`);
  if (answers.sensoresOperacionais === false) items.push('Falha de sensores');
  if (answers.cintosColocados === false) items.push('Cinto em falta');
  if (answers.faixaEsquerdaLivre !== undefined) items.push(`Esquerda ${answers.faixaEsquerdaLivre ? 'livre' : 'ocupada'}`);
  if (answers.faixaDireitaLivre !== undefined) items.push(`Direita ${answers.faixaDireitaLivre ? 'livre' : 'ocupada'}`);
  sceneLegend.replaceChildren(...items.map(item => {
    const label = document.createElement('span');
    label.textContent = item;
    return label;
  }));
}

function distanceToSceneTop(distance) {
  if (distance === undefined) return 66;
  const normalized = Math.min(Math.max(distance, 0), 100) / 100;
  return 40 + ((1 - normalized) * 145);
}

function renderAnswerSummary() {
  answerSummary.replaceChildren();
  history.forEach(id => {
    const question = questions.find(item => item.id === id);
    if (!question || answers[id] === undefined) return;
    const chip = document.createElement('span');
    chip.className = 'answer-chip';
    const value = answerLabel(question, answers[id]);
    chip.textContent = `${shortQuestionName(id)}: ${value}`;
    answerSummary.append(chip);
  });
}

function answerLabel(question, value) {
  if (question.type === 'choice') return value ? 'Sim' : 'Não';
  if (question.type === 'number') return `${value} ${question.unit}`;
  const options = typeof question.options === 'function' ? question.options(answers) : question.options;
  return options.find(([, optionValue]) => optionValue === value)?.[0] || value;
}

function shortQuestionName(id) {
  return {
    obstaculoNaFaixa: 'Obstáculo', distanciaObstaculo: 'Distância', velocidade: 'Velocidade',
    sensoresOperacionais: 'Sensores', falhaSensorCritico: 'Falha crítica',
    numeroFaixas: 'N.º faixas', faixaAtual: 'Faixa atual',
    condutorDistraido: 'Distração', nivelFadiga: 'Fadiga', cintosColocados: 'Cintos',
    estadoPressaoPneus: 'Pneus', pisoInstavel: 'Piso', chuvaIntensa: 'Chuva',
    visibilidadeReduzida: 'Visibilidade', peaoNaTrajetoria: 'Peão/ciclista',
    distanciaPeao: 'Distância peão',
    faixaEsquerdaLivre: 'Faixa esquerda', faixaDireitaLivre: 'Faixa direita',
    limiteVelocidade: 'Limite'
  }[id];
}

function completeSensorData() {
  return {
    velocidade: answers.velocidade ?? 0,
    limiteVelocidade: answers.limiteVelocidade ?? 90,
    distanciaObstaculo: answers.obstaculoNaFaixa ? answers.distanciaObstaculo : 1000,
    obstaculoNaFaixa: answers.obstaculoNaFaixa ?? false,
    faixaEsquerdaLivre: answers.faixaEsquerdaLivre ?? false,
    faixaDireitaLivre: answers.faixaDireitaLivre ?? false,
    numeroFaixas: answers.numeroFaixas ?? 1,
    faixaAtual: answers.faixaAtual ?? 1,
    estadoPressaoPneus: answers.estadoPressaoPneus ?? 'NORMAL',
    pisoInstavel: answers.pisoInstavel ?? false,
    condutorDistraido: answers.condutorDistraido ?? false,
    sensoresOperacionais: answers.sensoresOperacionais ?? true,
    falhaSensorCritico: answers.falhaSensorCritico ?? false,
    nivelFadiga: answers.nivelFadiga ?? 'NENHUMA',
    cintosColocados: answers.cintosColocados ?? true,
    chuvaIntensa: answers.chuvaIntensa ?? false,
    visibilidadeReduzida: answers.visibilidadeReduzida ?? false,
    peaoNaTrajetoria: answers.peaoNaTrajetoria ?? false,
    distanciaPeao: answers.peaoNaTrajetoria ? answers.distanciaPeao : 1000
  };
}

async function evaluateConsultation() {
  evaluating = true;
  form.hidden = true;
  questionProgress.textContent = 'A calcular a conclusão...';
  progressBar.style.width = '100%';
  const selectedEngine = document.querySelector('input[name="engine"]:checked').value;

  try {
    const response = await fetch('/api/evaluate', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({engine: selectedEngine, sensors: completeSensorData()})
    });
    const data = await response.json();
    if (!response.ok) throw new Error(data.error || 'Não foi possível avaliar o cenário.');
    renderResults(data);
  } catch (error) {
    resultsSection.hidden = false;
    agreement.textContent = '';
    resultsContainer.innerHTML = `<p class="error">${escapeHtml(error.message)}</p>`;
  } finally {
    evaluating = false;
  }
}

function renderResults(data) {
  resultsSection.hidden = false;
  resultsContainer.replaceChildren();
  if (earlyConclusion) {
    const notice = document.createElement('p');
    notice.className = 'terminal-notice';
    notice.innerHTML = `<strong>Consulta terminada antecipadamente.</strong> ${escapeHtml(earlyConclusion)}`;
    resultsContainer.append(notice);
  }
  if (data.enginesAgree === null) {
    agreement.textContent = 'Execução individual';
    agreement.className = 'agreement';
  } else {
    agreement.textContent = data.enginesAgree ? 'Resultados equivalentes' : 'Divergência detetada';
    agreement.className = `agreement ${data.enginesAgree ? 'ok' : 'warning'}`;
  }
  [data.prolog, data.drools].filter(Boolean).forEach(renderEngine);
  resultsSection.scrollIntoView({behavior: 'smooth', block: 'start'});
}

function renderEngine(engine) {
  const panel = document.querySelector('#engine-template').content.cloneNode(true);
  panel.querySelector('h3').textContent = engine.engine;
  panel.querySelector('.time').textContent = `${engine.executionTimeMs} ms`;
  panel.querySelector('.decision').textContent = labels[engine.selectedDecision] || engine.selectedDecision;
  panel.querySelector('.explanation').textContent = engine.error || engine.explanation;
  const conflicts = panel.querySelector('.conflicts');
  engine.conflicts.forEach(text => conflicts.insertAdjacentHTML('beforeend', `<p class="conflict">${escapeHtml(text)}</p>`));
  engine.activeRules.forEach(rule => panel.querySelector('.active-rules').append(ruleElement(rule, true)));
  const inactiveRules = panel.querySelector('.inactive-rules');
  if (earlyConclusion) {
    const explanation = document.createElement('p');
    explanation.textContent = 'As restantes alternativas não foram investigadas porque uma regra terminal de segurança determinou a conclusão.';
    inactiveRules.append(explanation);
  } else {
    engine.inactiveRules.forEach(rule => inactiveRules.append(ruleElement(rule, false)));
  }
  resultsContainer.append(panel);
}

function ruleElement(rule, active) {
  const element = document.createElement('div');
  element.className = 'rule';
  const detail = active ? rule.reason : rule.failedConditions.join('; ');
  element.innerHTML = `<span class="rule-id">${escapeHtml(rule.ruleId)} · P${rule.priority}</span>
    <strong>${escapeHtml(rule.ruleName)}</strong><p>${escapeHtml(detail)}</p>`;
  return element;
}

async function loadKnowledgeBase() {
  try {
    const response = await fetch('/api/knowledge-base');
    const knowledge = await response.json();
    document.querySelector('#kb-version').textContent = `Versão ${knowledge.version}`;
    const table = document.querySelector('#rules-table');
    table.replaceChildren();
    ['Regra', 'Decisão', 'Prioridade', 'Motivo'].forEach(value => {
      table.insertAdjacentHTML('beforeend', `<div class="table-head">${value}</div>`);
    });
    knowledge.rules.forEach(rule => {
      [rule.id, labels[rule.decision] || rule.decision, rule.priority, rule.reason].forEach(value => {
        const cell = document.createElement('div');
        cell.textContent = value;
        table.append(cell);
      });
    });
  } catch {
    document.querySelector('#kb-version').textContent = 'Indisponível';
  }
}

function escapeHtml(value) {
  const node = document.createElement('span');
  node.textContent = String(value);
  return node.innerHTML;
}

loadKnowledgeBase();
restartConsultation();
