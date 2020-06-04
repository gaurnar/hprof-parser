package edu.tufts.eaftan.hprofparser.viewer.storage;

import java.io.BufferedOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ObjectInfoBinarySearchFileStorage {

    public enum ObjectType {
        INSTANCE,
        OBJECT_ARRAY,
        PRIMITIVE_ARRAY
    }

    public static class ObjectInfo {
        private final long id;
        private final long dumpFileOffset;
        private final ObjectType type;

        public ObjectInfo(long id, long dumpFileOffset, ObjectType type) {
            this.id = id;
            this.dumpFileOffset = dumpFileOffset;
            this.type = type;
        }

        public long getId() {
            return id;
        }

        public long getDumpFileOffset() {
            return dumpFileOffset;
        }

        public ObjectType getType() {
            return type;
        }
    }

    // TODO calibrate sizes even more?
    private static final int CHUNK_RECORDS_SIZE = 1000000;
    private static final int RESULT_BUFFER_RECORDS_SIZE = 1000;
    private static final int SORTED_FILE_BUFFER_RECORDS_SIZE = 100;

    private static final int RECORD_SIZE = 8 + 8 + 1; // id + offset + type

    private static final long EMPTY_VALUE = -1;

    private List<File> sortedFiles = new ArrayList<>();

    private List<ObjectInfo> objectInfosChunk = new ArrayList<>(CHUNK_RECORDS_SIZE);

    private boolean finished = false;

    private RandomAccessFile resultRandomAccessFile;

    public void registerObjectInfo(ObjectInfo info) throws IOException {
        if (finished) {
            throw new RuntimeException("already finished!");
        }

        if (info.id == EMPTY_VALUE) {
            throw new RuntimeException("object id clashes with reserved value"); // TODO is it ok?
        }

        objectInfosChunk.add(info);

        if (objectInfosChunk.size() == CHUNK_RECORDS_SIZE) {
            createNewSortedFile();
            objectInfosChunk.clear();
        }
    }

    public void finishRegistering() throws IOException {
        if (finished) {
            throw new RuntimeException("already finished!");
        }

        if (objectInfosChunk.size() != 0) {
            createNewSortedFile();
        }

        objectInfosChunk = null;

        List<FileInputStream> sortedFileInputStreams = new ArrayList<>(sortedFiles.size());

        File resultFile = File.createTempFile("myhprof_obj_info_", ".tmp");
        resultFile.deleteOnExit();

        try (FileOutputStream resultOutputStream = new FileOutputStream(resultFile)) {
            ByteBuffer resultByteBuffer = ByteBuffer.allocateDirect(RESULT_BUFFER_RECORDS_SIZE * RECORD_SIZE);

            List<ByteBuffer> sortedFileByteBuffers = new ArrayList<>(sortedFiles.size());

            for (File sortedFile : sortedFiles) {
                FileInputStream sortedFileInputStream = new FileInputStream(sortedFile);

                ByteBuffer byteBuffer = ByteBuffer.allocateDirect(SORTED_FILE_BUFFER_RECORDS_SIZE * RECORD_SIZE);
                int bytesRead = sortedFileInputStream.getChannel().read(byteBuffer);

                byteBuffer.position(0);
                byteBuffer.limit(bytesRead);

                sortedFileByteBuffers.add(byteBuffer);
                sortedFileInputStreams.add(sortedFileInputStream);
            }

            long[] nextIdsFromFiles = new long[sortedFiles.size()];

            for (int i = 0; i < nextIdsFromFiles.length; i++) {
                nextIdsFromFiles[i] = sortedFileByteBuffers.get(i).getLong();
            }

            while (true) {
                int fileWithMinIndex = -1;
                long minId = -1;

                for (int i = 0; i < nextIdsFromFiles.length; i++) {
                    if (nextIdsFromFiles[i] == EMPTY_VALUE) {
                        continue;
                    }
                    if (fileWithMinIndex == -1 || nextIdsFromFiles[i] < minId) {
                        fileWithMinIndex = i;
                        minId = nextIdsFromFiles[i];
                    }
                }

                if (fileWithMinIndex == -1) {
                    break;
                }

                ByteBuffer fileBufferWithMinId = sortedFileByteBuffers.get(fileWithMinIndex);

                resultByteBuffer.putLong(minId);
                resultByteBuffer.putLong(fileBufferWithMinId.getLong()); // offset
                resultByteBuffer.put(fileBufferWithMinId.get()); // type

                if (!resultByteBuffer.hasRemaining()) {
                    resultByteBuffer.position(0);
                    resultOutputStream.getChannel().write(resultByteBuffer);

                    resultByteBuffer.clear();
                }

                if (!fileBufferWithMinId.hasRemaining()) {
                    fileBufferWithMinId.clear();

                    int bytesRead =
                        sortedFileInputStreams.get(fileWithMinIndex).getChannel().read(fileBufferWithMinId);

                    if (bytesRead == -1) {
                        nextIdsFromFiles[fileWithMinIndex] = -1;
                        continue;
                    }

                    fileBufferWithMinId.position(0);
                    fileBufferWithMinId.limit(bytesRead);
                }

                nextIdsFromFiles[fileWithMinIndex] = fileBufferWithMinId.getLong();
            }

            if (resultByteBuffer.position() > 0) {
                resultByteBuffer.limit(resultByteBuffer.position());
                resultByteBuffer.position(0);

                resultOutputStream.getChannel().write(resultByteBuffer);
            }
        } finally {
            for (FileInputStream sortedFileInputStream : sortedFileInputStreams) {
                sortedFileInputStream.close();
            }
        }

        sortedFiles = null;

        resultRandomAccessFile = new RandomAccessFile(resultFile, "r");

        finished = true;
    }

    public ObjectInfo getObjectInfo(long id) throws IOException {
        if (!finished) {
            throw new RuntimeException("not yet finished!");
        }

        long from = 0;
        long to = resultRandomAccessFile.length();

        // binary search
        while (from != to) {
            int instancesCount = (int) (to - from) / RECORD_SIZE;
            int middleInstanceIdx = instancesCount / 2;
            long middleOffset = from + middleInstanceIdx * RECORD_SIZE;

            resultRandomAccessFile.seek(middleOffset);
            long middleId = resultRandomAccessFile.readLong();

            if (middleId == id) {
                return new ObjectInfo(id, resultRandomAccessFile.readLong(), ObjectType.values()[resultRandomAccessFile.readByte()]);
            } else if (middleId < id) {
                from = middleOffset + RECORD_SIZE;
            } else {
                to = middleOffset;
            }
        }

        throw new RuntimeException("instance not found with id=" + id);
    }

    private void createNewSortedFile() throws IOException {
        String newSortedFilePrefix = "myhprof_obj_info_chunk_" + objectInfosChunk.size() + "_";

        File newSortedFile = File.createTempFile(newSortedFilePrefix, ".tmp");
        newSortedFile.deleteOnExit();

        try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(new FileOutputStream(newSortedFile))) {
            DataOutput output = new DataOutputStream(bufferedOutputStream);

            objectInfosChunk.sort(Comparator.comparing(objectInfo -> objectInfo.id));

            for (ObjectInfo objectInfo : objectInfosChunk) {
                output.writeLong(objectInfo.id);
                output.writeLong(objectInfo.dumpFileOffset);
                output.writeByte(objectInfo.type.ordinal());
            }
        }

        sortedFiles.add(newSortedFile);
    }
}
