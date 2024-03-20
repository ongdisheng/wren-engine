package io.accio.cache;

import com.google.common.collect.ImmutableList;
import io.accio.base.AccioException;
import io.accio.base.ConnectorRecordIterator;
import io.accio.base.client.duckdb.DuckDBConfig;
import io.accio.base.wireprotocol.PgMetastore;

import javax.annotation.PreDestroy;
import javax.inject.Inject;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;

import static io.accio.base.client.duckdb.DuckdbUtil.convertDuckDBUnits;
import static io.accio.base.metadata.StandardErrorCode.EXCEEDED_GLOBAL_MEMORY_LIMIT;
import static io.accio.base.metadata.StandardErrorCode.EXCEEDED_TIME_LIMIT;
import static io.accio.base.metadata.StandardErrorCode.GENERIC_INTERNAL_ERROR;
import static io.airlift.concurrent.Threads.threadsNamed;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.runAsync;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;

public class CacheTaskManager
        implements Closeable
{
    private final PgMetastore pgMetastore;
    private final ExecutorService taskExecutorService;
    private final DuckDBConfig duckDBConfig;
    private final double cacheMemoryLimit;

    @Inject
    public CacheTaskManager(DuckDBConfig duckDBConfig, PgMetastore pgMetastore)
    {
        this.duckDBConfig = requireNonNull(duckDBConfig, "duckDBConfig is null");
        this.pgMetastore = requireNonNull(pgMetastore, "pgMetastore is null");
        this.taskExecutorService = newFixedThreadPool(duckDBConfig.getMaxConcurrentTasks(), threadsNamed("duckdb-task-%s"));
        this.cacheMemoryLimit = duckDBConfig.getMaxCacheTableSizeRatio() * duckDBConfig.getMemoryLimit().toBytes();
    }

    public CompletableFuture<Void> addCacheTask(Runnable runnable)
    {
        return runAsync(runnable, taskExecutorService);
    }

    public <T> T addCacheQueryTask(Callable<T> callable)
    {
        try {
            return taskExecutorService.submit(callable).get(duckDBConfig.getMaxCacheQueryTimeout(), SECONDS);
        }
        catch (TimeoutException e) {
            throw new AccioException(EXCEEDED_TIME_LIMIT, "Query time limit exceeded", e);
        }
        catch (InterruptedException | ExecutionException e) {
            throw new AccioException(GENERIC_INTERNAL_ERROR, e);
        }
    }

    // for canner use
    public void addCacheQueryDDLTask(Runnable runnable)
    {
        try {
            taskExecutorService.submit(runnable).get(duckDBConfig.getMaxCacheQueryTimeout(), SECONDS);
        }
        catch (TimeoutException e) {
            throw new AccioException(EXCEEDED_TIME_LIMIT, "Query time limit exceeded", e);
        }
        catch (InterruptedException | ExecutionException e) {
            throw new AccioException(GENERIC_INTERNAL_ERROR, e);
        }
    }

    public long getMemoryUsageBytes()
    {
        try (ConnectorRecordIterator result = pgMetastore.directQuery("SELECT memory_usage FROM pragma_database_size()", ImmutableList.of())) {
            Object[] row = result.next();
            return convertDuckDBUnits(row[0].toString()).toBytes();
        }
        catch (Exception e) {
            throw new AccioException(GENERIC_INTERNAL_ERROR, "Failed to get memory usage", e);
        }
    }

    public void checkCacheMemoryLimit()
    {
        long usage = getMemoryUsageBytes();
        if (usage >= cacheMemoryLimit) {
            throw new AccioException(EXCEEDED_GLOBAL_MEMORY_LIMIT, "Cache memory limit exceeded. Usage: " + usage + " bytes, Limit: " + cacheMemoryLimit + " bytes");
        }
    }

    @PreDestroy
    @Override
    public void close()
            throws IOException
    {
        taskExecutorService.shutdownNow();
        pgMetastore.close();
    }
}