package edu.tufts.eaftan.hprofparser.handler;

import edu.tufts.eaftan.hprofparser.parser.datastructures.Value;

public interface InstanceDumpFileOffsetAwareRecordHandler extends RecordHandler {

    void instanceDumpAtOffset(long objId, int stackTraceSerialNum, long classObjId,
                              Value<?>[] instanceFieldValues, long fileOffset);

    void objArrayDumpAtOffset(long objId, int stackTraceSerialNum, long elemClassObjId,
                              long[] elems, long fileOffset);

    void primArrayDumpAtOffset(long objId, int stackTraceSerialNum, byte elemType,
                               Value<?>[] elems, long fileOffset);
}
