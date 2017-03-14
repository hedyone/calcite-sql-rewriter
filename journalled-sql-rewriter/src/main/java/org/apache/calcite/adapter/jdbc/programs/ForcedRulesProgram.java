package org.apache.calcite.adapter.jdbc.programs;

import java.util.List;

import org.apache.calcite.adapter.jdbc.tools.JdbcRelBuilderFactory;
import org.apache.calcite.adapter.jdbc.tools.JdbcRelBuilderFactoryFactory;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.plan.RelTraitSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.tools.Program;
import org.apache.calcite.util.Litmus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ForcedRulesProgram implements Program {
	private static final Logger logger = LoggerFactory.getLogger(ForcedRulesProgram.class);

	private final JdbcRelBuilderFactoryFactory relBuilderFactoryFactory;
	private final ForcedRule[] rules;

	public ForcedRulesProgram(JdbcRelBuilderFactoryFactory relBuilderFactoryFactory, ForcedRule... rules) {
		this.relBuilderFactoryFactory = relBuilderFactoryFactory;
		this.rules = rules;
	}

	@Override
	public RelNode run(
			RelOptPlanner planner,
			RelNode rel,
			RelTraitSet requiredOutputTraits
	) {
		logger.debug("Running forced rules on:\n" + RelOptUtil.toString(rel));
		return replace(rel, rules, relBuilderFactoryFactory.create(planner.getContext()));
	}

	private RelNode replace(RelNode original, ForcedRule[] rules, JdbcRelBuilderFactory relBuilderFactory) {
		RelNode p = original;
		for (ForcedRule rule : rules) {
			RelNode updated = rule.apply(p, relBuilderFactory);
			if (updated != null) {
				logger.trace("Rule: " + rule.toString() +
						"\nReplacing:\n" + RelOptUtil.toString(p) +
						"\nWith:\n" + RelOptUtil.toString(updated)
				);
				// Must maintain row types so that nothing explodes
				RelOptUtil.equal(
						"RowType of original", p.getRowType(),
						"RowType of replaced", updated.getRowType(),
						Litmus.THROW
				);
				p = updated;
				break;
			}
		}

		List<RelNode> oldInputs = p.getInputs();
		for (int i = 0; i < oldInputs.size(); i++) {
			RelNode originalInput = oldInputs.get(i);
			RelNode replacedInput = replace(originalInput, rules, relBuilderFactory);
			if (replacedInput != originalInput) {
				p.replaceInput(i, replacedInput);
			}
		}
		return p;
	}
}