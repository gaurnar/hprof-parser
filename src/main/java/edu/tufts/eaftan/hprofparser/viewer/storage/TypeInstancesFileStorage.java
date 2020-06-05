package edu.tufts.eaftan.hprofparser.viewer.storage;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TypeInstancesFileStorage {

    private static final int INSTANCES_PER_SEGMENT = 100;

    private final RandomAccessFile file;
    private final Map<Long, InstancesIds> instancesIdsByTypeIdMap = new HashMap<>();

    private boolean finished = false;

    public TypeInstancesFileStorage() throws IOException {
        File tempFile = File.createTempFile("myhprof_class_instances_", ".tmp");
        tempFile.deleteOnExit();

        file = new RandomAccessFile(tempFile, "rw");
    }

    public void registerInstance(long typeId, long id) {
        if (finished) {
            throw new RuntimeException("already finished registering");
        }

        if (instancesIdsByTypeIdMap.containsKey(typeId)) {
            InstancesIds ids = instancesIdsByTypeIdMap.get(typeId);

            ids.inMemorySegmentBuffer.put(id);

            if (ids.inMemorySegmentBuffer.remaining() == 0) {
                writeSegmentToFile(ids);
            }
        } else {
            InstancesIds ids = new InstancesIds();

            ids.inMemorySegmentBuffer.put(id);

            instancesIdsByTypeIdMap.put(typeId, ids);
        }
    }

    public void finishRegistering() {
        if (finished) {
            throw new RuntimeException("already finished!");
        }

        // writing in memory segments

        instancesIdsByTypeIdMap.values().forEach(instancesIds -> {
            writeSegmentToFile(instancesIds);

            instancesIds.inMemorySegmentByteBuffer = null;
            instancesIds.inMemorySegmentBuffer = null;
        });

        finished = true;
    }

    public List<Long> listInstancesIds(long typeId, int offset, int limit) throws IOException {
        if (!finished) {
            throw new RuntimeException("not finished!");
        }

        if (!instancesIdsByTypeIdMap.containsKey(typeId)) {
            throw new RuntimeException("class not found with id: " + typeId);
        }

        InstancesIds ids = instancesIdsByTypeIdMap.get(typeId);

        int segmentsToSkip = offset / INSTANCES_PER_SEGMENT;

        if (segmentsToSkip >= ids.segmentsOffsets.size()) {
            return Collections.emptyList();
        }

        int offsetInFirstSegment = offset - segmentsToSkip * INSTANCES_PER_SEGMENT;

        List<Long> instancesIds = new ArrayList<>(limit);

        //
        // reading from first segment
        //

        file.seek(ids.segmentsOffsets.get(segmentsToSkip));
        file.skipBytes(offsetInFirstSegment * 8);

        int instancesToReadFirstSegment;

        if (ids.segmentsOffsets.size() == 1) {
            // special case - first segment is also the last one; which may be not full
            // TODO can we refactor to avoid this?
            instancesToReadFirstSegment = Math.min(limit, ids.lastFileSegmentInstancesCount);
        } else {
            instancesToReadFirstSegment = Math.min(limit, INSTANCES_PER_SEGMENT - offsetInFirstSegment);
        }

        for (int j = 0; j < instancesToReadFirstSegment; j++) {
            instancesIds.add(file.readLong());
        }

        //
        // reading from other segments except last one
        //

        int remainingInstances = limit - instancesToReadFirstSegment;

        for (int i = segmentsToSkip + 1; i < (ids.segmentsOffsets.size() - 1) && remainingInstances != 0; i++) {
            file.seek(ids.segmentsOffsets.get(i));

            int instancesToRead = Math.min(remainingInstances, INSTANCES_PER_SEGMENT);

            for (int j = 0; j < instancesToRead; j++) {
                instancesIds.add(file.readLong());
            }

            remainingInstances -= instancesToRead;
        }

        //
        // reading from last segment
        //

        if (ids.segmentsOffsets.size() > 1) {
            file.seek(ids.segmentsOffsets.get(ids.segmentsOffsets.size() - 1));

            int instancesToReadLastSegment = Math.min(remainingInstances, ids.lastFileSegmentInstancesCount);

            for (int j = 0; j < instancesToReadLastSegment; j++) {
                instancesIds.add(file.readLong());
            }
        }

        return instancesIds;
    }

    private void writeSegmentToFile(InstancesIds ids) {
        try {
            ids.lastFileSegmentInstancesCount = INSTANCES_PER_SEGMENT - ids.inMemorySegmentBuffer.remaining();
            ids.segmentsOffsets.add(file.length());

            file.seek(file.length());
            file.write(ids.inMemorySegmentByteBuffer.array());

            ids.inMemorySegmentBuffer.clear();
        } catch (IOException e) {
            throw new RuntimeException("failed to write segment to file", e);
        }
    }

    private static class InstancesIds {
        List<Long> segmentsOffsets = new ArrayList<>();
        int lastFileSegmentInstancesCount = -1;

        ByteBuffer inMemorySegmentByteBuffer = ByteBuffer.allocate(INSTANCES_PER_SEGMENT * 8);
        LongBuffer inMemorySegmentBuffer = inMemorySegmentByteBuffer.asLongBuffer();
    }
}
