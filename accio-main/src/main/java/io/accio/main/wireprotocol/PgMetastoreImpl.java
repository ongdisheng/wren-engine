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

package io.accio.main.wireprotocol;

import io.accio.base.AccioException;
import io.accio.base.ConnectorRecordIterator;
import io.accio.base.Parameter;
import io.accio.base.client.AutoCloseableIterator;
import io.accio.base.client.Client;
import io.accio.base.client.duckdb.CacheStorageConfig;
import io.accio.base.client.duckdb.DuckDBConfig;
import io.accio.base.client.duckdb.DuckdbClient;
import io.accio.base.client.duckdb.DuckdbTypes;
import io.accio.base.config.AccioConfig;
import io.accio.base.config.ConfigManager;
import io.accio.base.sql.SqlConverter;
import io.accio.base.type.VarcharType;
import io.accio.base.wireprotocol.PgMetastore;
import io.accio.cache.DuckdbRecordIterator;
import io.accio.main.connector.duckdb.DuckDBSqlConverter;
import io.airlift.log.Logger;

import javax.inject.Inject;

import java.util.List;

import static io.accio.base.metadata.StandardErrorCode.GENERIC_INTERNAL_ERROR;
import static io.accio.main.connector.duckdb.DuckDBMetadata.convertParameters;
import static io.accio.main.pgcatalog.PgCatalogUtils.PG_CATALOG_NAME;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

public class PgMetastoreImpl
        implements PgMetastore
{
    private static final Logger LOG = Logger.get(PgMetastoreImpl.class);
    private final ConfigManager configManager;
    private final DuckDBSqlConverter duckDBSqlConverter;
    private final DuckdbClient duckdbClient;

    @Inject
    public PgMetastoreImpl(
            ConfigManager configManager,
            DuckDBSqlConverter duckDBSqlConverter)
    {
        this.configManager = requireNonNull(configManager, "configManager is null");
        this.duckDBSqlConverter = duckDBSqlConverter;
        this.duckdbClient = buildDuckDBClient();
    }

    @Override
    public boolean isSchemaExist(String name)
    {
        try (AutoCloseableIterator<Object[]> iter = duckdbClient
                .query("SELECT 1 FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = ?", List.of(new Parameter(VarcharType.VARCHAR, name)))) {
            return iter.hasNext();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void dropTableIfExists(String name)
    {
        try {
            duckdbClient.executeDDL(format("BEGIN TRANSACTION;DROP TABLE IF EXISTS %s;COMMIT;", name));
        }
        catch (Exception e) {
            LOG.error(e, "Failed to drop table %s", name);
        }
    }

    @Override
    public void directDDL(String sql)
    {
        duckdbClient.executeDDL(sql);
    }

    @Override
    public ConnectorRecordIterator directQuery(String sql, List<Parameter> parameters)
    {
        try {
            return DuckdbRecordIterator.of(duckdbClient, sql, convertParameters(parameters));
        }
        catch (Exception e) {
            throw new AccioException(GENERIC_INTERNAL_ERROR, e);
        }
    }

    @Override
    public String getPgCatalogName()
    {
        return PG_CATALOG_NAME;
    }

    @Override
    public String handlePgType(String type)
    {
        if (type.startsWith("_")) {
            return format("%s[]", handlePgType(type.substring(1)));
        }
        else if (!DuckdbTypes.getDuckDBTypeNames().contains(type)) {
            return "VARCHAR";
        }
        return type;
    }

    @Override
    public Client getClient()
    {
        return duckdbClient;
    }

    @Override
    public SqlConverter getSqlConverter()
    {
        return duckDBSqlConverter;
    }

    @Override
    public void close()
    {
        duckdbClient.close();
    }

    private DuckdbClient buildDuckDBClient()
    {
        return DuckdbClient.builder()
                .setDuckDBConfig(configManager.getConfig(DuckDBConfig.class))
                .setCacheStorageConfig(getCacheStorageConfigIfExists())
                .build();
    }

    private CacheStorageConfig getCacheStorageConfigIfExists()
    {
        try {
            return configManager.getConfig(CacheStorageConfig.class);
        }
        catch (Exception e) {
            LOG.warn(e, "%s connector does not support cache storage. Cache is disable.", configManager.getConfig(AccioConfig.class).getDataSourceType().name());
            return null;
        }
    }
}