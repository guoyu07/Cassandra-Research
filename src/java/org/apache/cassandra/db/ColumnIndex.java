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
package org.apache.cassandra.db;

import java.io.IOException;
import java.util.*;

import com.google.common.annotations.VisibleForTesting;

import org.apache.cassandra.config.DatabaseDescriptor;
import org.apache.cassandra.db.rows.*;
import org.apache.cassandra.io.sstable.IndexHelper;
import org.apache.cassandra.io.sstable.format.Version;
import org.apache.cassandra.io.util.SequentialWriter;
import org.apache.cassandra.utils.ByteBufferUtil;

public class ColumnIndex
{
    public final List<IndexHelper.IndexInfo> columnsIndex;

    private static final ColumnIndex EMPTY = new ColumnIndex(Collections.<IndexHelper.IndexInfo>emptyList());

    private ColumnIndex(List<IndexHelper.IndexInfo> columnsIndex)
    {
        assert columnsIndex != null;

        this.columnsIndex = columnsIndex;
    }

    public static ColumnIndex writeAndBuildIndex(UnfilteredRowIterator iterator, SequentialWriter output, SerializationHeader header, Version version) throws IOException
    {
        assert !iterator.isEmpty() && version.storeRows();

        Builder builder = new Builder(iterator, output, header, version.correspondingMessagingVersion());
        return builder.build();
    }

    @VisibleForTesting
    public static ColumnIndex nothing()
    {
        return EMPTY;
    }

    /**
     * Help to create an index for a column family based on size of columns,
     * and write said columns to disk.
     */
    private static class Builder
    {
        private final UnfilteredRowIterator iterator;
        private final SequentialWriter writer;
        private final SerializationHeader header;
        private final int version;

        private final ColumnIndex result;
        private final long initialPosition;
        private long startPosition = -1;

        private int written;

        private ClusteringPrefix firstClustering;
        private ClusteringPrefix lastClustering;

        private DeletionTime openMarker;

        public Builder(UnfilteredRowIterator iterator,
                       SequentialWriter writer,
                       SerializationHeader header,
                       int version)
        {
            this.iterator = iterator;
            this.writer = writer;
            this.header = header;
            this.version = version;

            this.result = new ColumnIndex(new ArrayList<IndexHelper.IndexInfo>());
            this.initialPosition = writer.getFilePointer();
        }

        private void writePartitionHeader(UnfilteredRowIterator iterator) throws IOException
        {
            ByteBufferUtil.writeWithShortLength(iterator.partitionKey().getKey(), writer);
            DeletionTime.serializer.serialize(iterator.partitionLevelDeletion(), writer);
            if (header.hasStatic())
                UnfilteredSerializer.serializer.serialize(iterator.staticRow(), header, writer, version);
        }

        public ColumnIndex build() throws IOException
        {
            writePartitionHeader(iterator);

            while (iterator.hasNext())
                add(iterator.next());

            return close();
        }

//<<<<<<< HEAD
//        /**
//         * The important distinction wrt build() is that we may be building for a row that ends up
//         * being compacted away entirely, i.e., the input consists only of expired tombstones (or
//         * columns shadowed by expired tombstone).  Thus, it is the caller's responsibility
//         * to decide whether to write the header for an empty row.
//         */
//        //在进行Compaction时使用
//        public ColumnIndex buildForCompaction(Iterator<OnDiskAtom> columns) throws IOException
//=======
        private long currentPosition()
        {
            return writer.getFilePointer() - initialPosition;
        }

        private void addIndexBlock()
        {
            IndexHelper.IndexInfo cIndexInfo = new IndexHelper.IndexInfo(firstClustering,
                                                                         lastClustering,
                                                                         startPosition,
                                                                         currentPosition() - startPosition,
                                                                         openMarker);
            result.columnsIndex.add(cIndexInfo);
            firstClustering = null;
        }

        private void add(Unfiltered unfiltered) throws IOException
        {
            if (firstClustering == null)
            {
                // Beginning of an index block. Remember the start and position
                firstClustering = unfiltered.clustering();
                startPosition = currentPosition();
            }

            UnfilteredSerializer.serializer.serialize(unfiltered, header, writer, version);
            lastClustering = unfiltered.clustering();
            ++written;

//<<<<<<< HEAD
//            // if we hit the column index size that we have to index after, go ahead and index it.
//            if (blockSize >= DatabaseDescriptor.getColumnIndexSize()) //默认是64K
//=======
            if (unfiltered.kind() == Unfiltered.Kind.RANGE_TOMBSTONE_MARKER)
            {
                RangeTombstoneMarker marker = (RangeTombstoneMarker)unfiltered;
                openMarker = marker.isOpen(false) ? marker.openDeletionTime(false) : null;
            }

            // if we hit the column index size that we have to index after, go ahead and index it.
            if (currentPosition() - startPosition >= DatabaseDescriptor.getColumnIndexSize())
                addIndexBlock();

        }

//<<<<<<< HEAD
//        //对应rowHeaderSize
//        private void maybeWriteRowHeader() throws IOException
//=======
        private ColumnIndex close() throws IOException
        {
            UnfilteredSerializer.serializer.writeEndOfPartition(writer);

            // It's possible we add no rows, just a top level deletion
            if (written == 0)
                return ColumnIndex.EMPTY;

            // the last column may have fallen on an index boundary already.  if not, index it explicitly.
//<<<<<<< HEAD
//            if (result.columnsIndex.isEmpty() || lastBlockClosing != lastColumn)
//            {
//                //对每行的数据索引时会直接跳过行头，从行里面的列开始
//                IndexHelper.IndexInfo cIndexInfo = new IndexHelper.IndexInfo(firstColumn.name(), lastColumn.name(), indexOffset + startPosition, endPosition - startPosition);
//                result.columnsIndex.add(cIndexInfo);
//            }
//=======
            if (firstClustering != null)
                addIndexBlock();

            // we should always have at least one computed index block, but we only write it out if there is more than that.
            assert result.columnsIndex.size() > 0;
            return result;
        }
    }
}
