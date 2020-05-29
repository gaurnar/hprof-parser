package edu.tufts.eaftan.hprofparser.viewer.storage;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class InstancesOffsetExternalSortedStorage {

    private static final int CHUNK_SIZE = 10000;

    private List<RandomAccessFile> sortedFiles = new ArrayList<>();

    private List<InstanceOffset> chunkList = new ArrayList<>(CHUNK_SIZE);

    private boolean finished = false;

    private RandomAccessFile resultFile;

    public void registerInstance(long instanceId, long offset) throws IOException {
        if (finished) {
            throw new RuntimeException("already finished!");
        }

        chunkList.add(new InstanceOffset(instanceId, offset));

        if (chunkList.size() == CHUNK_SIZE) {
            createNewSortedFile();
            chunkList.clear();
        }
    }

    public void finishRegistering() throws IOException {
        if (finished) {
            throw new RuntimeException("already finished!");
        }

        if (chunkList.size() != 0) {
            createNewSortedFile();
        }

        File tempFile = File.createTempFile("myhprof_instances_" + chunkList.size() + "_", ".tmp");
        resultFile = new RandomAccessFile(tempFile, "rw");

        // TODO refactor this

        ByteBuffer resultByteBuffer = ByteBuffer.allocate(16 * 1000); // TODO some other constant?
        LongBuffer resultLongBuffer = resultByteBuffer.asLongBuffer();

        List<ByteBuffer> fileByteBuffers = new ArrayList<>(sortedFiles.size());
        List<LongBuffer> fileLongBuffers = new ArrayList<>(sortedFiles.size());

        sortedFiles.forEach(randomAccessFile -> {
            try {
                randomAccessFile.seek(0);

                ByteBuffer byteBuffer = ByteBuffer.allocate(16 * 100); // TODO some other constant?
                int bytesRead = randomAccessFile.read(byteBuffer.array());
                byteBuffer.limit(bytesRead);

                fileByteBuffers.add(byteBuffer);
                fileLongBuffers.add(byteBuffer.asLongBuffer());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        List<InstanceOffset> candidateOffsetsPerFile = new ArrayList<>(sortedFiles.size());

        fileLongBuffers.forEach(longBuffer -> {
            long id = longBuffer.get();
            long offset = longBuffer.get();
            candidateOffsetsPerFile.add(new InstanceOffset(id, offset));
        });

        while (true) {
            int minimumCandidateIndex = -1;
            long minimumCandidateId = candidateOffsetsPerFile.get(0).id;

            for (int i = 1; i < sortedFiles.size(); i++) {
                if (candidateOffsetsPerFile.get(i) == null) {
                    continue;
                }
                if (minimumCandidateIndex == -1 || candidateOffsetsPerFile.get(i).id < minimumCandidateId) {
                    minimumCandidateIndex = i;
                    minimumCandidateId = candidateOffsetsPerFile.get(i).id;
                }
            }

            if (minimumCandidateIndex == -1) {
                break;
            }

            resultLongBuffer.put(minimumCandidateId);
            resultLongBuffer.put(candidateOffsetsPerFile.get(minimumCandidateIndex).offset);

            if (resultLongBuffer.remaining() == 0) {
                resultFile.write(resultByteBuffer.array());
                resultLongBuffer.clear();
            }

            if (fileLongBuffers.get(minimumCandidateIndex).remaining() == 0) {
                ByteBuffer minimumByteBuffer = fileByteBuffers.get(minimumCandidateIndex);

                minimumByteBuffer.clear();
                int bytesRead = sortedFiles.get(minimumCandidateIndex).read(minimumByteBuffer.array());
                minimumByteBuffer.limit(bytesRead);
            }

            if (fileLongBuffers.get(minimumCandidateIndex).remaining() == 0) {
                candidateOffsetsPerFile.set(minimumCandidateIndex, null);
            } else {
                LongBuffer minimumLongBuffer = fileLongBuffers.get(minimumCandidateIndex);

                long id = minimumLongBuffer.get();
                long offset = minimumLongBuffer.get();

                candidateOffsetsPerFile.set(minimumCandidateIndex, new InstanceOffset(id, offset));
            }
        }

        for (RandomAccessFile sortedFile : sortedFiles) {
            sortedFile.close();
        }

        sortedFiles = null;

        finished = true;
    }

    public long getInstanceOffset(long instanceId) throws IOException {
        if (!finished) {
            throw new RuntimeException("not yet finished!");
        }

        long from = 0;
        long to = resultFile.length();

        // binary search
        while (from != to) {
            int instancesCount = (int) (to - from) / 8 / 2;
            int middleInstanceIdx = instancesCount / 2;
            long middleOffset = from + middleInstanceIdx * 8 * 2;

            resultFile.seek(middleOffset);
            long middleId = resultFile.readLong();

            if (middleId == instanceId) {
                return resultFile.readLong();
            } else if (middleId < instanceId) {
                from = middleOffset + 8 * 2;
            } else {
                to = middleOffset;
            }
        }

        throw new RuntimeException("instance not found with id=" + instanceId);
    }

    private void createNewSortedFile() throws IOException {
        File tempFile = File.createTempFile("myhprof_instances_chunk_" + chunkList.size() + "_", ".tmp");

        RandomAccessFile randomAccessFile = new RandomAccessFile(tempFile, "rw");

        ByteBuffer byteBuffer = ByteBuffer.allocate(chunkList.size() * 2 * 8);
        LongBuffer longBuffer = byteBuffer.asLongBuffer();

        chunkList.stream()
            .sorted(Comparator.comparing(instanceOffset -> instanceOffset.id))
            .forEach(instanceOffset -> {
                longBuffer.put(instanceOffset.id);
                longBuffer.put(instanceOffset.offset);
            });

        randomAccessFile.write(byteBuffer.array());

        sortedFiles.add(randomAccessFile);
    }

    private static class InstanceOffset {
        long id;
        long offset;

        public InstanceOffset(long id, long offset) {
            this.id = id;
            this.offset = offset;
        }
    }
}
