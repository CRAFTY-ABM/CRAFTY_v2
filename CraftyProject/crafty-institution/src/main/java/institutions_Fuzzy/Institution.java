package institutions_Fuzzy;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import net.sourceforge.jFuzzyLogic.FIS;
import net.sourceforge.jFuzzyLogic.FunctionBlock;
import utils.PolicyLoader;

/*
 * Rationale for skipping policies with zero estimated quantity:
 * Skipping policies with zero estimated quantity has two sides:
 * 1. Pro: It makes the logic more intutitve because policy makers won't adjust policies that are being used.
 * 2. Con: If the zero-quantity policies have not been adjusted for a long time, it may lose track of the target model's
 * current state. If this policy's qunatity becomes non-zero again, it may start from a value that is far from effective.
 * 
 * For example, if a policy is subsidizing maize production and the target is to reach a higher overall food production. If there are 30 years without any
 * maize production, i.e., the policy's estimated quantity is 0, the policy will not be adjusted throughout the 30 years until some land users start to produce maize again.
 * and result in a non-zero estimated quantity. At this point, the policy may not be effective anymore because it lost the track of the model's current state.
 * In this stituation, the subsidies for maize production can be either too small or too large.
 * Another solution is to keep tracking the zero-quantity policies and adjusting them as the model state updates.
 * This might sound counterintuitive because in the real world policymakers are highly unlikely to adjust maize subsidies because no one is using them.
 * However, this solution can be interpreted as a one-time re-evaluation of the policy in the real world. Real-world policymakers may re-caculate maize subisdization based on the current model state 
 * once the maize subsideis are needed again. Policymakers might consider many factors to make at least a reasonable amount of subsidies. 
 * So, I think we need to keep track of the zero-quantity policies and adjust them whenever the model state is updated.
 * This solution is therefore more reasonable than the first solution.
 
 */
/*
 * @author yongchao Zeng
 *
 */

public class Institution {

	/** Logger for recording runtime information */
	private static final Logger LOGGER = Logger.getLogger(Institution.class.getName());

	String institutionName;
	/** Stores received time series data for each input variable */
	HashMap<String, ArrayList<Double>> input_data = new HashMap<>();

	/**
	 * Stores the final policy values after all calculations. This is the final
	 * output from Institution.
	 */
	HashMap<String, Double> policyHashMap = new HashMap<>();

	/** Set of all input variable names from policies. */
	HashSet<String> policyInputNameSet = new HashSet<>();

	/** List of all policies loaded from configuration */
	List<Policy> policyList;

	/** Set of all policy names from configuration */
	HashSet<String> policyNameSet = new HashSet<>();

	/** Store received estimated quantities for each policy */
	HashMap<String, Double> estimated_policy_quantity = new HashMap<>();

	/** Time lag used for institution activation */
	int time_lag;

	/** Fuzzy Inference System for policy determination */
	FIS fis;

	/** Total available budget for policy implementation */
	double total_budget = 0;

	int simulation_steps = 0;

	boolean start_zero;

	private boolean budgeting = false;

	/**
	 * Constructor for the institution class
	 * 
	 * @param fcl_path Path to the fuzzy control language file
	 * @param time_lag Time lag parameter for PID error calculation
	 * @throws IOException If files cannot be loaded
	 */
	public Institution(String policy_json_path, String fcl_path, int time_lag, boolean start_zero) throws IOException {
		loadPolicies(policy_json_path);

		fis = FIS.load(fcl_path, true);
		this.time_lag = time_lag;
		this.start_zero = start_zero;
	}

	/**
	 * Constructor for the institution class that takes pre-loaded policies
	 * 
	 * @param institutionName Name of the institution
	 * @param policies        List of policies for this institution
	 * @param fcl_path        Path to the fuzzy control language file
	 * @param time_lag        Time lag parameter for PID error calculation
	 * @param start_zero      Whether to start policy evaluation at step 0
	 * @throws IOException If files cannot be loaded
	 */
	public Institution(String institutionName, List<Policy> policies, String fcl_path, int time_lag, boolean start_zero)
			throws IOException {
		this.institutionName = institutionName;
		this.policyList = policies;

		// Populate sets with policy names and all input variable names
		for (Policy policy : policyList) {
			policyNameSet.add(policy.getPolicyName());
			policyInputNameSet.addAll(policy.getInputNameToGoal().keySet());
			policy.initPolicy(); // Initialize policy fields that are not in the JSON file.
		}

		fis = FIS.load(fcl_path, true);
		this.time_lag = time_lag;
		this.start_zero = start_zero;
	}

