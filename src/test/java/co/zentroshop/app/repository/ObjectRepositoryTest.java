package co.zentroshop.app.repository;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ObjectRepositoryTest {

    @TempDir
    Path directory;

    @Test
    void missingAndEmptyFilesRepresentAnEmptyRepository() throws IOException {
        Path file = directory.resolve("rows.bin");
        TestRepository repository = new TestRepository(file);
        assertTrue(repository.getAll().isEmpty());
        Files.createFile(file);
        assertTrue(repository.getAll().isEmpty());
        repository.save(new Row(1, "first"));
        assertEquals("first", repository.getById(1).value);
    }

    @Test
    void crudKeepsInsertUpdateAndDeleteContracts() {
        TestRepository repository = new TestRepository(directory.resolve("rows.bin"));
        repository.save(new Row(1, "first"));
        assertThrows(ObjectRepository.DuplicateEntityException.class,
                () -> repository.save(new Row(1, "duplicate")));
        Row loaded = repository.getById(1);
        loaded.value = "changed";
        assertEquals("first", repository.getById(1).value);
        repository.update(loaded);
        assertEquals("changed", repository.getById(1).value);
        assertThrows(ObjectRepository.EntityNotFoundException.class,
                () -> repository.update(new Row(2, "missing")));
        assertFalse(repository.deleteById(2));
        assertThrows(ObjectRepository.EntityNotFoundException.class,
                () -> repository.delete(new Row(2, "missing")));
        assertTrue(repository.deleteById(1));
        assertTrue(repository.findById(1).isEmpty());
        repository.saveAll(List.of(new Row(3, "third"), new Row(4, "fourth")));
        repository.deleteAll();
        assertEquals(0, repository.count());
    }

    @Test
    void failedSerializationPreservesThePreviousFileAndLeavesNoTemporaryFile() throws IOException {
        Path file = directory.resolve("rows.bin");
        TestRepository repository = new TestRepository(file);
        repository.save(new Row(1, "first"));
        byte[] previous = Files.readAllBytes(file);
        assertThrows(ObjectRepository.StorageException.class,
                () -> repository.save(new Row(2, new Object())));
        assertArrayEquals(previous, Files.readAllBytes(file));
        assertEquals("first", repository.getById(1).value);
        assertEquals(1, repository.count());
        try (var files = Files.list(directory)) {
            assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void failedUpdatePreservesExistingEntity() throws IOException {
        Path file = directory.resolve("rows.bin");
        TestRepository repository = new TestRepository(file);
        repository.save(new Row(1, "first"));
        byte[] previous = Files.readAllBytes(file);
        assertThrows(ObjectRepository.StorageException.class,
                () -> repository.update(new Row(1, new Object())));
        assertArrayEquals(previous, Files.readAllBytes(file));
        assertEquals("first", repository.getById(1).value);
    }

    @Test
    void truncatedFilesAreReportedAndNeverOverwrittenBySave() throws IOException {
        Path file = directory.resolve("rows.bin");
        TestRepository repository = new TestRepository(file);
        repository.save(new Row(1, "first"));
        byte[] truncated = Arrays.copyOf(Files.readAllBytes(file), 6);
        Files.write(file, truncated);
        assertThrows(ObjectRepository.StorageException.class, repository::getAll);
        assertThrows(ObjectRepository.StorageException.class,
                () -> repository.save(new Row(2, "second")));
        assertArrayEquals(truncated, Files.readAllBytes(file));
    }

    @Test
    void invalidSerializedStructuresAreStorageErrors() throws IOException {
        List<Object> invalidDocuments = Arrays.asList(
                null,
                "not a list",
                Arrays.asList((Object) null),
                List.of("wrong entity type"),
                List.of(new Row(null, "missing ID")),
                List.of(new Row(1, "first"), new Row(1, "duplicate ID")));
        for (int i = 0; i < invalidDocuments.size(); i++) {
            Path file = directory.resolve("invalid-" + i + ".bin");
            writeObject(file, invalidDocuments.get(i));
            byte[] previous = Files.readAllBytes(file);
            TestRepository repository = new TestRepository(file);
            assertThrows(ObjectRepository.StorageException.class, repository::getAll,
                    "Invalid document " + i);
            assertThrows(ObjectRepository.StorageException.class,
                    () -> repository.save(new Row(9, "new")));
            assertArrayEquals(previous, Files.readAllBytes(file));
        }
    }

    @Test
    void rejectedBatchesDoNotPartiallyChangeTheFile() throws IOException {
        Path file = directory.resolve("rows.bin");
        TestRepository repository = new TestRepository(file);
        repository.save(new Row(1, "first"));
        byte[] previous = Files.readAllBytes(file);
        assertThrows(ObjectRepository.DuplicateEntityException.class,
                () -> repository.saveAll(List.of(new Row(2, "second"), new Row(1, "duplicate"))));
        assertArrayEquals(previous, Files.readAllBytes(file));
        assertThrows(ObjectRepository.DuplicateEntityException.class,
                () -> repository.saveAll(List.of(new Row(2, "second"), new Row(2, "duplicate"))));
        assertArrayEquals(previous, Files.readAllBytes(file));
        assertThrows(ObjectRepository.EntityNotFoundException.class,
                () -> repository.deleteAll(List.of(new Row(1, "first"), new Row(2, "missing"))));
        assertArrayEquals(previous, Files.readAllBytes(file));
        assertThrows(ObjectRepository.StorageException.class,
                () -> repository.saveAll(List.of(new Row(2, "second"), new Row(3, new Object()))));
        assertArrayEquals(previous, Files.readAllBytes(file));
    }

    @Test
    void pagingReturnsExpectedContentAndMetadata() {
        TestRepository repository = new TestRepository(directory.resolve("rows.bin"));
        repository.saveAll(List.of(new Row(3, "third"), new Row(1, "first"), new Row(2, "second")));
        ObjectRepository.Page<Row> page = repository.findAll(1, 2);
        assertEquals(List.of(2), ids(page.getContent()));
        assertEquals(2, page.getTotalPages());
        assertEquals(3, page.getTotalElements());
        assertTrue(page.hasPrevious());
        assertFalse(page.hasNext());
        assertEquals(List.of(1, 2), ids(repository.findAll(0, 2, Comparator.comparing(row -> row.id)).getContent()));
        assertEquals(List.of(3), ids(repository.filter(row -> row.id > 1).page(0, 1).getContent()));
        assertThrows(UnsupportedOperationException.class, () -> page.getContent().clear());
    }

    @Test
    void largePageNumbersAndSizesDoNotOverflow() {
        TestRepository repository = new TestRepository(directory.resolve("rows.bin"));
        repository.saveAll(List.of(new Row(1, "first"), new Row(2, "second")));
        for (int pageNumber : new int[] {1_073_741_824, Integer.MAX_VALUE}) {
            for (int pageSize : new int[] {2, Integer.MAX_VALUE}) {
                assertTrue(repository.findAll(pageNumber, pageSize).isEmpty());
                assertTrue(repository.findAll(pageNumber, pageSize, Comparator.comparing(row -> row.id)).isEmpty());
                assertTrue(repository.filter(row -> true).page(pageNumber, pageSize).isEmpty());
            }
        }
        assertEquals(2, repository.findAll(0, Integer.MAX_VALUE).getContent().size());
        var lastPage = new ObjectRepository.Page<Row>(List.of(), Integer.MAX_VALUE, 1, 2);
        assertFalse(lastPage.hasNext());
        assertTrue(lastPage.toString().startsWith("Page 2147483648 "));
        assertEquals(Integer.MAX_VALUE,
                new ObjectRepository.Page<Row>(List.of(), 0, 1, Long.MAX_VALUE).getTotalPages());
        assertFalse(new ObjectRepository.Page<Row>(List.of(), Integer.MAX_VALUE, 1, Long.MAX_VALUE).hasNext());
    }

    @Test
    void filterLimitStopsEvaluatingOnceTheRequestedMatchesAreFound() {
        TestRepository repository = new TestRepository(directory.resolve("rows.bin"));
        repository.saveAll(List.of(new Row(1, "first"), new Row(2, "second"), new Row(3, "third")));
        AtomicInteger evaluations = new AtomicInteger();
        var filter = repository.filter(row -> {
            evaluations.incrementAndGet();
            return true;
        });
        assertTrue(filter.limit(0).isEmpty());
        assertEquals(0, evaluations.get());
        assertEquals(List.of(1, 2), ids(filter.limit(2)));
        assertEquals(2, evaluations.get());
        assertThrows(NullPointerException.class, () -> filter.map(null));
        assertEquals(2, evaluations.get());
    }

    @Test
    void pagingRejectsInvalidArguments() {
        TestRepository repository = new TestRepository(directory.resolve("rows.bin"));
        assertThrows(IllegalArgumentException.class, () -> repository.findAll(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> repository.findAll(0, 0));
        assertThrows(IllegalArgumentException.class, () -> repository.findAll(-1, 1, Comparator.comparing(row -> row.id)));
        assertThrows(IllegalArgumentException.class, () -> repository.filter(row -> true).page(0, 0));
    }

    @Test
    @Timeout(30)
    void concurrentInstancesPreserveEveryInsertedRecord() throws Exception {
        Path file = directory.resolve("rows.bin");
        int writers = 4;
        int recordsPerWriter = 12;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(writers);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int writer = 0; writer < writers; writer++) {
                int firstId = writer * recordsPerWriter;
                // Alternating normalized paths exercises locking across equivalent path spellings.
                Path alias = writer % 2 == 0 ? file : directory.resolve(".").resolve("rows.bin");
                TestRepository repository = new TestRepository(alias);
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < recordsPerWriter; i++) {
                        repository.save(new Row(firstId + i, "value"));
                        repository.findAll();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
            List<Integer> ids = ids(new TestRepository(file).getAll());
            assertEquals(writers * recordsPerWriter, ids.size());
            assertEquals(ids.size(), new HashSet<>(ids).size());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    @Timeout(45)
    void separateProcessesPreserveEveryInsertedRecord() throws Exception {
        Path file = directory.resolve("rows.bin");
        Path gate = directory.resolve("start");
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        String java = Path.of(System.getProperty("java.home"), "bin", executable).toString();
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        List<Process> processes = new ArrayList<>();
        List<Path> logs = new ArrayList<>();
        try {
            for (int writer = 0; writer < 2; writer++) {
                Path ready = directory.resolve("ready-" + writer);
                Path log = directory.resolve("writer-" + writer + ".log");
                logs.add(log);
                processes.add(new ProcessBuilder(java, "-cp", classPath,
                        RepositoryWriterProcess.class.getName(), file.toString(),
                        Integer.toString(writer * 16), "16", ready.toString(), gate.toString())
                        .redirectErrorStream(true).redirectOutput(log.toFile()).start());
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (!Files.exists(directory.resolve("ready-0")) || !Files.exists(directory.resolve("ready-1"))) {
                assertTrue(System.nanoTime() < deadline, "Writer processes did not become ready");
                for (int writer = 0; writer < processes.size(); writer++) {
                    assertTrue(processes.get(writer).isAlive(), Files.readString(logs.get(writer)));
                }
                Thread.sleep(10);
            }
            Files.createFile(gate);
            for (int writer = 0; writer < processes.size(); writer++) {
                Process process = processes.get(writer);
                assertTrue(process.waitFor(20, TimeUnit.SECONDS), "Writer process timed out");
                assertEquals(0, process.exitValue(), Files.readString(logs.get(writer)));
            }
            List<Integer> ids = ids(new TestRepository(file).getAll());
            assertEquals(32, ids.size());
            assertEquals(32, new HashSet<>(ids).size());
        } finally {
            for (Process process : processes) {
                if (process.isAlive()) {
                    process.destroyForcibly();
                    process.waitFor(5, TimeUnit.SECONDS);
                }
            }
        }
    }

    private static List<Integer> ids(List<Row> rows) {
        return rows.stream().map(row -> row.id).toList();
    }

    private static void writeObject(Path file, Object object) throws IOException {
        try (ObjectOutputStream stream = new ObjectOutputStream(Files.newOutputStream(file))) {
            stream.writeObject(object);
        }
    }

    static final class Row implements Serializable {
        private static final long serialVersionUID = 1L;
        final Integer id;
        Object value;

        Row(Integer id, Object value) {
            this.id = id;
            this.value = value;
        }
    }

    static final class TestRepository extends ObjectRepository<Row, Integer> {
        TestRepository(Path file) {
            super(file.toString(), row -> row.id);
        }
    }
}
