package com.linkedin.thirdeye.taskexecution.impl.dag;

import com.linkedin.thirdeye.taskexecution.dag.DAG;
import com.linkedin.thirdeye.taskexecution.dag.NodeIdentifier;
import com.linkedin.thirdeye.taskexecution.dataflow.ExecutionResult;
import com.linkedin.thirdeye.taskexecution.dataflow.ExecutionResults;
import com.linkedin.thirdeye.taskexecution.dataflow.ExecutionResultsReader;
import com.linkedin.thirdeye.taskexecution.processor.Processor;
import com.linkedin.thirdeye.taskexecution.processor.ProcessorConfig;
import com.linkedin.thirdeye.taskexecution.processor.ProcessorContext;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;


public class DAGExecutorBasicTest {
  private static final Logger LOG = LoggerFactory.getLogger(DAGExecutorBasicTest.class);
  private ExecutorService threadPool = Executors.newFixedThreadPool(10);
  private static final String EXECUTION_LOG_KEY = "";

  /**
   * DAG: root
   */
  @Test
  public void testOneNodeExecution() {
    DAG<LogicalNode> dag = new LogicalPlan();
    LogicalNode root = new LogicalNode("root", LogProcessor.class);
    dag.addNode(root);

    DAGExecutor<LogicalNode> dagExecutor = new DAGExecutor<>(threadPool);
    DAGConfig dagConfig = new DAGConfig();
    dagConfig.setStopAtFailure(true);
    dagExecutor.execute(dag, dagConfig);

    List<String> executionLog = checkAndGetFinalResult(dagExecutor.getNode(root.getIdentifier()));
    List<String> expectedLog = new ArrayList<String>() {{
      add("root");
    }};
    Assert.assertEquals(executionLog, expectedLog);
  }

  /**
   * DAG: 1 -> 2 -> 3
   */
  @Test
  public void testOneNodeChainExecution() {
    DAG<LogicalNode> dag = new LogicalPlan();
    LogicalNode node1 = new LogicalNode("1", LogProcessor.class);
    LogicalNode node2 = new LogicalNode("2", LogProcessor.class);
    LogicalNode node3 = new LogicalNode("3", LogProcessor.class);
    dag.addEdge(node1, node2);
    dag.addEdge(node2, node3);

    DAGExecutor<LogicalNode> dagExecutor = new DAGExecutor<>(threadPool);
    dagExecutor.execute(dag, new DAGConfig());

    // TODO: Check dagExecutor's execution status

    List<String> executionLog = checkAndGetFinalResult(dagExecutor.getNode(node3.getIdentifier()));
    List<String> expectedResult = new ArrayList<String>() {{
      add("1");
      add("2");
      add("3");
    }};
    List<List<String>> expectedResults = new ArrayList<>();
    expectedResults.add(expectedResult);

    Assert.assertTrue(checkLinearizability(executionLog, expectedResults));
  }

