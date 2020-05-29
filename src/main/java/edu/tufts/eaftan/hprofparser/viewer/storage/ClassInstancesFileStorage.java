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

public class ClassInstancesFileStorage {

    private static final int INSTANCES_PER_SEGMENT = 100;

    private final RandomAccessFile file;
    private final Map<Long, InstancesOffsets> offsetsByClassIdMap = new HashMap<>();

    private boolean finished = false;

    public ClassInstancesFileStorage() throws IOException {
        File tempFile = File.createTempFile("myhprof_class_instances_", ".tmp");

        file = new RandomAccessFile(tempFile, "rw");
    }

    public void registerInstance(long classId, long instanceFileOffset) throws IOException {
        if (finished) {
            throw new RuntimeException("already finished registering");
        }

        if (offsetsByClassIdMap.containsKey(classId)) {
            InstancesOffsets offsets = offsetsByClassIdMap.get(classId);

            offsets.inMemorySegmentBuffer.put(instanceFileOffset);

            if (offsets.inMemorySegmentBuffer.remaining() == 0) {
                writeSegmentToFile(offsets);
            }
        } else {
            InstancesOffsets offsets = new InstancesOffsets();

            offsets.inMemorySegmentBuffer.put(instanceFileOffset);

            offsetsByClassIdMap.put(classId, offsets);
        }
    }

    public void finishRegistering() {
        if (finished) {
            throw new RuntimeException("already finished!");
        }

        // writing in memory segments

        offsetsByClassIdMap.values().forEach(instancesOffsets -> {
            try {
                writeSegmentToFile(instancesOffsets);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            instancesOffsets.inMemorySegmentByteBuffer = null;
            instancesOffsets.inMemorySegmentBuffer = null;
        });

        finished = true;
    }

    public List<Long> listClassInstanceFileOffsets(long classId, int offset, int limit) throws IOException {
        if (!finished) {
            throw new RuntimeException("not finished!");
        }

        if (!offsetsByClassIdMap.containsKey(classId)) {
            throw new RuntimeException("class not found with id: " + classId);
        }

        InstancesOffsets offsets = offsetsByClassIdMap.get(classId);

        int segmentsToSkip = offset / INSTANCES_PER_SEGMENT;

        if (segmentsToSkip >= offsets.segmentsOffsets.size()) {
            return Collections.emptyList();
        }

        int offsetInFirstSegment = offset - segmentsToSkip * INSTANCES_PER_SEGMENT;

        List<Long> instancesOffsets = new ArrayList<>(limit);

        //
        // reading from first segment
        //

        file.seek(offsets.segmentsOffsets.get(segmentsToSkip));
        file.skipBytes(offsetInFirstSegment * 8);

        int instancesToReadFirstSegment;

        if (offsets.segmentsOffsets.size() == 1) {
            // special case - first segment is also the last one; which may be not full
            // TODO can we refactor to avoid this?
            instancesToReadFirstSegment = Math.min(limit, offsets.lastFileSegmentInstancesCount);
        } else {
            instancesToReadFirstSegment = Math.min(limit, INSTANCES_PER_SEGMENT - offsetInFirstSegment);
        }

        for (int j = 0; j < instancesToReadFirstSegment; j++) {
            instancesOffsets.add(file.readLong());
        }

        //
        // reading from other segments except last one
        //

        int remainingInstances = limit - instancesToReadFirstSegment;

        for (int i = segmentsToSkip + 1; i < (offsets.segmentsOffsets.size() - 1) && remainingInstances != 0; i++) {
            file.seek(offsets.segmentsOffsets.get(i));

            int instancesToRead = Math.min(remainingInstances, INSTANCES_PER_SEGMENT);

            for (int j = 0; j < instancesToRead; j++) {
                instancesOffsets.add(file.readLong());
            }

            remainingInstances -= instancesToRead;
        }

        //
        // reading from last segment
        //

        if (offsets.segmentsOffsets.size() > 1) {
            file.seek(offsets.segmentsOffsets.get(offsets.segmentsOffsets.size() - 1));

            int instancesToReadLastSegment = Math.min(remainingInstances, offsets.lastFileSegmentInstancesCount);

            for (int j = 0; j < instancesToReadLastSegment; j++) {
                instancesOffsets.add(file.readLong());
            }
        }

        return instancesOffsets;
    }

    private void writeSegmentToFile(InstancesOffsets offsets) throws IOException {
        offsets.lastFileSegmentInstancesCount = INSTANCES_PER_SEGMENT - offsets.inMemorySegmentBuffer.remaining();
        offsets.segmentsOffsets.add(file.length());

        file.seek(file.length());
        file.write(offsets.inMemorySegmentByteBuffer.array());

        offsets.inMemorySegmentBuffer.clear();
    }

    private static class InstancesOffsets {
        List<Long> segmentsOffsets = new ArrayList<>();
        int lastFileSegmentInstancesCount = -1;

        ByteBuffer inMemorySegmentByteBuffer = ByteBuffer.allocate(INSTANCES_PER_SEGMENT * 8);
        LongBuffer inMemorySegmentBuffer = inMemorySegmentByteBuffer.asLongBuffer();
    }
}
