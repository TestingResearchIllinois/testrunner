package edu.illinois.diaper;

public class NoOpStateCapture implements IStateCapture {

    private String testName;

    public NoOpStateCapture(String entityFQN) {
        this.testName = entityFQN;
    }
    public void runCapture() {
        //System.out.println("Running NoOp State Capture on: " + this.testName);
    }


}
