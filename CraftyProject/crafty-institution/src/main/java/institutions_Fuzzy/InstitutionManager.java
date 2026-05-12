package institutions_Fuzzy;
import com.fasterxml.jackson.databind.ObjectMapper;

import utils.InstitutionOutput;
import utils.TargetModelOutput;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;

/*
 * @author yongchao Zeng
 *
 */

public class InstitutionManager {
    private static final Logger LOGGER = Logger.getLogger(InstitutionManager.class.getName());
    List<Institution> institutions; 
    HashMap<String, Institution> institutionMap = new HashMap<>();
    /**
     * Loads institutions from a JSON file
     * 
     * @param institutionsJsonPath Path to the JSON file containing institution configurations
     * @return List of Institution objects
     * @throws IOException If the file cannot be read or parsed
     */
    public List<Institution> loadInstitutions(String institutionsJsonPath, String fclPath, int timeLag, boolean startZero) throws IOException {
        LOGGER.info("Loading institutions from: " + institutionsJsonPath);
        
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(new File(institutionsJsonPath));
        JsonNode institutionsNode = rootNode.get("institutions");
        
        if (institutionsNode == null || !institutionsNode.isArray()) {
            throw new IOException("Invalid JSON format: 'institutions' array not found");
        }
        
        institutions = new ArrayList<>();
        
        for (JsonNode institutionNode : institutionsNode) {
            String institutionName = institutionNode.get("institutionName").asText();
            LOGGER.info("Loading institution: " + institutionName);
            
            JsonNode policiesNode = institutionNode.get("policies");
            if (policiesNode == null || !policiesNode.isArray()) {
                throw new IOException("Invalid JSON format: 'policies' array not found for institution: " + institutionName);
            }
            
            List<Policy> policies = new ArrayList<>();
            for (JsonNode policyNode : policiesNode) {
                Policy policy = mapper.treeToValue(policyNode, Policy.class);
                policy.initPolicy();
                policies.add(policy);
            }
            
            Institution institution = new Institution(institutionName, policies, fclPath, timeLag, startZero);
            institutions.add(institution);
            institutionMap.put(institutionName, institution);
        }
        
        LOGGER.info("Successfully loaded " + institutions.size() + " institutions");
        return institutions;
    }

    // Using this method is encouraged.
    public InstitutionOutput step(TargetModelOutput targetModelOutput) {
        HashMap<String, ArrayList<Double>> targetModelOutputMap = targetModelOutput.modelOutput();
        HashMap<String, HashMap<String, Double>> estimatedQuantity = targetModelOutput.estimatedQuantities();
        HashMap<String, Double> budgetsHashMap = targetModelOutput.budgets();
        HashMap<String, HashMap<String, Double>> institutionPolicyMap = new HashMap<>();
        for (Institution institution : institutions) {
            HashMap<String, ArrayList<Double>> inputDatHashMapCopy = new HashMap<>(targetModelOutputMap);
            HashMap<String, Double> estimatedQuantityForInstitution = estimatedQuantity.get(institution.getName());
            double budget = budgetsHashMap.get(institution.getName());
            HashMap<String, Double> institutionOuput = institution.step(inputDatHashMapCopy, estimatedQuantityForInstitution, budget);
            institutionPolicyMap.put(institution.getName(), institutionOuput);
        }
        return new InstitutionOutput(institutionPolicyMap);
    }
    
