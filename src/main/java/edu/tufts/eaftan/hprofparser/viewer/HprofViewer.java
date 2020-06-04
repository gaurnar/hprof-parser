package edu.tufts.eaftan.hprofparser.viewer;

import edu.tufts.eaftan.hprofparser.handler.NullRecordHandler;
import edu.tufts.eaftan.hprofparser.handler.RecordHandler;
import edu.tufts.eaftan.hprofparser.parser.HprofParser;
import edu.tufts.eaftan.hprofparser.parser.HprofParser.ParseOptions;
import edu.tufts.eaftan.hprofparser.parser.datastructures.Constant;
import edu.tufts.eaftan.hprofparser.parser.datastructures.InstanceField;
import edu.tufts.eaftan.hprofparser.parser.datastructures.Static;
import edu.tufts.eaftan.hprofparser.parser.datastructures.Value;
import edu.tufts.eaftan.hprofparser.viewer.storage.ClassInstancesFileStorage;
import edu.tufts.eaftan.hprofparser.viewer.storage.ObjectInfoBinarySearchFileStorage;
import edu.tufts.eaftan.hprofparser.viewer.storage.ObjectInfoBinarySearchFileStorage.ObjectInfo;
import edu.tufts.eaftan.hprofparser.viewer.storage.ObjectInfoBinarySearchFileStorage.ObjectType;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TODO no field names on id=33058500736 com/intellij/usages/ShowUsageViewSettings (idea.hprof)
 */
public class HprofViewer {

    public static class HeapDumpClass {
        private final long id;
        private final String name;
        private final int instancesCount;

        public HeapDumpClass(long id, String name, int instancesCount) {
            this.id = id;
            this.name = name;
            this.instancesCount = instancesCount;
        }

        public long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public int getInstancesCount() {
            return instancesCount;
        }

        @Override
        public String toString() {
            return "HeapDumpClass{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", instancesCount=" + instancesCount +
                '}';
        }
    }

    public static class HeapDumpClassInstance {
        private final long id;
        private final Map<String, String> fieldValuePreviews;

        public HeapDumpClassInstance(long id, Map<String, String> fieldValuePreviews) {
            this.id = id;
            this.fieldValuePreviews = fieldValuePreviews;
        }

        public long getId() {
            return id;
        }

        public Map<String, String> getFieldValuePreviews() {
            return fieldValuePreviews;
        }

        @Override
        public String toString() {
            return "HeapDumpClassInstance{" +
                "fieldValuePreviews=" + fieldValuePreviews +
                '}';
        }
    }

    private final File hprofFile;

    private final HprofParser parser;

    // TODO not needed for viewing; move to RecordHandler
    // TODO move to file storage?
    private Map<Long, ClassProcessingInfo> classInfoByClassObjIdMap = new HashMap<>();
    private Map<Long, List<ClassProcessingInfo>> classInfoByNameIdMap = new HashMap<>();
    private Map<Long, List<ClassFieldProcessingInfo>> classFieldInfoByNameIdMap = new HashMap<>();

    private Map<Long, List<String>> classFieldNamesByClassObjIdMap = new HashMap<>();

    private final ClassInstancesFileStorage classInstancesStorage = new ClassInstancesFileStorage();
    private final ObjectInfoBinarySearchFileStorage instancesOffsetStorage =
        new ObjectInfoBinarySearchFileStorage();

    private List<HeapDumpClass> classes;

    public HprofViewer(File hprofFile) throws IOException {
        this.hprofFile = hprofFile;

        parser = new HprofParser();

        parser.parse(hprofFile, new MainRecordHandler(),
                     new ParseOptions(true, true, true));

        classInstancesStorage.finishRegistering();
        instancesOffsetStorage.finishRegistering();

        // TODO parallel stream?
        classes = classInfoByClassObjIdMap.values().stream()
            // TODO some other way to filter?
            .filter(heapDumpClass -> heapDumpClass.count > 0
                && heapDumpClass.name != null) // TODO why can it be null?
            .sorted(Comparator.comparing(heapDumpClass -> heapDumpClass.count, Comparator.reverseOrder()))
            .map(classProcessingInfo -> {
                List<String> fieldNames = new ArrayList<>();

                for (int i = 0; i < classProcessingInfo.fieldProcessingInfos.size(); i++) {
                    ClassFieldProcessingInfo info = classProcessingInfo.fieldProcessingInfos.get(i);
                    fieldNames.add(info.name == null ? "field" + (i + 1) : info.name);
                }

                classFieldNamesByClassObjIdMap.put(classProcessingInfo.id, fieldNames);

                return new HeapDumpClass(classProcessingInfo.id,
                                         classProcessingInfo.name,
                                         classProcessingInfo.count);
            })
            .collect(Collectors.toList());

        classInfoByClassObjIdMap = null;
        classInfoByNameIdMap = null;
        classFieldInfoByNameIdMap = null;
    }

