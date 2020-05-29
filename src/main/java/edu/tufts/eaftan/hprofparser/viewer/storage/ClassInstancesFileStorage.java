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

/**
 * TODO something simpler?
 */
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
        // TODO remove copy-paste

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

        List<Long> instancesOffsets = new ArrayList<>(limit);

        int remainingOffset = offset;
        int remainingInstances = limit;

        file.seek(offsets.firstSegmentOffset);

        long newOffset;
        int numInstances;

        // traversing segments to apply offset
        while (true) {
            newOffset = file.readLong();
            numInstances = file.readInt();

            if (numInstances <= remainingOffset) {
                if (newOffset == 0) {
                    // no instances at all
                    return Collections.emptyList();
                } else {
                    remainingOffset -= numInstances;
                    file.seek(newOffset);
                }
            } else {
                file.skipBytes(remainingOffset * 8);
                break;
            }
        }

        // traversing segments to fetch instances
        while (true) {
            int instancesToRead = Math.min(remainingInstances, numInstances);
            for (int i = 0; i < instancesToRead; i++) {
                instancesOffsets.add(file.readLong());
            }

            remainingInstances -= instancesToRead;

            if (remainingInstances == 0 || newOffset == 0) {
                break;
            }

            file.seek(newOffset);
            newOffset = file.readLong();
            numInstances = file.readInt();
        }

        return instancesOffsets;
    }

    private void writeSegmentToFile(InstancesOffsets offsets) throws IOException {
        if (offsets.lastSegmentOffset == -1) {
            offsets.firstSegmentOffset = file.length();
        } else {
            file.seek(offsets.lastSegmentOffset);
            file.writeLong(file.length());
        }

        offsets.lastSegmentOffset = file.length();

        // writing new segment
        // TODO these writes are slow!
        file.seek(file.length());
        file.writeLong(0); // no next segment
        file.writeInt(INSTANCES_PER_SEGMENT - offsets.inMemorySegmentBuffer.remaining());
        file.write(offsets.inMemorySegmentByteBuffer.array());

        offsets.inMemorySegmentBuffer.clear();
    }

    private static class InstancesOffsets {
        long firstSegmentOffset = -1;
        long lastSegmentOffset = -1;

        ByteBuffer inMemorySegmentByteBuffer = ByteBuffer.allocate(INSTANCES_PER_SEGMENT * 8);
        LongBuffer inMemorySegmentBuffer = inMemorySegmentByteBuffer.asLongBuffer();
    }
}
