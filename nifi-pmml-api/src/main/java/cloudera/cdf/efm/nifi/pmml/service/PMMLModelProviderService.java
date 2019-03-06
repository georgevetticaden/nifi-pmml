package cloudera.cdf.efm.nifi.pmml.service;

import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.controller.ControllerService;
import org.codehaus.groovy.tools.shell.Evaluator;

@Tags({"pmml", "dswb"})
@CapabilityDescription("API to get Evalutor for PMML Model")
public interface PMMLModelProviderService extends ControllerService{

	Evaluator getPMMLEvaluator();
}
