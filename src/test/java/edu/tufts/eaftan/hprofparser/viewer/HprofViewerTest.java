package edu.tufts.eaftan.hprofparser.viewer;

import edu.tufts.eaftan.hprofparser.viewer.HprofViewer.HeapDumpClass;
import java.io.File;
import java.util.List;
import org.junit.Test;

public class HprofViewerTest {

    @Test
    public void test() throws Exception {
        Thread.sleep(10000);
        HprofViewer viewer = new HprofViewer(
            new File(HprofViewerTest.class.getResource("/idea.hprof").toURI()));
//            new File(HprofViewerTest.class.getResource("/java.hprof").toURI()));
        List<HeapDumpClass> classes = viewer.listClasses();
        System.out.println(classes.size());
//        Thread.sleep(10000);
    }
}