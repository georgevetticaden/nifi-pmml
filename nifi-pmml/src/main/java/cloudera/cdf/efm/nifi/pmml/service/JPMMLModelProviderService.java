package cloudera.cdf.efm.nifi.pmml.service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnEnabled;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.controller.AbstractControllerService;
import org.apache.nifi.controller.ConfigurationContext;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.reporting.InitializationException;
import org.jpmml.evaluator.DefaultVisitorBattery;
import org.jpmml.evaluator.Evaluator;
import org.jpmml.evaluator.LoadingModelEvaluatorBuilder;

@Tags({"pmml", "dswb", "jpmml"})
@CapabilityDescription("JPMML implementaiton for PMMlModelProvider")
public class JPMMLModelProviderService extends AbstractControllerService implements PMMLModelProviderService {

	
    public static final PropertyDescriptor PMML_MODEL = new PropertyDescriptor.Builder()
        .name("PMML Model")
        .description("PMML Content for the Predictive Model")
        .required(true)
        .build();

    private static final List<PropertyDescriptor> properties;

    static {
        final List<PropertyDescriptor> props = new ArrayList<>();
        props.add(PMML_MODEL);
        properties = Collections.unmodifiableList(props);
    }

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return properties;
    }

    private volatile Evaluator jPMMLEvaluator;
    
    /**
     * @param context the configuration context
     * @throws InitializationException if unable create/initialize/verify PMML Model
     */
    @OnEnabled
    public void onEnabled(final ConfigurationContext context) throws InitializationException {
    	final String pmmlContent = context.getProperty(PMML_MODEL).getValue();
    	
		try {
			InputStream pmmlContentInputStream = new ByteArrayInputStream(pmmlContent.getBytes()); 
			jPMMLEvaluator = new LoadingModelEvaluatorBuilder()
			.setLocatable(false)
			.setVisitors(new DefaultVisitorBattery())
			//.setOutputFilter(OutputFilters.KEEP_FINAL_RESULTS)
			.load(pmmlContentInputStream)
			.build();
		} catch (Exception e) {
			String errMsg = "Could not initialize/load PMML ["+pmmlContent +"]";
			getLogger().error(errMsg);
			throw new InitializationException(errMsg, e);
		}     	
		
		try {
			jPMMLEvaluator.verify();
		} catch (Exception e) {
			String errMsg = "Verificiation on PMML ["+pmmlContent +"] failed";
			throw new InitializationException(errMsg, e);
		}
   
    }
    
    
	@Override
	public Evaluator getPMMLEvaluator() {
		return jPMMLEvaluator;
	}
	
	

}