  /**
   * DAG:
   *     root1 -> node12 -> leaf1
   *
   *     root2 -> node22 ---> node23 ----> leaf2
   *                     \             /
   *                      \-> node24 -/
   */
  @Test
  public void testTwoNodeChainsExecution() {
    DAG<LogicalNode> dag = new LogicalPlan();
    LogicalNode node11 = new LogicalNode("root1", LogProcessor.class);
    LogicalNode node12 = new LogicalNode("node12", LogProcessor.class);
    LogicalNode leaf1 = new LogicalNode("leaf1", LogProcessor.class);
    dag.addEdge(node11, node12);
    dag.addEdge(node12, leaf1);

    LogicalNode node21 = new LogicalNode("root2", LogProcessor.class);
    LogicalNode node22 = new LogicalNode("node22", LogProcessor.class);
    LogicalNode node23 = new LogicalNode("node23", LogProcessor.class);
    LogicalNode node24 = new LogicalNode("node24", LogProcessor.class);
    LogicalNode leaf2 = new LogicalNode("leaf2", LogProcessor.class);
    dag.addEdge(node21, node22);
    dag.addEdge(node22, node23);
    dag.addEdge(node23, leaf2);
    dag.addEdge(node22, node24);
    dag.addEdge(node24, leaf2);

    DAGExecutor<LogicalNode> dagExecutor = new DAGExecutor<>(threadPool);
    dagExecutor.execute(dag, new DAGConfig());

    // Check path 1
    {
      List<String> executionLog = checkAndGetFinalResult(dagExecutor.getNode(leaf1.getIdentifier()));
      List<String> expectedResult = new ArrayList<String>() {{
        add("root1");
        add("node12");
        add("leaf1");
      }};
      List<List<String>> expectedResults = new ArrayList<>();
      expectedResults.add(expectedResult);
      Assert.assertTrue(checkLinearizability(executionLog, expectedResults));
    }
    // Check path 2
    {
      List<String> executionLog = checkAndGetFinalResult(dagExecutor.getNode(leaf2.getIdentifier()));
      List<String> expectedResult1 = new ArrayList<String>() {{
        add("root2");
        add("node22");
        add("node23");
        add("leaf2");
      }};
      List<String> expectedResult2 = new ArrayList<String>() {{
        add("root2");
        add("node22");
        add("node24");
        add("leaf2");
      }};
      List<List<String>> expectedResults = new ArrayList<>();
      expectedResults.add(expectedResult1);
      expectedResults.add(expectedResult2);
      Assert.assertTrue(checkLinearizability(executionLog, expectedResults));
    }
  }

  /**
   * DAG:
   *           /---------> 12 -------------\
   *         /                              \
   *       /    /---------> 23 ------------\ \
   * root -> 22                           -----> leaf
   *            \-> 24 --> 25 -----> 27 -/
   *                   \         /
   *                    \-> 26 -/
   */
  @Test
  public void testComplexGraphExecution() {
    DAG<LogicalNode> dag = new LogicalPlan();
    LogicalNode root = new LogicalNode("root", LogProcessor.class);
    LogicalNode leaf = new LogicalNode("leaf", LogProcessor.class);

    // sub-path 2
    LogicalNode node22 = new LogicalNode("22", LogProcessor.class);
    LogicalNode node23 = new LogicalNode("23", LogProcessor.class);
    LogicalNode node24 = new LogicalNode("24", LogProcessor.class);
    LogicalNode node25 = new LogicalNode("25", LogProcessor.class);
    LogicalNode node26 = new LogicalNode("26", LogProcessor.class);
    LogicalNode node27 = new LogicalNode("27", LogProcessor.class);
    dag.addEdge(root, node22);
    dag.addEdge(node22, node23);
    dag.addEdge(node22, node24);
    dag.addEdge(node24, node25);
    dag.addEdge(node24, node26);
    dag.addEdge(node25, node27);
    dag.addEdge(node26, node27);
    dag.addEdge(node23, leaf);
    dag.addEdge(node27, leaf);

    // sub-path 1
    LogicalNode node12 = new LogicalNode("12", LogProcessor.class);
    dag.addEdge(root, node12);
    dag.addEdge(node12, leaf);

    DAGExecutor<LogicalNode> dagExecutor = new DAGExecutor<>(threadPool);
    dagExecutor.execute(dag, new DAGConfig());

    List<String> executionLog = checkAndGetFinalResult(dagExecutor.getNode(leaf.getIdentifier()));
    List<String> expectedOrder1 = new ArrayList<String>() {{
      add("root");
      add("12");
      add("leaf");
    }};
    List<String> expectedOrder2 = new ArrayList<String>() {{
      add("root");
      add("22");
      add("23");
      add("leaf");
    }};
    List<String> expectedOrder3 = new ArrayList<String>() {{
      add("root");
      add("22");
      add("24");
      add("25");
      add("27");
      add("leaf");
    }};
    List<String> expectedOrder4 = new ArrayList<String>() {{
      add("root");
      add("22");
      add("24");
      add("26");
      add("27");
      add("leaf");
    }};
    List<List<String>> expectedOrders = new ArrayList<>();
    expectedOrders.add(expectedOrder1);
    expectedOrders.add(expectedOrder2);
    expectedOrders.add(expectedOrder3);
    expectedOrders.add(expectedOrder4);
    Assert.assertTrue(checkLinearizability(executionLog, expectedOrders));
  }

