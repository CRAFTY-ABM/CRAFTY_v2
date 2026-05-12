package institutions_Fuzzy;
import java.util.HashMap;
import java.util.List;

/*
 * @author yongchao Zeng
 *
 */

public class Policy {
    // Configurable via JSON
    private String policyName;
    private HashMap<String, Double> inputNameToGoal;
    private HashMap<String, Double> gapNameToGap = new HashMap<>();
    private String block;
    private double inertiaLow;
    private double inertiaUp;
    private double unitCost;
    private double estimatedQuantity;
    private double weights;
    private double stepSize;  // also known as "initial guess" or "initial policy"
    private double policyLow;
    private double policyUp;
    // Runtime-only variables (not required in JSON)
    private double policyModifier;
    private double policyChange;
    private double policy;
    private double totalCost;



    public Policy() {
        // Default constructor required for Jackson
    }

    public void initPolicy() {
        policyModifier = 1.0;
        policy = stepSize;
        policyChange = 0.0;
        totalCost = 0.0;
    }

    // Getters & Setters
    public double getTotalCost() { return totalCost; }
    public void setTotalCost(double totalCost) { this.totalCost = totalCost; }

    public HashMap<String, Double> getGapNameToGap() { return gapNameToGap; }
    public void setGapNameToGap(HashMap<String, Double> gapNameToGap) { this.gapNameToGap = gapNameToGap; }

    public String getPolicyName() { return policyName; }
    public void setPolicyName(String policyName) { this.policyName = policyName; }

    public HashMap<String, Double> getInputNameToGoal() { return inputNameToGoal; }
    public void setInputNameToGoal(HashMap<String, Double> inputNameToGoal) { this.inputNameToGoal = inputNameToGoal; } 

    public String getBlock() { return block; }
    public void setBlock(String block) { this.block = block; }

    public double getInertiaLow() { return inertiaLow; }
    public void setInertiaLow(double inertiaLow) { this.inertiaLow = inertiaLow; }

    public double getInertiaUp() { return inertiaUp; }
    public void setInertiaUp(double inertiaUp) { this.inertiaUp = inertiaUp; }

    public double getUnitCost() { return unitCost; }
    public void setUnitCost(double unitCost) { this.unitCost = unitCost; }

    public double getEstimatedQuantity() { return estimatedQuantity; }
    public void setEstimatedQuantity(double estimatedQuantity) { this.estimatedQuantity = estimatedQuantity; }

    public double getWeights() { return weights; }
    public void setWeights(double weights) { this.weights = weights; }

    public double getStepSize() { return stepSize; }
    public void setStepSize(double stepSize) { this.stepSize = stepSize; }

    public double getPolicyLow() { return policyLow; }
    public void setPolicyLow(double policyLow) { this.policyLow = policyLow; }

    public double getPolicyUp() { return policyUp; }
    public void setPolicyUp(double policyUp) { this.policyUp = policyUp; }

    // Runtime fields
    
    public double getPolicyModifier() { return policyModifier; }
    public void setPolicyModifier(double policyModifier) { this.policyModifier = policyModifier; }

    public double getPolicyChange() { return policyChange; }
    public void setPolicyChange(double policyChange) { this.policyChange = policyChange; }

    public double getPolicy() { return policy; }
    public void setPolicy(double policy) { this.policy = policy; }

}
