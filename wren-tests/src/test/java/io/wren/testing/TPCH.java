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

package io.wren.testing;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Resources;
import io.wren.base.WrenTypes;
import io.wren.base.dto.Column;
import io.wren.base.dto.Model;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.lang.String.format;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.ENGLISH;
import static java.util.Objects.requireNonNull;

public class TPCH
{
    public static final TPCH INSTANCE = new TPCH();

    public enum Table
    {
        ORDERS,
        LINEITEM,
        CUSTOMER,
        NATION,
        REGION,
        PART,
        SUPPLIER,
        PARTSUPP
    }

    private final List<String> queries;
    private final Map<Table, String> paths;
    private final List<Model> models;

    private TPCH()
    {
        queries = buildQueries();

        paths = getTablePaths();

        models = buildModels();
    }

    public static List<Model> getModels(String tablePrefix)
    {
        return INSTANCE.models.stream()
                .map(m -> Model.model(
                        m.getName(),
                        m.getRefSql().replace("tablePrefix", tablePrefix),
                        m.getColumns(),
                        m.getPrimaryKey()))
                .collect(toImmutableList());
    }

    public static List<String> getQueries()
    {
        return INSTANCE.queries;
    }

    public static String getDataPath(Table table)
    {
        return INSTANCE.paths.get(table);
    }

    private List<String> buildQueries()
    {
        return IntStream.range(1, 23)
                .filter(i -> i != 15) // views not supported
                .mapToObj(this::getTpchQuery)
                .collect(toImmutableList());
    }

    private String getTpchQuery(int q)
    {
        return readResource("tpch/queries/" + q + ".sql");
    }

