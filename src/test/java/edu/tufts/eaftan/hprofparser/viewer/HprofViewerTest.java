package edu.tufts.eaftan.hprofparser.viewer;

import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpClass;
import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpClassInstance;
import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpType;
import java.io.File;
import java.util.List;
import org.junit.Test;

public class HprofViewerTest {

    @Test
    public void test() throws Exception {
//        Thread.sleep(10000);

        long startMillis = System.currentTimeMillis();

        HprofViewer viewer = new HprofViewer(
            new File(HprofViewerTest.class.getResource("/idea.hprof").toURI()));
//            new File(HprofViewerTest.class.getResource("/java.hprof").toURI()));

        long endMillis = System.currentTimeMillis();

//        Thread.sleep(10000);

        List<HeapDumpType> types = viewer.listTypes();

        System.out.println(String.format("Loaded dump with %d types in %.2f seconds",
                                         types.size(), (float) (endMillis - startMillis) / 1000));

        List<HeapDumpClassInstance> instances = viewer.listClassInstances(((HeapDumpClass) types.get(0)).getClassId(),
                                                                          0, 10);

        HeapDumpClassInstance instance = viewer.showClassInstance(instances.get(0).getId());

        System.out.println(String.format("Class %s has instances count: %d\nFirst instance fields: %s",
                                         types.get(0).getName(), types.get(0).getInstancesCount(),
                                         instance.getInstanceFields()));
    }
}