package org.apache.calcite.adapter.jdbc;

import java.util.ArrayList;
import java.util.List;

import org.apache.calcite.adapter.jdbc.tools.JdbcRelBuilder;
import org.apache.calcite.adapter.jdbc.tools.JdbcRelBuilderFactory;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.TableModify.Operation;
import org.apache.calcite.rel.logical.LogicalTableModify;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;

public class JournalledDeleteRule extends AbstractForcedRule {

	public JournalledDeleteRule() {
		super(Operation.DELETE);
	}

	@Override
	public RelNode doApply(LogicalTableModify tableModify, JournalledJdbcTable journalTable,
			JdbcRelBuilderFactory relBuilderFactory) {

		JdbcRelBuilder relBuilder = relBuilderFactory.create(
				tableModify.getCluster(),
				tableModify.getTable().getRelOptSchema()
		);
		relBuilder.push(tableModify.getInput());

		List<String> columnNames = new ArrayList<>();
		List<RexNode> sources = new ArrayList<>();
		for (RexNode n : relBuilder.fields()) {
			sources.add(n);
			columnNames.add(null);
		}

		RexNode newVersion;
		switch(journalTable.getVersionType()) {
			case TIMESTAMP:
				// TODO: would be nice if possible to let Version be set to default, and set SubsequentVersion to equal Version
				// (would allow arbitrary version column types, not tied to timestamp)
				newVersion = relBuilder.call(SqlStdOperatorTable.CURRENT_TIMESTAMP);
				break;
			case BIGINT:
				newVersion = relBuilder.call(SqlStdOperatorTable.PLUS, relBuilder.field(journalTable.getVersionField()), relBuilder.literal(1));
				break;
			default:
				throw new UnsupportedOperationException();
		}
		sources.add(newVersion);
		sources.add(newVersion);
		columnNames.add(journalTable.getVersionField());
		columnNames.add(journalTable.getSubsequentVersionField());

		relBuilder.project(sources, columnNames);

		relBuilder.insertCopying(
				tableModify,
				journalTable.getJournalTable()
		);

		return relBuilder.build();
	}
}