  /**
   * DAG: 1 -> 2 -> 3
   */
  @Test
  public void testFailedChainExecution() {
    DAG<LogicalNode> dag = new LogicalPlan();
    LogicalNode node1 = new LogicalNode("1", LogProcessor.class);
    LogicalNode node2 = new LogicalNode("2", FailedProcessor.class);
    LogicalNode node3 = new LogicalNode("3", LogProcessor.class);
    dag.addEdge(node1, node2);
    dag.addEdge(node2, node3);

    DAGConfig dagConfig = new DAGConfig();
    dagConfig.setStopAtFailure(false);
    DAGExecutor<LogicalNode> dagExecutor = new DAGExecutor<>(threadPool);
    dagExecutor.execute(dag, dagConfig);

    List<String> executionLog = checkAndGetFinalResult(dagExecutor.getNode(node3.getIdentifier()));
    List<String> expectedResult = new ArrayList<String>() {{
      add("3");
    }};
    List<List<String>> expectedResults = new ArrayList<>();
    expectedResults.add(expectedResult);

    Assert.assertTrue(checkLinearizability(executionLog, expectedResults));
  }

  /**
   * An operator that appends node name to a list, which is passed in from its incoming nodes.
   */
  public static class LogProcessor implements Processor {
    private static final Logger LOG = LoggerFactory.getLogger(LogProcessor.class);

    @Override
    public void initialize(ProcessorConfig processorConfig) {
    }

    @Override
    public ExecutionResult run(ProcessorContext processorContext) {
      LOG.info("Running node: {}", processorContext.getNodeIdentifier().getName());
      Map<NodeIdentifier, ExecutionResults> inputs = processorContext.getInputs();
      List<String> executionLog = new ArrayList<>();
      for (ExecutionResults parentResult : inputs.values()) {
        Object result = parentResult.getResult(EXECUTION_LOG_KEY).result();
        if (result instanceof List) {
          List<String> list = (List<String>) result;
          for (String s : list) {
            if (!executionLog.contains(s)) {
              executionLog.add(s);
            }
          }
        }
      }
      executionLog.add(processorContext.getNodeIdentifier().getName());
      ExecutionResult operatorResult = new ExecutionResult();
      operatorResult.setResult(EXECUTION_LOG_KEY, executionLog);
      return operatorResult;
    }
  }

  /**
   * An operator that always fails.
   */
  public static class FailedProcessor implements Processor {
    @Override
    public void initialize(ProcessorConfig processorConfig) {
    }

    @Override
    public ExecutionResult run(ProcessorContext processorContext) {
      throw new UnsupportedOperationException("Failed in purpose.");
    }
  }

  /**
   * Returns the execution log of the given the node.
   *
   * @param node the last node in the DAG.
   *
   * @return the final execution log of the DAG.
   */
  static List<String> checkAndGetFinalResult(LogicalNode node) {
    ExecutionResultsReader executionResultsReader = node.getExecutionResultsReader();
    Assert.assertNotNull(executionResultsReader);
    Assert.assertTrue(executionResultsReader.hasNext());

    ExecutionResult finalResult = executionResultsReader.next();
    Assert.assertNotNull(finalResult);
    Assert.assertNotNull(finalResult.result());

    List<String> result = (List<String>) finalResult.result();
    Assert.assertNotNull(result);
    return result;
  }

