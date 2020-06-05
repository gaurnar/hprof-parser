package edu.tufts.eaftan.hprofparser.viewer;

import edu.tufts.eaftan.hprofparser.handler.NullRecordHandler;
import edu.tufts.eaftan.hprofparser.handler.RecordHandler;
import edu.tufts.eaftan.hprofparser.parser.HprofParser;
import edu.tufts.eaftan.hprofparser.parser.HprofParser.ParseOptions;
import edu.tufts.eaftan.hprofparser.parser.datastructures.Constant;
import edu.tufts.eaftan.hprofparser.parser.datastructures.InstanceField;
import edu.tufts.eaftan.hprofparser.parser.datastructures.Static;
import edu.tufts.eaftan.hprofparser.parser.datastructures.Type;
import edu.tufts.eaftan.hprofparser.parser.datastructures.Value;
import edu.tufts.eaftan.hprofparser.viewer.storage.ObjectInfoBinarySearchFileStorage;
import edu.tufts.eaftan.hprofparser.viewer.storage.ObjectInfoBinarySearchFileStorage.ObjectInfo;
import edu.tufts.eaftan.hprofparser.viewer.storage.ObjectInfoBinarySearchFileStorage.ObjectType;
import edu.tufts.eaftan.hprofparser.viewer.storage.ObjectInfoBinarySearchFileStorage.PrimitiveType;
import edu.tufts.eaftan.hprofparser.viewer.storage.TypeInstancesFileStorage;
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

    public static abstract class HeapDumpType {
        protected final String name;
        protected final int instancesCount;

        protected HeapDumpType(String name, int instancesCount) {
            this.name = name;
            this.instancesCount = instancesCount;
        }

        public String getName() {
            return name;
        }

        public int getInstancesCount() {
            return instancesCount;
        }
    }

    public static class HeapDumpClass extends HeapDumpType {
        private final long classId;

        protected HeapDumpClass(String name, int instancesCount, long classId) {
            super(name, instancesCount);
            this.classId = classId;
        }

        public long getClassId() {
            return classId;
        }

        @Override
        public String toString() {
            return "HeapDumpClass{" +
                "name='" + name + '\'' +
                ", instancesCount=" + instancesCount +
                ", classId=" + classId +
                '}';
        }
    }

    public static class HeapDumpObjectArray extends HeapDumpType {
        private final long elementClassId;

        protected HeapDumpObjectArray(String name, int instancesCount, long elementClassId) {
            super(name, instancesCount);
            this.elementClassId = elementClassId;
        }

        public long getElementClassId() {
            return elementClassId;
        }

        @Override
        public String toString() {
            return "HeapDumpObjectArray{" +
                "name='" + name + '\'' +
                ", instancesCount=" + instancesCount +
                ", elementClassId=" + elementClassId +
                '}';
        }
    }

    public static class HeapDumpPrimitiveArray extends HeapDumpType {
        private final PrimitiveType primitiveType;

        protected HeapDumpPrimitiveArray(String name, int instancesCount,
                                         PrimitiveType primitiveType) {
            super(name, instancesCount);
            this.primitiveType = primitiveType;
        }

        public PrimitiveType getPrimitiveType() {
            return primitiveType;
        }

        @Override
        public String toString() {
            return "HeapDumpPrimitiveArray{" +
                "name='" + name + '\'' +
                ", instancesCount=" + instancesCount +
                ", primitiveType=" + primitiveType +
                '}';
        }
    }

    public static abstract class HeapDumpClassInstanceField {
        protected final String fieldName;

        protected HeapDumpClassInstanceField(String fieldName) {
            this.fieldName = fieldName;
        }

        public String getFieldName() {
            return fieldName;
        }
    }

    public static class HeapDumpClassInstancePrimitiveField extends HeapDumpClassInstanceField {
        private final String valueAsString;

        public HeapDumpClassInstancePrimitiveField(String fieldName, String valueAsString) {
            super(fieldName);
            this.valueAsString = valueAsString;
        }

        public String getValueAsString() {
            return valueAsString;
        }

        @Override
        public String toString() {
            return "HeapDumpClassInstancePrimitiveField{" +
                "fieldName='" + fieldName + '\'' +
                ", valueAsString='" + valueAsString + '\'' +
                '}';
        }
    }

    public static class HeapDumpClassInstanceObjectField extends HeapDumpClassInstanceField {
        private final long objId;
        private final String typeName;

        protected HeapDumpClassInstanceObjectField(String fieldName, long objId, String typeName) {
            super(fieldName);
            this.objId = objId;
            this.typeName = typeName;
        }

        public long getObjId() {
            return objId;
        }

        public String getTypeName() {
            return typeName;
        }

        @Override
        public String toString() {
            return "HeapDumpClassInstanceObjectField{" +
                "fieldName='" + fieldName + '\'' +
                ", objId=" + objId +
                ", typeName='" + typeName + '\'' +
                '}';
        }
    }

    public static abstract class HeapDumpObject {
        protected final long id;

        protected HeapDumpObject(long id) {
            this.id = id;
        }

        public long getId() {
            return id;
        }
    }

    public static class HeapDumpClassObject extends HeapDumpObject {
        private final String className;

        public HeapDumpClassObject(long id, String className) {
            super(id);
            this.className = className;
        }

        public String getClassName() {
            return className;
        }
    }

    public static class HeapDumpClassInstance extends HeapDumpObject {
        private final List<HeapDumpClassInstanceField> instanceFields;

        public HeapDumpClassInstance(long id, List<HeapDumpClassInstanceField> instanceFields) {
            super(id);
            this.instanceFields = instanceFields;
        }

        public List<HeapDumpClassInstanceField> getInstanceFields() {
            return instanceFields;
        }

        @Override
        public String toString() {
            return "HeapDumpClassInstance{" +
                "id=" + id +
                ", instanceFields=" + instanceFields +
                '}';
        }
    }

    public static abstract class HeapDumpArrayInstance extends HeapDumpObject {
        protected HeapDumpArrayInstance(long id) {
            super(id);
        }
    }

    public static class HeapDumpObjectArrayInstance extends HeapDumpArrayInstance {
        public HeapDumpObjectArrayInstance(long id) {
            super(id);
        }
    }

    public static class HeapDumpPrimitiveArrayInstance extends HeapDumpArrayInstance {
        public HeapDumpPrimitiveArrayInstance(long id) {
            super(id);
        }
    }

    private final HprofParser parser;

    // TODO not needed for viewing; move to RecordHandler
    // TODO move to file storage?
    private Map<Long, ClassProcessingInfo> classInfoByClassObjIdMap = new HashMap<>();
    private Map<Long, List<ClassProcessingInfo>> classInfoByNameIdMap = new HashMap<>();
    private Map<Long, List<ClassFieldProcessingInfo>> classFieldInfoByNameIdMap = new HashMap<>();
    private Map<Long, InstancesCount> objectArraysCountByElementClassIdMap = new HashMap<>();
    private Map<PrimitiveType, InstancesCount> primitiveArraysCountByTypeMap = new HashMap<>();

    private Map<Long, List<String>> classFieldNamesByClassObjIdMap = new HashMap<>();
    private Map<Long, String> classNamesByClassObjIdMap = new HashMap<>();

    private final TypeInstancesFileStorage classInstancesStorage = new TypeInstancesFileStorage();
    private final TypeInstancesFileStorage objectArraysInstancesStorage = new TypeInstancesFileStorage();
    private final TypeInstancesFileStorage primitiveArraysInstancesStorage = new TypeInstancesFileStorage();
    private final ObjectInfoBinarySearchFileStorage objectInfoStorage = new ObjectInfoBinarySearchFileStorage();

    private List<HeapDumpType> types;

    public HprofViewer(File hprofFile) throws IOException {

        parser = new HprofParser();

        parser.parse(hprofFile, new MainRecordHandler(),
                     new ParseOptions(true, true, true));

        // TODO merge into one?
        classInstancesStorage.finishRegistering();
        objectArraysInstancesStorage.finishRegistering();
        primitiveArraysInstancesStorage.finishRegistering();

        objectInfoStorage.finishRegistering();

        types = new ArrayList<>(classInfoByClassObjIdMap.size()
                                    + objectArraysCountByElementClassIdMap.size()
                                    + primitiveArraysCountByTypeMap.size());

        // TODO do in parallel?
        classInfoByClassObjIdMap.forEach((classId, classProcessingInfo) -> {
            List<String> fieldNames = new ArrayList<>();

            for (int i = 0; i < classProcessingInfo.fieldProcessingInfos.size(); i++) {
                ClassFieldProcessingInfo info = classProcessingInfo.fieldProcessingInfos.get(i);
                fieldNames.add(info.name == null ? "field" + (i + 1) : info.name);
            }

            classFieldNamesByClassObjIdMap.put(classId, fieldNames);
            classNamesByClassObjIdMap.put(classId, classProcessingInfo.name);

            if (classProcessingInfo.count > 0) {
                types.add(new HeapDumpClass(
                    // TODO why can it be null?
                    classProcessingInfo.name != null ? classProcessingInfo.name : "<???>",
                    classProcessingInfo.count, classId));
            }
        });

        objectArraysCountByElementClassIdMap.forEach((elementClassId, instancesCount) -> {
            String elementClassName = classNamesByClassObjIdMap.get(elementClassId);
            types.add(new HeapDumpObjectArray(elementClassName + "[]", instancesCount.count, elementClassId));
        });

        primitiveArraysCountByTypeMap.forEach(
            (primitiveType, instancesCount) -> types.add(
                new HeapDumpPrimitiveArray(primitiveType.toString().toLowerCase() + "[]",
                                           instancesCount.count, primitiveType)));

        types.sort(Comparator.comparing(HeapDumpType::getInstancesCount, Comparator.reverseOrder()));

        classInfoByClassObjIdMap = null;
        classInfoByNameIdMap = null;
        classFieldInfoByNameIdMap = null;
    }

    public List<HeapDumpType> listTypes() {
        return types; // TODO make read only list
    }

    public List<HeapDumpClassInstance> listClassInstances(long classId, int offset, int limit) throws IOException {
        return classInstancesStorage.listInstancesIds(classId, offset, limit).stream()
            .map(this::readHeapDumpClassInstance)
            .collect(Collectors.toList());
    }

    public List<HeapDumpArrayInstance> listObjectArrayInstances(long elementClassId, int offset, int limit)
        throws IOException {
        return objectArraysInstancesStorage.listInstancesIds(elementClassId, offset, limit).stream()
            .map(HeapDumpObjectArrayInstance::new)
            .collect(Collectors.toList());
    }

    public List<HeapDumpArrayInstance> listPrimitiveArrayInstances(PrimitiveType type, int offset, int limit)
        throws IOException {
        return primitiveArraysInstancesStorage.listInstancesIds(type.ordinal(), offset, limit).stream()
            .map(HeapDumpPrimitiveArrayInstance::new)
            .collect(Collectors.toList());
    }

    public List<HeapDumpObject> listObjectArrayElements(long arrayId, int offset, int limit) {
        List<HeapDumpObject> arrayElements = new ArrayList<>(limit);

        RecordHandler recordHandler = new NullRecordHandler() {
            @Override
            public void objArrayDump(long objId, int stackTraceSerialNum, long elemClassObjId, long[] elems) {
                for (long elemObjId : elems) {
                    if (elemObjId == 0) { // null value
                        arrayElements.add(null);
                    } else if (classNamesByClassObjIdMap.containsKey(elemObjId)) {
                        String className = classNamesByClassObjIdMap.get(elemObjId);
                        arrayElements.add(new HeapDumpClassObject(elemObjId, className));
                    } else {
                        ObjectInfo objectInfo = objectInfoStorage.getObjectInfo(elemObjId);

                        if (objectInfo.getType() == ObjectType.INSTANCE) {
                            arrayElements.add(readHeapDumpClassInstance(elemObjId));
                        } else if (objectInfo.getType() == ObjectType.OBJECT_ARRAY) {
                            arrayElements.add(new HeapDumpObjectArrayInstance(elemObjId));
                        } else { // PRIMITIVE_ARRAY
                            arrayElements.add(new HeapDumpPrimitiveArrayInstance(elemObjId));
                        }
                    }
                }
            }
        };

        long fileOffset = objectInfoStorage.getObjectInfo(arrayId).getDumpFileOffset();

        try {
            parser.readObjArrayDumpAtOffset(fileOffset, recordHandler, offset, limit);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return arrayElements;
    }

    public List<String> listPrimitiveArrayElements(long arrayId, int offset, int limit) {
        List<String> arrayElements = new ArrayList<>(limit);

        RecordHandler recordHandler = new NullRecordHandler() {
            @Override
            public void primArrayDump(long objId, int stackTraceSerialNum, byte elemType, Value<?>[] elems) {
                for (Value value : elems) {
                    arrayElements.add(value.value.toString());
                }
            }
        };

        long fileOffset = objectInfoStorage.getObjectInfo(arrayId).getDumpFileOffset();

        try {
            parser.readPrimArrayDumpAtOffset(fileOffset, recordHandler, offset, limit);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return arrayElements;
    }

    public HeapDumpClassInstance showClassInstance(long instanceId) {
        return readHeapDumpClassInstance(objectInfoStorage.getObjectInfo(instanceId).getDumpFileOffset());
    }

    private HeapDumpClassInstance readHeapDumpClassInstance(long instanceId) {
        List<HeapDumpClassInstanceField> instanceFields = new ArrayList<>();

        RecordHandler recordHandler = new NullRecordHandler() {
            @Override
            public void instanceDump(long objId, int stackTraceSerialNum, long classObjId,
                                     Value<?>[] instanceFieldValues) {
                List<String> fieldNames = classFieldNamesByClassObjIdMap.get(classObjId);

                for (int i = 0; i < fieldNames.size(); i++) {
                    Value value = instanceFieldValues[i];

                    HeapDumpClassInstanceField field;

                    if (value.type == Type.OBJ) {
                        long valueObjectId = (long) value.value;

                        if (valueObjectId == 0) { // null value
                            field = new HeapDumpClassInstanceObjectField(fieldNames.get(i),
                                                                         0,
                                                                         // TODO get type info from class dump
                                                                         null);
                        } else if (classNamesByClassObjIdMap.containsKey(valueObjectId)) {
                            String className = classNamesByClassObjIdMap.get(valueObjectId);
                            field = new HeapDumpClassInstanceObjectField(fieldNames.get(i),
                                                                         valueObjectId,
                                                                         "Class<" + className + ">");
                        } else {
                            ObjectInfo objectInfo = objectInfoStorage.getObjectInfo(valueObjectId);

                            String typeName;

                            if (objectInfo.getType() == ObjectType.INSTANCE) {
                                typeName = classNamesByClassObjIdMap.get(objectInfo.getClassId());
                            } else if (objectInfo.getType() == ObjectType.OBJECT_ARRAY) {
                                if (classNamesByClassObjIdMap.containsKey(objectInfo.getClassId())) {
                                    // TODO remove copy paste
                                    String className = classNamesByClassObjIdMap.get(objectInfo.getClassId());
                                    typeName = "Class<" + className + ">[]";
                                } else {
                                    typeName = classNamesByClassObjIdMap.get(objectInfo.getClassId()) + "[]";
                                }
                            } else { // PRIMITIVE_ARRAY
                                typeName = objectInfo.getPrimitiveType().toString().toLowerCase() + "[]";
                            }

                            field = new HeapDumpClassInstanceObjectField(fieldNames.get(i),
                                                                         valueObjectId,
                                                                         typeName);
                        }
                    } else { // all primitive values
                        field = new HeapDumpClassInstancePrimitiveField(fieldNames.get(i), value.value.toString());
                    }

                    instanceFields.add(field);
                }
            }
        };

        long fileOffset = objectInfoStorage.getObjectInfo(instanceId).getDumpFileOffset();

        try {
            parser.readInstanceDumpAtOffset(fileOffset, recordHandler);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return new HeapDumpClassInstance(instanceId, instanceFields);
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
                classInfoByNameIdMap.get(id).forEach(info -> info.name = sanitizeClassNameFromHeapDump(data));
            } else if (classFieldInfoByNameIdMap.containsKey(id)) {
                classFieldInfoByNameIdMap.get(id).forEach(info -> info.name = sanitizeClassNameFromHeapDump(data));
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

            classInstancesStorage.registerInstance(classObjId, objId);
            objectInfoStorage.registerObjectInfo(new ObjectInfo(objId, classObjId, fileOffset, ObjectType.INSTANCE));
        }

        @Override
        public void objArrayDumpAtOffset(long objId, int stackTraceSerialNum, long elemClassObjId, long fileOffset) {
            objectArraysCountByElementClassIdMap.putIfAbsent(elemClassObjId, new InstancesCount());

            objectArraysCountByElementClassIdMap.get(elemClassObjId).count++;

            objectArraysInstancesStorage.registerInstance(elemClassObjId, objId);
            objectInfoStorage.registerObjectInfo(new ObjectInfo(objId, elemClassObjId, fileOffset,
                                                                ObjectType.OBJECT_ARRAY));
        }

        @Override
        public void primArrayDumpAtOffset(long objId, int stackTraceSerialNum, byte elemType, long fileOffset) {
            PrimitiveType primitiveType = primitiveTypeFromHprofElementType(elemType);

            primitiveArraysCountByTypeMap.putIfAbsent(primitiveType, new InstancesCount());

            primitiveArraysCountByTypeMap.get(primitiveType).count++;

            primitiveArraysInstancesStorage.registerInstance(primitiveTypeFromHprofElementType(elemType).ordinal(),
                                                             objId);
            objectInfoStorage.registerObjectInfo(new ObjectInfo(objId, primitiveType, fileOffset));
        }
    }

    private PrimitiveType primitiveTypeFromHprofElementType(byte type) {
        switch (type) {
            case 4:
                return PrimitiveType.BOOL;
            case 5:
                return PrimitiveType.CHAR;
            case 6:
                return PrimitiveType.FLOAT;
            case 7:
                return PrimitiveType.DOUBLE;
            case 8:
                return PrimitiveType.BYTE;
            case 9:
                return PrimitiveType.SHORT;
            case 10:
                return PrimitiveType.INT;
            case 11:
                return PrimitiveType.LONG;
            default:
                throw new RuntimeException("Unexpected primitive type: " + type);
        }
    }

    private String sanitizeClassNameFromHeapDump(String heapDumpName) {
        String result = heapDumpName.replaceAll("/", ".");
        if (result.startsWith("[L")) {
            result = result.substring(2, result.length() - 1);
        }
        return result;
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

    private static class InstancesCount {
        int count;
    }
}
