package cloudera.cdf.efm.nifi.pmml.processor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.annotation.behavior.EventDriven;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.SideEffectFree;
import org.apache.nifi.annotation.behavior.SupportsBatching;
import org.apache.nifi.annotation.behavior.InputRequirement.Requirement;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.SeeAlso;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.dmg.pmml.FieldName;
import org.jpmml.evaluator.Evaluator;
import org.jpmml.evaluator.EvaluatorUtil;
import org.jpmml.evaluator.FieldValue;
import org.jpmml.evaluator.InputField;

import cloudera.cdf.efm.nifi.pmml.service.PMMLModelProviderService;


@EventDriven
@SideEffectFree
@SupportsBatching
@InputRequirement(Requirement.INPUT_REQUIRED)
@Tags({"pmml", "predict", "score", "model execution"})
@SeeAlso({PMMLModelProviderService.class})
@CapabilityDescription("Executes PMML Model based on flow file attributes with smae name as Model inputs")
public class ExecutePMMLModel extends AbstractProcessor {

	
    public static final PropertyDescriptor PMML_MODEL_PROVIDER_SERVICE_PROP = new PropertyDescriptor.Builder()
    	.name("PMMLModel Provider Service")
    	.description("")
    	.identifiesControllerService(PMMLModelProviderService.class)
    	.required(true)
    	.build();
    
    public static final PropertyDescriptor MODEL_OUTPUT_ATTRIBUTE_NAME_PROP = new PropertyDescriptor.Builder()
    .name("The model output attribute name")
    .description("The attribute to use to store the result of the model")
    .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
    .required(true)
    .build();    
    
    // relationships
    public static final Relationship REL_SUCCESS = new Relationship.Builder()
            .description("All successful FlowFiles are routed to this relationship").name("success").build();
    public static final Relationship REL_FAIL = new Relationship.Builder()
            .description("A failure to in executing PMML Model will route the FlowFile here.").name("set state fail").build();
        
    
    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        final List<PropertyDescriptor> properties = new ArrayList<>();
        properties.add(PMML_MODEL_PROVIDER_SERVICE_PROP);
        properties.add(MODEL_OUTPUT_ATTRIBUTE_NAME_PROP);
        return properties;
    }	    


    @Override
    public Set<Relationship> getRelationships() {
        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(REL_SUCCESS);
        relationships.add(REL_FAIL);
        return relationships;
    }    
    
    
	@Override
	public void onTrigger(ProcessContext ctx, ProcessSession session)
			throws ProcessException {
        FlowFile flowFile = session.get();

        if (flowFile == null) {
            return;
        }
        
        final PMMLModelProviderService pmmlModelProviderServiec = ctx.getProperty(PMML_MODEL_PROVIDER_SERVICE_PROP).asControllerService(PMMLModelProviderService.class);
        
        Evaluator pmmlEvaluator = pmmlModelProviderServiec.getPMMLEvaluator();
        
		/* Get the model inputFields */
		List<? extends InputField> inputFields = pmmlEvaluator.getInputFields(); 
		
		getLogger().info("The input fields for model are: " + inputFields);
		
        /*
         * Iterate through each model input field and grab the value for it the flowfile attribute. If attribute
         * doesn't exist then throw exception
         */
		Map<FieldName, FieldValue> modelArgs = new LinkedHashMap<>();
		for(InputField inputField: inputFields) {
			FieldName inputName = inputField.getName();
			String rawValue = flowFile.getAttribute(inputName.getValue());
			
			if(StringUtils.isEmpty(rawValue)) {
				getLogger().error("Flow File attribute["+ inputName.getValue() + "] is required for PMML Model Input[ "+ inputField + " ]");
				session.transfer(flowFile, REL_FAIL);
			}
			
			// Transforming an arbitrary user-supplied value to a known-good PMML value
			FieldValue inputValue = inputField.prepare(rawValue);
			
			modelArgs.put(inputName, inputValue);
		}				
		getLogger().info("Args for PMML Model execution are: " + modelArgs);
		
		
		// Evaluating the model with known-good arguments
		Map<FieldName, ?> modelResults = pmmlEvaluator.evaluate(modelArgs);
		
		// Decoupling results from the JPMML-Evaluator runtime environment
		Map<String, ? > modelResultsNormalized = EvaluatorUtil.decodeAll(modelResults);		
		
		getLogger().info("The result of model execution is: " + modelResultsNormalized);
		
		
        final String modelOutputValueAttribute = ctx.getProperty(MODEL_OUTPUT_ATTRIBUTE_NAME_PROP).getValue();

		
		// Store the model output results as flow file attributes
		for(String modelResultKey: modelResultsNormalized.keySet()) {
			session.putAttribute(flowFile, modelResultKey, (String) modelResultsNormalized.get(modelResultKey));
			// also store the the result in user supplied attribute property name. If model has multipel results, then last returned.
			session.putAttribute(flowFile, modelOutputValueAttribute, (String) modelResultsNormalized.get(modelResultKey));
			
		}
		
		session.transfer(flowFile,REL_SUCCESS);
	}
	


}