    public List<HeapDumpClass> listClasses() {
        return classes;
    }

    public List<HeapDumpClassInstance> listClassInstances(long classId, int offset, int limit) throws IOException {
        return classInstancesStorage.listClassInstanceFileOffsets(classId, offset, limit).stream()
            .map(this::readHeapDumpClassInstance)
            .collect(Collectors.toList());
    }

    public HeapDumpClassInstance showInstance(long instanceId) throws IOException {
        return readHeapDumpClassInstance(instancesOffsetStorage.getObjectInfo(instanceId).getDumpFileOffset());
    }

    private HeapDumpClassInstance readHeapDumpClassInstance(long fileOffset) {
        long[] id = new long[1];
        Map<String, String> fieldPreviews = new HashMap<>();

        RecordHandler fieldRecordHandler = new NullRecordHandler() {
            @Override
            public void instanceDump(long objId, int stackTraceSerialNum, long classObjId,
                                     Value<?>[] instanceFieldValues) {
                id[0] = objId;

                List<String> fieldNames = classFieldNamesByClassObjIdMap.get(classObjId);
                for (int i = 0; i < fieldNames.size(); i++) {
                    fieldPreviews.put(fieldNames.get(i), instanceFieldValues[i].value.toString());
                }
            }
        };

        try {
            // TODO read previews?
            parser.readInstanceDumpAtOffset(fileOffset, fieldRecordHandler);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return new HeapDumpClassInstance(id[0], fieldPreviews);
    }

    private class MainRecordHandler extends NullRecordHandler {

        @Override
        public void classDump(long classObjId, int stackTraceSerialNum, long superClassObjId, long classLoaderObjId,
                              long signersObjId, long protectionDomainObjId, long reserved1, long reserved2,
                              int instanceSize, Constant[] constants, Static[] statics,
                              InstanceField[] instanceFields) {
            // TODO handle constants and statics
            // TODO record size

            classInfoByClassObjIdMap.putIfAbsent(classObjId, new ClassProcessingInfo(classObjId));

            ClassProcessingInfo classInfo = classInfoByClassObjIdMap.get(classObjId);

            if (classInfo.fieldProcessingInfos == null) {
                classInfo.fieldProcessingInfos =
                    Arrays.stream(instanceFields)
                        .map(instanceField -> {
                            long fieldNameId = instanceField.fieldNameStringId;

                            classFieldInfoByNameIdMap.putIfAbsent(fieldNameId, new ArrayList<>());

                            ClassFieldProcessingInfo fieldInfo = new ClassFieldProcessingInfo();
                            classFieldInfoByNameIdMap.get(fieldNameId).add(fieldInfo);

                            return fieldInfo;
                        })
                        .collect(Collectors.toList());
            }
        }

        @Override
        public void stringInUTF8(long id, String data) {
            // we are called strictly after all classes loads/dumps
            if (classInfoByNameIdMap.containsKey(id)) {
                classInfoByNameIdMap.get(id).forEach(info -> info.name = data);
            } else if (classFieldInfoByNameIdMap.containsKey(id)) {
                classFieldInfoByNameIdMap.get(id).forEach(info -> info.name = data);
            }
        }

        @Override
        public void loadClass(int classSerialNum, long classObjId, int stackTraceSerialNum, long classNameStringId) {
            classInfoByClassObjIdMap.putIfAbsent(classObjId, new ClassProcessingInfo(classObjId));
            classInfoByNameIdMap.putIfAbsent(classNameStringId, new ArrayList<>());

            classInfoByNameIdMap.get(classNameStringId).add(classInfoByClassObjIdMap.get(classObjId));
        }

        @Override
        public void instanceDumpAtOffset(long objId, int stackTraceSerialNum, long classObjId, long fileOffset) {
            classInfoByClassObjIdMap.putIfAbsent(classObjId, new ClassProcessingInfo(classObjId));

            classInfoByClassObjIdMap.get(classObjId).count++;

            try {
                classInstancesStorage.registerInstance(classObjId, fileOffset);
                instancesOffsetStorage.registerObjectInfo(new ObjectInfo(objId, fileOffset, ObjectType.INSTANCE));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void objArrayDumpAtOffset(long objId, int stackTraceSerialNum, long elemClassObjId, long fileOffset) {
            // TODO
        }

        @Override
        public void primArrayDumpAtOffset(long objId, int stackTraceSerialNum, byte elemType, long fileOffset) {
            // TODO
        }
    }

    private static class ClassProcessingInfo {
        long id;
        String name;
        int count;
        List<ClassFieldProcessingInfo> fieldProcessingInfos;

        public ClassProcessingInfo(long id) {
            this.id = id;
        }
    }

    private static class ClassFieldProcessingInfo {
        String name;
    }
}
