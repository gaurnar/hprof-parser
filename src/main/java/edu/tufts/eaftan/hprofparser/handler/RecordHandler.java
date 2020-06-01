package edu.tufts.eaftan.hprofparser.handler;

import edu.tufts.eaftan.hprofparser.parser.datastructures.AllocSite;
import edu.tufts.eaftan.hprofparser.parser.datastructures.CPUSample;
import edu.tufts.eaftan.hprofparser.parser.datastructures.Constant;
import edu.tufts.eaftan.hprofparser.parser.datastructures.InstanceField;
import edu.tufts.eaftan.hprofparser.parser.datastructures.Static;
import edu.tufts.eaftan.hprofparser.parser.datastructures.Value;

/**
 * Primary interface to be used with the hprof parser.  The parser takes an implementation of
 * this interface and calls the matching callback method on each record encountered.
 * Implementations of this interface can do things like printing the record or building a graph.
 * 
 * <p>You may assume that all references passed into the handler methods are non-null.
 * 
 * <p>Generally you want to subclass {@code NullRecordHandler} rather than implement this interface
 * directly. 
 */
public interface RecordHandler {

  void header(String format, int idSize, long time);

  void stringInUTF8(long id, String data);

  void loadClass(int classSerialNum, long classObjId, int stackTraceSerialNum,
      long classNameStringId);

  void unloadClass(int classSerialNum);

  void stackFrame(long stackFrameId,
      long methodNameStringId,
      long methodSigStringId,
      long sourceFileNameStringId,
      int classSerialNum,
      int location);

  void stackTrace(int stackTraceSerialNum, int threadSerialNum, int numFrames,
      long[] stackFrameIds);

  void allocSites(short bitMaskFlags,
      float cutoffRatio,
      int totalLiveBytes,
      int totalLiveInstances,
      long totalBytesAllocated,
      long totalInstancesAllocated,
      AllocSite[] sites);

  void heapSummary(int totalLiveBytes, int totalLiveInstances,
      long totalBytesAllocated, long totalInstancesAllocated);

  void startThread(int threadSerialNum,
      long threadObjectId,
      int stackTraceSerialNum,
      long threadNameStringId,
      long threadGroupNameId,
      long threadParentGroupNameId);

  void endThread(int threadSerialNum);

  void heapDump();

  void heapDumpEnd();

  void heapDumpSegment();

  void cpuSamples(int totalNumOfSamples, CPUSample[] samples);

  void controlSettings(int bitMaskFlags, short stackTraceDepth);

  void rootUnknown(long objId);

  void rootJNIGlobal(long objId, long JNIGlobalRefId);

  void rootJNILocal(long objId, int threadSerialNum, int frameNum);

  void rootJavaFrame(long objId, int threadSerialNum, int frameNum);

  void rootNativeStack(long objId, int threadSerialNum);

  void rootStickyClass(long objId);

  void rootThreadBlock(long objId, int threadSerialNum);

  void rootMonitorUsed(long objId);

  void rootThreadObj(long objId, int threadSerialNum, int stackTraceSerialNum);

  void classDump(long classObjId,
      int stackTraceSerialNum,
      long superClassObjId,
      long classLoaderObjId,
      long signersObjId,
      long protectionDomainObjId,
      long reserved1,
      long reserved2,
      int instanceSize,
      Constant[] constants,
      Static[] statics,
      InstanceField[] instanceFields);

  void instanceDump(long objId, int stackTraceSerialNum, long classObjId, Value<?>[] instanceFieldValues);

  void instanceDumpAtOffset(long objId, int stackTraceSerialNum, long classObjId, long fileOffset);

  void objArrayDump(long objId, int stackTraceSerialNum, long elemClassObjId, long[] elems);

  void objArrayDumpAtOffset(long objId, int stackTraceSerialNum, long elemClassObjId, long fileOffset);

  void primArrayDump(long objId, int stackTraceSerialNum, byte elemType, Value<?>[] elems);

  void primArrayDumpAtOffset(long objId, int stackTraceSerialNum, byte elemType, long fileOffset);

  void finished();

}