    private static String readResource(String name)
    {
        try {
            return Resources.toString(Resources.getResource(name), UTF_8);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Map<Table, String> getTablePaths()
    {
        return Arrays.stream(Table.values())
                .collect(toImmutableMap(Function.identity(), this::getTablePath));
    }

    private String getTablePath(Table table)
    {
        return requireNonNull(TPCH.class.getClassLoader().getResource(format("tpch/data/%s.parquet", table.name().toLowerCase(ENGLISH)))).getPath();
    }

    private List<Model> buildModels()
    {
        return ImmutableList.<Model>builder()
                .add(getOrders())
                .add(getLineItem())
                .add(getCustomer())
                .add(getNation())
                .add(getRegion())
                .add(getPart())
                .add(getSupplier())
                .add(getPartSupp())
                .build();
    }

    private Model getOrders()
    {
        return Model.model("orders",
                "select * from tablePrefix.orders",
                List.of(
                        Column.column("o_orderkey", WrenTypes.INTEGER, null, false, "o_orderkey"),
                        Column.column("o_custkey", WrenTypes.INTEGER, null, false, "o_custkey"),
                        Column.column("o_orderstatus", WrenTypes.VARCHAR, null, false, "o_orderstatus"),
                        Column.column("o_totalprice", WrenTypes.INTEGER, null, false, "o_totalprice"),
                        Column.column("o_orderdate", WrenTypes.DATE, null, false, "o_orderdate"),
                        Column.column("o_orderpriority", WrenTypes.VARCHAR, null, false, "o_orderpriority"),
                        Column.column("o_clerk", WrenTypes.VARCHAR, null, false, "o_clerk"),
                        Column.column("o_shippriority", WrenTypes.INTEGER, null, false, "o_shippriority"),
                        Column.column("o_comment", WrenTypes.VARCHAR, null, false, "o_comment")),
                "o_orderkey");
    }

    private Model getLineItem()
    {
        return Model.model("lineitem",
                "select * from tablePrefix.lineitem",
                List.of(
                        Column.column("l_orderkey", WrenTypes.INTEGER, null, false, "l_orderkey"),
                        Column.column("l_partkey", WrenTypes.INTEGER, null, false, "l_partkey"),
                        Column.column("l_suppkey", WrenTypes.INTEGER, null, false, "l_suppkey"),
                        Column.column("l_linenumber", WrenTypes.INTEGER, null, false, "l_linenumber"),
                        Column.column("l_quantity", WrenTypes.INTEGER, null, false, "l_quantity"),
                        Column.column("l_extendedprice", WrenTypes.INTEGER, null, false, "l_extendedprice"),
                        Column.column("l_discount", WrenTypes.INTEGER, null, false, "l_discount"),
                        Column.column("l_tax", WrenTypes.INTEGER, null, false, "l_tax"),
                        Column.column("l_returnflag", WrenTypes.VARCHAR, null, false, "l_returnflag"),
                        Column.column("l_linestatus", WrenTypes.VARCHAR, null, false, "l_linestatus"),
                        Column.column("l_shipdate", WrenTypes.DATE, null, false, "l_shipdate"),
                        Column.column("l_commitdate", WrenTypes.DATE, null, false, "l_commitdate"),
                        Column.column("l_receiptdate", WrenTypes.DATE, null, false, "l_receiptdate"),
                        Column.column("l_shipinstruct", WrenTypes.VARCHAR, null, false, "l_shipinstruct"),
                        Column.column("l_shipmode", WrenTypes.VARCHAR, null, false, "l_shipmode"),
                        Column.column("l_comment", WrenTypes.VARCHAR, null, false, "l_comment"),
                        Column.column("orderkey_linenumber", WrenTypes.VARCHAR, null, true, "concat(l_orderkey, '-', l_linenumber)")),
                "orderkey_linenumber");
    }

    private Model getCustomer()
    {
        return Model.model("customer",
                "select * from tablePrefix.customer",
                List.of(
                        Column.column("c_custkey", WrenTypes.INTEGER, null, false, "c_custkey"),
                        Column.column("c_name", WrenTypes.VARCHAR, null, false, "c_name"),
                        Column.column("c_address", WrenTypes.VARCHAR, null, false, "c_address"),
                        Column.column("c_nationkey", WrenTypes.INTEGER, null, false, "c_nationkey"),
                        Column.column("c_phone", WrenTypes.VARCHAR, null, false, "c_phone"),
                        Column.column("c_acctbal", WrenTypes.DOUBLE, null, false, "c_acctbal"),
                        Column.column("c_mktsegment", WrenTypes.VARCHAR, null, false, "c_mktsegment"),
                        Column.column("c_comment", WrenTypes.VARCHAR, null, false, "c_comment")),
                "c_custkey");
    }

    private Model getNation()
    {
        return Model.model("nation",
                "select * from tablePrefix.nation",
                List.of(
                        Column.column("n_nationkey", WrenTypes.INTEGER, null, false, "n_nationkey"),
                        Column.column("n_name", WrenTypes.VARCHAR, null, false, "n_name"),
                        Column.column("n_regionkey", WrenTypes.INTEGER, null, false, "n_regionkey"),
                        Column.column("n_comment", WrenTypes.VARCHAR, null, false, "n_comment")),
                "n_nationkey");
    }

    private Model getRegion()
    {
        return Model.model("region",
                "select * from tablePrefix.region",
                List.of(
                        Column.column("r_regionkey", WrenTypes.INTEGER, null, false, "r_regionkey"),
                        Column.column("r_name", WrenTypes.VARCHAR, null, false, "r_name"),
                        Column.column("r_comment", WrenTypes.VARCHAR, null, false, "r_comment")),
                "r_regionkey");
    }

    private Model getPart()
    {
        return Model.model("part",
                "select * from tablePrefix.part",
                List.of(
                        Column.column("p_partkey", WrenTypes.INTEGER, null, false, "p_partkey"),
                        Column.column("p_name", WrenTypes.VARCHAR, null, false, "p_name"),
                        Column.column("p_mfgr", WrenTypes.VARCHAR, null, false, "p_mfgr"),
                        Column.column("p_brand", WrenTypes.VARCHAR, null, false, "p_brand"),
                        Column.column("p_type", WrenTypes.VARCHAR, null, false, "p_type"),
                        Column.column("p_size", WrenTypes.INTEGER, null, false, "p_size"),
                        Column.column("p_container", WrenTypes.VARCHAR, null, false, "p_container"),
                        Column.column("p_retailprice", WrenTypes.DOUBLE, null, false, "p_retailprice"),
                        Column.column("p_comment", WrenTypes.VARCHAR, null, false, "p_comment")),
                "p_partkey");
    }

    private Model getSupplier()
    {
        return Model.model("supplier",
                "select * from tablePrefix.supplier",
                List.of(
                        Column.column("s_suppkey", WrenTypes.INTEGER, null, false, "s_suppkey"),
                        Column.column("s_name", WrenTypes.VARCHAR, null, false, "s_name"),
                        Column.column("s_address", WrenTypes.VARCHAR, null, false, "s_address"),
                        Column.column("s_nationkey", WrenTypes.INTEGER, null, false, "s_nationkey"),
                        Column.column("s_phone", WrenTypes.VARCHAR, null, false, "s_phone"),
                        Column.column("s_acctbal", WrenTypes.DOUBLE, null, false, "s_acctbal"),
                        Column.column("s_comment", WrenTypes.VARCHAR, null, false, "s_comment")),
                "s_suppkey");
    }

    private Model getPartSupp()
    {
        return Model.model("partsupp",
                "select * from tablePrefix.partsupp",
                List.of(
                        Column.column("ps_partkey", WrenTypes.INTEGER, null, false, "ps_partkey"),
                        Column.column("ps_suppkey", WrenTypes.INTEGER, null, false, "ps_suppkey"),
                        Column.column("ps_availqty", WrenTypes.INTEGER, null, false, "ps_availqty"),
                        Column.column("ps_supplycost", WrenTypes.DOUBLE, null, false, "ps_supplycost"),
                        Column.column("ps_comment", WrenTypes.VARCHAR, null, false, "ps_comment"),
                        Column.column("partkey_suppkey", WrenTypes.VARCHAR, null, true, "concat(ps_partkey, '-', ps_suppkey)")),
                "partkey_suppkey");
    }
}