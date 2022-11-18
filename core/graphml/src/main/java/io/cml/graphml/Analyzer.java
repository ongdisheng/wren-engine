/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.cml.graphml;

import io.trino.sql.tree.Node;
import io.trino.sql.tree.QualifiedName;
import io.trino.sql.tree.Statement;
import io.trino.sql.tree.Table;

import java.util.HashSet;
import java.util.Set;

import static java.util.Objects.requireNonNull;

public final class Analyzer
{
    private Analyzer() {}

    public static Analysis analyze(Statement statement)
    {
        Analysis analysis = new Analysis();
        new Visitor(analysis).process(statement);
        return analysis;
    }

    public static class Analysis
    {
        private final Set<QualifiedName> tables = new HashSet<>();

        private void addTable(QualifiedName tableName)
        {
            tables.add(tableName);
        }

        public Set<QualifiedName> getTables()
        {
            return Set.copyOf(tables);
        }
    }

    private static class Visitor
            extends BaseVisitor
    {
        private final Analysis analysis;

        public Visitor(Analysis analysis)
        {
            this.analysis = requireNonNull(analysis, "analysis is null");
        }

        @Override
        protected Node visitTable(Table node, Void context)
        {
            analysis.addTable(node.getName());
            return super.visitTable(node, context);
        }
    }
}