	/**
	 * Loads policy configurations from a JSON file and initializes policy-related
	 * data structures
	 * 
	 * @param policy_json_path Path to the JSON file containing policy
	 *                         configurations
	 * @throws IOException If the policy file cannot be read or parsed
	 */
	protected void loadPolicies(String policy_json_path) throws IOException {
		// Load policies from JSON file
		policyList = PolicyLoader.loadPolicies(policy_json_path);

		// Populate sets with policy names and all input variable names, and initialize
		// each policy's gap names based on input variable names.
		for (Policy policy : policyList) {
			policyNameSet.add(policy.getPolicyName());
			policyInputNameSet.addAll(policy.getInputNameToGoal().keySet());
			policy.initPolicy(); // Initialize policy fields that are not in the JSON file.
		}
	}

	/**
	 * Main method to run the institution's decision-making process
	 * 
	 * @param input_data                HashMap of Time series data for the each
	 *                                  input variable
	 * @param received_budget           Total available budget for policy
	 *                                  implementation
	 * @param estimated_policy_quantity Estimated quantities for each policy
	 * @return HashMap containing the final policy values
	 */
	public HashMap<String, Double> step(HashMap<String, ArrayList<Double>> input_data,
			HashMap<String, Double> estimated_policy_quantity, double received_budget) {
		LOGGER.info("Starting institution run with budget: " + received_budget);

		getInputData(input_data, estimated_policy_quantity, received_budget);

		/*
		 * Run policy evaluation and updates every time_lag steps. At step 0, only run
		 * if start_zero is true
		 */
		if (simulation_steps % time_lag == 0) {
			if ((simulation_steps == 0 && start_zero) || (simulation_steps != 0)) {

				evaluateGaps();

				InferPolicyChanges();
				policyList.forEach(policy -> {
					String ansiGreen = "\u001B[32m";
					String ansiReset = "\u001B[0m";
					LOGGER.info(ansiGreen + "Policy " + policy.getPolicyName() + " change: " + policy.getPolicyChange()
							+ ansiReset);
				});

				applyInertia();

				updateModifier();
				policyList.forEach(policy -> {
					String ansiRed = "\u001B[31m";
					String ansiReset = "\u001B[0m";
					LOGGER.info(ansiRed + "Policy " + policy.getPolicyName() + " Modifier: "
							+ policy.getPolicyModifier() + ansiReset);
				});
			}
		}

		updatePolicyBeforeBudgeting();

		estimatePolicyCosts();

		if (budgeting) {
			LOGGER.info("Budgeting");
			budgetAllocation();
		}

		finalizePolicies();

		simulation_steps += 1;

		return policyHashMap;
	}

	public void getInputData(HashMap<String, ArrayList<Double>> input_data,
			HashMap<String, Double> estimated_policy_quantity, double total_budget) {
		Set<String> configInputs = policyInputNameSet;
		Set<String> receivedInputs = input_data.keySet();

		// Check if all required inputs are present
		if (!receivedInputs.containsAll(configInputs)) {
			Set<String> missingInputs = new HashSet<>(configInputs);
			missingInputs.removeAll(receivedInputs);
			throw new IllegalArgumentException("Missing required inputs: " + missingInputs);
		}
		// get only the required inputs
		for (String inputName : configInputs) {
			this.input_data.put(inputName, input_data.get(inputName));
		}

		// Check if all policy names from configuration exist in estimated quantities
		if (!estimated_policy_quantity.keySet().containsAll(policyNameSet)) {
			throw new IllegalArgumentException(
					"Missing required policy quantities in estimated values" + policyNameSet);
		}
		// obtain only the required policies
		for (String policyName : policyNameSet) {
			this.estimated_policy_quantity.put(policyName, estimated_policy_quantity.get(policyName));
		}

		this.total_budget = total_budget;
	}

	/**
	 * Evaluates the gaps between current state and goals for each input variable
	 * Uses PID error calculation for each input
	 */
	protected void evaluateGaps() {

		for (Policy policy : policyList) {
			// Skip policies with zero estimated quantity
			// if(estimated_policy_quantity.get(policy.getPolicyName()) == 0) {
			// continue;
			// }

			// Calculate PID errors for each input using streams
			policy.getInputNameToGoal().forEach((input_name, policy_goal) -> {
				ArrayList<Double> time_series = input_data.get(input_name);
				double pid_error = PIDErrorCalculator.calculateWeightedError(time_series, policy_goal, 0.0, // P
																											// coefficient
						1.0, // I coefficient
						0.0, // D coefficient
						time_lag);
				// This naming convention must be used in the FCL file
				String gapName = input_name + "_gap";
				policy.getGapNameToGap().put(gapName, pid_error);
				System.out.println("| pid_error= " + pid_error + "print Gap:  input_name= " + input_name
						+ "| policy_goal= " + policy_goal + " supply " + time_series.getLast());
			});

		}
	}

