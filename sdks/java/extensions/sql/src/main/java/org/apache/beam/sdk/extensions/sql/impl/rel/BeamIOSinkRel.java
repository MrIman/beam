/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.beam.sdk.extensions.sql.impl.rel;

import java.util.List;
import org.apache.beam.sdk.extensions.sql.BeamSqlTable;
import org.apache.beam.sdk.extensions.sql.impl.rule.BeamIOSinkRule;
import org.apache.beam.sdk.transforms.PTransform;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PInput;
import org.apache.beam.sdk.values.Row;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.prepare.Prepare;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.TableModify;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql2rel.RelStructuredTypeFlattener;

/** BeamRelNode to replace a {@code TableModify} node. */
public class BeamIOSinkRel extends TableModify
    implements BeamRelNode, RelStructuredTypeFlattener.SelfFlatteningRel {

  private final BeamSqlTable sqlTable;
  private boolean isFlattening = false;

  public BeamIOSinkRel(
      RelOptCluster cluster,
      RelOptTable table,
      Prepare.CatalogReader catalogReader,
      RelNode child,
      Operation operation,
      List<String> updateColumnList,
      List<RexNode> sourceExpressionList,
      boolean flattened,
      BeamSqlTable sqlTable) {
    super(
        cluster,
        cluster.traitSetOf(BeamLogicalConvention.INSTANCE),
        table,
        catalogReader,
        child,
        operation,
        updateColumnList,
        sourceExpressionList,
        flattened);
    this.sqlTable = sqlTable;
  }

  @Override
  public RelNode copy(RelTraitSet traitSet, List<RelNode> inputs) {
    boolean flattened = isFlattened() || isFlattening;
    BeamIOSinkRel newRel =
        new BeamIOSinkRel(
            getCluster(),
            getTable(),
            getCatalogReader(),
            sole(inputs),
            getOperation(),
            getUpdateColumnList(),
            getSourceExpressionList(),
            flattened,
            sqlTable);
    newRel.traitSet = traitSet;
    return newRel;
  }

  @Override
  public void flattenRel(RelStructuredTypeFlattener flattener) {
    // rewriteGeneric calls this.copy. Setting isFlattining passes
    // this context into copy for modification of the flattened flag.
    isFlattening = true;
    flattener.rewriteGeneric(this);
    isFlattening = false;
  }

  @Override
  public void register(RelOptPlanner planner) {
    planner.addRule(BeamIOSinkRule.INSTANCE);
    super.register(planner);
  }

  @Override
  public PTransform<PInput, PCollection<Row>> buildPTransform() {
    return new Transform();
  }

  private class Transform extends PTransform<PInput, PCollection<Row>> {

    @Override
    public PCollection<Row> expand(PInput pinput) {
      PCollection<Row> input = (PCollection<Row>) pinput;

      sqlTable.buildIOWriter(input);

      return input;
    }
  }
}
