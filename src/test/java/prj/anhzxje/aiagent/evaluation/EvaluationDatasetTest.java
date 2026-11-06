package prj.anhzxje.aiagent.evaluation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static prj.anhzxje.aiagent.evaluation.EvaluationTypes.*;

class EvaluationDatasetTest {
    @TempDir Path temp;
    @Test void annotationsMatchPinnedSourceAndFixturesCompileOffline() throws Exception {
        var loader=new EvaluationDataset(Path.of("evaluation"));
        loader.checkSplits("datasets/development.json","datasets/test.json");
        var cases=new ArrayList<>(loader.load("datasets/development.json")); cases.addAll(loader.load("datasets/test.json"));
        assertEquals(14,cases.stream().flatMap(c->c.truth().stream()).filter(d->d.classification()==Label.CONFIRMED_BUG).count());
        assertEquals(6,cases.stream().flatMap(c->c.truth().stream()).filter(d->d.classification()==Label.NON_BUG).count());
        var compiler=javax.tools.ToolProvider.getSystemJavaCompiler(); assertNotNull(compiler);
        for(var fixture:cases) {
            Path output=Files.createDirectory(temp.resolve(fixture.fixture().fixtureId()));
            try(var files=Files.walk(fixture.sourceRoot())) {
                var args=new ArrayList<>(List.of("-proc:none","--release","21","-parameters","-classpath",System.getProperty("java.class.path"),"-d",output.toString()));
                files.filter(p->p.toString().endsWith(".java")).forEach(p->args.add(p.toString()));
                assertEquals(0,compiler.run(null,null,null,args.toArray(String[]::new)),fixture.fixture().fixtureId());
            }
            verifyBehavior(fixture.fixture().fixtureId(),output);
        }
    }
    private void verifyBehavior(String project,Path output) throws Exception {
        try(var classes=new java.net.URLClassLoader(new java.net.URL[]{output.toUri().toURL()},getClass().getClassLoader())) {
            if(project.equals("inventory-api")) {
                Class<?> store=classes.loadClass("fixture.inventory.InventoryStore");
                var constructor=classes.loadClass("fixture.inventory.InventoryController").getDeclaredConstructor(store);constructor.setAccessible(true);
                Object controller=constructor.newInstance(new Object[]{null});
                var available=controller.getClass().getDeclaredMethod("available",int.class,int.class);available.setAccessible(true);
                assertEquals(false,available.invoke(controller,2,5)); // Authored bug: valid stock request is rejected.
                assertEquals(true,available.invoke(controller,6,5));
                var label=controller.getClass().getDeclaredMethod("label",String.class);label.setAccessible(true);
                var failure=assertThrows(java.lang.reflect.InvocationTargetException.class,()->label.invoke(controller,new Object[]{null}));
                assertInstanceOf(NullPointerException.class,failure.getCause());
                Path harmless=Files.writeString(temp.resolve("outside-documents.txt"),"fixture-only");
                var document=controller.getClass().getDeclaredMethod("document",String.class);document.setAccessible(true);
                assertArrayEquals("fixture-only".getBytes(java.nio.charset.StandardCharsets.UTF_8),(byte[])document.invoke(controller,harmless.toString()));
            } else if(project.equals("booking-api")) {
                Class<?> storeType=classes.loadClass("fixture.booking.BookingStore");var constructor=storeType.getDeclaredConstructor();constructor.setAccessible(true);Object store=constructor.newInstance();
                var cancel=storeType.getDeclaredMethod("cancel",long.class);cancel.setAccessible(true);
                assertEquals("CONFIRMED",cancel.invoke(store,100L)); // Authored wrong state transition.
                var controllerConstructor=classes.loadClass("fixture.booking.BookingController").getDeclaredConstructor(storeType);controllerConstructor.setAccessible(true);
                Object controller=controllerConstructor.newInstance(store);var at=controller.getClass().getDeclaredMethod("at",int.class);at.setAccessible(true);
                var failure=assertThrows(java.lang.reflect.InvocationTargetException.class,()->at.invoke(controller,2));
                assertInstanceOf(IndexOutOfBoundsException.class,failure.getCause());
            } else if(project.equals("ledger-api")) {
                Class<?> type=classes.loadClass("fixture.ledger.Ledger");var constructor=type.getDeclaredConstructor();constructor.setAccessible(true);Object ledger=constructor.newInstance();
                var credit=type.getDeclaredMethod("credit",long.class);credit.setAccessible(true);
                try(var pool=java.util.concurrent.Executors.newFixedThreadPool(8)) {
                    var work=new ArrayList<java.util.concurrent.Future<?>>();
                    for(int i=0;i<100;i++) work.add(pool.submit(()->{try{credit.invoke(ledger,1L);}catch(Exception error){throw new AssertionError(error);}}));
                    for(var result:work) result.get();
                }
                var balance=type.getDeclaredMethod("balance");balance.setAccessible(true);assertEquals(100L,balance.invoke(ledger));
                assertInstanceOf(IllegalArgumentException.class,assertThrows(java.lang.reflect.InvocationTargetException.class,()->credit.invoke(ledger,0L)).getCause());
            }
        }
    }
    Path copy() throws Exception {
        Path root=Path.of("evaluation");
        for(String dir:List.of("fixtures","datasets","ground-truth")) try(var paths=Files.walk(root.resolve(dir))) {
            for(Path source:paths.toList()) {
                Path dest=temp.resolve(root.relativize(source));
                if(Files.isDirectory(source)) Files.createDirectories(dest); else Files.copy(source,dest);
            }
        }
        return temp;
    }
    @Test void rejectsSourceDrift() throws Exception {
        var root=copy(); Files.writeString(root.resolve("fixtures/inventory-api/new.txt"),"changed");
        assertThrows(IllegalArgumentException.class,()->new EvaluationDataset(root).load("datasets/development.json"));
    }
    @Test void rejectsMissingTruthAndInvalidDatasetSchema() throws Exception {
        var root=copy(); var file=root.resolve("datasets/development.json");
        var doc=EvaluationFiles.JSON.readTree(file.toFile()); ((com.fasterxml.jackson.databind.node.ObjectNode)doc).put("schemaVersion",99);
        EvaluationFiles.JSON.writeValue(file.toFile(),doc);
        assertThrows(IllegalArgumentException.class,()->new EvaluationDataset(root).load("datasets/development.json"));
        ((com.fasterxml.jackson.databind.node.ObjectNode)doc).put("schemaVersion",1);
        ((com.fasterxml.jackson.databind.node.ObjectNode)doc.at("/fixtures/0")).put("groundTruth","ground-truth/missing.json");
        EvaluationFiles.JSON.writeValue(file.toFile(),doc);
        assertThrows(java.io.IOException.class,()->new EvaluationDataset(root).load("datasets/development.json"));
    }
    @Test void rejectsDuplicateDefectIds() throws Exception {
        var root=copy(); Path file=root.resolve("ground-truth/inventory-api.json");
        var doc=EvaluationFiles.JSON.readTree(file.toFile());
        ((com.fasterxml.jackson.databind.node.ObjectNode)doc.get(1)).put("defectId",doc.get(0).path("defectId").asText());
        EvaluationFiles.JSON.writeValue(file.toFile(),doc);
        assertThrows(IllegalArgumentException.class,()->new EvaluationDataset(root).load("datasets/development.json"));
    }
    @Test void detectsCrossSplitProjectLeakage() throws Exception {
        var root=copy(); var dev=EvaluationFiles.JSON.readTree(root.resolve("datasets/development.json").toFile());
        var test=EvaluationFiles.JSON.readTree(root.resolve("datasets/test.json").toFile());
        ((com.fasterxml.jackson.databind.node.ArrayNode)test.path("fixtures")).set(0,dev.at("/fixtures/0"));
        EvaluationFiles.JSON.writeValue(root.resolve("datasets/test.json").toFile(),test);
        assertThrows(IllegalArgumentException.class,()->new EvaluationDataset(root).checkSplits("datasets/development.json","datasets/test.json"));
    }
}
