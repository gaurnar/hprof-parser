package edu.tufts.eaftan.hprofparser.viewer;

import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpClass;
import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpClassInstance;
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

        List<HeapDumpClass> classes = viewer.listClasses();

        System.out.println(String.format("Loaded dump with %d classes in %.2f seconds",
                                         classes.size(), (float) (endMillis - startMillis) / 1000));

        List<HeapDumpClassInstance> instances = viewer.listClassInstances(classes.get(0).getId(), 0, 10);

        HeapDumpClassInstance instance = viewer.showInstance(instances.get(0).getId());

        System.out.println(String.format("Class %s has instances count: %d\nFirst instance fields: %s",
                                         classes.get(0).getName(), classes.get(0).getInstancesCount(),
                                         instance.getFieldValuePreviews()));
    }
}