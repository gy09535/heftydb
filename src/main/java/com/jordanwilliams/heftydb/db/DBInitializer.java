/*
 * Copyright (c) 2014. Jordan Williams
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jordanwilliams.heftydb.db;

import com.jordanwilliams.heftydb.data.Tuple;
import com.jordanwilliams.heftydb.index.IndexBlock;
import com.jordanwilliams.heftydb.metrics.Metrics;
import com.jordanwilliams.heftydb.state.Caches;
import com.jordanwilliams.heftydb.state.Paths;
import com.jordanwilliams.heftydb.table.MutableTable;
import com.jordanwilliams.heftydb.table.Table;
import com.jordanwilliams.heftydb.table.file.FileTable;
import com.jordanwilliams.heftydb.table.file.FileTableWriter;
import com.jordanwilliams.heftydb.table.file.TupleBlock;
import com.jordanwilliams.heftydb.table.memory.MemoryTable;
import com.jordanwilliams.heftydb.write.CommitLog;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Restores the state of an existing database and does any clean up needed to get into a consistent state.
 */
public class DBInitializer {

    private final Config config;
    private final Paths paths;
    private final Caches caches;
    private final Metrics metrics;
    private long maxSnapshotId;

    public DBInitializer(Config config, Metrics metrics) {
        this.config = config;
        this.metrics = metrics;
        this.paths = new Paths(config.tableDirectory(), config.logDirectory());
        this.caches = new Caches(new TupleBlock.Cache(config.tableCacheSize(), metrics),
                new IndexBlock.Cache(config.indexCacheSize(), metrics));
    }

    public DBState initialize() throws IOException {
        deleteTempTables();
        writeTablesFromLogs();
        List<Table> tables = loadTables();
        return new DBState(tables, config, paths, caches, maxSnapshotId);
    }

    private List<Table> loadTables() throws IOException {
        List<Table> tables = new ArrayList<Table>();
        Set<Long> tableIds = paths.tableFileIds();

        for (Long id : tableIds) {
            Table table = FileTable.open(id, paths, caches.recordBlockCache(), caches.indexBlockCache(), metrics);
            maxSnapshotId = Math.max(table.maxSnapshotId(), maxSnapshotId);
            tables.add(table);
        }

        return tables;
    }

    private void deleteTempTables() throws IOException {
        Set<Long> tempIds = paths.tempTableFileIds();

        for (Long id : tempIds) {
            Files.deleteIfExists(paths.tempPath(id));
            Files.deleteIfExists(paths.indexPath(id));
            Files.deleteIfExists(paths.filterPath(id));
        }
    }

    private void writeTablesFromLogs() throws IOException {
        Set<Long> logIds = paths.logFileIds();

        for (Long id : logIds) {
            CommitLog log = CommitLog.open(id, paths);
            Table memoryTable = readTable(log);
            log.close();
            if ((memoryTable.tupleCount() > 0)) {
                FileTableWriter.Task tableWriterTask = new FileTableWriter.Task.Builder().tableId(id).config(config)
                        .paths(paths).level(1).tupleCount(memoryTable.tupleCount()).source(memoryTable.ascendingIterator
                                (Long.MAX_VALUE)).build();

                tableWriterTask.run();
            }
            Files.deleteIfExists(paths.logPath(id));
        }
    }

    private Table readTable(CommitLog log) {
        MutableTable memoryTable = new MemoryTable(log.tableId());

        for (Tuple tuple : log) {
            memoryTable.put(tuple);
        }

        return memoryTable;
    }
}
