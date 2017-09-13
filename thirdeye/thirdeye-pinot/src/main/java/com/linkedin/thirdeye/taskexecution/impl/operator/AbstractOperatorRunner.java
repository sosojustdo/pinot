package com.linkedin.thirdeye.taskexecution.impl.operator;

import com.linkedin.thirdeye.taskexecution.dataflow.reader.Reader;
import com.linkedin.thirdeye.taskexecution.impl.physicaldag.FrameworkNode;
import com.linkedin.thirdeye.taskexecution.dag.NodeIdentifier;
import com.linkedin.thirdeye.taskexecution.impl.physicaldag.ExecutionStatus;
import com.linkedin.thirdeye.taskexecution.impl.physicaldag.NodeConfig;
import com.linkedin.thirdeye.taskexecution.operator.Operator;
import com.linkedin.thirdeye.taskexecution.operator.OperatorConfig;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractOperatorRunner extends FrameworkNode {
  private static final Logger LOG = LoggerFactory.getLogger(AbstractOperatorRunner.class);

  private FrameworkNode logicalNode;
  private Map<NodeIdentifier, Reader> incomingReaderMap = new HashMap<>();
  protected ExecutionStatus executionStatus = ExecutionStatus.RUNNING;

  public AbstractOperatorRunner(NodeIdentifier nodeIdentifier, NodeConfig nodeConfig, Class operatorClass,
      FrameworkNode logicalNode) {
    super(nodeIdentifier, nodeConfig, operatorClass);
    this.logicalNode = logicalNode;
  }

  public void addInput(NodeIdentifier nodeIdentifier, Reader reader) {
    incomingReaderMap.put(nodeIdentifier, reader);
  }

  public Map<NodeIdentifier, Reader> getIncomingReaderMap() {
    return incomingReaderMap;
  }

  @Override
  public FrameworkNode getLogicalNode() {
    return logicalNode;
  }

  @Override
  public Collection<FrameworkNode> getPhysicalNode() {
    return Collections.emptyList();
  }

  @Override
  public ExecutionStatus getExecutionStatus() {
    return executionStatus;
  }

  protected void setFailure(Exception e) {
    LOG.error("Failed to execute node: {}.", nodeIdentifier, e);
    if (nodeConfig.skipAtFailure()) {
      executionStatus = ExecutionStatus.SKIPPED;
    } else {
      executionStatus = ExecutionStatus.FAILED;
    }
  }

  // TODO: Implement this method
  static OperatorConfig convertNodeConfigToOperatorConfig(NodeConfig nodeConfig) {
    return null;
  }

  static Operator initializeOperator(Class operatorClass, OperatorConfig operatorConfig)
      throws IllegalAccessException, InstantiationException {
    try {
      Operator operator = (Operator) operatorClass.newInstance();
      operator.initialize(operatorConfig);
      return operator;
    } catch (Exception e) {
      // We cannot do anything if something bad happens here excepting rethrow the exception.
      LOG.warn("Failed to initialize {}", operatorClass.getName());
      throw e;
    }
  }
}