    /**
     * Steps all institutions using data from a JSON file and returns results as a nested HashMap
     * 
     * @param jsonFilePath Path to the JSON file containing institution data
     * @return HashMap with institution names as keys and their output data as values
     * @throws IOException If the file cannot be read or parsed
     */
    public InstitutionOutput stepFromJson(String jsonFilePath) throws IOException {
        LOGGER.info("Loading institution step data from: " + jsonFilePath);
        
        ObjectMapper mapper = new ObjectMapper();
        JsonNode rootNode = mapper.readTree(new File(jsonFilePath));
        
        HashMap<String, ArrayList<Double>> inputDataMap = new HashMap<>();
        HashMap<String, HashMap<String, Double>> estimatedQuantityMap = new HashMap<>();
        HashMap<String, Double> budgetMap = new HashMap<>();
        
        // Process each institution in the JSON file
        rootNode.fields().forEachRemaining(institutionEntry -> {
            String institutionName = institutionEntry.getKey();
            JsonNode institutionNode = institutionEntry.getValue();
            
            // Process input data
            JsonNode inputDataNode = institutionNode.get("inputData");
            if (inputDataNode != null && inputDataNode.isObject()) {
                inputDataNode.fields().forEachRemaining(entry -> {
                    String key = entry.getKey();
                    ArrayList<Double> values = new ArrayList<>();
                    if (entry.getValue().isArray()) {
                        for (JsonNode valueNode : entry.getValue()) {
                            values.add(valueNode.asDouble());
                        }
                    }
                    inputDataMap.put(key, values);
                });
            }
            
            // Process estimated quantity
            JsonNode estimatedQuantityNode = institutionNode.get("estimatedQuantity");
            if (estimatedQuantityNode != null && estimatedQuantityNode.isObject()) {
                HashMap<String, Double> institutionEstimatedQuantity = new HashMap<>();
                estimatedQuantityNode.fields().forEachRemaining(entry -> {
                    institutionEstimatedQuantity.put(entry.getKey(), entry.getValue().asDouble());
                });
                estimatedQuantityMap.put(institutionName, institutionEstimatedQuantity);
            }
            
            // Process budget
            if (institutionNode.has("budget")) {
                budgetMap.put(institutionName, institutionNode.get("budget").asDouble());
            }
        });
        
        // Call the existing step method with the parsed data
        return step(new TargetModelOutput(inputDataMap, estimatedQuantityMap, budgetMap));
    }
    
    /**
     * Steps all institutions using data from a JSON file and outputs results to a JSON file
     * 
     * @param inputJsonPath Path to the input JSON file containing institution data
     * @param outputJsonPath Path to the output JSON file where results will be written
     * @throws IOException If the files cannot be read, parsed, or written
     */
    public void stepFromJsonToJson(String inputJsonPath, String outputJsonPath) throws IOException {
        // The results are a nested HashMap:keys are institution names, values are HashMap of institution output policy names and values
        InstitutionOutput results = stepFromJson(inputJsonPath);
        
        // Convert results to JSON and write to file
        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(new File(outputJsonPath), results);
        
        LOGGER.info("Successfully wrote institution step results to: " + outputJsonPath);
    }

    public Institution getInstitution(String institutionName) throws IllegalArgumentException {
        return institutionMap.get(institutionName);
    }

    public InstitutionOutput getInitPolicies(){
        HashMap<String, HashMap<String, Double>> institutionPolicyMap = new HashMap<>();
        for (Institution institution : institutions) {
            String institutionName = institution.getName();
            HashMap<String, Double> policyMap = new HashMap<>();
            for(Policy policy : institution.getPolicies()){
                String policyName = policy.getPolicyName();
                double policyValue = policy.getStepSize();
                policyMap.put(policyName, policyValue);
            }
            institutionPolicyMap.put(institutionName, policyMap);
        }
        return new InstitutionOutput(institutionPolicyMap);
    }

    public InstitutionOutput getInitZeroPolicies(){
        HashMap<String, HashMap<String, Double>> institutionPolicyMap = new HashMap<>();
        for (Institution institution : institutions) {
            String institutionName = institution.getName();
            HashMap<String, Double> policyMap = new HashMap<>();
            for(Policy policy : institution.getPolicies()){
                String policyName = policy.getPolicyName();
                double policyValue = 0;
                policyMap.put(policyName, policyValue);
            }
            institutionPolicyMap.put(institutionName, policyMap);
        }
        return new InstitutionOutput(institutionPolicyMap);
    }

	@Override
	public String toString() {
		return "InstitutionManager [institutions=" + institutions + ", institutionMap=" + institutionMap + "]";
	}

	public List<Institution> getInstitutions() {
		return institutions;
	}


	public HashMap<String, Institution> getInstitutionMap() {
		return institutionMap;
	}
    
    
}