	/**
	 * Uses fuzzy logic inference to determine policy changes based on gaps Each
	 * input variable has its own fuzzy logic block
	 */
	protected void InferPolicyChanges() {
		for (Policy policy : policyList) {
			// Skip policies with zero estimated quantity
			// if(estimated_policy_quantity.get(policy.getPolicyName()) == 0) {
			// continue;
			// }
			String fuzzy_block_name = policy.getBlock();

			// Check if fuzzy block exists
			FunctionBlock fuzzy_block = fis.getFunctionBlock(fuzzy_block_name);
			if (fuzzy_block == null) {
				LOGGER.log(Level.SEVERE, "Fuzzy block '" + fuzzy_block_name + "' not found in FCL file");
				throw new IllegalStateException("Fuzzy block '" + fuzzy_block_name + "' not found in FCL file");
			}

			// Set variables and check for existence
			for (Map.Entry<String, Double> entry : policy.getGapNameToGap().entrySet()) {
				String gap_name = entry.getKey();
				Double gap_value = entry.getValue();

				try {
					fuzzy_block.setVariable(gap_name, gap_value);
				} catch (Exception e) {
					LOGGER.log(Level.SEVERE,
							"Variable '" + gap_name + "' not found in fuzzy block '" + fuzzy_block_name + "'", e);
					throw new IllegalStateException(
							"Variable '" + gap_name + "' not found in fuzzy block '" + fuzzy_block_name + "'");
				}
			}

			// Evaluate and get result
			try {
				fuzzy_block.evaluate();
				var interventionVar = fuzzy_block.getVariable("intervention");
				if (interventionVar == null) {
					LOGGER.log(Level.SEVERE,
							"Variable 'intervention' not found in fuzzy block '" + fuzzy_block_name + "'");
					throw new IllegalStateException(
							"Variable 'intervention' not found in fuzzy block '" + fuzzy_block_name + "'");
				}
				double policy_change = interventionVar.getValue();
				policy.setPolicyChange(policy_change);
			} catch (Exception e) {
				LOGGER.log(Level.SEVERE, "Error evaluating fuzzy block '" + fuzzy_block_name + "'", e);
				throw new IllegalStateException(
						"Error evaluating fuzzy block '" + fuzzy_block_name + "': " + e.getMessage());
			}
		}
	}

	/**
	 * Applies inertia constraints to policy changes Limits how quickly policies can
	 * change based on configuration
	 */
	protected void applyInertia() {

		for (Policy policy : policyList) {
			// Skip policies with zero estimated quantity
			// if(estimated_policy_quantity.get(policy.getPolicyName()) == 0) {
			// continue;
			// }

			double lower_bound = policy.getInertiaLow();
			double upper_bound = policy.getInertiaUp();
			double policy_change_raw = policy.getPolicyChange();
			double clamped_policy_change = clamp(policy_change_raw, lower_bound, upper_bound);
			policy.setPolicyChange(clamped_policy_change);
		}
	}

	/**
	 * Updates policy modifiers by adding the new policy changes These modifiers
	 * accumulate over time
	 */
	protected void updateModifier() {
		for (Policy policy : policyList) {
			// Skip policies with zero estimated quantity
			// if(estimated_policy_quantity.get(policy.getPolicyName()) == 0) {
			// continue;
			// }

			double old_value = policy.getPolicyModifier();
			double new_value = old_value + policy.getPolicyChange();
			policy.setPolicyModifier(new_value);
		}
	}

	/**
	 * Calculates actual policy values from modifiers and step sizes Applies bounds
	 * to ensure policies stay within allowed ranges
	 */
	protected void updatePolicyBeforeBudgeting() {

		for (Policy policy : policyList) {

			// If policy has no estimated quantity, set its value to 0 and skip further
			// processing
			if (estimated_policy_quantity.get(policy.getPolicyName()) == 0) {
				policy.setPolicy(0);
				continue;
			}
			double unbounded_policy = policy.getPolicyModifier() * policy.getStepSize();
			double bounded_policy = clamp(unbounded_policy, policy.getPolicyLow(), policy.getPolicyUp());
			policy.setPolicy(bounded_policy);
			/**
			 * //Still have to think more about policy modifier update. // So far, I think
			 * we need to add this. But still need test this. double updated_policy_modifier
			 * = bounded_policy / step_sizes.get(input_name);
			 * policy_modifiers.put(input_name, updated_policy_modifier);
			 */
		}
	}

