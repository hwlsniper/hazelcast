/*
 * Copyright (c) 2008-2019, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.internal.partition.operation;

import com.hazelcast.core.Member;
import com.hazelcast.core.MemberLeftException;
import com.hazelcast.internal.partition.InternalPartitionService;
import com.hazelcast.internal.partition.MigrationCycleOperation;
import com.hazelcast.internal.partition.MigrationInfo;
import com.hazelcast.internal.partition.impl.InternalPartitionServiceImpl;
import com.hazelcast.internal.partition.impl.PartitionDataSerializerHook;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.spi.ExceptionAction;
import com.hazelcast.spi.NodeEngine;
import com.hazelcast.spi.exception.TargetNotMemberException;

import java.io.IOException;

/**
 * Sent by the master node to commit a migration on the migration destination.
 * It updates the partition table on the migration destination and finalizes the migration.
 */
public class MigrationCommitOperation extends AbstractPartitionOperation implements MigrationCycleOperation {

    private MigrationInfo migration;

    private String expectedMemberUuid;

    private transient boolean success;

    public MigrationCommitOperation() {
    }

    public MigrationCommitOperation(MigrationInfo migration, String expectedMemberUuid) {
        this.migration = migration;
        this.expectedMemberUuid = expectedMemberUuid;
    }

    @Override
    public void run() {
        NodeEngine nodeEngine = getNodeEngine();
        final Member localMember = nodeEngine.getLocalMember();
        if (!localMember.getUuid().equals(expectedMemberUuid)) {
            throw new IllegalStateException("This " + localMember
                    + " is migration commit destination but most probably it's restarted "
                    + "and not the expected target.");
        }

        InternalPartitionServiceImpl service = getService();
        success = service.commitMigrationOnDestination(migration, getCallerAddress());
    }

    @Override
    public Object getResponse() {
        return success;
    }

    @Override
    public String getServiceName() {
        return InternalPartitionService.SERVICE_NAME;
    }

    @Override
    public ExceptionAction onInvocationException(Throwable throwable) {
        if (throwable instanceof MemberLeftException
                || throwable instanceof TargetNotMemberException) {
            return ExceptionAction.THROW_EXCEPTION;
        }
        return super.onInvocationException(throwable);
    }

    @Override
    protected void readInternal(ObjectDataInput in) throws IOException {
        super.readInternal(in);
        expectedMemberUuid = in.readUTF();
        migration = in.readObject();
    }

    @Override
    protected void writeInternal(ObjectDataOutput out) throws IOException {
        super.writeInternal(out);
        out.writeUTF(expectedMemberUuid);
        out.writeObject(migration);
    }

    @Override
    public int getId() {
        return PartitionDataSerializerHook.MIGRATION_COMMIT;
    }
}
