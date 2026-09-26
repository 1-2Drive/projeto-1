package pt.ipp.conducao.engine;

import pt.ipp.conducao.model.EngineResult;
import pt.ipp.conducao.model.KnowledgeBase;
import pt.ipp.conducao.model.SensorInput;

public interface InferenceEngine {
    EngineResult evaluate(SensorInput input, KnowledgeBase knowledgeBase);
}