	/**
	 * Estimates the cost of each policy based on unit costs and quantities
	 * 
	 * @param estimated_policy_quantity Estimated quantities for each policy
	 */
	protected void estimatePolicyCosts() { // comes from model
		for (Policy policy : policyList) {
			double estimated_quantity = estimated_policy_quantity.get(policy.getPolicyName());
			policy.setEstimatedQuantity(estimated_quantity);
			double estimated_cost = policy.getUnitCost() * estimated_quantity;
			policy.setTotalCost(estimated_cost);
		}
	}

	/**
	 * Allocates budget to policies based on optimization Currently a placeholder
	 * for quadratic optimization implementation
	 */
	protected void budgetAllocation() {
		HashMap<String, Double> weights = new HashMap<>();
		HashMap<String, Double> target_budgets = new HashMap<>();
		double constraintValue = total_budget;
		List<Policy> policiesNonZeroCost = new ArrayList<>();

		for (Policy policy : policyList) {
			// If the policy has a non-zero unit cost, add it to the weights and targets.
			// Because we only need to optimize budgets for about policies having costs.
			// This also means you can set policy costs to zero if you don't want to
			// consider budgeting for that policy.
			// If the estimated quantity is zero, we set the policy value to zero, which has
			// already been done in updatePolicyBeforeBudgeting().
			// Skip policies with zero estimated quantity
			if (estimated_policy_quantity.get(policy.getPolicyName()) == 0) {
				continue;
			}

			if (policy.getUnitCost() != 0) {
				weights.put(policy.getPolicyName(), policy.getWeights());
				target_budgets.put(policy.getPolicyName(), policy.getTotalCost());
				policiesNonZeroCost.add(policy);
			}
		}

		// If no policies with costs or no budget, skip optimization
		if (policiesNonZeroCost.isEmpty() || constraintValue <= 0) {
			LOGGER.info("Budget allocation skipped: "
					+ (policiesNonZeroCost.isEmpty() ? "No policies with costs" : "No budget available"));
			return;
		}

		// allocate the budget by solving an optimization problem. Solution is a map of
		// policy names to their acutal budgets
		Map<String, Double> solutions = QuadraticOptimizationSolver.solveUsingKKT(weights, target_budgets,
				constraintValue);

		// Now calculate the actual policy values based on the solution
		policiesNonZeroCost.forEach(policy -> {
			double actual_budget = solutions.get(policy.getPolicyName());
			double doable_policy = actual_budget / policy.getEstimatedQuantity();
			policy.setPolicy(doable_policy);

			// We do not update budget inside the institution, because no one knows how much
			// budget will the environment model use.
			// The user's model should be able to know how much budget will be used.
			// So Institution expects to take budget directly from the user model, allowing
			// for flexibility and accuracy.
			// total_budget -= actual_budget;
		});
	}

	protected void finalizePolicies() {
		for (Policy policy : policyList) {
			double final_policy = policy.getPolicy();
			policyHashMap.put(policy.getPolicyName(), final_policy);
		}
	}

	/**
	 * Utility method to clamp a value between lower and upper bounds
	 * 
	 * @param raw_value   Value to be clamped
	 * @param lower_bound Lower bound
	 * @param upper_bound Upper bound
	 * @return Clamped value
	 */
	protected double clamp(double raw_value, double lower_bound, double upper_bound) {
		double min = Math.min(lower_bound, upper_bound);
		double max = Math.max(lower_bound, upper_bound);

		if (raw_value < min)
			return min;
		if (raw_value > max)
			return max;
		return raw_value;
	}

	public Policy getPolicyByName(String policyName) throws IllegalArgumentException {
		for (Policy policy : policyList) {
			if (policy.getPolicyName().equals(policyName)) {
				return policy;
			}
		}
		return null;
	}

	public void setBudgeting(boolean budgeting) {
		this.budgeting = budgeting;
	}

	public String getName() {
		return institutionName;
	}

	public List<Policy> getPolicies() {
		return policyList;
	}

	@Override
	public String toString() {
		return "Institution [institutionName=" + institutionName + ", input_data=" + input_data + ", policyHashMap="
				+ policyHashMap + ", policyInputNameSet=" + policyInputNameSet + ", policyList=" + policyList
				+ ", policyNameSet=" + policyNameSet + ", estimated_policy_quantity=" + estimated_policy_quantity
				+ ", time_lag=" + time_lag + ", fis=" + fis + ", total_budget=" + total_budget + ", simulation_steps="
				+ simulation_steps + ", start_zero=" + start_zero + ", budgeting=" + budgeting + "]";
	}

}
