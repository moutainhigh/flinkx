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

package com.dtstack.flinkx.odps.writer;

import com.aliyun.odps.Odps;
import com.aliyun.odps.data.Record;
import com.aliyun.odps.tunnel.TableTunnel;
import com.aliyun.odps.tunnel.TunnelException;
import com.aliyun.odps.tunnel.io.TunnelBufferedWriter;
import com.dtstack.flinkx.common.ColumnType;
import com.dtstack.flinkx.exception.WriteRecordException;
import com.dtstack.flinkx.outputformat.RichOutputFormat;
import com.dtstack.flinkx.util.DateUtil;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.types.Row;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

/**
 * The Odps implementation of OutputFormat
 *
 * Company: www.dtstack.com
 * @author huyifan.zju@163.com
 */
public class OdpsOutputFormat extends RichOutputFormat {

    protected String[] columnTypes;

    protected String[] columnNames;

    protected String writeMode;

    protected String partition;

    protected String projectName;

    protected String tableName;

    protected Map<String,String> odpsConfig;

    private Odps odps;

    private TableTunnel tunnel;

    private TableTunnel.UploadSession session;

    private TunnelBufferedWriter recordWriter;

    @Override
    public void configure(Configuration configuration) {
        odps = OdpsUtil.initOdps(odpsConfig);
        tunnel = new TableTunnel(odps);
    }

    @Override
    public void openInternal(int taskNumber, int numTasks) throws IOException {
        session = OdpsUtil.createMasterTunnelUpload(tunnel, projectName, tableName, partition);
        try {
            recordWriter = (TunnelBufferedWriter) session.openBufferedWriter();
        } catch (TunnelException e) {
            throw new RuntimeException("can not open record writer");
        }
    }

    @Override
    public void writeSingleRecordInternal(Row row) throws WriteRecordException{
        Record record = row2record(row, columnTypes);
        try {
            recordWriter.write(record);
        } catch(Exception ex) {
            throw new WriteRecordException(ex.getMessage(), ex);
        }

    }

    @Override
    protected void writeMultipleRecordsInternal() throws Exception {
        throw new UnsupportedOperationException();
    }

    private Record row2record(Row row, String[] columnTypes) throws WriteRecordException {
        Record record = session.newRecord();
        int i = 0;
        try {
            for (; i < row.getArity(); ++i) {
                Object column = row.getField(i);
                ColumnType columnType = ColumnType.valueOf(columnTypes[i].toUpperCase());
                String rowData = column.toString();
                switch (columnType) {
                    case BOOLEAN:
                        record.setBoolean(i, Boolean.valueOf(rowData));
                        break;
                    case BIGINT:
                        record.setBigint(i, Long.valueOf(rowData));
                        break;
                    case DOUBLE:
                        record.setDouble(i, Double.valueOf(rowData));
                        break;
                    case DECIMAL:
                        record.setDecimal(i, new BigDecimal(rowData));
                        break;
                    case STRING:
                        record.setString(i, rowData);
                        break;
                    case DATE:
                    case TIMESTAMP:
                        record.setDatetime(i, DateUtil.columnToTimestamp(column));
                        break;
                    default:
                        throw new IllegalArgumentException();
                }

            }

        } catch(Exception ex) {
            String msg = getClass().getName() + " Writing record error: when converting field[" + i + "] in Row(" + row + ")";
            throw new WriteRecordException(msg, ex, i, row);
        }

        return record;
    }

    @Override
    public void closeInternal() throws IOException {
        if(recordWriter != null) {
            recordWriter.close();
        }

        try {
            session.commit();
        } catch (TunnelException e) {
            e.printStackTrace();
        }

    }

}