  /**
   * Utility method to check if the given execution log, which is a partially-ordered execution that is logged as a
   * sequence of execution, satisfies the expected linearizations, which is given by multiple totally-ordered
   * sub-executions.
   *
   * For example, this DAG execution:
   *
   *     root2 -> node22 ---> node23 ----> leaf2
   *                     \             /
   *                      \-> node24 -/
   *
   * contains two totally-ordered sub-executions:
   *     1. root2 -> node22 -> node23 -> leaf2, and
   *     2. root2 -> node22 -> node24 -> leaf2.
   *
   * Therefore, the input (i.e., executionLog) could be:
   *     1. root2 -> node22 -> node23 -> node24 -> leaf2 or
   *     2. root2 -> node22 -> node24 -> node23 -> leaf2 (node24 is swapped with node23).
   * The expected orders are the two totally-ordered sub-executions.
   *
   * This method checks if the execution order in the log contains all sub-execution in the expected orders.
   *
   * @param executionLog the execution log, which could be a partially-ordered execution.
   *
   * @param expectedOrders the expected sub-executions, which are totally-ordered executions.
   * @return true if the execution log satisfies the expected linearizations.
   */
  private boolean checkLinearizability(List<String> executionLog, List<List<String>> expectedOrders) {
    Set<String> checkedNodes = new HashSet<>();
    for (int i = 0; i < expectedOrders.size(); i++) {
      List<String> expectedOrder = expectedOrders.get(i);
      if (expectedOrder.size() > executionLog.size()) {
        throw new IllegalArgumentException("Execution log is shorter than the " + i + "th expected order.");
      }
      int expectedIdx = 0;
      int logIdx = 0;
      while (expectedIdx < expectedOrder.size() && logIdx < executionLog.size()) {
        if (expectedOrder.get(expectedIdx).equals(executionLog.get(logIdx))) {
          checkedNodes.add(expectedOrder.get(expectedIdx));
          ++expectedIdx;
        }
        ++logIdx;
      }
      if (expectedIdx < expectedOrder.size()-1) {
        LOG.warn("The location {} in the {}th expected order is not found or out of order.", expectedIdx, i);
        LOG.warn("The expected order: {}", expectedOrder.toString());
        ArrayList<String> subExecutionLog = new ArrayList<>();
        for (String s : executionLog) {
          if (expectedOrder.contains(s)) {
            subExecutionLog.add(s);
          }
        }
        LOG.warn("The execution log: {}", subExecutionLog.toString());
        return false;
      }
    }
    if (checkedNodes.size() < executionLog.size()) {
      LOG.warn("Execution log contains more nodes than expected logs.");
      LOG.warn("Num nodes in expected log: {}, execution log: {}", checkedNodes.size(), executionLog.size());
      Iterator<String> ite = executionLog.iterator();
      while(ite.hasNext()) {
        String s = ite.next();
        if (checkedNodes.contains(s)) {
          checkedNodes.remove(s);
          ite.remove();
        }
      }
      LOG.warn("Additional nodes in execution log: {}", executionLog.toString());
      return false;
    }
    return true;
  }

  /// The followings are tests for the method {@link checkLinearizability}.
  @Test(dataProvider = "ExpectedOrders")
  public void testCheckLegalLinearizability(List<List<String>> expectedOrders) {
    List<String> executionLog = new ArrayList<String>() {{
      add("1");
      add("4");
      add("2");
      add("5");
      add("3");
    }};

    Assert.assertTrue(checkLinearizability(executionLog, expectedOrders));
  }

  @Test(dataProvider = "ExpectedOrders")
  public void testCheckIllegalLinearizability(List<List<String>> expectedOrders) {
    List<String> executionLog = new ArrayList<String>() {{
      add("1");
      add("5");
      add("2");
      add("4");
      add("3");
    }};

    Assert.assertFalse(checkLinearizability(executionLog, expectedOrders));
  }

  @Test(dataProvider = "ExpectedOrders")
  public void testCheckNonExistNodeLinearizability(List<List<String>> expectedOrders) {
    List<String> executionLog = new ArrayList<String>() {{
      add("1");
      add("4");
      add("5");
      add("2");
      add("5");
      add("3");
      add("6");
    }};

    Assert.assertFalse(checkLinearizability(executionLog, expectedOrders));
  }

  @DataProvider(name = "ExpectedOrders")
  public static Object[][] expectedOrders() {
    List<List<String>> expectedOrders = new ArrayList<>();
    List<String> expectedOrder1 = new ArrayList<String>() {{
      add("1");
      add("2");
      add("3");
    }};
    List<String> expectedOrder2 = new ArrayList<String>() {{
      add("1");
      add("4");
      add("5");
      add("3");
    }};
    expectedOrders.add(expectedOrder1);
    expectedOrders.add(expectedOrder2);

    return new Object[][] { { expectedOrders } };
  }
}
